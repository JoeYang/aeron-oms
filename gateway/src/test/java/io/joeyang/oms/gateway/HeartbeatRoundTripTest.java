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

  /** 100 samples of 1..100 µs: the percentile convention matches the external protocol. */
  @Test
  void summarizesTheMeasuredWindow() {
    final long[] rtts = new long[100];
    for (int i = 0; i < 100; i++) {
      rtts[i] = (i + 1) * 1_000L;
    }

    assertEquals(
        "bench: n=100 min=1.0 p50=51.0 p90=91.0 p99=100.0 max=100.0 (us)",
        HeartbeatRoundTrip.summarize(rtts));
  }

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
