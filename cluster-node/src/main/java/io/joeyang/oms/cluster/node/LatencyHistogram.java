package io.joeyang.oms.cluster.node;

/**
 * Preallocated log-linear histogram for nanosecond latencies: 64 power-of-two buckets, each split
 * into 32 linear sub-buckets (~3% worst-case relative error), values below 32 ns exact, maximum
 * tracked exactly. No allocation after construction — safe to use once per applied message.
 */
final class LatencyHistogram {

  private static final int SUB_BUCKET_BITS = 5;
  private static final int SUB_BUCKETS = 1 << SUB_BUCKET_BITS;

  private final long[] counts = new long[64 * SUB_BUCKETS];
  private long count;
  private long max;

  void record(final long nanos) {
    final long value = Math.max(nanos, 0);
    count++;
    if (value > max) {
      max = value;
    }
    counts[indexOf(value)]++;
  }

  long count() {
    return count;
  }

  long max() {
    return max;
  }

  /**
   * Value at the given percentile — the lower bound of the sub-bucket holding it.
   *
   * @param percentile percentile in (0, 100]
   * @return representative latency in nanoseconds
   */
  long valueAtPercentile(final double percentile) {
    final long rank = Math.max(1, (long) Math.ceil(percentile / 100.0 * count));
    long seen = 0;
    for (int i = 0; i < counts.length; i++) {
      seen += counts[i];
      if (seen >= rank) {
        return valueOf(i);
      }
    }
    return max;
  }

  private static int indexOf(final long value) {
    if (value < SUB_BUCKETS) {
      return (int) value;
    }
    final int bucket = 63 - Long.numberOfLeadingZeros(value);
    final int sub = (int) (value >>> (bucket - SUB_BUCKET_BITS)) & (SUB_BUCKETS - 1);
    return (bucket << SUB_BUCKET_BITS) | sub;
  }

  private static long valueOf(final int index) {
    if (index < SUB_BUCKETS) {
      return index;
    }
    final int bucket = index >>> SUB_BUCKET_BITS;
    final int sub = index & (SUB_BUCKETS - 1);
    return (1L << bucket) + ((long) sub << (bucket - SUB_BUCKET_BITS));
  }
}
