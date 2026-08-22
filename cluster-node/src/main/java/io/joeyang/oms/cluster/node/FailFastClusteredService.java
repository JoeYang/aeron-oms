package io.joeyang.oms.cluster.node;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.AgentTerminationException;

/**
 * Turns any exception from the state machine into node termination.
 *
 * <p>Aeron's default is to hand the exception to an error handler and continue — the failing
 * message is silently skipped, which is undetectable divergence. Rethrowing as {@link
 * AgentTerminationException} stops the container's agent instead, and the recorded failure lets the
 * host shut the whole node down. A stopped node is loud; a skipped message never is.
 */
final class FailFastClusteredService implements ClusteredService {

  private final ClusteredService delegate;
  private final CompletableFuture<Throwable> failure;

  FailFastClusteredService(
      final ClusteredService delegate, final CompletableFuture<Throwable> failure) {
    this.delegate = delegate;
    this.failure = failure;
  }

  private void guard(final Runnable callback) {
    try {
      callback.run();
    } catch (final Throwable t) {
      failure.complete(t);
      throw new AgentTerminationException("state machine failed; stopping the node", t);
    }
  }

  @Override
  public void onStart(final Cluster cluster, final Image snapshotImage) {
    guard(() -> delegate.onStart(cluster, snapshotImage));
  }

  @Override
  public void onSessionOpen(final ClientSession session, final long timestamp) {
    guard(() -> delegate.onSessionOpen(session, timestamp));
  }

  @Override
  public void onSessionClose(
      final ClientSession session, final long timestamp, final CloseReason closeReason) {
    guard(() -> delegate.onSessionClose(session, timestamp, closeReason));
  }

  @Override
  public void onSessionMessage(
      final ClientSession session,
      final long timestamp,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final Header header) {
    guard(() -> delegate.onSessionMessage(session, timestamp, buffer, offset, length, header));
  }

  @Override
  public void onTimerEvent(final long correlationId, final long timestamp) {
    guard(() -> delegate.onTimerEvent(correlationId, timestamp));
  }

  @Override
  public void onTakeSnapshot(final ExclusivePublication snapshotPublication) {
    guard(() -> delegate.onTakeSnapshot(snapshotPublication));
  }

  @Override
  public void onRoleChange(final Cluster.Role newRole) {
    guard(() -> delegate.onRoleChange(newRole));
  }

  @Override
  public void onTerminate(final Cluster cluster) {
    guard(() -> delegate.onTerminate(cluster));
  }
}
