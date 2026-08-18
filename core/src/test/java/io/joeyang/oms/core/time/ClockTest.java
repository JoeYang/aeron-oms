package io.joeyang.oms.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The port itself. These assert substitutability — the property that lets a test supply a clock
 * without the code under test knowing which one it got.
 */
class ClockTest {

  /** Reads through the port, so the call site cannot see the implementation. */
  private static long readThrough(final Clock clock) {
    return clock.timeNanos();
  }

  @Test
  void lambdaSatisfiesThePort() {
    final Clock clock = () -> 42L;

    assertEquals(42L, readThrough(clock));
  }

  @Test
  void everyImplementationIsInterchangeable() {
    final SequencedClock sequenced = new SequencedClock();
    sequenced.update(7L);
    final List<Clock> clocks = List.of(sequenced, new FixedClock(7L), () -> 7L);

    for (final Clock clock : clocks) {
      assertEquals(7L, readThrough(clock), clock.getClass().getName());
    }
  }

  @Test
  void fixedClockIsValidTestDouble() {
    assertEquals(1_234L, readThrough(new FixedClock(1_234L)));
  }
}
