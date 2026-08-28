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
 *
 * <p>Sends pipeline within a bounded {@link SendWindow}: up to {@code window} messages outstanding,
 * acks matched FIFO (one session, sequenced egress), one golden line printed per ack in sequenced
 * order. At window 1 this degenerates to the original closed loop — one in flight, ack before the
 * next send.
 */
final class FatHeartbeatRoundTrip implements RoundTrip {

  static final int PAYLOAD_LENGTH = 32_000;
  private static final long PROGRESS_TIMEOUT_MS = 10_000;

  private final int windowSize;
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final FatHeartbeatAckDecoder ackDecoder = new FatHeartbeatAckDecoder();
  private SendWindow window;
  private PrintStream out;
  private int totalCount;
  private long echoedTimestamp = Long.MIN_VALUE;
  private long echoedChecksum;

  FatHeartbeatRoundTrip() {
    this(1);
  }

  FatHeartbeatRoundTrip(final int windowSize) {
    this.windowSize = windowSize;
  }

  long echoedTimestamp() {
    return echoedTimestamp;
  }

  long echoedChecksum() {
    return echoedChecksum;
  }

  /** Arms the window and the ack printer for a run; package-private for the ack-path tests. */
  void beginRun(final PrintStream out, final int count) {
    this.window = new SendWindow(windowSize);
    this.out = out;
    this.totalCount = count;
  }

  /** Records one successful offer against the window. */
  void noteSent() {
    window.onSent(System.nanoTime());
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
    final long rttNanos = window.onAck(System.nanoTime());
    out.printf(
        "fat %2d/%d  sequenced=%d checksum=%d  rtt=%.1f us%n",
        window.acked(), totalCount, echoedTimestamp, echoedChecksum, rttNanos / 1_000.0);
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
    beginRun(out, count);

    long sequence = 0;
    long deadline = System.currentTimeMillis() + PROGRESS_TIMEOUT_MS;
    while (window.acked() < count) {
      if (sequence < count && window.hasRoom()) {
        final int length = encodeFatHeartbeat(buffer, 0, clock, sequence + 1, payloadScratch);
        // The first offer is the limit check: a channel whose term length cannot admit a fat
        // message (max message = term/8) throws here, and that must be a loud, named failure
        // before a recording is committed to - never a stall.
        try {
          while (cluster.offer(buffer, 0, length) < 0) {
            deadline = pollForProgress(cluster, deadline);
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
        noteSent();
        sequence++;
        deadline = System.currentTimeMillis() + PROGRESS_TIMEOUT_MS;
        if (sequence < count && intervalMs > 0) {
          Thread.sleep(intervalMs);
        }
      } else {
        deadline = pollForProgress(cluster, deadline);
      }
    }
  }

  /** Polls egress; any ack refreshes the deadline, no progress checks it. */
  private long pollForProgress(final AeronCluster cluster, final long deadline) {
    final long ackedBefore = window.acked();
    cluster.pollEgress();
    if (window.acked() > ackedBefore) {
      return System.currentTimeMillis() + PROGRESS_TIMEOUT_MS;
    }
    if (System.currentTimeMillis() > deadline) {
      throw new IllegalStateException(
          "no offer accepted and no ack within "
              + PROGRESS_TIMEOUT_MS
              + " ms ("
              + window.acked()
              + "/"
              + count()
              + " acked, "
              + window.outstanding()
              + " outstanding)");
    }
    Thread.onSpinWait();
    return deadline;
  }

  private int count() {
    return totalCount;
  }
}
