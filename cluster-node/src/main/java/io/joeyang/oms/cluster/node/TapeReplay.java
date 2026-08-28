package io.joeyang.oms.cluster.node;

import io.aeron.DirectBufferVector;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.Header;
import io.joeyang.oms.cluster.service.IncrementalPayloadChecksum;
import io.joeyang.oms.cluster.service.OmsClusteredService;
import io.joeyang.oms.core.memory.MemoryAdvice;
import io.joeyang.oms.sbe.FatHeartbeatAckDecoder;
import io.joeyang.oms.sbe.FatHeartbeatDecoder;
import io.joeyang.oms.sbe.HeartbeatDecoder;
import io.joeyang.oms.sbe.MessageHeaderDecoder;
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
      long heartbeats,
      long otherEntries,
      long[] echoedTimestamps,
      long[] echoedChecksums,
      long elapsedNanos) {}

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
    return replay(archiveDir, captureEchoes, null);
  }

  /**
   * Replays with an optional apply-latency histogram: when non-null, each state-machine apply is
   * timed with two {@code System.nanoTime()} reads and recorded. The timing itself costs a few tens
   * of nanoseconds per message — a latency view, not a throughput benchmark.
   *
   * @param archiveDir an unpacked tape's {@code archive/} directory
   * @param captureEchoes whether to accumulate every echoed timestamp in the result
   * @param latency histogram to record per-apply nanoseconds into, or null
   * @return the replay outcome
   */
  public static Result replay(
      final File archiveDir, final boolean captureEchoes, final LatencyHistogram latency) {
    return replay(archiveDir, captureEchoes, latency, null);
  }

  /**
   * Replays with optional latency capture and optional memory advice on the tape mapping.
   *
   * @param archiveDir an unpacked tape's {@code archive/} directory
   * @param captureEchoes whether echoed timestamps are accumulated
   * @param latency histogram receiving per-apply nanoseconds, or {@code null}
   * @param advice applied to each segment mapping at map time, or {@code null}
   * @return the replay result
   */
  public static Result replay(
      final File archiveDir,
      final boolean captureEchoes,
      final LatencyHistogram latency,
      final MemoryAdvice advice) {
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
        if (latency != null) {
          final long before = System.nanoTime();
          service.onSessionMessage(session, timestamp, buffer, offset, length, header);
          latency.record(System.nanoTime() - before);
        } else {
          service.onSessionMessage(session, timestamp, buffer, offset, length, header);
        }
        sessionMessages++;
      }

      @Override
      public void onOtherEntry(final long logPosition, final int templateId) {
        otherEntries++;
      }
    }

    final Applier applier = new Applier();
    final long startNanos = System.nanoTime();
    TapeWalker.walk(archiveDir, applier, advice);
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
        session.echoCount,
        applier.otherEntries,
        session.echoedTimestamps(),
        session.echoedChecksums(),
        elapsedNanos);
  }

  /**
   * Replays with fat chains checksummed in place — no reassembly copy. Fragmented session messages
   * are consumed as slices: the app headers decode from the first slice, the payload checksums
   * incrementally across fragment boundaries, and the captured timestamp and checksum are exactly
   * what the service's ack would carry. Unfragmented entries still apply through the real service,
   * so echo capture stays in log order across both paths.
   *
   * @param archiveDir an unpacked tape's {@code archive/} directory
   * @param captureEchoes whether to accumulate every echoed timestamp and checksum
   * @return the replay outcome, shape-identical to {@link #replay(File, boolean)}
   */
  public static Result replayZeroCopy(final File archiveDir, final boolean captureEchoes) {
    final CapturingSession session = new CapturingSession(captureEchoes);
    final OmsClusteredService service = new OmsClusteredService();
    final Header header = new Header(0, Integer.numberOfTrailingZeros(64 * 1024));
    final MessageHeaderDecoder appHeader = new MessageHeaderDecoder();
    final FatHeartbeatDecoder fatDecoder = new FatHeartbeatDecoder();
    final IncrementalPayloadChecksum checksum = new IncrementalPayloadChecksum();

    final class ZeroCopyApplier implements TapeWalker.EntryHandler, TapeWalker.ChainSliceHandler {
      long sessionMessages;
      long otherEntries;
      boolean expectingFirstSlice;
      long chainTimestamp;
      int declaredPayload;
      long seenPayload;

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

      @Override
      public void onFragmentedSessionMessageStart(final long logPosition, final long timestamp) {
        chainTimestamp = timestamp;
        expectingFirstSlice = true;
        declaredPayload = -1;
        seenPayload = 0;
        checksum.reset();
      }

      @Override
      public void onPayloadSlice(final DirectBuffer buffer, final int offset, final int length) {
        if (!expectingFirstSlice) {
          checksum.update(buffer, offset, length);
          seenPayload += length;
          return;
        }
        expectingFirstSlice = false;
        if (length < MessageHeaderDecoder.ENCODED_LENGTH) {
          throw new IllegalStateException(
              "first slice of " + length + " bytes cannot hold the app message header");
        }
        appHeader.wrap(buffer, offset);
        if (appHeader.schemaId() != FatHeartbeatDecoder.SCHEMA_ID
            || appHeader.templateId() != FatHeartbeatDecoder.TEMPLATE_ID) {
          throw new IllegalStateException(
              "unexpected fragmented template " + appHeader.templateId());
        }
        final int headerBytes =
            appHeader.encodedLength()
                + appHeader.blockLength()
                + FatHeartbeatDecoder.payloadHeaderLength();
        if (length < headerBytes) {
          throw new IllegalStateException(
              "first slice of " + length + " bytes cannot hold the fat headers");
        }
        fatDecoder.wrap(
            buffer,
            offset + appHeader.encodedLength(),
            appHeader.blockLength(),
            appHeader.version());
        declaredPayload = fatDecoder.payloadLength();
        final int payloadStart = offset + headerBytes;
        final int payloadInFirst = length - headerBytes;
        checksum.update(buffer, payloadStart, payloadInFirst);
        seenPayload += payloadInFirst;
      }

      @Override
      public void onFragmentedSessionMessageEnd() {
        if (seenPayload != declaredPayload) {
          throw new IllegalStateException(
              "payload length mismatch: declared "
                  + declaredPayload
                  + " but the chain carried "
                  + seenPayload);
        }
        session.captureDirect(chainTimestamp, checksum.finish());
        checksum.reset();
        sessionMessages++;
      }
    }

    final ZeroCopyApplier applier = new ZeroCopyApplier();
    final long startNanos = System.nanoTime();
    TapeWalker.walk(archiveDir, applier, applier);
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
        session.echoCount,
        applier.otherEntries,
        session.echoedTimestamps(),
        session.echoedChecksums(),
        elapsedNanos);
  }

  /** Accepts every offer, counts every echo, and optionally captures its sequenced timestamp. */
  private static final class CapturingSession implements ClientSession {

    private final LongArrayList echoed;
    private final LongArrayList checksums;
    long echoCount;
    private final io.joeyang.oms.sbe.MessageHeaderDecoder echoHeader =
        new io.joeyang.oms.sbe.MessageHeaderDecoder();
    private final HeartbeatDecoder heartbeat = new HeartbeatDecoder();
    private final FatHeartbeatAckDecoder fatAck = new FatHeartbeatAckDecoder();

    CapturingSession(final boolean captureEchoes) {
      this.echoed = captureEchoes ? new LongArrayList() : null;
      this.checksums = captureEchoes ? new LongArrayList() : null;
    }

    long[] echoedTimestamps() {
      return echoed == null ? new long[0] : echoed.toLongArray();
    }

    long[] echoedChecksums() {
      return checksums == null ? new long[0] : checksums.toLongArray();
    }

    /** Records an echo the zero-copy path computed itself, keeping capture in log order. */
    void captureDirect(final long timestamp, final long checksum) {
      if (echoed != null) {
        echoed.add(timestamp);
        checksums.add(checksum);
      }
      echoCount++;
    }

    @Override
    public long offer(final DirectBuffer buffer, final int offset, final int length) {
      echoHeader.wrap(buffer, offset);
      if (echoHeader.templateId() == FatHeartbeatAckDecoder.TEMPLATE_ID) {
        fatAck.wrap(
            buffer,
            offset + echoHeader.encodedLength(),
            echoHeader.blockLength(),
            echoHeader.version());
        if (echoed != null) {
          echoed.add(fatAck.timestampNanos());
          checksums.add(fatAck.payloadChecksum());
        }
      } else {
        heartbeat.wrap(
            buffer,
            offset + echoHeader.encodedLength(),
            echoHeader.blockLength(),
            echoHeader.version());
        if (echoed != null) {
          echoed.add(heartbeat.timestampNanos());
        }
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
