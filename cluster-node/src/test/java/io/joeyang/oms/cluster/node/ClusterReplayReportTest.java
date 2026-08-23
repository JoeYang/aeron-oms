package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.joeyang.oms.cluster.service.OmsClusteredService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Journal test for cluster-mode replay: a node recovering over the unpacked golden tape must replay
 * every recorded message through the service and report the count once it reaches leader.
 */
class ClusterReplayReportTest {

  private static final String RUNFILES = System.getenv("TEST_SRCDIR") + "/_main/journal/";
  private static final Pattern REPORT = Pattern.compile("cluster-replay: (\\d+) messages");
  private static final long REPORT_TIMEOUT_MS = 30_000;

  @TempDir File tempDir;

  @Test
  void recoveryFromTheGoldenTapeReportsTheManifestCount() throws Exception {
    final File node0 = new File(tempDir, "node-0");
    if (!node0.mkdirs()) {
      fail("cannot create " + node0);
    }
    final Process tar =
        new ProcessBuilder(
                "tar", "-xzf", RUNFILES + "heartbeats-v1.tar.gz", "-C", node0.getAbsolutePath())
            .inheritIO()
            .start();
    assertEquals(0, tar.waitFor(), "fixture tarball must unpack");

    final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    final ReplayReportingService reporting =
        new ReplayReportingService(
            new OmsClusteredService(), new PrintStream(captured, true, StandardCharsets.UTF_8));

    try (SingleNodeCluster node =
        SingleNodeCluster.launch(
            new SingleNodeCluster.Config(tempDir, false, 21172, reporting, false, false))) {
      final long deadline = System.currentTimeMillis() + REPORT_TIMEOUT_MS;
      while (true) {
        final Matcher matcher = REPORT.matcher(captured.toString(StandardCharsets.UTF_8));
        if (matcher.find()) {
          assertEquals(3000, Long.parseLong(matcher.group(1)), "replayed count equals manifest");
          return;
        }
        if (System.currentTimeMillis() > deadline) {
          fail("no cluster-replay report within " + REPORT_TIMEOUT_MS + " ms");
        }
        Thread.sleep(100);
      }
    }
  }
}
