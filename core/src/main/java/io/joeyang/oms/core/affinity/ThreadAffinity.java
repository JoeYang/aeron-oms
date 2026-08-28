package io.joeyang.oms.core.affinity;

/**
 * Pins the calling thread onto one CPU.
 *
 * <p>Busy-spin without pinning is wasted effort: a spinning thread the scheduler migrates pays
 * exactly the cache and TLB cost it was spinning to avoid. The launch-layer {@code taskset} mask
 * cannot do this job — it starts every JVM thread on the housekeeping cores, and was measured (PR
 * #25) to hurt the tail when used alone. Each hot thread must move <em>itself</em> onto its
 * isolated core, from inside its own {@code Runnable}, because affinity set from another thread — a
 * {@code ThreadFactory} body, for example — pins the creating thread instead.
 *
 * <p>The port is deliberately this narrow: no mask queries, no multi-CPU sets, no "unpin". The
 * project pins a thread to exactly one core for its lifetime or not at all; a wider interface would
 * be scaffolding.
 *
 * <p>Implementations verify the pin by reading the affinity back and fail fast on mismatch — a
 * silently unpinned duty cycle looks healthy and misses every budget. Failure is an exception by
 * design: pinning happens at thread start, before the hot path's no-throw rule applies.
 *
 * <p>Only platform threads may be pinned. A virtual thread does not own an OS thread, so any
 * affinity set from one is lost at the next mount.
 */
@FunctionalInterface
public interface ThreadAffinity {

  /**
   * Pins the calling thread to the given CPU and verifies the pin took effect.
   *
   * @param cpu zero-based CPU id as the kernel numbers them
   * @throws IllegalArgumentException if the CPU does not exist on this machine
   * @throws IllegalStateException if the pin fails or cannot be verified
   */
  void pinCurrentThread(int cpu);
}
