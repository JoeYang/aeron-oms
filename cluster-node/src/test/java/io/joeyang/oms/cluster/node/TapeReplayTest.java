package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Journal tests for app-mode replay: the committed golden tape must replay through the bare state
 * machine with exactly the recorded count and outputs, deterministically, and a damaged tape must
 * fail loudly rather than pass on a partial replay.
 */
class TapeReplayTest {

  private static final String RUNFILES = System.getenv("TEST_SRCDIR") + "/_main/journal/";

  @TempDir File tempDir;

  private File unpackedArchiveDir() throws IOException, InterruptedException {
    final Process tar =
        new ProcessBuilder(
                "tar", "-xzf", RUNFILES + "heartbeats-v1.tar.gz", "-C", tempDir.getAbsolutePath())
            .inheritIO()
            .start();
    assertEquals(0, tar.waitFor(), "fixture tarball must unpack");
    return new File(tempDir, "archive");
  }

  private static long manifestCount() throws IOException {
    for (final String line : Files.readAllLines(Path.of(RUNFILES + "heartbeats-v1.manifest.txt"))) {
      if (line.startsWith("messages:")) {
        return Long.parseLong(line.substring("messages:".length()).trim());
      }
    }
    throw new AssertionError("manifest has no messages line");
  }

  private static long[] goldenOutputs() throws IOException {
    return Files.readAllLines(Path.of(RUNFILES + "heartbeats-v1.golden-outputs.txt")).stream()
        .mapToLong(Long::parseLong)
        .toArray();
  }

  @Test
  void latencyReportIncludesDeepTailPercentile() {
    final LatencyHistogram histogram = new LatencyHistogram();
    histogram.record(20);

    final String report = TapeReplayMain.latencyReport(histogram);

    assertTrue(report.startsWith("apply-latency: n=1 "), report);
    assertTrue(report.contains(" p99.9="), report);
    assertTrue(report.contains(" p99.99="), report);
    assertTrue(report.contains(" max="), report);
  }

  @Test
  void replayAppliesEveryRecordedHeartbeatAndEchoesTheGoldenOutputs() throws Exception {
    final TapeReplay.Result result = TapeReplay.replay(unpackedArchiveDir());

    assertEquals(manifestCount(), result.heartbeats(), "every recorded heartbeat applies");
    assertArrayEquals(
        goldenOutputs(),
        result.echoedTimestamps(),
        "replay echoes exactly the sequenced timestamps captured at record time");
  }

  @Test
  void countOnlyReplayCountsEveryHeartbeatWithoutCapturingEchoes() throws Exception {
    final TapeReplay.Result result = TapeReplay.replay(unpackedArchiveDir(), false);

    assertEquals(manifestCount(), result.heartbeats(), "every recorded heartbeat still applies");
    assertEquals(
        0,
        result.echoedTimestamps().length,
        "count-only replay must not accumulate echoed timestamps");
  }

  @Test
  void capturingReplayEntryPointStillEchoesTheGoldenOutputs() throws Exception {
    final TapeReplay.Result result = TapeReplay.replay(unpackedArchiveDir(), true);

    assertEquals(manifestCount(), result.heartbeats(), "every recorded heartbeat applies");
    assertArrayEquals(
        goldenOutputs(),
        result.echoedTimestamps(),
        "captureEchoes=true must behave exactly like the single-argument replay");
  }

  @Test
  void replayingTwiceIsIdentical() throws Exception {
    final File archiveDir = unpackedArchiveDir();

    final TapeReplay.Result first = TapeReplay.replay(archiveDir);
    final TapeReplay.Result second = TapeReplay.replay(archiveDir);

    assertEquals(first.heartbeats(), second.heartbeats());
    assertArrayEquals(first.echoedTimestamps(), second.echoedTimestamps());
  }

  @Test
  void truncatedTapeFailsLoudly() throws Exception {
    final File archiveDir = unpackedArchiveDir();
    final File[] segments = archiveDir.listFiles((dir, name) -> name.endsWith(".rec"));
    assertEquals(1, segments.length, "the golden tape holds one segment");
    // Cut inside the recorded data, off frame alignment, so the damage cannot look like
    // a clean end of the recording.
    try (FileChannel channel = FileChannel.open(segments[0].toPath(), StandardOpenOption.WRITE)) {
      channel.truncate(100_010);
    }

    assertThrows(IllegalStateException.class, () -> TapeReplay.replay(archiveDir));
  }
}
