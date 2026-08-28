package io.joeyang.oms.cluster.node;

import io.aeron.cluster.codecs.MessageHeaderEncoder;
import io.aeron.cluster.codecs.SessionMessageHeaderEncoder;
import io.aeron.logbuffer.FrameDescriptor;
import io.aeron.protocol.DataHeaderFlyweight;
import io.aeron.protocol.HeaderFlyweight;
import io.joeyang.oms.sbe.FatHeartbeatEncoder;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import org.agrona.BitUtil;
import org.agrona.concurrent.UnsafeBuffer;

/** Builders for synthetic recorded segments, frame by frame — the fat-tape test fixture. */
final class SyntheticTapes {

  private SyntheticTapes() {}

  static byte[] pattern(final int length) {
    final byte[] payload = new byte[length];
    for (int i = 0; i < length; i++) {
      payload[i] = (byte) (i * 31 + 7);
    }
    return payload;
  }

  /** [cluster messageHeader][SessionMessageHeader][FatHeartbeat frame] — one log entry body. */
  static byte[] fatEntry(final long timestamp, final byte[] payload) {
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

  static int writeFrame(
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

  /** Writes the entry as a BEGIN/…/END chain of {@code chunk}-byte fragments. */
  static int writeFragmented(
      final UnsafeBuffer segment, final int start, final byte[] entry, final int chunk) {
    int offset = start;
    int written = 0;
    while (written < entry.length) {
      final int bodyLength = Math.min(chunk, entry.length - written);
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

  static File segmentFile(final File dir, final UnsafeBuffer segment, final int length)
      throws IOException {
    final File file = new File(dir, "0-0.rec");
    final byte[] bytes = new byte[length];
    segment.getBytes(0, bytes);
    Files.write(file.toPath(), bytes);
    return file;
  }
}
