package io.joeyang.oms.cluster.service;

import java.nio.ByteOrder;
import org.agrona.DirectBuffer;

/**
 * {@link PayloadChecksum} consumable in slices: the same rotate-left-1 XOR over little-endian
 * longs, with byte-level carry across slice boundaries so a payload split at arbitrary fragment
 * edges — which never fall on word edges — checksums to the identical value. Bytes accumulate
 * little-endian into a partial word; each completed word folds; {@link #finish()} folds a non-empty
 * partial as the zero-padded tail, bit-identical to the contiguous tail rule.
 *
 * <p>The hot loop still folds aligned 8-byte words once the carry fills: only the few bytes at each
 * slice boundary go byte-wise. Public because the tape walker (cluster-node) is the caller that
 * sees fragment boundaries; the cluster runtime itself always delivers contiguous buffers.
 */
public final class IncrementalPayloadChecksum {

  private long checksum;
  private long partialWord;
  private int partialBytes;

  /** Clears all accumulated state for reuse on the next payload. */
  public void reset() {
    checksum = 0;
    partialWord = 0;
    partialBytes = 0;
  }

  /**
   * Consumes one slice of the payload.
   *
   * @param buffer buffer holding the slice
   * @param offset slice start
   * @param length slice length in bytes
   */
  public void update(final DirectBuffer buffer, final int offset, final int length) {
    int index = offset;
    int remaining = length;
    while (partialBytes != 0 && remaining > 0) {
      partialWord |= (buffer.getByte(index) & 0xFFL) << (partialBytes * 8);
      partialBytes++;
      index++;
      remaining--;
      if (partialBytes == 8) {
        fold(partialWord);
        partialWord = 0;
        partialBytes = 0;
      }
    }
    while (remaining >= 8) {
      fold(buffer.getLong(index, ByteOrder.LITTLE_ENDIAN));
      index += 8;
      remaining -= 8;
    }
    while (remaining > 0) {
      partialWord |= (buffer.getByte(index) & 0xFFL) << (partialBytes * 8);
      partialBytes++;
      index++;
      remaining--;
    }
  }

  /**
   * Folds any partial tail and returns the checksum. Call {@link #reset()} before reuse.
   *
   * @return the 64-bit checksum; zero for an empty payload
   */
  public long finish() {
    if (partialBytes > 0) {
      fold(partialWord);
      partialWord = 0;
      partialBytes = 0;
    }
    return checksum;
  }

  private void fold(final long word) {
    checksum = Long.rotateLeft(checksum, 1) ^ word;
  }
}
