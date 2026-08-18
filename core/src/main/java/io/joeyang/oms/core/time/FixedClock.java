package io.joeyang.oms.core.time;

/**
 * A clock frozen at construction.
 *
 * <p>The test double. Its value is that it cannot surprise a test: assert on an exact time without
 * coordinating updates, and no elapsed wall time changes the answer.
 *
 * <p>Deliberately unvalidated, unlike {@link SequencedClock}. A double that rejects awkward values
 * cannot be used to exercise the code that handles them, so negative and boundary values are
 * accepted here on purpose.
 */
public final class FixedClock implements Clock {

  private final long timeNanos;

  /**
   * Creates a clock frozen at the given time.
   *
   * @param timeNanos the value every read returns; any value is accepted
   */
  public FixedClock(final long timeNanos) {
    this.timeNanos = timeNanos;
  }

  @Override
  public long timeNanos() {
    return timeNanos;
  }

  @Override
  public String toString() {
    return "FixedClock{timeNanos=" + timeNanos + "}";
  }
}
