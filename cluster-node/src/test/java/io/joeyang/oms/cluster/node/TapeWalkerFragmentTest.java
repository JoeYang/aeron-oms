package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aeron.logbuffer.FrameDescriptor;
import io.joeyang.oms.sbe.FatHeartbeatDecoder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fragment reassembly against synthetic segments built frame by frame. A fat entry spans a
 * BEGIN/…/END chain; the walker must hand the state machine the assembled entry, and a chain left
 * open — by truncation or by a segment ending mid-chain — must fail loudly, never apply a partial
 * payload.
 */
class TapeWalkerFragmentTest {

  private static final int CHUNK = 1000;

  @TempDir File tempDir;

  private record Delivered(long timestamp, byte[] payload) {}

  private List<Delivered> walk() {
    final List<Delivered> delivered = new ArrayList<>();
    TapeWalker.walk(
        tempDir,
        new TapeWalker.EntryHandler() {
          @Override
          public void onSessionMessage(
              final long logPosition,
              final long timestamp,
              final DirectBuffer buffer,
              final int offset,
              final int length) {
            final io.joeyang.oms.sbe.MessageHeaderDecoder header =
                new io.joeyang.oms.sbe.MessageHeaderDecoder();
            header.wrap(buffer, offset);
            final FatHeartbeatDecoder fat = new FatHeartbeatDecoder();
            fat.wrap(
                buffer, offset + header.encodedLength(), header.blockLength(), header.version());
            final byte[] payload = new byte[fat.payloadLength()];
            fat.getPayload(payload, 0, payload.length);
            delivered.add(new Delivered(timestamp, payload));
          }

          @Override
          public void onOtherEntry(final long logPosition, final int templateId) {}
        });
    return delivered;
  }

  @Test
  void reassemblesFragmentChainIntoOneEntry() throws IOException {
    final byte[] payload = SyntheticTapes.pattern(32_000);
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[128 * 1024]);
    SyntheticTapes.writeFragmented(segment, 0, SyntheticTapes.fatEntry(9_000L, payload), CHUNK);
    SyntheticTapes.segmentFile(tempDir, segment, segment.capacity());

    final List<Delivered> delivered = walk();

    assertEquals(1, delivered.size(), "one chain is one entry");
    assertEquals(9_000L, delivered.get(0).timestamp(), "sequenced time from the first fragment");
    assertArrayEquals(payload, delivered.get(0).payload(), "payload survives reassembly intact");
  }

  @Test
  void chainLeftOpenAtSegmentEndFailsLoudly() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(9_000L, SyntheticTapes.pattern(32_000));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[128 * 1024]);
    // BEGIN and one middle fragment only — the END never arrives before the zeroed tail.
    final int offset =
        SyntheticTapes.writeFrame(segment, 0, FrameDescriptor.BEGIN_FRAG_FLAG, entry, 0, CHUNK);
    SyntheticTapes.writeFrame(segment, offset, (byte) 0, entry, CHUNK, CHUNK);
    SyntheticTapes.segmentFile(tempDir, segment, segment.capacity());

    final IllegalStateException e = assertThrows(IllegalStateException.class, this::walk);
    assertTrue(e.getMessage().contains("chain"), e.getMessage());
  }

  @Test
  void continuationWithoutBeginFailsLoudly() throws IOException {
    final byte[] entry = SyntheticTapes.fatEntry(9_000L, SyntheticTapes.pattern(4_000));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[64 * 1024]);
    SyntheticTapes.writeFrame(segment, 0, FrameDescriptor.END_FRAG_FLAG, entry, 0, CHUNK);
    SyntheticTapes.segmentFile(tempDir, segment, segment.capacity());

    final IllegalStateException e = assertThrows(IllegalStateException.class, this::walk);
    assertTrue(e.getMessage().contains("chain"), e.getMessage());
  }
}
