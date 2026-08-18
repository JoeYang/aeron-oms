package io.joeyang.oms.core.time;

/**
 * A clock whose only input is a timestamp that arrived as sequenced input.
 *
 * <p>This is the implementation deterministic code uses. It holds a value and returns it; it never
 * reads an ambient source, so replaying a log through it reproduces every reading exactly.
 *
 * <p>Aeron Cluster hands a service its timestamp on each callback — {@code
 * onSessionMessage(session, timestamp, ...)} — and that value is in the replicated log, which is
 * what makes it reproducible. The owner calls {@link #update(long)} once at the top of each
 * callback, and everything downstream reads {@link #timeNanos()}.
 *
 * <p>Deliberately no Aeron types. A {@code long} is the whole interface to the cluster, so this
 * class and its tests need no media driver and no transport on the compile path.
 *
 * <p>Not thread-safe, and not intended to be. A cluster service is single-threaded by construction;
 * adding synchronisation here would cost the hot path for a concurrency that does not exist.
 *
 * <p><strong>A latch is only as correct as its caller.</strong> This class cannot tell whether the
 * value it was handed came from the log or from a wall clock. No type can enforce that.
 */
public final class SequencedClock implements Clock {

  private long timeNanos;

  /** Creates a clock reading the epoch, meaning no timestamp has been supplied yet. */
  public SequencedClock() {
    this.timeNanos = 0L;
  }

  /**
   * Advances the clock to a timestamp received as sequenced input.
   *
   * <p>An equal timestamp is accepted: a cluster delivers several messages inside one clock tick,
   * and rejecting that would break normal operation. A smaller one is rejected, because it means
   * either mis-wiring or a corrupt log, and a silently accepted regression corrupts state in a way
   * that replays identically and therefore looks correct.
   *
   * <p>Rejection throws rather than returning a status code. The hot-path rule against throwing
   * covers expected runtime conditions such as back-pressure; this is an invariant violation. In
   * correct operation the cost is one predictable branch, and the exception is allocated only when
   * the system is already broken.
   *
   * @param newTimeNanos nanoseconds since the Unix epoch, not before the current value
   * @throws IllegalArgumentException if the timestamp is negative or goes backwards
   */
  public void update(final long newTimeNanos) {
    if (newTimeNanos < 0L) {
      throw new IllegalArgumentException("timestamp is before the epoch: " + newTimeNanos);
    }
    if (newTimeNanos < timeNanos) {
      throw new IllegalArgumentException(
          "timestamp went backwards: " + newTimeNanos + " is before the current " + timeNanos);
    }

    this.timeNanos = newTimeNanos;
  }

  @Override
  public long timeNanos() {
    return timeNanos;
  }

  @Override
  public String toString() {
    return "SequencedClock{timeNanos=" + timeNanos + "}";
  }
}
