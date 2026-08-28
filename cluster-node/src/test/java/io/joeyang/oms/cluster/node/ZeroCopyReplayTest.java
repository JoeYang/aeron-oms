package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The zero-copy replay's one obligation: a Result indistinguishable from the copy path's, on the
 * same tape. Everything else — the walker's slice fidelity, the incremental checksum's equivalence
 * — is pinned in its own tests; here the two whole pipelines face each other.
 */
class ZeroCopyReplayTest {

  private static final int CHUNK = 1376;

  @TempDir File tempDir;

  private File syntheticArchive(final int... payloadLengths) throws IOException {
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[256 * 1024]);
    int offset = 0;
    long timestamp = 1000;
    for (final int payloadLength : payloadLengths) {
      final byte[] entry =
          SyntheticTapes.fatEntry(timestamp++, SyntheticTapes.pattern(payloadLength));
      offset = SyntheticTapes.writeFragmented(segment, offset, entry, CHUNK);
    }
    SyntheticTapes.segmentFile(tempDir, segment, segment.capacity());
    return tempDir;
  }

  @Test
  void zeroCopyResultEqualsTheCopyPathResult() throws IOException {
    final File archive = syntheticArchive(32_000, 7013, 100, 32_000);

    final TapeReplay.Result copy = TapeReplay.replay(archive, true);
    final TapeReplay.Result zeroCopy = TapeReplay.replayZeroCopy(archive, true);

    assertEquals(copy.heartbeats(), zeroCopy.heartbeats());
    assertEquals(copy.otherEntries(), zeroCopy.otherEntries());
    assertArrayEquals(copy.echoedTimestamps(), zeroCopy.echoedTimestamps());
    assertArrayEquals(copy.echoedChecksums(), zeroCopy.echoedChecksums());
  }

  @Test
  void countOnlyModeCountsWithoutCapturing() throws IOException {
    final File archive = syntheticArchive(32_000, 32_000);

    final TapeReplay.Result result = TapeReplay.replayZeroCopy(archive, false);

    assertEquals(2, result.heartbeats());
    assertEquals(0, result.echoedTimestamps().length);
  }

  @Test
  void firstSliceTooShortForTheAppHeadersFailsLoudly() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(5L, SyntheticTapes.pattern(500));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[16 * 1024]);
    // 40-byte fragments: the BEGIN fragment holds the 32-byte session headers but only 8 more
    // bytes — not enough for the app header, block, and payload-length field.
    SyntheticTapes.writeFragmented(segment, 0, entry, 40);
    SyntheticTapes.segmentFile(tempDir, segment, segment.capacity());

    final IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> TapeReplay.replayZeroCopy(tempDir, true));
    assertTrue(e.getMessage().contains("first slice"), e.getMessage());
  }

  @Test
  void fragmentedNonFatTemplateFailsLoudly() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(5L, SyntheticTapes.pattern(5000));
    // Corrupt the app template id (bytes 34-35, little-endian, after the 32-byte session
    // headers and the 2-byte blockLength field).
    entry[34] = 99;
    entry[35] = 0;
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[16 * 1024]);
    SyntheticTapes.writeFragmented(segment, 0, entry, CHUNK);
    SyntheticTapes.segmentFile(tempDir, segment, segment.capacity());

    final IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> TapeReplay.replayZeroCopy(tempDir, true));
    assertTrue(e.getMessage().contains("template"), e.getMessage());
  }

  @Test
  void payloadLengthDisagreeingWithTheChainFailsLoudly() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(5L, SyntheticTapes.pattern(5000));
    // Shrink the declared payload length (2-byte little-endian field at offset 72, after
    // 32 bytes of session headers, the 8-byte app header, and the 32-byte block): the chain
    // then carries more bytes than the message claims.
    final UnsafeBuffer entryView = new UnsafeBuffer(entry);
    entryView.putShort(
        32
            + io.joeyang.oms.sbe.MessageHeaderEncoder.ENCODED_LENGTH
            + io.joeyang.oms.sbe.FatHeartbeatEncoder.BLOCK_LENGTH,
        (short) 4000,
        ByteOrder.LITTLE_ENDIAN);
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[16 * 1024]);
    SyntheticTapes.writeFragmented(segment, 0, entry, CHUNK);
    SyntheticTapes.segmentFile(tempDir, segment, segment.capacity());

    final IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> TapeReplay.replayZeroCopy(tempDir, true));
    assertTrue(e.getMessage().contains("payload"), e.getMessage());
  }
}
