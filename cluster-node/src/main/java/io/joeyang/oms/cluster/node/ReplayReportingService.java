package io.joeyang.oms.cluster.node;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import java.io.PrintStream;
import org.agrona.DirectBuffer;

/**
 * Delegating wrapper that observes restart-recovery replay: counts applied session messages and
 * prints one summary line — count, duration, msgs/sec — when the node first reaches leader role.
 * Purely observational; the wrapped service's behaviour is unchanged. Timing uses {@code
 * System.nanoTime()}, which is why this lives in {@code cluster-node} and not in the deterministic
 * {@code cluster-service} tier.
 */
final class ReplayReportingService implements ClusteredService {

  private final ClusteredService delegate;
  private final PrintStream out;
  private long count;
  private long firstNanos;
  private long lastNanos;
  private boolean reported;

  ReplayReportingService(final ClusteredService delegate, final PrintStream out) {
    this.delegate = delegate;
    this.out = out;
  }

  @Override
  public void onStart(final Cluster cluster, final Image snapshotImage) {
    delegate.onStart(cluster, snapshotImage);
  }

  @Override
  public void onSessionOpen(final ClientSession session, final long timestamp) {
    delegate.onSessionOpen(session, timestamp);
  }

  @Override
  public void onSessionClose(
      final ClientSession session, final long timestamp, final CloseReason closeReason) {
    delegate.onSessionClose(session, timestamp, closeReason);
  }

  @Override
  public void onSessionMessage(
      final ClientSession session,
      final long timestamp,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final Header header) {
    if (!reported) {
      if (count == 0) {
        firstNanos = System.nanoTime();
      }
      count++;
      lastNanos = System.nanoTime();
    }
    delegate.onSessionMessage(session, timestamp, buffer, offset, length, header);
  }

  @Override
  public void onTimerEvent(final long correlationId, final long timestamp) {
    delegate.onTimerEvent(correlationId, timestamp);
  }

  @Override
  public void onTakeSnapshot(final ExclusivePublication snapshotPublication) {
    delegate.onTakeSnapshot(snapshotPublication);
  }

  @Override
  public void onRoleChange(final Cluster.Role newRole) {
    delegate.onRoleChange(newRole);
    if (newRole == Cluster.Role.LEADER && !reported) {
      reported = true;
      final double seconds = (lastNanos - firstNanos) / 1e9;
      out.printf(
          java.util.Locale.ROOT,
          "cluster-replay: %d messages in %.3f s = %,.0f msg/s%n",
          count,
          seconds,
          seconds > 0 ? count / seconds : 0.0);
    }
  }

  @Override
  public void onTerminate(final Cluster cluster) {
    delegate.onTerminate(cluster);
  }
}
