package io.joeyang.oms.cluster.node;

import io.joeyang.oms.core.affinity.LinuxThreadAffinity;
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
 * <p>Usage: {@code tape-replay <archive-dir> <manifest> <golden-outputs|-> [--warmup <archive-dir>]
 * [--latency] [--pin <cpu>]}. A golden argument of {@code -} verifies the count only (for tapes
 * recorded with {@code SKIP_GOLDENS}). {@code --warmup} replays another tape first in this same
 * JVM, unreported, so the measured replay runs on a warm JIT. {@code --pin} moves the applying
 * thread onto the given CPU before any replay, verified, or exits loudly. Exits non-zero on any
 * mismatch — a partial or divergent replay must never look like success.
 */
public final class TapeReplayMain {

  private TapeReplayMain() {}

  static final int NO_PIN = -1;

  /** Parsed command line: trailing flags after the three positional arguments. */
  record Options(String warmupDir, boolean withLatency, int pinCpu, boolean ok) {}

  static Options parseOptions(final String[] args) {
    String warmupDir = null;
    boolean withLatency = false;
    int pinCpu = NO_PIN;
    boolean usageOk = args.length >= 3;
    for (int i = 3; usageOk && i < args.length; i++) {
      if ("--warmup".equals(args[i]) && i + 1 < args.length) {
        warmupDir = args[++i];
      } else if ("--latency".equals(args[i])) {
        withLatency = true;
      } else if ("--pin".equals(args[i]) && i + 1 < args.length) {
        try {
          pinCpu = Integer.parseInt(args[++i]);
        } catch (final NumberFormatException e) {
          usageOk = false;
        }
      } else {
        usageOk = false;
      }
    }
    return new Options(warmupDir, withLatency, pinCpu, usageOk);
  }

  /**
   * Process entry point.
   *
   * @param args archive directory, manifest path, golden-outputs path or {@code -}, then optional
   *     flags: {@code --warmup <archive-dir>}, {@code --latency}, {@code --pin <cpu>}
   * @throws IOException if the manifest or golden outputs cannot be read
   */
  public static void main(final String[] args) throws IOException {
    final Options options = parseOptions(args);
    if (!options.ok()) {
      System.err.println(
          "usage: tape-replay <archive-dir> <manifest> <golden-outputs|-> "
              + "[--warmup <archive-dir>] [--latency] [--pin <cpu>]");
      System.exit(2);
    }

    // The pin comes first: main is the applying thread, and a pin that fails must be a loud
    // exit before any replay work, never a silently unpinned measurement.
    if (options.pinCpu() != NO_PIN) {
      try {
        new LinuxThreadAffinity().pinCurrentThread(options.pinCpu());
      } catch (final RuntimeException e) {
        System.err.println("PIN FAILED: " + e.getMessage());
        System.exit(3);
      }
      System.out.printf(Locale.ROOT, "pinned: cpu %d (affinity verified)%n", options.pinCpu());
    }

    // Count-only replays skip echo capture entirely: nothing reads the timestamps, and
    // storing 100M of them costs hundreds of megabytes of list growth. The warmup uses the
    // same mode so it warms exactly the path the measured replay takes.
    final boolean countOnly = "-".equals(args[2]);
    if (options.warmupDir() != null) {
      final TapeReplay.Result warmup = TapeReplay.replay(new File(options.warmupDir()), !countOnly);
      System.out.printf(
          Locale.ROOT, "warmup: %d heartbeats replayed, unreported%n", warmup.heartbeats());
    }

    final LatencyHistogram latency = options.withLatency() ? new LatencyHistogram() : null;
    final TapeReplay.Result result = TapeReplay.replay(new File(args[0]), !countOnly, latency);

    long expected = -1;
    for (final String line : Files.readAllLines(Path.of(args[1]))) {
      if (line.startsWith("messages:")) {
        expected = Long.parseLong(line.substring("messages:".length()).trim());
      }
    }
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
    if (latency != null) {
      System.out.println(latencyReport(latency));
    }

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

  static String latencyReport(final LatencyHistogram latency) {
    return String.format(
        Locale.ROOT,
        "apply-latency: n=%d p50=%d p90=%d p99=%d p99.9=%d p99.99=%d max=%d ns "
            + "(timing adds two nanoTime reads per apply)",
        latency.count(),
        latency.valueAtPercentile(50.0),
        latency.valueAtPercentile(90.0),
        latency.valueAtPercentile(99.0),
        latency.valueAtPercentile(99.9),
        latency.valueAtPercentile(99.99),
        latency.max());
  }
}
