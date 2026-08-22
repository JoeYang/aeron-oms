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
      final long intervalMs,
      final PrintStream out)
      throws InterruptedException {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

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

      out.printf(
          "heartbeat %2d/%d  sequenced=%d ns  (%s)  rtt=%.1f us%n",
          i, count, echoedNanos, Instant.ofEpochSecond(0, echoedNanos), rttNanos / 1_000.0);

      if (i < count) {
        Thread.sleep(intervalMs);
      }
    }
  }

  private static void checkDeadline(final long deadline, final String what) {
    if (System.currentTimeMillis() > deadline) {
      throw new IllegalStateException(what + " within " + ECHO_TIMEOUT_MS + " ms");
    }
  }
}
