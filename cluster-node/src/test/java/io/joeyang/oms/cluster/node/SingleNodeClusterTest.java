package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import io.joeyang.oms.cluster.service.OmsClusteredService;
import io.joeyang.oms.sbe.HeartbeatEncoder;
import io.joeyang.oms.sbe.MessageHeaderEncoder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests against a real single-node cluster: real election, real journal, real commit
 * path. Each test owns its node's lifecycle and uses its own port range so tests cannot interfere
 * with each other or with a developer-run cluster on the default ports.
 */
class SingleNodeClusterTest {

  private static final long ROUND_TRIP_TIMEOUT_MS = 10_000;

  @TempDir File tempDir;

  private static SingleNodeCluster launch(
      final File base, final boolean clean, final int port, final ClusteredService service) {
    return SingleNodeCluster.launch(new SingleNodeCluster.Config(base, clean, port, service));
  }

  private static long roundTrip(final SingleNodeCluster node, final int basePort) {
    final AtomicLong echoed = new AtomicLong(Long.MIN_VALUE);
    try (AeronCluster client =
        AeronCluster.connect(
            new AeronCluster.Context()
                .aeronDirectoryName(node.aeronDirectoryName())
                .ingressChannel("aeron:udp?term-length=64k")
                .ingressEndpoints("0=localhost:" + basePort)
                .egressChannel("aeron:udp?endpoint=localhost:0")
                .egressListener(
                    (sessionId, timestamp, buffer, offset, length, header) ->
                        echoed.set(decodeTimestamp(buffer, offset))))) {

      final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
      final int length = encodeHeartbeat(buffer);
      final long deadline = System.currentTimeMillis() + ROUND_TRIP_TIMEOUT_MS;
      while (client.offer(buffer, 0, length) < 0) {
        if (System.currentTimeMillis() > deadline) {
          fail("ingress offer not accepted within " + ROUND_TRIP_TIMEOUT_MS + " ms");
        }
        Thread.yield();
      }
      while (echoed.get() == Long.MIN_VALUE) {
        if (System.currentTimeMillis() > deadline) {
          fail("no sequenced echo within " + ROUND_TRIP_TIMEOUT_MS + " ms");
        }
        client.pollEgress();
        Thread.yield();
      }
      return echoed.get();
    }
  }

  private static int encodeHeartbeat(final UnsafeBuffer buffer) {
    final MessageHeaderEncoder header = new MessageHeaderEncoder();
    final HeartbeatEncoder heartbeat = new HeartbeatEncoder();
    heartbeat.wrapAndApplyHeader(buffer, 0, header).timestampNanos(0L);
    return header.encodedLength() + heartbeat.encodedLength();
  }

  private static long decodeTimestamp(final DirectBuffer buffer, final int offset) {
    final io.joeyang.oms.sbe.MessageHeaderDecoder header =
        new io.joeyang.oms.sbe.MessageHeaderDecoder();
    header.wrap(buffer, offset);
    final io.joeyang.oms.sbe.HeartbeatDecoder heartbeat = new io.joeyang.oms.sbe.HeartbeatDecoder();
    heartbeat.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
    return heartbeat.timestampNanos();
  }

  @Test
  void roundTripCarriesEpochNanoseconds() {
    try (SingleNodeCluster node = launch(tempDir, true, 21102, new OmsClusteredService())) {
      final long sequencedNanos = roundTrip(node, 21102);

      final long nowNanos = System.currentTimeMillis() * 1_000_000L;
      final long hourNanos = TimeUnit.HOURS.toNanos(1);
      assertTrue(
          sequencedNanos > nowNanos - hourNanos && sequencedNanos < nowNanos + hourNanos,
          "sequenced timestamp should be epoch nanoseconds near now: " + sequencedNanos);
    }
  }

  @Test
  void restartWithoutCleanRetainsTheJournal() throws IOException {
    final File archiveDir = new File(new File(tempDir, "node-0"), "archive");

    try (SingleNodeCluster node = launch(tempDir, true, 21112, new OmsClusteredService())) {
      roundTrip(node, 21112);
    }
    final File marker = new File(archiveDir, "retention-marker");
    assertTrue(marker.createNewFile(), "marker written into the journal directory");

    try (SingleNodeCluster node = launch(tempDir, false, 21112, new OmsClusteredService())) {
      roundTrip(node, 21112);
      assertTrue(marker.exists(), "restart without the clean flag must not touch the journal");
      assertTrue(
          Files.list(archiveDir.toPath()).count() > 1,
          "recording segments retained beside the marker");
    }
  }

  @Test
  void cleanFlagWipesTheJournal() throws IOException {
    final File archiveDir = new File(new File(tempDir, "node-0"), "archive");

    try (SingleNodeCluster node = launch(tempDir, true, 21122, new OmsClusteredService())) {
      roundTrip(node, 21122);
    }
    final File marker = new File(archiveDir, "retention-marker");
    assertTrue(marker.createNewFile());

    try (SingleNodeCluster node = launch(tempDir, true, 21122, new OmsClusteredService())) {
      roundTrip(node, 21122);
      assertFalse(marker.exists(), "the clean flag is an explicit reset and wipes prior state");
    }
  }

  @Test
  void throwingStateMachineStopsTheNode()
      throws InterruptedException, ExecutionException, TimeoutException {
    try (SingleNodeCluster node = launch(tempDir, true, 21132, new PoisonService())) {
      try (AeronCluster client =
          AeronCluster.connect(
              new AeronCluster.Context()
                  .aeronDirectoryName(node.aeronDirectoryName())
                  .ingressChannel("aeron:udp?term-length=64k")
                  .ingressEndpoints("0=localhost:21132")
                  .egressChannel("aeron:udp?endpoint=localhost:0"))) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[8]);
        while (client.offer(buffer, 0, 8) < 0) {
          Thread.yield();
        }
        final Throwable failure = node.failure().get(10, TimeUnit.SECONDS);
        assertInstanceOf(IllegalStateException.class, failure, "the service's own exception");
        assertEquals("poison", failure.getMessage());
      }
    }
  }

  /** Throws on the first applied message — stands in for any state-machine invariant failure. */
  private static final class PoisonService implements ClusteredService {
    @Override
    public void onSessionMessage(
        final ClientSession session,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header) {
      throw new IllegalStateException("poison");
    }

    @Override
    public void onStart(final Cluster cluster, final Image snapshotImage) {}

    @Override
    public void onSessionOpen(final ClientSession session, final long timestamp) {}

    @Override
    public void onSessionClose(
        final ClientSession session, final long timestamp, final CloseReason closeReason) {}

    @Override
    public void onTimerEvent(final long correlationId, final long timestamp) {}

    @Override
    public void onTakeSnapshot(final ExclusivePublication snapshotPublication) {}

    @Override
    public void onRoleChange(final Cluster.Role newRole) {}

    @Override
    public void onTerminate(final Cluster cluster) {}
  }
}
