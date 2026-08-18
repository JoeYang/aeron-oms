package io.joeyang.oms.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The test double. Its whole value is that it cannot surprise a test. */
class FixedClockTest {

  @Test
  void alwaysReturnsTheValueItWasConstructedWith() {
    final FixedClock clock = new FixedClock(1_700_000_000_000_000_000L);

    assertEquals(1_700_000_000_000_000_000L, clock.timeNanos());
    assertEquals(1_700_000_000_000_000_000L, clock.timeNanos());
    assertEquals(1_700_000_000_000_000_000L, clock.timeNanos());
  }

  @Test
  void doesNotAdvanceWithRealTime() throws InterruptedException {
    final FixedClock clock = new FixedClock(99L);

    Thread.sleep(20);

    assertEquals(99L, clock.timeNanos());
  }

  /**
   * Deliberately unvalidated, unlike {@link SequencedClock}. A test double that rejects awkward
   * values cannot be used to exercise the code paths that handle them.
   */
  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE})
  void acceptsAnyValueIncludingOnesTheSequencedClockRejects(final long value) {
    assertEquals(value, new FixedClock(value).timeNanos());
  }
}
