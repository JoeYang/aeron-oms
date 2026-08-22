package io.joeyang.oms.gateway;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.joeyang.oms.core.time.SystemClock;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;

/**
 * Entry point for the gateway process: an {@code AeronCluster} client that streams Heartbeats into
 * the cluster and prints each sequenced echo.
 *
 * <p>It reaches the state machine only by submitting ordered ingress to the cluster, never by
 * depending on the clustered service directly — the build enforces that.
 *
 * <p>Configuration by system property ({@code --jvm_flag=-Dname=value} through the launcher):
 * {@code oms.cluster.port} (default 9002), {@code oms.gateway.count} (default 10), {@code
 * oms.gateway.interval.ms} (default 1000).
 */
public final class GatewayMain {

  private GatewayMain() {}

  /**
   * Process entry point.
   *
   * @param args command-line arguments; currently unused
   * @throws InterruptedException if interrupted while pacing the stream
   */
  public static void main(final String[] args) throws InterruptedException {
    final int basePort = Integer.getInteger("oms.cluster.port", 9002);
    final int count = Integer.getInteger("oms.gateway.count", 10);
    final long intervalMs = Long.getLong("oms.gateway.interval.ms", 1_000L);

    System.out.printf(
        "gateway: sending %d heartbeats to localhost:%d, one per %d ms%n",
        count, basePort, intervalMs);

    final MediaDriver.Context driverContext =
        new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true);
    if (Boolean.getBoolean("oms.lowlatency")) {
      driverContext
          .threadingMode(ThreadingMode.DEDICATED)
          .conductorIdleStrategy(new YieldingIdleStrategy())
          .senderIdleStrategy(new BusySpinIdleStrategy())
          .receiverIdleStrategy(new BusySpinIdleStrategy());
    }

    final HeartbeatRoundTrip roundTrip = new HeartbeatRoundTrip();
    try (MediaDriver driver = MediaDriver.launchEmbedded(driverContext);
        AeronCluster cluster =
            AeronCluster.connect(
                new AeronCluster.Context()
                    .aeronDirectoryName(driver.aeronDirectoryName())
                    .egressListener(roundTrip)
                    .ingressChannel("aeron:udp?term-length=64k")
                    .ingressEndpoints("0=localhost:" + basePort)
                    .egressChannel("aeron:udp?endpoint=localhost:0"))) {
      roundTrip.run(cluster, new SystemClock(), count, intervalMs, System.out);
    }
    System.out.println("done.");
  }
}
