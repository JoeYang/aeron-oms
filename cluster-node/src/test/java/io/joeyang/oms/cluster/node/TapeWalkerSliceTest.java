package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aeron.logbuffer.FrameDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Slice mode must be indistinguishable from the copy path in what it says a chain contained: the
 * concatenated slices are byte-identical to the reassembled entry, and every strictness guarantee
 * of the walk holds unchanged.
 */
class TapeWalkerSliceTest {

  private static final int CHUNK = 1000;

  @TempDir File tempDir;

  /** Captures both delivery paths side by side. */
  private static final class Capture
      implements TapeWalker.EntryHandler, TapeWalker.ChainSliceHandler {
    final List<byte[]> copied = new ArrayList<>();
    final List<Long> copiedTimestamps = new ArrayList<>();
    final List<Long> sliceTimestamps = new ArrayList<>();
    final List<Long> slicePositions = new ArrayList<>();
    final ByteArrayOutputStream sliceBytes = new ByteArrayOutputStream();
    int sliceStarts;
    int sliceEnds;
    int otherEntries;

    @Override
    public void onSessionMessage(
        final long logPosition,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length) {
      final byte[] entry = new byte[length];
      buffer.getBytes(offset, entry);
      copied.add(entry);
      copiedTimestamps.add(timestamp);
    }

    @Override
    public void onOtherEntry(final long logPosition, final int templateId) {
      otherEntries++;
    }

    @Override
    public void onFragmentedSessionMessageStart(final long logPosition, final long timestamp) {
      sliceStarts++;
      slicePositions.add(logPosition);
      sliceTimestamps.add(timestamp);
    }

    @Override
    public void onPayloadSlice(final DirectBuffer buffer, final int offset, final int length) {
      final byte[] slice = new byte[length];
      buffer.getBytes(offset, slice);
      sliceBytes.writeBytes(slice);
    }

    @Override
    public void onFragmentedSessionMessageEnd() {
      sliceEnds++;
    }
  }

  private File archiveWith(final UnsafeBuffer segment, final int length) throws IOException {
    SyntheticTapes.segmentFile(tempDir, segment, length);
    return tempDir;
  }

  @Test
  void slicesConcatenateToTheCopyPathEntry() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(42L, SyntheticTapes.pattern(7013));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[16 * 1024]);
    SyntheticTapes.writeFragmented(segment, 0, entry, CHUNK);
    final File archive = archiveWith(segment, segment.capacity());

    final Capture copyRun = new Capture();
    TapeWalker.walk(archive, copyRun);
    final Capture sliceRun = new Capture();
    TapeWalker.walk(archive, sliceRun, sliceRun);

    assertEquals(1, copyRun.copied.size(), "copy path sees the chain");
    assertEquals(1, sliceRun.sliceStarts);
    assertEquals(1, sliceRun.sliceEnds);
    assertEquals(0, sliceRun.copied.size(), "sliced chain must not also be copied");
    assertArrayEquals(
        copyRun.copied.get(0),
        sliceRun.sliceBytes.toByteArray(),
        "concatenated slices are byte-identical to the reassembled entry");
    assertEquals(copyRun.copiedTimestamps.get(0), sliceRun.sliceTimestamps.get(0));
  }

  @Test
  void unfragmentedEntriesStillUseTheEntryHandler() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(7L, SyntheticTapes.pattern(100));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[4 * 1024]);
    SyntheticTapes.writeFrame(
        segment,
        0,
        (byte) (FrameDescriptor.BEGIN_FRAG_FLAG | FrameDescriptor.END_FRAG_FLAG),
        entry,
        0,
        entry.length);
    final File archive = archiveWith(segment, segment.capacity());

    final Capture capture = new Capture();
    TapeWalker.walk(archive, capture, capture);

    assertEquals(1, capture.copied.size(), "unfragmented delivery is unchanged");
    assertEquals(0, capture.sliceStarts);
  }

  @Test
  void beginFragmentTooShortForTheSessionHeadersFallsBackToTheCopyPath() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(9L, SyntheticTapes.pattern(200));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[8 * 1024]);
    SyntheticTapes.writeFragmented(segment, 0, entry, 16);
    final File archive = archiveWith(segment, segment.capacity());

    final Capture capture = new Capture();
    TapeWalker.walk(archive, capture, capture);

    assertEquals(1, capture.copied.size(), "short BEGIN falls back to reassembly");
    assertEquals(0, capture.sliceStarts);
  }

  @Test
  void chainLeftOpenAtSegmentEndStillFailsLoudlyInSliceMode() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(1L, SyntheticTapes.pattern(3000));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[8 * 1024]);
    // Write only the BEGIN fragment of a longer chain: the chain never closes.
    SyntheticTapes.writeFrame(segment, 0, FrameDescriptor.BEGIN_FRAG_FLAG, entry, 0, CHUNK);
    final File archive = archiveWith(segment, segment.capacity());

    final Capture capture = new Capture();
    final IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> TapeWalker.walk(archive, capture, capture));
    assertTrue(e.getMessage().contains("chain"), e.getMessage());
  }

  @Test
  void chainReopenedBeforeEndStillFailsLoudlyInSliceMode() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(1L, SyntheticTapes.pattern(3000));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[8 * 1024]);
    int offset =
        SyntheticTapes.writeFrame(segment, 0, FrameDescriptor.BEGIN_FRAG_FLAG, entry, 0, CHUNK);
    SyntheticTapes.writeFrame(segment, offset, FrameDescriptor.BEGIN_FRAG_FLAG, entry, 0, CHUNK);
    final File archive = archiveWith(segment, segment.capacity());

    final Capture capture = new Capture();
    final IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> TapeWalker.walk(archive, capture, capture));
    assertTrue(e.getMessage().contains("reopened"), e.getMessage());
  }
}
