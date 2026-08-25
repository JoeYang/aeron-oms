package io.joeyang.oms.cluster.node;

import io.aeron.cluster.service.ClusteredService;
import io.joeyang.oms.cluster.service.OmsClusteredService;
import java.io.File;
import org.agrona.concurrent.ShutdownSignalBarrier;

/**
 * Entry point for the cluster node process: one complete single-member cluster hosting the OMS
 * state machine.
 *
 * <p>Configuration is by system property, passed through the Bazel launcher as {@code
 * --jvm_flag=-Dname=value}:
 *
 * <ul>
 *   <li>{@code oms.data.dir} — state base directory (default: {@code data/} under the directory
 *       {@code bazel run} was invoked from)
 *   <li>{@code oms.cluster.clean} — wipe the journal on start; an explicit dev reset
 *   <li>{@code oms.cluster.port} — first of five consecutive localhost ports (default 9002)
 *   <li>{@code oms.replay.report} — print one line with the count and duration of the
 *       restart-recovery replay once the node reaches leader; observational only
 *   <li>{@code oms.replay.warmup} — exclude the first N replay applies from the report's timing
 *       window (JIT warmup; same discard convention as the perf protocol)
 * </ul>
 */
public final class ClusterNodeMain {

  private ClusterNodeMain() {}

  private static File baseDir() {
    final String configured = System.getProperty("oms.data.dir");
    if (configured != null) {
      return new File(configured);
    }
    // Under `bazel run` the working directory is the runfiles tree; BUILD_WORKING_DIRECTORY
    // is where the user actually invoked from, and the journal must never land in runfiles.
    final String invokedFrom = System.getenv("BUILD_WORKING_DIRECTORY");
    return invokedFrom != null ? new File(invokedFrom, "data") : new File("data");
  }

  /**
   * Process entry point.
   *
   * @param args command-line arguments; currently unused
   */
  public static void main(final String[] args) {
    final File base = baseDir();
    final boolean clean = Boolean.getBoolean("oms.cluster.clean");
    final int basePort = Integer.getInteger("oms.cluster.port", 9002);
    final boolean lowLatency = Boolean.getBoolean("oms.lowlatency");
    final boolean ipc = Boolean.getBoolean("oms.ipc");

    final ClusteredService service =
        Boolean.getBoolean("oms.replay.report")
            ? new ReplayReportingService(
                new OmsClusteredService(), System.out, Long.getLong("oms.replay.warmup", 0))
            : new OmsClusteredService();

    boolean failed = false;
    // The barrier replaces the SIGINT/SIGTERM handlers and runs a non-daemon thread; it
    // MUST be closed before main returns or the JVM can never exit — every component shuts
    // down cleanly and the process still hangs, warning every ten seconds. Closed by
    // try-with-resources on every path, including a failed launch; reverse order closes
    // the node first, then releases the signal handler.
    try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        SingleNodeCluster node =
            SingleNodeCluster.launch(
                new SingleNodeCluster.Config(base, clean, basePort, service, lowLatency, ipc))) {
      node.failure().whenComplete((error, ignored) -> barrier.signal());

      System.out.println("cluster-node up: single member, quorum of one");
      System.out.println("  journal : " + new File(base, "node-0") + "/{archive,consensus}");
      System.out.println(
          "  ingress : " + (ipc ? "aeron:ipc (same-host clients)" : "localhost:" + basePort));
      System.out.println(
          "  clean   : " + clean + " (reset with --jvm_flag=-Doms.cluster.clean=true)");
      System.out.println(
          "  profile : " + (lowLatency ? "low-latency (dedicated + busy-spin)" : "default"));
      System.out.println("Ctrl+C to stop.");

      barrier.await();
      failed = node.failure().isDone();
      if (failed) {
        System.err.println("state machine failed; stopping the node:");
        node.failure().getNow(null).printStackTrace();
      }
    }
    if (failed) {
      System.exit(1);
    }
  }
}
