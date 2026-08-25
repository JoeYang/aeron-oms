package io.joeyang.oms.cluster.node;

import io.aeron.DirectBufferVector;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.Header;
import io.joeyang.oms.cluster.service.OmsClusteredService;
import io.joeyang.oms.sbe.HeartbeatDecoder;
import java.io.File;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;

/**
 * Replays a golden tape's recorded cluster log directly into the bare state machine — no cluster,
 * no media driver. Decoding is {@link TapeWalker}'s job; this class applies each entry to a fresh
 * {@code OmsClusteredService} and captures what it echoes.
 */
public final class TapeReplay {

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
   * OmsClusteredService}, capturing every echoed timestamp.
   *
   * @param archiveDir an unpacked tape's {@code archive/} directory
   * @return the replay outcome
   */
  public static Result replay(final File archiveDir) {
    return replay(archiveDir, true);
  }

  /**
   * Replays the recording found in the given archive directory through a fresh {@code
   * OmsClusteredService}.
   *
   * <p>With {@code captureEchoes} false the echoes are only counted, not stored — the result's
   * {@link Result#echoedTimestamps()} is empty. Count-only verification of a large tape then runs
   * without growing a timestamp list the caller never reads.
   *
   * @param archiveDir an unpacked tape's {@code archive/} directory
   * @param captureEchoes whether to accumulate every echoed timestamp in the result
   * @return the replay outcome
   */
  public static Result replay(final File archiveDir, final boolean captureEchoes) {
    final CapturingSession session = new CapturingSession(captureEchoes);
    final OmsClusteredService service = new OmsClusteredService();
    final Header header = new Header(0, Integer.numberOfTrailingZeros(64 * 1024));

    final class Applier implements TapeWalker.EntryHandler {
      long sessionMessages;
      long otherEntries;

      @Override
      public void onSessionMessage(
          final long logPosition,
          final long timestamp,
          final DirectBuffer buffer,
          final int offset,
          final int length) {
        service.onSessionMessage(session, timestamp, buffer, offset, length, header);
        sessionMessages++;
      }

      @Override
      public void onOtherEntry(final long logPosition, final int templateId) {
        otherEntries++;
      }
    }

    final Applier applier = new Applier();
    final long startNanos = System.nanoTime();
    TapeWalker.walk(archiveDir, applier);
    final long elapsedNanos = System.nanoTime() - startNanos;

    if (session.echoCount != applier.sessionMessages) {
      throw new IllegalStateException(
          "applied "
              + applier.sessionMessages
              + " session messages but captured "
              + session.echoCount
              + " echoes");
    }
    return new Result(
        session.echoCount, applier.otherEntries, session.echoedTimestamps(), elapsedNanos);
  }

  /** Accepts every offer, counts every echo, and optionally captures its sequenced timestamp. */
  private static final class CapturingSession implements ClientSession {

    private final LongArrayList echoed;
    long echoCount;
    private final io.joeyang.oms.sbe.MessageHeaderDecoder echoHeader =
        new io.joeyang.oms.sbe.MessageHeaderDecoder();
    private final HeartbeatDecoder heartbeat = new HeartbeatDecoder();

    CapturingSession(final boolean captureEchoes) {
      this.echoed = captureEchoes ? new LongArrayList() : null;
    }

    long[] echoedTimestamps() {
      return echoed == null ? new long[0] : echoed.toLongArray();
    }

    @Override
    public long offer(final DirectBuffer buffer, final int offset, final int length) {
      echoHeader.wrap(buffer, offset);
      heartbeat.wrap(
          buffer,
          offset + echoHeader.encodedLength(),
          echoHeader.blockLength(),
          echoHeader.version());
      final long timestampNanos = heartbeat.timestampNanos();
      if (echoed != null) {
        echoed.add(timestampNanos);
      }
      echoCount++;
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
