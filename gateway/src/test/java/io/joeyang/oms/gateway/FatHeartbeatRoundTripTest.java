package io.joeyang.oms.gateway;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.joeyang.oms.core.time.FixedClock;
import io.joeyang.oms.sbe.FatHeartbeatAckEncoder;
import io.joeyang.oms.sbe.FatHeartbeatDecoder;
import io.joeyang.oms.sbe.MessageHeaderDecoder;
import io.joeyang.oms.sbe.MessageHeaderEncoder;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The fat sender's payload must be a deterministic function of the message sequence — never random
 * — because a recorded tape has to mean one exact byte stream, and expected checksums must be
 * computable from the sequence alone.
 */
class FatHeartbeatRoundTripTest {

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[40_000]);
  private final byte[] scratch = new byte[100];

  @Test
  void encodesSequenceDerivedPayloadAndTheClockPortsTime() {
    final int length =
        FatHeartbeatRoundTrip.encodeFatHeartbeat(buffer, 0, new FixedClock(123L), 5L, scratch);

    final MessageHeaderDecoder header = new MessageHeaderDecoder();
    header.wrap(buffer, 0);
    assertEquals(FatHeartbeatDecoder.TEMPLATE_ID, header.templateId());
    final FatHeartbeatDecoder fat = new FatHeartbeatDecoder();
    fat.wrap(buffer, header.encodedLength(), header.blockLength(), header.version());
    assertEquals(123L, fat.timestampNanos(), "the frozen clock's value, exactly");
    final byte[] payload = new byte[fat.payloadLength()];
    fat.getPayload(payload, 0, payload.length);
    assertEquals(scratch.length, payload.length, "payload fills the caller's scratch size");
    for (int i = 0; i < payload.length; i++) {
      assertEquals((byte) (5L + i), payload[i], "payload byte " + i + " derives from sequence");
    }
    assertEquals(
        MessageHeaderEncoder.ENCODED_LENGTH + fat.encodedLength(), length, "reported length");
  }

  @Test
  void samePayloadForSameSequenceEveryTime() {
    FatHeartbeatRoundTrip.encodeFatHeartbeat(buffer, 0, new FixedClock(1L), 9L, scratch);
    final byte[] first = scratch.clone();
    FatHeartbeatRoundTrip.encodeFatHeartbeat(buffer, 0, new FixedClock(2L), 9L, scratch);

    assertArrayEquals(first, scratch, "sequence alone determines the payload");
  }

  @Test
  void listenerCapturesTheAcksTimestampAndChecksum() {
    final FatHeartbeatRoundTrip roundTrip = new FatHeartbeatRoundTrip();
    roundTrip.beginRun(new java.io.PrintStream(new java.io.ByteArrayOutputStream()), 1);
    roundTrip.noteSent();
    new FatHeartbeatAckEncoder()
        .wrapAndApplyHeader(buffer, 0, new io.joeyang.oms.sbe.MessageHeaderEncoder())
        .timestampNanos(777L)
        .payloadChecksum(0xBEEFL);

    roundTrip.onMessage(1L, 0L, buffer, 0, 48, null);

    assertEquals(777L, roundTrip.echoedTimestamp());
    assertEquals(0xBEEFL, roundTrip.echoedChecksum());
  }

  @Test
  void windowedAcksPrintGoldenLinesInSequencedOrder() {
    final FatHeartbeatRoundTrip roundTrip = new FatHeartbeatRoundTrip(2);
    final java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
    roundTrip.beginRun(new java.io.PrintStream(captured), 5);
    roundTrip.noteSent();
    roundTrip.noteSent();
    final io.joeyang.oms.sbe.FatHeartbeatAckEncoder ack = new FatHeartbeatAckEncoder();
    ack.wrapAndApplyHeader(buffer, 0, new io.joeyang.oms.sbe.MessageHeaderEncoder())
        .timestampNanos(111L)
        .payloadChecksum(7L);
    roundTrip.onMessage(1L, 0L, buffer, 0, 48, null);
    ack.wrapAndApplyHeader(buffer, 0, new io.joeyang.oms.sbe.MessageHeaderEncoder())
        .timestampNanos(222L)
        .payloadChecksum(8L);
    roundTrip.onMessage(1L, 0L, buffer, 0, 48, null);

    final String[] lines = captured.toString().split(System.lineSeparator());
    assertEquals(2, lines.length);
    assertEquals(0, lines[0].indexOf("fat  1/5"), "first ack line: " + lines[0]);
    assertTrue(lines[0].contains("sequenced=111 checksum=7"), lines[0]);
    assertEquals(0, lines[1].indexOf("fat  2/5"), "second ack line: " + lines[1]);
    assertTrue(lines[1].contains("sequenced=222 checksum=8"), lines[1]);
  }

  @Test
  void ackWithoutAnOutstandingSendFailsLoudly() {
    final FatHeartbeatRoundTrip roundTrip = new FatHeartbeatRoundTrip(2);
    roundTrip.beginRun(new java.io.PrintStream(new java.io.ByteArrayOutputStream()), 1);
    new FatHeartbeatAckEncoder()
        .wrapAndApplyHeader(buffer, 0, new io.joeyang.oms.sbe.MessageHeaderEncoder())
        .timestampNanos(1L)
        .payloadChecksum(2L);

    assertThrows(
        IllegalStateException.class, () -> roundTrip.onMessage(1L, 0L, buffer, 0, 48, null));
  }
}
