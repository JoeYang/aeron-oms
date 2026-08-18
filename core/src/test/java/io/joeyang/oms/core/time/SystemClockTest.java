package io.joeyang.oms.core.time;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The wall-clock implementation, for code outside the replay boundary. */
class SystemClockTest {

  private static final long ONE_MINUTE_NANOS = 60L * 1_000_000_000L;

  @Test
  void returnsPlausibleCurrentTime() {
    final Instant before = Instant.now();

    final long reading = new SystemClock().timeNanos();

    final long reference = before.getEpochSecond() * 1_000_000_000L + before.getNano();
    assertTrue(
        Math.abs(reading - reference) < ONE_MINUTE_NANOS,
        "reading " + reading + " is not within a minute of " + reference);
  }

  @Test
  void doesNotGoBackwardsAcrossSuccessiveReads() {
    final SystemClock clock = new SystemClock();
    long previous = clock.timeNanos();

    for (int i = 0; i < 10_000; i++) {
      final long current = clock.timeNanos();

      assertTrue(
          current >= previous, "went backwards at read " + i + ": " + current + " < " + previous);
      previous = current;
    }
  }

  /** Distinguishes it from the deterministic clock: this one is expected to advance by itself. */
  @Test
  void advancesWithoutBeingTold() throws InterruptedException {
    final SystemClock clock = new SystemClock();
    final long first = clock.timeNanos();

    Thread.sleep(20);

    assertTrue(clock.timeNanos() > first, "wall clock did not advance over 20ms");
  }

  @Test
  void isUsableThroughThePort() {
    final Clock clock = new SystemClock();

    assertTrue(clock.timeNanos() > 0L);
  }
}
