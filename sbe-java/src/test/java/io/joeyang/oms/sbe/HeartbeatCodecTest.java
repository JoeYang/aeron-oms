package io.joeyang.oms.sbe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Proves the SBE pipeline end to end: schema to generated codec to bytes and back.
 *
 * <p>Nothing here is committed source. The codecs under test are generated from {@code
 * //sbe:message-schema.xml} on every build, so a failure means the schema changed, not that
 * checked-in code drifted.
 */
class HeartbeatCodecTest {

  private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final HeartbeatEncoder encoder = new HeartbeatEncoder();
  private final HeartbeatDecoder decoder = new HeartbeatDecoder();

  private long encodeAt(final int offset, final long timestampNanos) {
    encoder.wrapAndApplyHeader(buffer, offset, headerEncoder).timestampNanos(timestampNanos);
    return headerEncoder.encodedLength() + encoder.encodedLength();
  }

  private long decodeAt(final int offset) {
    headerDecoder.wrap(buffer, offset);
    decoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());
    return decoder.timestampNanos();
  }

  @Test
  void roundTripsTheTimestamp() {
    encodeAt(0, 1_700_000_000_123_456_789L);

    assertEquals(1_700_000_000_123_456_789L, decodeAt(0));
  }

  /**
   * Boundaries matter for a fixed-width field: a sign error or a width mistake in the schema shows
   * up here and nowhere else.
   */
  @ParameterizedTest
  @ValueSource(longs = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE})
  void roundTripsBoundaryValues(final long value) {
    encodeAt(0, value);

    assertEquals(value, decodeAt(0));
  }

  /** Messages never sit at offset zero in a real stream, so the codec must not assume it. */
  @Test
  void roundTripsAtNonZeroOffset() {
    encodeAt(64, 42L);

    assertEquals(42L, decodeAt(64));
  }

  @Test
  void headerCarriesTheRoutingFields() {
    encodeAt(0, 7L);

    headerDecoder.wrap(buffer, 0);

    assertEquals(HeartbeatEncoder.SCHEMA_ID, headerDecoder.schemaId());
    assertEquals(HeartbeatEncoder.TEMPLATE_ID, headerDecoder.templateId());
    assertEquals(HeartbeatEncoder.BLOCK_LENGTH, headerDecoder.blockLength());
    assertEquals(HeartbeatEncoder.SCHEMA_VERSION, headerDecoder.version());
  }

  /**
   * Pins the wire identity. Schema id, template id and block length become permanent the moment a
   * message reaches a cluster log — a later renumbering would silently make old logs undecodable.
   * This test exists so that change cannot be made quietly.
   */
  @Test
  void wireIdentityIsPinned() {
    assertEquals(1, HeartbeatEncoder.SCHEMA_ID, "schema id is part of the log format");
    assertEquals(1, HeartbeatEncoder.TEMPLATE_ID, "template id is part of the log format");
    assertEquals(1, HeartbeatEncoder.SCHEMA_VERSION, "schema version is part of the log format");
    assertEquals(
        32, HeartbeatEncoder.BLOCK_LENGTH, "8 bytes of fields plus 24 reserved for growth");
  }

  @Test
  void encodedLengthIsHeaderPlusBlock() {
    final long length = encodeAt(0, 1L);

    assertEquals(headerEncoder.encodedLength() + HeartbeatEncoder.BLOCK_LENGTH, length);
    assertEquals(40L, length, "8 byte header plus the 32 byte reserved block");
  }

  /**
   * The null sentinel is how a decoder reports a field absent from an older message. With {@code
   * minValue="0"} it can never also be a legal value, so "absent" and "sent" stay distinguishable
   * for the life of the log.
   */
  @Test
  void nullSentinelIsOutsideTheLegalRange() {
    assertEquals(0L, HeartbeatEncoder.timestampNanosMinValue(), "legal range starts at zero");
    assertEquals(Long.MIN_VALUE, HeartbeatEncoder.timestampNanosNullValue(), "the null sentinel");
  }
}
