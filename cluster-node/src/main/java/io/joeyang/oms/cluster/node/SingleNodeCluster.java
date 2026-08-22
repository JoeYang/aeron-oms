package io.joeyang.oms.cluster.node;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.NanosecondClusterClock;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import org.agrona.CloseHelper;

/**
 * Hosts everything one cluster member needs — MediaDriver, Archive, ConsensusModule and the service
 * container — in one process. A single member forms a quorum of one, so every mechanism is real:
 * election, commit, journal, replay. Adding peers later changes only who acknowledges commit.
 *
 * <p>The journal is both directories under the base: {@code node-0/archive/} holds the recorded
 * bytes, {@code node-0/consensus/} holds the RecordingLog that gives them meaning. They are
 * retained together. {@code node-0/driver/} is transport state and is wiped on every start.
 */
public final class SingleNodeCluster implements AutoCloseable {

  /**
   * One member's configuration.
   *
   * @param baseDir root for all state; everything lands under {@code node-0/}
   * @param clean wipe the journal on start — an explicit dev reset, never the default
   * @param basePort first of five consecutive localhost ports: ingress, consensus, log, catchup,
   *     archive control
   * @param service the state machine to host
   */
  public record Config(File baseDir, boolean clean, int basePort, ClusteredService service) {}

  private final ClusteredMediaDriver driver;
  private final ClusteredServiceContainer container;
  private final CompletableFuture<Throwable> failure;
  private final String aeronDirectoryName;

  private SingleNodeCluster(
      final ClusteredMediaDriver driver,
      final ClusteredServiceContainer container,
      final CompletableFuture<Throwable> failure,
      final String aeronDirectoryName) {
    this.driver = driver;
    this.container = container;
    this.failure = failure;
    this.aeronDirectoryName = aeronDirectoryName;
  }

  /** Launches the member; returns once every component is started. */
  public static SingleNodeCluster launch(final Config config) {
    final File nodeDir = new File(config.baseDir(), "node-0");
    final String driverDir = new File(nodeDir, "driver").getAbsolutePath();
    final File archiveDir = new File(nodeDir, "archive");
    final File consensusDir = new File(nodeDir, "consensus");

    final int port = config.basePort();
    final String members =
        "0,localhost:"
            + port
            + ",localhost:"
            + (port + 1)
            + ",localhost:"
            + (port + 2)
            + ",localhost:"
            + (port + 3)
            + ",localhost:"
            + (port + 4);
    final CompletableFuture<Throwable> failure = new CompletableFuture<>();

    ClusteredMediaDriver driver = null;
    ClusteredServiceContainer container = null;
    try {
      driver =
          ClusteredMediaDriver.launch(
              new MediaDriver.Context()
                  .aeronDirectoryName(driverDir)
                  .threadingMode(ThreadingMode.SHARED)
                  .dirDeleteOnStart(true),
              new Archive.Context()
                  .aeronDirectoryName(driverDir)
                  .archiveDir(archiveDir)
                  .controlChannel("aeron:udp?endpoint=localhost:" + (port + 4))
                  .replicationChannel("aeron:udp?endpoint=localhost:0")
                  .threadingMode(ArchiveThreadingMode.SHARED)
                  .deleteArchiveOnStart(config.clean()),
              new ConsensusModule.Context()
                  .aeronDirectoryName(driverDir)
                  .clusterDir(consensusDir)
                  .clusterMemberId(0)
                  .clusterMembers(members)
                  .clusterClock(new NanosecondClusterClock())
                  .ingressChannel("aeron:udp?term-length=64k")
                  .replicationChannel("aeron:udp?endpoint=localhost:0")
                  .deleteDirOnStart(config.clean()));
      container =
          ClusteredServiceContainer.launch(
              new ClusteredServiceContainer.Context()
                  .aeronDirectoryName(driverDir)
                  .clusterDir(consensusDir)
                  .clusteredService(new FailFastClusteredService(config.service(), failure)));
      return new SingleNodeCluster(driver, container, failure, driverDir);
    } catch (final RuntimeException e) {
      CloseHelper.quietCloseAll(container, driver);
      throw e;
    }
  }

  /** The media driver directory; an in-process client connects through it over IPC. */
  public String aeronDirectoryName() {
    return aeronDirectoryName;
  }

  /** Completes when the state machine fails. The node is unusable afterwards. */
  public CompletableFuture<Throwable> failure() {
    return failure;
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(container, driver);
  }
}
