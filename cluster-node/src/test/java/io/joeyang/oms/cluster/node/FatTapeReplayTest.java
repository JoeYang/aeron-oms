package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fat replay end to end against a synthetic tape: the assembled entry reaches the state machine,
 * and the captured ack carries the sequenced timestamp and a checksum the test verifies with its
 * own independent rotate-xor reference.
 */
class FatTapeReplayTest {

  @TempDir File tempDir;

  /** Independent byte-wise reference of the service's checksum definition. */
  private static long referenceChecksum(final byte[] payload) {
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

  @Test
  void replayCapturesAckTimestampsAndChecksums() throws Exception {
    final byte[] payload = SyntheticTapes.pattern(32_000);
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[128 * 1024]);
    SyntheticTapes.writeFragmented(segment, 0, SyntheticTapes.fatEntry(5_000L, payload), 1408);
    SyntheticTapes.segmentFile(tempDir, segment, segment.capacity());

    final TapeReplay.Result result = TapeReplay.replay(tempDir, true);

    assertEquals(1, result.heartbeats(), "one fat entry applies as one echo");
    assertArrayEquals(new long[] {5_000L}, result.echoedTimestamps());
    assertArrayEquals(new long[] {referenceChecksum(payload)}, result.echoedChecksums());
  }

  @Test
  void goldenColumnsParseOneAndTwoColumnFiles() {
    final long[][] one = TapeReplayMain.parseGoldenColumns(List.of("100", "200"));
    assertArrayEquals(new long[] {100, 200}, one[0]);
    assertNull(one[1], "one-column goldens have no checksums");

    final long[][] two = TapeReplayMain.parseGoldenColumns(List.of("100 7", "200 -9"));
    assertArrayEquals(new long[] {100, 200}, two[0]);
    assertArrayEquals(new long[] {7, -9}, two[1]);
  }

  @Test
  void goldenMismatchNamesThePosition() {
    final String clean =
        TapeReplayMain.goldenMismatch(
            new long[] {1, 2}, new long[] {5, 6}, new long[] {1, 2}, new long[] {5, 6});
    assertNull(clean, "matching goldens produce no mismatch");

    final String mismatch =
        TapeReplayMain.goldenMismatch(
            new long[] {1, 2}, new long[] {5, 6}, new long[] {1, 2}, new long[] {5, 7});
    assertTrue(mismatch.contains("1"), "the mismatching position is named: " + mismatch);
    assertTrue(mismatch.contains("checksum"), mismatch);
  }
}
