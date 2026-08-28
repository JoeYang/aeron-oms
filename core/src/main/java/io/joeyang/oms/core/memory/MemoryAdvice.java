package io.joeyang.oms.core.memory;

/**
 * Advises the kernel about a mapped memory range.
 *
 * <p>The tape replay maps gigabytes of journal as 4 KB pages; the TLB holds a few thousand entries,
 * so the walk pays a page-table walk every few hundred nanoseconds of applies — the measured
 * p99.9/p99.99 band. Huge pages cut the page count 512×, and with the kernel's THP mode at {@code
 * madvise}, {@code MADV_HUGEPAGE} on the mapping is the one hook that turns them on.
 *
 * <p>The port is deliberately this narrow: one advice, for the one range-shaped need this project
 * has. Advice is a request, not a guarantee — whether the kernel actually backs the range with huge
 * pages is verified by the caller from {@code /proc/self/smaps}, mirroring the affinity port's
 * read-back rule: an optimisation that cannot be verified must not be reported as present.
 *
 * <p>Failure is an exception by design: advice happens at map time, before the hot path's no-throw
 * rule applies.
 */
@FunctionalInterface
public interface MemoryAdvice {

  /**
   * Asks the kernel to back the given mapped range with huge pages.
   *
   * @param address page-aligned start of a mapped range
   * @param length length of the range in bytes
   * @throws IllegalArgumentException if the range is not mapped or not page-aligned
   * @throws IllegalStateException if the advice fails for any other reason
   */
  void adviseHugePages(long address, long length);
}
