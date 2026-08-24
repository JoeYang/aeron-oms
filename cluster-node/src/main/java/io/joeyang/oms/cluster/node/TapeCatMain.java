package io.joeyang.oms.cluster.node;

import java.io.File;

/**
 * Entry point for the tape viewer.
 *
 * <p>Usage: {@code tape-cat <archive-dir> [--json]} — prints one line per recorded log entry;
 * {@code --json} switches to JSONL for piping into {@code jq} or {@code grep}.
 */
public final class TapeCatMain {

  private TapeCatMain() {}

  /**
   * Process entry point.
   *
   * @param args archive directory, optionally followed by {@code --json}
   */
  public static void main(final String[] args) {
    final boolean json = args.length == 2 && "--json".equals(args[1]);
    if (args.length < 1 || args.length > 2 || (args.length == 2 && !json)) {
      System.err.println("usage: tape-cat <archive-dir> [--json]");
      System.exit(2);
    }
    TapeCat.print(new File(args[0]), json, System.out);
  }
}
