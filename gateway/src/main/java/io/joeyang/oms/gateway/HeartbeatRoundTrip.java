package io.joeyang.oms.gateway;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.logbuffer.Header;
import io.joeyang.oms.core.time.Clock;
import io.joeyang.oms.sbe.HeartbeatDecoder;
import io.joeyang.oms.sbe.HeartbeatEncoder;
import io.joeyang.oms.sbe.MessageHeaderDecoder;
import io.joeyang.oms.sbe.MessageHeaderEncoder;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Sends a stream of Heartbeats into the cluster and prints each sequenced echo — one line per
 * message through the log, with the round-trip time.
 *
 * <p>Outbound timestamps come through the {@link Clock} port, never from a scattered ambient read;
 * the port is what keeps the time source swappable in tests.
 */
final class HeartbeatRoundTrip implements EgressListener {

  private static final long ECHO_TIMEOUT_MS = 10_000;

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final HeartbeatDecoder heartbeatDecoder = new HeartbeatDecoder();
  private long echoedNanos = Long.MIN_VALUE;

  static int encodeHeartbeat(
      final MutableDirectBuffer buffer, final int offset, final Clock clock) {
    final MessageHeaderEncoder header = new MessageHeaderEncoder();
    new HeartbeatEncoder()
        .wrapAndApplyHeader(buffer, offset, header)
        .timestampNanos(clock.timeNanos());
    return header.encodedLength() + HeartbeatEncoder.BLOCK_LENGTH;
  }

  @Override
  public void onMessage(
      final long clusterSessionId,
      final long timestamp,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final Header header) {
    headerDecoder.wrap(buffer, offset);
    heartbeatDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());
    echoedNanos = heartbeatDecoder.timestampNanos();
  }

  void run(
      final AeronCluster cluster,
      final Clock clock,
      final int count,
      final int warmup,
      final long intervalMs,
      final PrintStream out)
      throws InterruptedException {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
    final long[] rttNanosByMessage = new long[count];

    for (int i = 1; i <= count; i++) {
      echoedNanos = Long.MIN_VALUE;
      final int length = encodeHeartbeat(buffer, 0, clock);

      final long sentAt = System.nanoTime();
      final long deadline = System.currentTimeMillis() + ECHO_TIMEOUT_MS;
      while (cluster.offer(buffer, 0, length) < 0) {
        checkDeadline(deadline, "ingress offer not accepted");
        Thread.onSpinWait();
      }
      while (echoedNanos == Long.MIN_VALUE) {
        checkDeadline(deadline, "no sequenced echo");
        cluster.pollEgress();
        Thread.onSpinWait();
      }
      final long rttNanos = System.nanoTime() - sentAt;
      rttNanosByMessage[i - 1] = rttNanos;

      out.printf(
          "heartbeat %2d/%d  sequenced=%d ns  (%s)  rtt=%.1f us%n",
          i, count, echoedNanos, Instant.ofEpochSecond(0, echoedNanos), rttNanos / 1_000.0);

      if (i < count) {
        Thread.sleep(intervalMs);
      }
    }

    if (warmup > 0 && warmup < count) {
      out.println(summarize(Arrays.copyOfRange(rttNanosByMessage, warmup, count)));
    }
  }

  /**
   * Percentiles over the measured window, warmup already excluded. Cold samples measure the JIT and
   * parked duty cycles, not the system — mixing them in is how a benchmark lies.
   */
  static String summarize(final long[] measuredRttNanos) {
    final long[] sorted = measuredRttNanos.clone();
    Arrays.sort(sorted);
    return String.format(
        Locale.ROOT,
        "bench: n=%d min=%.1f p50=%.1f p90=%.1f p99=%.1f max=%.1f (us)",
        sorted.length,
        sorted[0] / 1_000.0,
        percentile(sorted, 50) / 1_000.0,
        percentile(sorted, 90) / 1_000.0,
        percentile(sorted, 99) / 1_000.0,
        sorted[sorted.length - 1] / 1_000.0);
  }

  private static long percentile(final long[] sorted, final int p) {
    return sorted[Math.min(sorted.length - 1, p * sorted.length / 100)];
  }

  private static void checkDeadline(final long deadline, final String what) {
    if (System.currentTimeMillis() > deadline) {
      throw new IllegalStateException(what + " within " + ECHO_TIMEOUT_MS + " ms");
    }
  }
}
