package io.joeyang.oms.core.time;

/**
 * The only way to read time.
 *
 * <p>Reading a clock directly — {@code System.currentTimeMillis()}, {@code Instant.now()} — is
 * forbidden in deterministic code, because a state machine that does it produces a different result
 * on every replay of the same log. Replay is the product: journal tests, audit reconstruction, and
 * cluster failover all depend on a log determining exactly one outcome. So time is supplied, never
 * ambient.
 *
 * <p>The unit is <strong>nanoseconds since the Unix epoch</strong>. Nanoseconds because the latency
 * budgets are stated in microseconds and a coarser port could not express them; since the epoch
 * rather than a monotonic reading because the value has to mean something in an audit record and be
 * comparable across processes.
 *
 * <p>The method is deliberately not called {@code nanoTime()}. {@link System#nanoTime()} is
 * monotonic and has no epoch, and borrowing the name would invite exactly the wrong assumption.
 *
 * <p>This shadows {@code java.time.Clock} when both are imported. That is accepted: this is the
 * project's clock, and the package name distinguishes it.
 *
 * <p>Callers convert into this unit at the boundary where the source unit is known. Aeron Cluster's
 * timestamp unit is configurable and defaults to milliseconds, so the conversion belongs at the
 * service edge, not inside an implementation that cannot verify what it was given.
 *
 * @see SequencedClock the implementation deterministic code uses
 * @see SystemClock the implementation for everything outside the replay boundary
 * @see FixedClock the test double
 */
@FunctionalInterface
public interface Clock {

  /**
   * Returns the current time.
   *
   * @return nanoseconds since 1970-01-01T00:00:00Z
   */
  long timeNanos();
}
