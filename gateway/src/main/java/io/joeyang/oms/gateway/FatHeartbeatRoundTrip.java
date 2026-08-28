package io.joeyang.oms.gateway;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.logbuffer.Header;
import io.joeyang.oms.core.time.Clock;
import io.joeyang.oms.sbe.FatHeartbeatAckDecoder;
import io.joeyang.oms.sbe.FatHeartbeatEncoder;
import io.joeyang.oms.sbe.MessageHeaderDecoder;
import io.joeyang.oms.sbe.MessageHeaderEncoder;
import java.io.PrintStream;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Sends a stream of FatHeartbeats — 32 KB payloads derived deterministically from the message
 * sequence — and prints each ack: the sequenced timestamp and the checksum the state machine
 * computed. The payload is a function of the sequence alone, never random, because a recorded tape
 * has to mean one exact byte stream and expected checksums must be computable at record time.
 */
final class FatHeartbeatRoundTrip implements RoundTrip {

  static final int PAYLOAD_LENGTH = 32_000;
  private static final long ECHO_TIMEOUT_MS = 10_000;

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final FatHeartbeatAckDecoder ackDecoder = new FatHeartbeatAckDecoder();
  private long echoedTimestamp = Long.MIN_VALUE;
  private long echoedChecksum;

  long echoedTimestamp() {
    return echoedTimestamp;
  }

  long echoedChecksum() {
    return echoedChecksum;
  }

  /**
   * Encodes one FatHeartbeat with the sequence-derived payload.
   *
   * @param buffer destination
   * @param offset destination offset
   * @param clock outbound stamp source (the port, never an ambient read)
   * @param sequence message sequence the payload derives from
   * @param payloadScratch preallocated payload buffer; its length is the payload length
   * @return encoded frame length
   */
  static int encodeFatHeartbeat(
      final MutableDirectBuffer buffer,
      final int offset,
      final Clock clock,
      final long sequence,
      final byte[] payloadScratch) {
    for (int i = 0; i < payloadScratch.length; i++) {
      payloadScratch[i] = (byte) (sequence + i);
    }
    final MessageHeaderEncoder header = new MessageHeaderEncoder();
    final FatHeartbeatEncoder fat = new FatHeartbeatEncoder();
    fat.wrapAndApplyHeader(buffer, offset, header).timestampNanos(clock.timeNanos());
    fat.putPayload(payloadScratch, 0, payloadScratch.length);
    return header.encodedLength() + fat.encodedLength();
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
    if (headerDecoder.templateId() != FatHeartbeatAckDecoder.TEMPLATE_ID) {
      return;
    }
    ackDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());
    echoedTimestamp = ackDecoder.timestampNanos();
    echoedChecksum = ackDecoder.payloadChecksum();
  }

  @Override
  public void run(
      final AeronCluster cluster,
      final Clock clock,
      final int count,
      final long intervalMs,
      final PrintStream out)
      throws InterruptedException {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[PAYLOAD_LENGTH + 64]);
    final byte[] payloadScratch = new byte[PAYLOAD_LENGTH];

    for (int i = 1; i <= count; i++) {
      echoedTimestamp = Long.MIN_VALUE;
      final int length = encodeFatHeartbeat(buffer, 0, clock, i, payloadScratch);

      final long sentAt = System.nanoTime();
      final long deadline = System.currentTimeMillis() + ECHO_TIMEOUT_MS;
      // The first offer is the limit check: a channel whose term length cannot admit a fat
      // message (max message = term/8) throws here, and that must be a loud, named failure
      // before a recording is committed to - never a stall.
      try {
        while (cluster.offer(buffer, 0, length) < 0) {
          checkDeadline(deadline, "ingress offer not accepted");
          Thread.onSpinWait();
        }
      } catch (final IllegalArgumentException | IllegalStateException e) {
        throw new IllegalStateException(
            "fat message of "
                + length
                + " bytes rejected by the ingress channel - "
                + "term lengths must admit term/8 >= message: "
                + e.getMessage(),
            e);
      }
      while (echoedTimestamp == Long.MIN_VALUE) {
        checkDeadline(deadline, "no sequenced ack");
        cluster.pollEgress();
        Thread.onSpinWait();
      }
      final long rttNanos = System.nanoTime() - sentAt;

      out.printf(
          "fat %2d/%d  sequenced=%d checksum=%d  rtt=%.1f us%n",
          i, count, echoedTimestamp, echoedChecksum, rttNanos / 1_000.0);

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
