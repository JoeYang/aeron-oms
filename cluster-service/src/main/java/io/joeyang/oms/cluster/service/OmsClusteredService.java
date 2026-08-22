package io.joeyang.oms.cluster.service;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import io.joeyang.oms.core.time.SequencedClock;
import io.joeyang.oms.sbe.HeartbeatDecoder;
import io.joeyang.oms.sbe.HeartbeatEncoder;
import io.joeyang.oms.sbe.MessageHeaderDecoder;
import io.joeyang.oms.sbe.MessageHeaderEncoder;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.YieldingIdleStrategy;

/**
 * The deterministic state machine. MVP behaviour: echo each committed Heartbeat, stamped with
 * sequenced time.
 *
 * <p>Time enters only through the {@code timestamp} callback parameter — it is in the replicated
 * log, so replay sees the identical value. Nothing here may read a wall clock, and the class is
 * constructible with no Aeron infrastructure running.
 *
 * <p>An invariant violation (cluster time running backwards) is allowed to throw: the hosting spec
 * turns that into a node stop, because a silently skipped message is undetectable divergence.
 */
public final class OmsClusteredService implements ClusteredService {

  private final SequencedClock clock = new SequencedClock();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final HeartbeatEncoder response = new HeartbeatEncoder();
  private final UnsafeBuffer egressBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(64));
  private final IdleStrategy retryIdle;

  /** Production construction: yields between egress retries. */
  public OmsClusteredService() {
    this(new YieldingIdleStrategy());
  }

  OmsClusteredService(final IdleStrategy retryIdle) {
    this.retryIdle = retryIdle;
  }

  @Override
  public void onSessionMessage(
      final ClientSession session,
      final long timestamp,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final Header header) {
    clock.update(timestamp);

    if (length < MessageHeaderDecoder.ENCODED_LENGTH) {
      return;
    }
    headerDecoder.wrap(buffer, offset);
    if (headerDecoder.schemaId() != HeartbeatDecoder.SCHEMA_ID
        || headerDecoder.templateId() != HeartbeatDecoder.TEMPLATE_ID) {
      return;
    }

    response.wrapAndApplyHeader(egressBuffer, 0, headerEncoder).timestampNanos(clock.timeNanos());
    final int responseLength = headerEncoder.encodedLength() + response.encodedLength();
    // MVP policy, stated in the spec: retry until accepted, whatever the failure code.
    // During replay and on followers, offers return a mocked positive result, so this
    // loop cannot stall a replaying node.
    while (session.offer(egressBuffer, 0, responseLength) < 0) {
      retryIdle.idle();
    }
    retryIdle.reset();
  }

  @Override
  public void onStart(final Cluster cluster, final Image snapshotImage) {}

  @Override
  public void onSessionOpen(final ClientSession session, final long timestamp) {}

  @Override
  public void onSessionClose(
      final ClientSession session, final long timestamp, final CloseReason closeReason) {}

  @Override
  public void onTimerEvent(final long correlationId, final long timestamp) {}

  @Override
  public void onTakeSnapshot(final ExclusivePublication snapshotPublication) {}

  @Override
  public void onRoleChange(final Cluster.Role newRole) {}

  @Override
  public void onTerminate(final Cluster cluster) {}
}
