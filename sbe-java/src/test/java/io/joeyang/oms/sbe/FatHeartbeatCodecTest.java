package io.joeyang.oms.sbe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Codec proof for the fat message: timestamp plus a variable-length payload. The payload length is
 * uint16 — a deliberate one-way door capping a FatHeartbeat below 64 KB; fatter than that is a
 * different message, not a widened field.
 */
class FatHeartbeatCodecTest {

  private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(70_000));
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final FatHeartbeatEncoder encoder = new FatHeartbeatEncoder();
  private final FatHeartbeatDecoder decoder = new FatHeartbeatDecoder();

  private static byte[] pattern(final int length) {
    final byte[] payload = new byte[length];
    for (int i = 0; i < length; i++) {
      payload[i] = (byte) (i * 31 + 7);
    }
    return payload;
  }

  private void encodeAt(final int offset, final long timestampNanos, final byte[] payload) {
    encoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .timestampNanos(timestampNanos)
        .putPayload(payload, 0, payload.length);
  }

  private byte[] decodePayloadAt(final int offset) {
    headerDecoder.wrap(buffer, offset);
    decoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());
    final byte[] payload = new byte[decoder.payloadLength()];
    decoder.getPayload(payload, 0, payload.length);
    return payload;
  }

  @Test
  void roundTripsTimestampAndPayload() {
    final byte[] payload = pattern(32_000);
    encodeAt(0, 1_700_000_000_123_456_789L, payload);

    assertArrayEquals(payload, decodePayloadAt(0));
    assertEquals(1_700_000_000_123_456_789L, decoder.timestampNanos());
  }

  /** Boundary payload sizes: empty, one byte, the target fat size, and the uint16 door. */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 32_000, 65_534})
  void roundTripsBoundaryPayloadSizes(final int length) {
    final byte[] payload = pattern(length);
    encodeAt(0, 7L, payload);

    assertArrayEquals(payload, decodePayloadAt(0));
  }

  /** Messages never sit at offset zero in a real stream, so the codec must not assume it. */
  @Test
  void roundTripsAtNonZeroOffset() {
    final byte[] payload = pattern(1024);
    encodeAt(64, 42L, payload);

    assertArrayEquals(payload, decodePayloadAt(64));
  }

  /** Appended message: new template id, bumped version, existing messages unmoved. */
  @Test
  void wireIdentityIsPinned() {
    assertEquals(1, FatHeartbeatEncoder.SCHEMA_ID, "schema id is part of the log format");
    assertEquals(2, FatHeartbeatEncoder.TEMPLATE_ID, "appended after Heartbeat");
    assertEquals(2, FatHeartbeatEncoder.SCHEMA_VERSION, "every schema change bumps version");
    assertEquals(
        32, FatHeartbeatEncoder.BLOCK_LENGTH, "8 bytes of fields plus 24 reserved for growth");
  }

  @Test
  void headerCarriesTheRoutingFields() {
    encodeAt(0, 7L, pattern(16));

    headerDecoder.wrap(buffer, 0);

    assertEquals(FatHeartbeatEncoder.SCHEMA_ID, headerDecoder.schemaId());
    assertEquals(FatHeartbeatEncoder.TEMPLATE_ID, headerDecoder.templateId());
    assertEquals(FatHeartbeatEncoder.BLOCK_LENGTH, headerDecoder.blockLength());
    assertEquals(FatHeartbeatEncoder.SCHEMA_VERSION, headerDecoder.version());
  }
}
