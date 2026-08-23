package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aeron.cluster.codecs.TimerEventDecoder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the tape viewer: the golden tape must list every session message in log order with the
 * golden timestamps, JSONL lines must each parse standalone, and unknown entry kinds must print
 * rather than fail.
 */
class TapeCatTest {

  private static final String RUNFILES = System.getenv("TEST_SRCDIR") + "/_main/journal/";

  // The sequenced cluster timestamp (t=) is what the service echoed and what the golden
  // outputs record. The payload's timestampNanos is the gateway's send-time stamp — an
  // earlier, different value; the gap between the two is the sequencing delay.
  private static final Pattern SEQUENCED = Pattern.compile("t=(\\d+)");

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

  private static long[] goldenOutputs() throws IOException {
    return Files.readAllLines(Path.of(RUNFILES + "heartbeats-v1.golden-outputs.txt")).stream()
        .mapToLong(Long::parseLong)
        .toArray();
  }

  private String view(final File archiveDir, final boolean json) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    TapeCat.print(archiveDir, json, new PrintStream(out, true, StandardCharsets.UTF_8));
    return out.toString(StandardCharsets.UTF_8);
  }

  @Test
  void goldenTapeViewListsEverySessionMessageInOrder() throws Exception {
    final List<String> lines = view(unpackedArchiveDir(), false).lines().toList();

    final long[] golden = goldenOutputs();
    final long[] seen =
        lines.stream()
            .filter(l -> l.contains("session-message"))
            .mapToLong(
                l -> {
                  final Matcher m = SEQUENCED.matcher(l);
                  assertTrue(m.find(), "session line carries the sequenced timestamp: " + l);
                  return Long.parseLong(m.group(1));
                })
            .toArray();

    assertEquals(golden.length, seen.length, "one line per recorded message");
    org.junit.jupiter.api.Assertions.assertArrayEquals(golden, seen);
    assertEquals(golden.length + 3, lines.size(), "plus the non-session entries");
  }

  @Test
  void jsonLinesEachParseStandalone() throws Exception {
    final String output = view(unpackedArchiveDir(), true);
    final Path file = Files.writeString(tempDir.toPath().resolve("view.jsonl"), output);

    output.lines().forEach(l -> assertTrue(l.startsWith("{") && l.endsWith("}"), l));

    // jq consumes JSONL natively and fails loudly on any malformed line.
    final Process jq =
        new ProcessBuilder("jq", "-s", "length", file.toString()).redirectErrorStream(true).start();
    final String count = new String(jq.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, jq.waitFor(), "every line parses as JSON: " + count);
    assertEquals(3003, Integer.parseInt(count.trim()), "one object per entry");
  }

  @Test
  void entryKindsNameKnownTemplatesAndNumberUnknownOnes() {
    assertEquals("timer-event", TapeCat.kindName(TimerEventDecoder.TEMPLATE_ID));
    assertEquals("template-9999", TapeCat.kindName(9999));
  }
}
