package io.joeyang.oms.core.affinity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests for the FFM {@code sched_setaffinity} binding. Pins run inside a throwaway platform thread
 * so the test runner's own affinity is never narrowed, and verification reads {@code
 * /proc/thread-self/status} — kernel truth, independent of the code under test.
 */
class LinuxThreadAffinityTest {

  private static String cpusAllowedList() throws Exception {
    for (final String line : Files.readAllLines(Path.of("/proc/thread-self/status"))) {
      if (line.startsWith("Cpus_allowed_list:")) {
        return line.substring("Cpus_allowed_list:".length()).trim();
      }
    }
    throw new AssertionError("no Cpus_allowed_list in /proc/thread-self/status");
  }

  private static void inThrowawayThread(final Runnable body) throws Exception {
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final Thread thread =
        new Thread(
            () -> {
              try {
                body.run();
              } catch (final Throwable t) {
                failure.set(t);
              }
            },
            "affinity-test");
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("pinned-thread body failed", failure.get());
    }
  }

  @Test
  void pinsTheCallingThreadToExactlyTheRequestedCpu() throws Exception {
    inThrowawayThread(
        () -> {
          new LinuxThreadAffinity().pinCurrentThread(0);
          try {
            assertEquals("0", cpusAllowedList());
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void nonexistentCpuIsRejectedByName() {
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new LinuxThreadAffinity().pinCurrentThread(10_000));
    assertTrue(e.getMessage().contains("10000"), e.getMessage());
  }

  @Test
  void absentButRepresentableCpuIsRejectedByTheKernel() {
    // 100 fits in the mask, so this rejection must come from sched_setaffinity's EINVAL,
    // exercising the captured-errno path rather than the upfront bounds check.
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> new LinuxThreadAffinity().pinCurrentThread(100));
    assertTrue(e.getMessage().contains("100"), e.getMessage());
  }

  @Test
  void negativeCpuIsRejectedByName() {
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> new LinuxThreadAffinity().pinCurrentThread(-1));
    assertTrue(e.getMessage().contains("-1"), e.getMessage());
  }
}
