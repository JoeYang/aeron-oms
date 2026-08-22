package io.joeyang.oms.cluster.service;

import io.aeron.DirectBufferVector;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.BufferClaim;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

/**
 * A {@link ClientSession} fake that honours the real offer contract: scripted results are returned
 * in order — including the negative codes the real transport produces — and bytes are captured only
 * when an offer is accepted. A fake that only ever succeeds would let the service pass tests
 * against a contract the real transport does not honour.
 */
final class FakeClientSession implements ClientSession {

  private final List<byte[]> delivered = new ArrayList<>();
  private long[] scriptedResults = new long[0];
  private int offerCount;

  /** Results for successive offers; once exhausted, every further offer is accepted. */
  void scriptOfferResults(final long... results) {
    this.scriptedResults = results;
  }

  int offerCount() {
    return offerCount;
  }

  List<byte[]> delivered() {
    return delivered;
  }

  @Override
  public long offer(final DirectBuffer buffer, final int offset, final int length) {
    final long result = offerCount < scriptedResults.length ? scriptedResults[offerCount] : length;
    offerCount++;
    if (result >= 0) {
      final byte[] copy = new byte[length];
      buffer.getBytes(offset, copy);
      delivered.add(copy);
    }
    return result;
  }

  @Override
  public long offer(final DirectBufferVector[] vectors) {
    throw new UnsupportedOperationException("vectored offer is not part of the MVP contract");
  }

  @Override
  public long tryClaim(final int length, final BufferClaim bufferClaim) {
    throw new UnsupportedOperationException("tryClaim is not part of the MVP contract");
  }

  @Override
  public long id() {
    return 7;
  }

  @Override
  public int responseStreamId() {
    return 0;
  }

  @Override
  public String responseChannel() {
    return "aeron:ipc";
  }

  @Override
  public byte[] encodedPrincipal() {
    return new byte[0];
  }

  @Override
  public void close() {}

  @Override
  public boolean isClosing() {
    return false;
  }
}
