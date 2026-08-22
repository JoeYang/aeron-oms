package io.joeyang.oms.cluster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aeron.Publication;
import io.joeyang.oms.sbe.HeartbeatDecoder;
import io.joeyang.oms.sbe.HeartbeatEncoder;
import io.joeyang.oms.sbe.MessageHeaderDecoder;
import io.joeyang.oms.sbe.MessageHeaderEncoder;
import java.nio.ByteBuffer;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the state machine, with no media driver and no Aeron infrastructure — the
 * service's whole input is the callback parameters, which is the point of its design.
 *
 * <p>The {@code Header} parameter is passed as {@code null} deliberately: the service must not
 * depend on transport framing, and these tests pin that.
 */
class OmsClusteredServiceTest {

  private static final int FRAME_LENGTH =
      MessageHeaderEncoder.ENCODED_LENGTH + HeartbeatEncoder.BLOCK_LENGTH;

  private final OmsClusteredService service = new OmsClusteredService(NoOpIdleStrategy.INSTANCE);
  private final FakeClientSession session = new FakeClientSession();
  private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));

  private void heartbeatAt(final int offset, final long payloadTimestamp) {
    new HeartbeatEncoder()
        .wrapAndApplyHeader(buffer, offset, new MessageHeaderEncoder())
        .timestampNanos(payloadTimestamp);
  }

  private static long decodeEchoTimestamp(final byte[] bytes) {
    final UnsafeBuffer wrapped = new UnsafeBuffer(bytes);
    final MessageHeaderDecoder header = new MessageHeaderDecoder();
    header.wrap(wrapped, 0);
    final HeartbeatDecoder heartbeat = new HeartbeatDecoder();
    heartbeat.wrap(wrapped, header.encodedLength(), header.blockLength(), header.version());
    return heartbeat.timestampNanos();
  }

  @Test
  void echoesTheSequencedTimeNotThePayload() {
    heartbeatAt(16, 42L);

    service.onSessionMessage(session, 1_000L, buffer, 16, FRAME_LENGTH, null);

    assertEquals(1, session.offerCount(), "exactly one egress offer");
    assertEquals(1_000L, decodeEchoTimestamp(session.delivered().get(0)), "sequenced time");
  }

  @Test
  void unknownTemplateEmitsNothing() {
    new MessageHeaderEncoder()
        .wrap(buffer, 0)
        .blockLength(32)
        .templateId(99)
        .schemaId(1)
        .version(1);

    service.onSessionMessage(session, 1_000L, buffer, 0, FRAME_LENGTH, null);

    assertEquals(0, session.offerCount(), "unknown templates are ignored without effect");
  }

  @Test
  void foreignSchemaEmitsNothing() {
    new MessageHeaderEncoder()
        .wrap(buffer, 0)
        .blockLength(32)
        .templateId(1)
        .schemaId(999)
        .version(1);

    service.onSessionMessage(session, 1_000L, buffer, 0, FRAME_LENGTH, null);

    assertEquals(0, session.offerCount(), "another schema's envelope is not ours to answer");
  }

  @Test
  void frameShorterThanHeaderEmitsNothing() {
    service.onSessionMessage(session, 1_000L, buffer, 0, 4, null);

    assertEquals(0, session.offerCount(), "a truncated frame is rejected, not decoded");
  }

  @Test
  void backPressureIsRetriedUntilAccepted() {
    session.scriptOfferResults(Publication.BACK_PRESSURED, Publication.BACK_PRESSURED, 100L);
    heartbeatAt(0, 0L);

    service.onSessionMessage(session, 2_000L, buffer, 0, FRAME_LENGTH, null);

    assertEquals(3, session.offerCount(), "retried through two back-pressure results");
    assertEquals(1, session.delivered().size(), "delivered exactly once in total");
    assertEquals(2_000L, decodeEchoTimestamp(session.delivered().get(0)));
  }

  /** The MVP policy is retry-forever, stated in the spec — including the terminal codes. */
  @Test
  void retriesThroughEveryNegativeCode() {
    session.scriptOfferResults(
        Publication.NOT_CONNECTED, Publication.ADMIN_ACTION, Publication.CLOSED, 100L);
    heartbeatAt(0, 0L);

    service.onSessionMessage(session, 3_000L, buffer, 0, FRAME_LENGTH, null);

    assertEquals(4, session.offerCount());
    assertEquals(1, session.delivered().size());
  }

  @Test
  void repeatedTimestampIsAccepted() {
    heartbeatAt(0, 0L);
    service.onSessionMessage(session, 5_000L, buffer, 0, FRAME_LENGTH, null);
    service.onSessionMessage(session, 5_000L, buffer, 0, FRAME_LENGTH, null);

    assertEquals(2, session.delivered().size());
    assertEquals(5_000L, decodeEchoTimestamp(session.delivered().get(1)), "time may stand still");
  }

  /**
   * Cluster time is monotonic by construction, so a backwards timestamp means something is deeply
   * wrong. The service lets the invariant throw — the hosting spec turns that into a node stop
   * rather than a silently skipped message.
   */
  @Test
  void backwardsTimestampPropagatesTheInvariantViolation() {
    heartbeatAt(0, 0L);
    service.onSessionMessage(session, 5_000L, buffer, 0, FRAME_LENGTH, null);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.onSessionMessage(session, 4_999L, buffer, 0, FRAME_LENGTH, null));
  }
}
