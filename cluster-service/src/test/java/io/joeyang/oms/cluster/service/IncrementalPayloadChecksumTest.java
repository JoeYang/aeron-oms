package io.joeyang.oms.cluster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The incremental form exists so fragment boundaries — which never fall on 8-byte word edges — can
 * be checksummed in place. Its one obligation: for every buffer and every way of slicing it, the
 * result equals the contiguous {@link PayloadChecksum}. The equivalence is exhaustive over small
 * lengths (every split point of every length) because the carry logic's edge cases all live within
 * one word of a boundary.
 */
class IncrementalPayloadChecksumTest {

  private static byte[] pattern(final int length) {
    final byte[] payload = new byte[length];
    for (int i = 0; i < length; i++) {
      payload[i] = (byte) (i * 31 + 7);
    }
    return payload;
  }

  private static UnsafeBuffer bufferOf(final byte[] payload) {
    final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(payload.length + 16));
    buffer.putBytes(16, payload);
    return buffer;
  }

  private static long incrementalOverSlices(final byte[] payload, final int... splitPoints) {
    final UnsafeBuffer buffer = bufferOf(payload);
    final IncrementalPayloadChecksum incremental = new IncrementalPayloadChecksum();
    int start = 0;
    for (final int split : splitPoints) {
      incremental.update(buffer, 16 + start, split - start);
      start = split;
    }
    incremental.update(buffer, 16 + start, payload.length - start);
    return incremental.finish();
  }

  @Test
  void handCheckableConstantsArePinned() {
    assertEquals(0L, incrementalOverSlices(new byte[0]), "empty payload checksums to zero");
    assertEquals(
        1L, incrementalOverSlices(new byte[] {0x01}), "one byte is its little-endian value");
    assertEquals(
        0x0807060504030201L,
        incrementalOverSlices(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}),
        "eight bytes are one little-endian long");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 7, 8, 9, 15, 16, 17, 23, 24, 25})
  void everySplitOfEverySmallLengthMatchesTheContiguousChecksum(final int length) {
    final byte[] payload = pattern(length);
    final long contiguous = PayloadChecksum.compute(bufferOf(payload), 16, length);

    assertEquals(contiguous, incrementalOverSlices(payload), "single slice, length " + length);
    for (int split = 0; split <= length; split++) {
      assertEquals(
          contiguous,
          incrementalOverSlices(payload, split),
          "length " + length + " split at " + split);
    }
    for (int a = 0; a <= length; a++) {
      for (int b = a; b <= length; b++) {
        assertEquals(
            contiguous,
            incrementalOverSlices(payload, a, b),
            "length " + length + " split at " + a + "," + b);
      }
    }
  }

  @Test
  void fragmentShapedSlicesMatchOnFatSizedPayload() {
    final byte[] payload = pattern(32_000);
    final long contiguous = PayloadChecksum.compute(bufferOf(payload), 16, payload.length);

    // 1376-byte fragment bodies — the real MTU shape — plus deliberately hostile splits.
    final int[] fragmentEdges = new int[22];
    for (int i = 0; i < fragmentEdges.length; i++) {
      fragmentEdges[i] = (i + 1) * 1376;
    }
    assertEquals(contiguous, incrementalOverSlices(payload, fragmentEdges), "MTU-shaped slices");
    assertEquals(contiguous, incrementalOverSlices(payload, 1, 2, 3, 9, 31999), "hostile splits");
  }

  @Test
  void emptySlicesAreHarmlessAnywhere() {
    final byte[] payload = pattern(20);
    final long contiguous = PayloadChecksum.compute(bufferOf(payload), 16, payload.length);

    assertEquals(contiguous, incrementalOverSlices(payload, 0, 0, 5, 5, 20, 20));
  }

  @Test
  void resetClearsAllCarryState() {
    final IncrementalPayloadChecksum incremental = new IncrementalPayloadChecksum();
    final byte[] first = pattern(13);
    incremental.update(bufferOf(first), 16, first.length);
    incremental.finish();
    incremental.reset();

    final byte[] second = pattern(9);
    incremental.update(bufferOf(second), 16, second.length);
    assertEquals(PayloadChecksum.compute(bufferOf(second), 16, 9), incremental.finish());
  }
}
