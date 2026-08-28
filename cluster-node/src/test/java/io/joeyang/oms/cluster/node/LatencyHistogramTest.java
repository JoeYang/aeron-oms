package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the preallocated log-linear latency histogram: exact small values, bounded
 * relative error for large ones, exact max, and correct percentile ordering.
 */
class LatencyHistogramTest {

  @Test
  void smallValuesAreExact() {
    final LatencyHistogram histogram = new LatencyHistogram();
    for (int i = 0; i < 100; i++) {
      histogram.record(17);
    }
    assertEquals(17, histogram.valueAtPercentile(50.0));
    assertEquals(17, histogram.valueAtPercentile(99.9));
    assertEquals(17, histogram.max());
    assertEquals(100, histogram.count());
  }

  @Test
  void p9999SeesTheDeepTailThatP999Misses() {
    final LatencyHistogram histogram = new LatencyHistogram();
    for (int i = 0; i < 9_998; i++) {
      histogram.record(20);
    }
    histogram.record(1_000_000);
    histogram.record(1_000_000);
    assertEquals(20, histogram.valueAtPercentile(99.9));
    assertWithin(4, 1_000_000, histogram.valueAtPercentile(99.99));
  }

  @Test
  void percentilesSplitBimodalDistribution() {
    final LatencyHistogram histogram = new LatencyHistogram();
    for (int i = 0; i < 990; i++) {
      histogram.record(100);
    }
    for (int i = 0; i < 10; i++) {
      histogram.record(10_000);
    }

    assertWithin(3, 100, histogram.valueAtPercentile(50.0));
    assertWithin(3, 100, histogram.valueAtPercentile(98.0));
    assertWithin(3, 10_000, histogram.valueAtPercentile(99.5));
    assertEquals(10_000, histogram.max(), "max is tracked exactly");
    assertEquals(1000, histogram.count());
  }

  @Test
  void largeValuesKeepBoundedRelativeError() {
    final LatencyHistogram histogram = new LatencyHistogram();
    histogram.record(1_234_567_890L);
    assertWithin(4, 1_234_567_890L, histogram.valueAtPercentile(50.0));
  }

  private static void assertWithin(final int percent, final long expected, final long actual) {
    final long tolerance = expected * percent / 100;
    assertTrue(
        Math.abs(actual - expected) <= tolerance,
        "expected " + expected + " ±" + percent + "%, got " + actual);
  }
}
