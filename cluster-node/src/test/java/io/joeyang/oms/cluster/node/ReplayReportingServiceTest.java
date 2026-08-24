package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the warmup window of the replay report: the first N applies warm the JVM and are
 * excluded from the reported count and timing; N=0 keeps the report exactly as before.
 */
class ReplayReportingServiceTest {

  /** The wrapper must not depend on what the wrapped service does. */
  private static final class NoopService implements ClusteredService {
    @Override
    public void onStart(final Cluster cluster, final Image snapshotImage) {}

    @Override
    public void onSessionOpen(final ClientSession session, final long timestamp) {}

    @Override
    public void onSessionClose(
        final ClientSession session, final long timestamp, final CloseReason closeReason) {}

    @Override
    public void onSessionMessage(
        final ClientSession session,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header) {}

    @Override
    public void onTimerEvent(final long correlationId, final long timestamp) {}

    @Override
    public void onTakeSnapshot(final ExclusivePublication snapshotPublication) {}

    @Override
    public void onRoleChange(final Cluster.Role newRole) {}

    @Override
    public void onTerminate(final Cluster cluster) {}
  }

  private static String report(final int warmup, final int applies) {
    final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    final ReplayReportingService service =
        new ReplayReportingService(
            new NoopService(), new PrintStream(captured, true, StandardCharsets.UTF_8), warmup);
    for (int i = 0; i < applies; i++) {
      service.onSessionMessage(null, i, null, 0, 0, null);
    }
    service.onRoleChange(Cluster.Role.LEADER);
    return captured.toString(StandardCharsets.UTF_8);
  }

  @Test
  void warmupAppliesAreExcludedFromTheReport() {
    final String report = report(2, 5);

    assertTrue(report.contains("cluster-replay: 3 messages"), report);
    assertTrue(report.contains("after 2 warmup applies"), report);
  }

  @Test
  void zeroWarmupKeepsTheReportUnchanged() {
    final String report = report(0, 5);

    assertTrue(report.contains("cluster-replay: 5 messages"), report);
    assertFalse(report.contains("warmup"), report);
  }

  @Test
  void tapeShorterThanWarmupReportsZeroVisibly() {
    final String report = report(10, 4);

    assertTrue(report.contains("cluster-replay: 0 messages"), report);
  }
}
