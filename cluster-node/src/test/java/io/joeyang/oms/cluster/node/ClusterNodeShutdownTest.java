package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for the shutdown hang: Ctrl+C (SIGINT/SIGTERM) must terminate the node process.
 *
 * <p>The bug: agrona's {@code ShutdownSignalBarrier} replaces the default signal handlers and runs
 * a non-daemon thread; unless the barrier is closed before {@code main} returns, the JVM cannot
 * exit — every component closes cleanly, and the process still hangs forever, warning "Did you
 * forget to call close() on it?" every ten seconds. Only a process-level test can see this, so this
 * test runs the real binary and sends the real signal.
 */
class ClusterNodeShutdownTest {

  private static final long STARTUP_TIMEOUT_MS = 30_000;

  @TempDir File tempDir;

  @Test
  void terminationSignalExitsTheProcess() throws IOException, InterruptedException {
    final String binary = System.getenv("TEST_SRCDIR") + "/_main/cluster-node/cluster-node";
    final Process wrapper =
        new ProcessBuilder(
                binary,
                "--jvm_flag=-Doms.data.dir=" + tempDir.getAbsolutePath(),
                "--jvm_flag=-Doms.cluster.clean=true",
                "--jvm_flag=-Doms.cluster.port=21162")
            .redirectErrorStream(true)
            .start();
    try {
      awaitStartupLine(wrapper);

      // The launcher may run java as a child rather than exec it; signal the JVM itself.
      final ProcessHandle jvm =
          wrapper
              .descendants()
              .filter(h -> h.info().command().map(c -> c.endsWith("java")).orElse(false))
              .findFirst()
              .orElse(wrapper.toHandle());

      jvm.destroy(); // SIGTERM — the same path as Ctrl+C's SIGINT through the barrier

      assertTrue(
          wrapper.waitFor(15, TimeUnit.SECONDS),
          "the node process must exit after a termination signal, not hang");
      // 143 = 128 + SIGTERM: after the barrier releases and every component closes, agrona
      // re-raises the signal so supervisors see the true cause of death. That is the clean
      // signalled shutdown, by Unix convention — an exit code of 0 would hide the signal.
      assertEquals(143, wrapper.exitValue(), "terminated by SIGTERM after clean teardown");
    } finally {
      wrapper.descendants().forEach(ProcessHandle::destroyForcibly);
      wrapper.destroyForcibly();
    }
  }

  private static void awaitStartupLine(final Process process) throws IOException {
    final long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains("cluster-node up")) {
          return;
        }
        if (System.currentTimeMillis() > deadline) {
          break;
        }
      }
    }
    fail("node did not report startup within " + STARTUP_TIMEOUT_MS + " ms");
  }
}
