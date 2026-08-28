package io.joeyang.oms.cluster.service;

import java.nio.ByteOrder;
import org.agrona.DirectBuffer;

/**
 * The payload checksum the state machine echoes: rotate-left-1 and XOR over little-endian longs,
 * tail bytes assembled little-endian into a final zero-padded long.
 *
 * <p>Order-sensitive (a plain sum is not), two ALU operations per 8 bytes so a 32 KB payload costs
 * ~4k steps — the apply pays the honest cost of reading every byte without drowning the measurement
 * in multiply latency. Deliberately not a CRC library (no new dependencies on the hot path) and not
 * cryptographic: this is integrity-of-replay, not security. The algorithm is pinned by
 * hand-checkable constants and an independent byte-wise reference in the tests, because goldens are
 * captured from what this code echoes — a wrong algorithm must fail a test, never become the
 * golden.
 */
final class PayloadChecksum {

  private PayloadChecksum() {}

  /**
   * Checksums the given range without allocating.
   *
   * @param buffer buffer holding the payload
   * @param offset payload start
   * @param length payload length in bytes
   * @return the 64-bit checksum; zero for an empty payload
   */
  static long compute(final DirectBuffer buffer, final int offset, final int length) {
    long checksum = 0;
    final int fullLongs = length / 8 * 8;
    for (int i = 0; i < fullLongs; i += 8) {
      checksum = Long.rotateLeft(checksum, 1) ^ buffer.getLong(offset + i, ByteOrder.LITTLE_ENDIAN);
    }
    if (fullLongs < length) {
      long tail = 0;
      for (int j = length - 1; j >= fullLongs; j--) {
        tail = (tail << 8) | (buffer.getByte(offset + j) & 0xFFL);
      }
      checksum = Long.rotateLeft(checksum, 1) ^ tail;
    }
    return checksum;
  }
}
