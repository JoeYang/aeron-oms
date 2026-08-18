package io.joeyang.oms.core.time;

import java.time.Instant;

/**
 * A clock reading the host's wall clock.
 *
 * <p>For code outside the replay boundary only — gateways, launchers, operational logging. Using
 * this inside a state machine destroys replay, which is the one thing the {@link Clock} port exists
 * to prevent.
 *
 * <p>Nothing mechanically stops that misuse yet. Bazel visibility could bar this class from
 * deterministic targets, but there is no cluster service code to protect so far. Tracked in {@code
 * todo/}.
 *
 * <p>Resolution is whatever {@link Instant#now()} provides, typically microseconds on Linux rather
 * than true nanoseconds. The value is exact as an instant; only the trailing digits are not
 * meaningful.
 */
public final class SystemClock implements Clock {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  /** Creates a clock reading the host's wall clock. */
  public SystemClock() {
    // Stateless. Declared so the class documents its own construction.
  }

  @Override
  public long timeNanos() {
    final Instant now = Instant.now();

    return now.getEpochSecond() * NANOS_PER_SECOND + now.getNano();
  }

  @Override
  public String toString() {
    return "SystemClock{}";
  }
}
