package io.joeyang.oms.cluster.node;

import io.aeron.DirectBufferVector;
import io.aeron.cluster.codecs.MessageHeaderDecoder;
import io.aeron.cluster.codecs.SessionMessageHeaderDecoder;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FrameDescriptor;
import io.aeron.logbuffer.Header;
import io.aeron.protocol.DataHeaderFlyweight;
import io.aeron.protocol.HeaderFlyweight;
import io.joeyang.oms.cluster.service.OmsClusteredService;
import io.joeyang.oms.sbe.HeartbeatDecoder;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Comparator;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Replays a golden tape's recorded cluster log directly into the bare state machine — no cluster,
 * no media driver — by walking the recording segment's data frames and unwrapping the cluster's
 * session-message framing to reach the application payload.
 *
 * <p>The reader is deliberately strict: a fragmented or truncated frame throws rather than
 * returning a partial replay, because a golden tape that half-replays silently would defeat its
 * purpose as a regression fixture.
 */
public final class TapeReplay {

  private static final byte UNFRAGMENTED =
      (byte) (FrameDescriptor.BEGIN_FRAG_FLAG | FrameDescriptor.END_FRAG_FLAG);

  private TapeReplay() {}

  /**
   * Outcome of one replay.
   *
   * @param heartbeats number of heartbeat echoes the service produced
   * @param otherEntries cluster log entries that are not session messages (timers, events)
   * @param echoedTimestamps the sequenced timestamps the service echoed, in apply order
   * @param elapsedNanos wall time spent decoding and applying
   */
  public record Result(
      long heartbeats, long otherEntries, long[] echoedTimestamps, long elapsedNanos) {}

  /**
   * Replays the recording found in the given archive directory through a fresh {@code
   * OmsClusteredService}.
   *
   * @param archiveDir an unpacked tape's {@code archive/} directory
   * @return the replay outcome
   */
  public static Result replay(final File archiveDir) {
    final File[] segments = archiveDir.listFiles((dir, name) -> name.endsWith(".rec"));
    if (segments == null || segments.length == 0) {
      throw new IllegalStateException("no recording segments in " + archiveDir);
    }
    final long recordings =
        Arrays.stream(segments).map(f -> f.getName().split("-")[0]).distinct().count();
    if (recordings != 1) {
      throw new IllegalStateException("expected one recording, found " + recordings);
    }
    Arrays.sort(
        segments, Comparator.comparingLong(f -> Long.parseLong(f.getName().split("[-.]")[1])));

    final CapturingSession session = new CapturingSession();
    final OmsClusteredService service = new OmsClusteredService();
    final Header header = new Header(0, Integer.numberOfTrailingZeros(64 * 1024));
    final MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
    final SessionMessageHeaderDecoder sessionHeader = new SessionMessageHeaderDecoder();

    long sessionMessages = 0;
    long otherEntries = 0;
    final long startNanos = System.nanoTime();
    segmentLoop:
    for (final File segment : segments) {
      final UnsafeBuffer buffer = map(segment);
      final int capacity = buffer.capacity();
      int offset = 0;
      while (offset < capacity) {
        if (capacity - offset < DataHeaderFlyweight.HEADER_LENGTH) {
          throw new IllegalStateException(
              "truncated: partial frame header at " + offset + " in " + segment.getName());
        }
        final int frameLength =
            buffer.getInt(
                offset + HeaderFlyweight.FRAME_LENGTH_FIELD_OFFSET, ByteOrder.LITTLE_ENDIAN);
        if (frameLength == 0) {
          break segmentLoop; // zeroed tail: the recording ends here
        }
        final int alignedLength = BitUtil.align(frameLength, FrameDescriptor.FRAME_ALIGNMENT);
        if (frameLength < DataHeaderFlyweight.HEADER_LENGTH || offset + alignedLength > capacity) {
          throw new IllegalStateException(
              "truncated: frame at " + offset + " extends beyond " + segment.getName());
        }
        final int type =
            buffer.getShort(offset + HeaderFlyweight.TYPE_FIELD_OFFSET, ByteOrder.LITTLE_ENDIAN)
                & 0xFFFF;
        if (type == HeaderFlyweight.HDR_TYPE_DATA) {
          final byte flags = buffer.getByte(offset + HeaderFlyweight.FLAGS_FIELD_OFFSET);
          if ((flags & UNFRAGMENTED) != UNFRAGMENTED) {
            throw new IllegalStateException(
                "fragmented frame at " + offset + " — not supported by this reader");
          }
          final int entryOffset = offset + DataHeaderFlyweight.HEADER_LENGTH;
          messageHeader.wrap(buffer, entryOffset);
          if (messageHeader.schemaId() == MessageHeaderDecoder.SCHEMA_ID
              && messageHeader.templateId() == SessionMessageHeaderDecoder.TEMPLATE_ID) {
            sessionHeader.wrap(
                buffer,
                entryOffset + messageHeader.encodedLength(),
                messageHeader.blockLength(),
                messageHeader.version());
            final int payloadOffset =
                entryOffset + messageHeader.encodedLength() + messageHeader.blockLength();
            final int payloadLength =
                frameLength
                    - DataHeaderFlyweight.HEADER_LENGTH
                    - messageHeader.encodedLength()
                    - messageHeader.blockLength();
            service.onSessionMessage(
                session, sessionHeader.timestamp(), buffer, payloadOffset, payloadLength, header);
            sessionMessages++;
          } else {
            otherEntries++;
          }
        }
        offset += alignedLength;
      }
    }
    final long elapsedNanos = System.nanoTime() - startNanos;

    if (session.echoed.size() != sessionMessages) {
      throw new IllegalStateException(
          "applied "
              + sessionMessages
              + " session messages but captured "
              + session.echoed.size()
              + " echoes");
    }
    return new Result(
        session.echoed.size(), otherEntries, session.echoed.toLongArray(), elapsedNanos);
  }

  private static UnsafeBuffer map(final File segment) {
    try (FileChannel channel = FileChannel.open(segment.toPath())) {
      final MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
      return new UnsafeBuffer(mapped);
    } catch (final IOException e) {
      throw new UncheckedIOException("cannot map " + segment, e);
    }
  }

  /** Accepts every offer and captures the echoed sequenced timestamp. */
  private static final class CapturingSession implements ClientSession {

    final LongArrayList echoed = new LongArrayList();
    private final io.joeyang.oms.sbe.MessageHeaderDecoder echoHeader =
        new io.joeyang.oms.sbe.MessageHeaderDecoder();
    private final HeartbeatDecoder heartbeat = new HeartbeatDecoder();

    @Override
    public long offer(final DirectBuffer buffer, final int offset, final int length) {
      echoHeader.wrap(buffer, offset);
      heartbeat.wrap(
          buffer,
          offset + echoHeader.encodedLength(),
          echoHeader.blockLength(),
          echoHeader.version());
      echoed.add(heartbeat.timestampNanos());
      return length;
    }

    @Override
    public long offer(final DirectBufferVector[] vectors) {
      throw new UnsupportedOperationException("vectored offer is not part of the replay contract");
    }

    @Override
    public long tryClaim(final int length, final BufferClaim bufferClaim) {
      throw new UnsupportedOperationException("tryClaim is not part of the replay contract");
    }

    @Override
    public long id() {
      return 0;
    }

    @Override
    public int responseStreamId() {
      return 0;
    }

    @Override
    public String responseChannel() {
      return "aeron:ipc";
    }

    @Override
    public byte[] encodedPrincipal() {
      return new byte[0];
    }

    @Override
    public void close() {}

    @Override
    public boolean isClosing() {
      return false;
    }
  }
}
