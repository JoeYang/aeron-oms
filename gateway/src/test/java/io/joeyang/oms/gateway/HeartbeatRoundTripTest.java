package io.joeyang.oms.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.joeyang.oms.core.time.FixedClock;
import io.joeyang.oms.sbe.HeartbeatDecoder;
import io.joeyang.oms.sbe.HeartbeatEncoder;
import io.joeyang.oms.sbe.MessageHeaderDecoder;
import io.joeyang.oms.sbe.MessageHeaderEncoder;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/** The outbound stamp must come through the {@code Clock} port — pinned with a frozen clock. */
class HeartbeatRoundTripTest {

  @Test
  void encodesTheClockPortsTime() {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

    final int length = HeartbeatRoundTrip.encodeHeartbeat(buffer, 0, new FixedClock(123_456_789L));

    assertEquals(
        MessageHeaderEncoder.ENCODED_LENGTH + HeartbeatEncoder.BLOCK_LENGTH,
        length,
        "header plus reserved block");

    final MessageHeaderDecoder header = new MessageHeaderDecoder();
    header.wrap(buffer, 0);
    assertEquals(HeartbeatDecoder.TEMPLATE_ID, header.templateId());

    final HeartbeatDecoder heartbeat = new HeartbeatDecoder();
    heartbeat.wrap(buffer, header.encodedLength(), header.blockLength(), header.version());
    assertEquals(123_456_789L, heartbeat.timestampNanos(), "the frozen clock's value, exactly");
  }
}
