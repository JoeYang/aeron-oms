package io.joeyang.oms.gateway;

/**
 * Outstanding-send accounting for a pipelined sender: at most {@code window} messages in flight,
 * FIFO ack-to-send matching. FIFO is sound because there is one cluster session and egress arrives
 * in sequenced order — the k-th ack answers the k-th send. Any accounting violation throws; a
 * silently mis-matched ack would corrupt golden provenance.
 */
final class SendWindow {

  private final long[] sentAtNanos;
  private long sent;
  private long acked;

  SendWindow(final int window) {
    if (window < 1) {
      throw new IllegalArgumentException("window must be >= 1: " + window);
    }
    this.sentAtNanos = new long[window];
  }

  boolean hasRoom() {
    return sent - acked < sentAtNanos.length;
  }

  void onSent(final long nowNanos) {
    if (!hasRoom()) {
      throw new IllegalStateException(
          "send beyond the window: " + sentAtNanos.length + " already outstanding");
    }
    sentAtNanos[(int) (sent % sentAtNanos.length)] = nowNanos;
    sent++;
  }

  /**
   * Matches the oldest outstanding send.
   *
   * @param nowNanos ack arrival time
   * @return round-trip nanoseconds for the matched send
   */
  long onAck(final long nowNanos) {
    if (acked == sent) {
      throw new IllegalStateException("ack with no outstanding send (acked=" + acked + ")");
    }
    final long rtt = nowNanos - sentAtNanos[(int) (acked % sentAtNanos.length)];
    acked++;
    return rtt;
  }

  int outstanding() {
    return (int) (sent - acked);
  }

  long sent() {
    return sent;
  }

  long acked() {
    return acked;
  }
}
