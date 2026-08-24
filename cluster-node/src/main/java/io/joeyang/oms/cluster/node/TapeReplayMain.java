package io.joeyang.oms.cluster.node;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Entry point for app-mode tape replay: applies a golden tape to the bare state machine and
 * verifies the outcome against the tape's manifest and golden outputs.
 *
 * <p>Usage: {@code tape-replay <archive-dir> <manifest> <golden-outputs|-> [--warmup
 * <archive-dir>]}. A golden argument of {@code -} verifies the count only (for tapes recorded with
 * {@code SKIP_GOLDENS}). {@code --warmup} replays another tape first in this same JVM, unreported,
 * so the measured replay runs on a warm JIT. Exits non-zero on any mismatch — a partial or
 * divergent replay must never look like success.
 */
public final class TapeReplayMain {

  private TapeReplayMain() {}

  /**
   * Process entry point.
   *
   * @param args archive directory, manifest path, golden-outputs path or {@code -}, optionally
   *     {@code --warmup} and a warmup archive directory
   * @throws IOException if the manifest or golden outputs cannot be read
   */
  public static void main(final String[] args) throws IOException {
    final boolean withWarmup = args.length == 5 && "--warmup".equals(args[3]);
    if (args.length != 3 && !withWarmup) {
      System.err.println(
          "usage: tape-replay <archive-dir> <manifest> <golden-outputs|-> "
              + "[--warmup <archive-dir>]");
      System.exit(2);
    }

    if (withWarmup) {
      final TapeReplay.Result warmup = TapeReplay.replay(new File(args[4]));
      System.out.printf(
          Locale.ROOT, "warmup: %d heartbeats replayed, unreported%n", warmup.heartbeats());
    }

    final TapeReplay.Result result = TapeReplay.replay(new File(args[0]));

    long expected = -1;
    for (final String line : Files.readAllLines(Path.of(args[1]))) {
      if (line.startsWith("messages:")) {
        expected = Long.parseLong(line.substring("messages:".length()).trim());
      }
    }
    final boolean countOnly = "-".equals(args[2]);
    final long[] golden =
        countOnly
            ? null
            : Files.readAllLines(Path.of(args[2])).stream().mapToLong(Long::parseLong).toArray();

    final double seconds = result.elapsedNanos() / 1e9;
    System.out.printf(
        Locale.ROOT,
        "app-replay: %d heartbeats (+%d other entries) in %.3f s = %,.0f msg/s%n",
        result.heartbeats(),
        result.otherEntries(),
        seconds,
        result.heartbeats() / seconds);

    if (result.heartbeats() != expected
        || (!countOnly && !Arrays.equals(golden, result.echoedTimestamps()))) {
      System.err.println(
          "REPLAY MISMATCH: manifest says " + expected + " messages; outputs must equal golden");
      System.exit(1);
    }
    System.out.println(
        countOnly
            ? "REPLAY OK: count matches the manifest (count-only tape)"
            : "REPLAY OK: count and outputs match the golden files");
  }
}
