package io.joeyang.oms.sbe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Codec proof for the fat echo: the sequenced timestamp and the payload checksum the state machine
 * computed. A dedicated ack keeps egress concerns out of the ingress messages.
 */
class FatHeartbeatAckCodecTest {

  private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final FatHeartbeatAckEncoder encoder = new FatHeartbeatAckEncoder();
  private final FatHeartbeatAckDecoder decoder = new FatHeartbeatAckDecoder();

  private void encodeAt(final int offset, final long timestampNanos, final long checksum) {
    encoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .timestampNanos(timestampNanos)
        .payloadChecksum(checksum);
  }

  private void decodeAt(final int offset) {
    headerDecoder.wrap(buffer, offset);
    decoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());
  }

  @Test
  void roundTripsTimestampAndChecksum() {
    encodeAt(0, 1_700_000_000_000_000_001L, 0xDEADBEEFCAFEBABEL);
    decodeAt(0);

    assertEquals(1_700_000_000_000_000_001L, decoder.timestampNanos());
    assertEquals(0xDEADBEEFCAFEBABEL, decoder.payloadChecksum());
  }

  /** Messages never sit at offset zero in a real stream, so the codec must not assume it. */
  @Test
  void roundTripsAtNonZeroOffset() {
    encodeAt(64, 42L, -1L);
    decodeAt(64);

    assertEquals(42L, decoder.timestampNanos());
    assertEquals(-1L, decoder.payloadChecksum());
  }

  @Test
  void wireIdentityIsPinned() {
    assertEquals(1, FatHeartbeatAckEncoder.SCHEMA_ID, "schema id is part of the log format");
    assertEquals(3, FatHeartbeatAckEncoder.TEMPLATE_ID, "appended after FatHeartbeat");
    assertEquals(2, FatHeartbeatAckEncoder.SCHEMA_VERSION, "added in the same version bump");
    assertEquals(
        32, FatHeartbeatAckEncoder.BLOCK_LENGTH, "16 bytes of fields plus 16 reserved for growth");
  }
}
