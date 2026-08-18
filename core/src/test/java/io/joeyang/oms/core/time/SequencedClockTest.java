package io.joeyang.oms.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The clock deterministic code uses. Every assertion here is really about replay: if any of these
 * fail, two replays of one log can produce different state.
 */
class SequencedClockTest {

  @Test
  void startsAtTheEpoch() {
    assertEquals(0L, new SequencedClock().timeNanos());
  }

  @Test
  void readReflectsTheLastSuppliedTimestamp() {
    final SequencedClock clock = new SequencedClock();

    clock.update(1_700_000_000_000_000_000L);

    assertEquals(1_700_000_000_000_000_000L, clock.timeNanos());
  }

  @Test
  void repeatedReadsAreStable() {
    final SequencedClock clock = new SequencedClock();
    clock.update(42L);

    final long first = clock.timeNanos();
    final long second = clock.timeNanos();
    final long third = clock.timeNanos();

    assertEquals(first, second);
    assertEquals(second, third);
  }

  /**
   * A cluster delivers several messages inside one clock tick, so an equal timestamp is normal
   * operation and must not be rejected.
   */
  @Test
  void acceptsRepeatedTimestamp() {
    final SequencedClock clock = new SequencedClock();
    clock.update(100L);

    clock.update(100L);

    assertEquals(100L, clock.timeNanos());
  }

  @Test
  void rejectsTimestampThatGoesBackwards() {
    final SequencedClock clock = new SequencedClock();
    clock.update(100L);

    final IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> clock.update(99L));

    assertTrue(thrown.getMessage().contains("99"), "message names the rejected value");
    assertTrue(thrown.getMessage().contains("100"), "message names the current value");
  }

  @Test
  void retainsItsValueAfterRejectedUpdate() {
    final SequencedClock clock = new SequencedClock();
    clock.update(100L);

    assertThrows(IllegalArgumentException.class, () -> clock.update(99L));

    assertEquals(100L, clock.timeNanos(), "a rejected update must not corrupt the clock");
  }

  @ParameterizedTest
  @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
  void rejectsTimestampBeforeTheEpoch(final long before) {
    final SequencedClock clock = new SequencedClock();

    assertThrows(IllegalArgumentException.class, () -> clock.update(before));

    assertEquals(0L, clock.timeNanos());
  }

  @Test
  void acceptsTheBoundaryValues() {
    final SequencedClock clock = new SequencedClock();

    clock.update(0L);
    assertEquals(0L, clock.timeNanos());

    clock.update(Long.MAX_VALUE);
    assertEquals(Long.MAX_VALUE, clock.timeNanos());
  }

  /**
   * The determinism assertion. Two clocks fed an identical sequence must agree at every step — this
   * is what makes replaying a log reproduce the original state.
   */
  @Test
  void replayingTheSameTimestampsReproducesTheSameReadings() {
    final long[] sequence = {0L, 5L, 5L, 1_000L, 1_000L, 999_999_999_999L, Long.MAX_VALUE};
    final SequencedClock original = new SequencedClock();
    final SequencedClock replayed = new SequencedClock();

    for (final long timestamp : sequence) {
      original.update(timestamp);
      replayed.update(timestamp);

      assertEquals(original.timeNanos(), replayed.timeNanos(), "diverged at " + timestamp);
    }
  }

  /** A clock that reads an ambient source would drift between these two reads; this one cannot. */
  @Test
  void doesNotAdvanceOnItsOwn() throws InterruptedException {
    final SequencedClock clock = new SequencedClock();
    clock.update(7L);

    Thread.sleep(5);

    assertEquals(7L, clock.timeNanos());
  }
}
