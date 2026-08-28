package io.joeyang.oms.cluster.node;

import io.aeron.cluster.codecs.MessageHeaderDecoder;
import io.aeron.cluster.codecs.SessionMessageHeaderDecoder;
import io.aeron.logbuffer.FrameDescriptor;
import io.aeron.protocol.DataHeaderFlyweight;
import io.aeron.protocol.HeaderFlyweight;
import io.joeyang.oms.core.memory.MemoryAdvice;
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
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The one decode path for a tape's recorded cluster log: walks the recording segments' data frames,
 * unwraps the cluster session-message framing, and hands each entry to a handler. Both the replayer
 * and the viewer consume tapes through this walk, so the golden tape guards a single decoder.
 *
 * <p>Deliberately strict: a truncated or fragmented frame throws rather than yielding a partial
 * walk, because a tape that half-decodes silently would defeat its purpose as a fixture.
 */
final class TapeWalker {

  /** Reassembly cap: the 64 KB payload door plus headers, with generous headroom. */
  private static final int MAX_ENTRY_LENGTH = 128 * 1024;

  private static final byte UNFRAGMENTED =
      (byte) (FrameDescriptor.BEGIN_FRAG_FLAG | FrameDescriptor.END_FRAG_FLAG);

  private TapeWalker() {}

  /** Receives each decoded log entry in order. */
  interface EntryHandler {

    /**
     * One application message, unwrapped to its payload.
     *
     * @param logPosition byte position of the entry's frame in the recorded log
     * @param timestamp the sequenced cluster timestamp carried by the entry
     * @param buffer buffer holding the application payload
     * @param offset payload start
     * @param length payload length
     */
    void onSessionMessage(
        long logPosition, long timestamp, DirectBuffer buffer, int offset, int length);

    /**
     * Any other cluster log entry (timers, session events, consensus events).
     *
     * @param logPosition byte position of the entry's frame in the recorded log
     * @param templateId the cluster codec template id of the entry
     */
    void onOtherEntry(long logPosition, int templateId);
  }

  /**
   * Walks the recording found in the given archive directory.
   *
   * @param archiveDir an unpacked tape's {@code archive/} directory
   * @param handler receives each entry in log order
   */
  static void walk(final File archiveDir, final EntryHandler handler) {
    walk(archiveDir, handler, null);
  }

  /**
   * Walks the recording, advising each segment mapping before it is read.
   *
   * @param archiveDir an unpacked tape's {@code archive/} directory
   * @param handler receives each entry in log order
   * @param advice applied to each segment mapping at map time, or {@code null} for none
   */
  static void walk(final File archiveDir, final EntryHandler handler, final MemoryAdvice advice) {
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

    final MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
    final SessionMessageHeaderDecoder sessionHeader = new SessionMessageHeaderDecoder();
    // Reassembly scratch for BEGIN/…/END chains. A chain never spans a term (the term
    // appender claims space for every fragment of a message at once), so it can never span
    // a segment either; the walker still checks that contract defensively below.
    final UnsafeBuffer scratch = new UnsafeBuffer(new byte[MAX_ENTRY_LENGTH]);
    int chainLength = 0;
    long chainStartPosition = 0;

    segmentLoop:
    for (final File segment : segments) {
      final long segmentBase = Long.parseLong(segment.getName().split("[-.]")[1]);
      final UnsafeBuffer buffer = map(segment, advice);
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
          if (chainLength > 0) {
            throw new IllegalStateException(
                "fragment chain left open at the zeroed tail of " + segment.getName());
          }
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
          final int bodyOffset = offset + DataHeaderFlyweight.HEADER_LENGTH;
          final int bodyLength = frameLength - DataHeaderFlyweight.HEADER_LENGTH;
          if ((flags & UNFRAGMENTED) == UNFRAGMENTED) {
            if (chainLength > 0) {
              throw new IllegalStateException(
                  "fragment chain interrupted by an unfragmented frame at " + offset);
            }
            deliver(
                handler,
                messageHeader,
                sessionHeader,
                buffer,
                bodyOffset,
                bodyLength,
                segmentBase + offset);
          } else if ((flags & FrameDescriptor.BEGIN_FRAG_FLAG) != 0) {
            if (chainLength > 0) {
              throw new IllegalStateException(
                  "fragment chain reopened before its END, at " + offset);
            }
            chainStartPosition = segmentBase + offset;
            scratch.putBytes(0, buffer, bodyOffset, bodyLength);
            chainLength = bodyLength;
          } else {
            if (chainLength == 0) {
              throw new IllegalStateException(
                  "fragment continuation without an open chain at " + offset);
            }
            if (chainLength + bodyLength > MAX_ENTRY_LENGTH) {
              throw new IllegalStateException(
                  "fragment chain exceeds " + MAX_ENTRY_LENGTH + " bytes at " + offset);
            }
            scratch.putBytes(chainLength, buffer, bodyOffset, bodyLength);
            chainLength += bodyLength;
            if ((flags & FrameDescriptor.END_FRAG_FLAG) != 0) {
              deliver(
                  handler,
                  messageHeader,
                  sessionHeader,
                  scratch,
                  0,
                  chainLength,
                  chainStartPosition);
              chainLength = 0;
            }
          }
        } else if (chainLength > 0) {
          throw new IllegalStateException(
              "fragment chain interrupted by a non-data frame at " + offset);
        }
        offset += alignedLength;
      }
      if (chainLength > 0) {
        throw new IllegalStateException(
            "fragment chain left open at the end of " + segment.getName());
      }
    }
  }

  private static void deliver(
      final EntryHandler handler,
      final MessageHeaderDecoder messageHeader,
      final SessionMessageHeaderDecoder sessionHeader,
      final DirectBuffer buffer,
      final int entryOffset,
      final int entryLength,
      final long logPosition) {
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
          entryLength - messageHeader.encodedLength() - messageHeader.blockLength();
      handler.onSessionMessage(
          logPosition, sessionHeader.timestamp(), buffer, payloadOffset, payloadLength);
    } else {
      handler.onOtherEntry(logPosition, messageHeader.templateId());
    }
  }

  private static UnsafeBuffer map(final File segment, final MemoryAdvice advice) {
    try (FileChannel channel = FileChannel.open(segment.toPath())) {
      final MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
      final UnsafeBuffer buffer = new UnsafeBuffer(mapped);
      if (advice != null) {
        advice.adviseHugePages(buffer.addressOffset(), buffer.capacity());
      }
      return buffer;
    } catch (final IOException e) {
      throw new UncheckedIOException("cannot map " + segment, e);
    }
  }
}
