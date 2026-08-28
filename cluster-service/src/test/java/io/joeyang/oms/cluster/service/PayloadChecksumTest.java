package io.joeyang.oms.cluster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the checksum algorithm two independent ways: hard constants small enough to verify by hand,
 * and a byte-wise reference implementation that must agree with the long-wise production code on
 * every boundary shape. Goldens are captured from what the service echoes, so a wrong algorithm
 * must die here — it must never get the chance to become the golden.
 */
class PayloadChecksumTest {

  /** Byte-wise reference: same definition, deliberately different code path. */
  private static long reference(final byte[] payload) {
    long checksum = 0;
    final int fullLongs = payload.length / 8 * 8;
    for (int i = 0; i < fullLongs; i += 8) {
      long value = 0;
      for (int j = 7; j >= 0; j--) {
        value = (value << 8) | (payload[i + j] & 0xFFL);
      }
      checksum = Long.rotateLeft(checksum, 1) ^ value;
    }
    if (fullLongs < payload.length) {
      long value = 0;
      for (int j = payload.length - 1; j >= fullLongs; j--) {
        value = (value << 8) | (payload[j] & 0xFFL);
      }
      checksum = Long.rotateLeft(checksum, 1) ^ value;
    }
    return checksum;
  }

  private static byte[] pattern(final int length) {
    final byte[] payload = new byte[length];
    for (int i = 0; i < length; i++) {
      payload[i] = (byte) (i * 31 + 7);
    }
    return payload;
  }

  private static long computeOver(final byte[] payload, final int offset) {
    final UnsafeBuffer buffer =
        new UnsafeBuffer(ByteBuffer.allocateDirect(offset + payload.length));
    buffer.putBytes(offset, payload);
    return PayloadChecksum.compute(buffer, offset, payload.length);
  }

  @Test
  void handCheckableConstantsArePinned() {
    assertEquals(0L, computeOver(new byte[0], 0), "empty payload checksums to zero");
    assertEquals(1L, computeOver(new byte[] {0x01}, 0), "one byte is its little-endian value");
    assertEquals(
        0x0807060504030201L,
        computeOver(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}, 0),
        "eight bytes are one little-endian long");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 7, 8, 9, 15, 16, 17, 1024, 32_000, 65_534})
  void agreesWithTheByteWiseReferenceOnEveryBoundaryShape(final int length) {
    final byte[] payload = pattern(length);

    assertEquals(reference(payload), computeOver(payload, 0));
    assertEquals(reference(payload), computeOver(payload, 24), "offset must not matter");
  }

  @Test
  void orderSensitivity() {
    final long ab =
        computeOver(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, 0);
    final long ba =
        computeOver(new byte[] {9, 10, 11, 12, 13, 14, 15, 16, 1, 2, 3, 4, 5, 6, 7, 8}, 0);

    assertEquals(true, ab != ba, "swapped 8-byte lanes must change the checksum");
  }
}
