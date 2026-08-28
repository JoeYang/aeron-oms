package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aeron.cluster.codecs.MessageHeaderEncoder;
import io.aeron.cluster.codecs.SessionMessageHeaderEncoder;
import io.aeron.logbuffer.FrameDescriptor;
import io.aeron.protocol.DataHeaderFlyweight;
import io.aeron.protocol.HeaderFlyweight;
import io.joeyang.oms.sbe.FatHeartbeatDecoder;
import io.joeyang.oms.sbe.FatHeartbeatEncoder;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.agrona.BitUtil;
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

  private static byte[] pattern(final int length) {
    final byte[] payload = new byte[length];
    for (int i = 0; i < length; i++) {
      payload[i] = (byte) (i * 31 + 7);
    }
    return payload;
  }

  /** [cluster messageHeader][SessionMessageHeader][FatHeartbeat frame] — the log entry body. */
  private static byte[] fatEntry(final long timestamp, final byte[] payload) {
    final UnsafeBuffer scratch = new UnsafeBuffer(new byte[80_000]);
    final MessageHeaderEncoder clusterHeader = new MessageHeaderEncoder();
    final SessionMessageHeaderEncoder sessionHeader = new SessionMessageHeaderEncoder();
    sessionHeader
        .wrapAndApplyHeader(scratch, 0, clusterHeader)
        .leadershipTermId(0)
        .clusterSessionId(7)
        .timestamp(timestamp);
    final int sessionEnd = clusterHeader.encodedLength() + sessionHeader.encodedLength();

    final io.joeyang.oms.sbe.MessageHeaderEncoder appHeader =
        new io.joeyang.oms.sbe.MessageHeaderEncoder();
    final FatHeartbeatEncoder fat = new FatHeartbeatEncoder();
    fat.wrapAndApplyHeader(scratch, sessionEnd, appHeader).timestampNanos(0L);
    fat.putPayload(payload, 0, payload.length);
    final int total = sessionEnd + appHeader.encodedLength() + fat.encodedLength();

    final byte[] entry = new byte[total];
    scratch.getBytes(0, entry);
    return entry;
  }

  private static int writeFrame(
      final UnsafeBuffer segment,
      final int offset,
      final byte flags,
      final byte[] body,
      final int bodyOffset,
      final int bodyLength) {
    final int frameLength = DataHeaderFlyweight.HEADER_LENGTH + bodyLength;
    segment.putInt(
        offset + HeaderFlyweight.FRAME_LENGTH_FIELD_OFFSET, frameLength, ByteOrder.LITTLE_ENDIAN);
    segment.putByte(offset + HeaderFlyweight.FLAGS_FIELD_OFFSET, flags);
    segment.putShort(
        offset + HeaderFlyweight.TYPE_FIELD_OFFSET,
        (short) HeaderFlyweight.HDR_TYPE_DATA,
        ByteOrder.LITTLE_ENDIAN);
    segment.putBytes(offset + DataHeaderFlyweight.HEADER_LENGTH, body, bodyOffset, bodyLength);
    return offset + BitUtil.align(frameLength, FrameDescriptor.FRAME_ALIGNMENT);
  }

  /** Writes the entry as a BEGIN/…/END chain of CHUNK-byte fragments; returns next offset. */
  private static int writeFragmented(final UnsafeBuffer segment, int offset, final byte[] entry) {
    int written = 0;
    while (written < entry.length) {
      final int bodyLength = Math.min(CHUNK, entry.length - written);
      byte flags = 0;
      if (written == 0) {
        flags |= FrameDescriptor.BEGIN_FRAG_FLAG;
      }
      if (written + bodyLength == entry.length) {
        flags |= FrameDescriptor.END_FRAG_FLAG;
      }
      offset = writeFrame(segment, offset, flags, entry, written, bodyLength);
      written += bodyLength;
    }
    return offset;
  }

  private File segmentFile(final UnsafeBuffer segment, final int length) throws IOException {
    final File file = new File(tempDir, "0-0.rec");
    final byte[] bytes = new byte[length];
    segment.getBytes(0, bytes);
    Files.write(file.toPath(), bytes);
    return file;
  }

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
    final byte[] payload = pattern(32_000);
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[128 * 1024]);
    writeFragmented(segment, 0, fatEntry(9_000L, payload));
    segmentFile(segment, segment.capacity());

    final List<Delivered> delivered = walk();

    assertEquals(1, delivered.size(), "one chain is one entry");
    assertEquals(9_000L, delivered.get(0).timestamp(), "sequenced time from the first fragment");
    assertArrayEquals(payload, delivered.get(0).payload(), "payload survives reassembly intact");
  }

  @Test
  void chainLeftOpenAtSegmentEndFailsLoudly() throws IOException {
    final byte[] entry = fatEntry(9_000L, pattern(32_000));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[128 * 1024]);
    // BEGIN and one middle fragment only — the END never arrives before the zeroed tail.
    int offset = writeFrame(segment, 0, FrameDescriptor.BEGIN_FRAG_FLAG, entry, 0, CHUNK);
    writeFrame(segment, offset, (byte) 0, entry, CHUNK, CHUNK);
    segmentFile(segment, segment.capacity());

    final IllegalStateException e = assertThrows(IllegalStateException.class, this::walk);
    assertTrue(e.getMessage().contains("chain"), e.getMessage());
  }

  @Test
  void continuationWithoutBeginFailsLoudly() throws IOException {
    final byte[] entry = fatEntry(9_000L, pattern(4_000));
    final UnsafeBuffer segment = new UnsafeBuffer(new byte[64 * 1024]);
    writeFrame(segment, 0, FrameDescriptor.END_FRAG_FLAG, entry, 0, CHUNK);
    segmentFile(segment, segment.capacity());

    final IllegalStateException e = assertThrows(IllegalStateException.class, this::walk);
    assertTrue(e.getMessage().contains("chain"), e.getMessage());
  }
}
