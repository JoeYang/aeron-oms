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
 * <p>Usage: {@code tape-replay <archive-dir> <manifest> <golden-outputs>}. Exits non-zero on any
 * mismatch — a partial or divergent replay must never look like success.
 */
public final class TapeReplayMain {

  private TapeReplayMain() {}

  /**
   * Process entry point.
   *
   * @param args archive directory, manifest path, golden-outputs path
   * @throws IOException if the manifest or golden outputs cannot be read
   */
  public static void main(final String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("usage: tape-replay <archive-dir> <manifest> <golden-outputs>");
      System.exit(2);
    }

    final TapeReplay.Result result = TapeReplay.replay(new File(args[0]));

    long expected = -1;
    for (final String line : Files.readAllLines(Path.of(args[1]))) {
      if (line.startsWith("messages:")) {
        expected = Long.parseLong(line.substring("messages:".length()).trim());
      }
    }
    final long[] golden =
        Files.readAllLines(Path.of(args[2])).stream().mapToLong(Long::parseLong).toArray();

    final double seconds = result.elapsedNanos() / 1e9;
    System.out.printf(
        Locale.ROOT,
        "app-replay: %d heartbeats (+%d other entries) in %.3f s = %,.0f msg/s%n",
        result.heartbeats(),
        result.otherEntries(),
        seconds,
        result.heartbeats() / seconds);

    if (result.heartbeats() != expected || !Arrays.equals(golden, result.echoedTimestamps())) {
      System.err.println(
          "REPLAY MISMATCH: manifest says " + expected + " messages; outputs must equal golden");
      System.exit(1);
    }
    System.out.println("REPLAY OK: count and outputs match the golden files");
  }
}
