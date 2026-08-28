package io.joeyang.oms.cluster.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Prefetch is fire-and-forget in the node; the tests join the future to observe the result. */
final class ArchivePrefetcherTest {

  @TempDir Path dir;

  private File file(final String name, final int size) throws IOException {
    final byte[] content = new byte[size];
    for (int i = 0; i < size; i++) {
      content[i] = (byte) i;
    }
    return Files.write(dir.resolve(name), content).toFile();
  }

  @Test
  void readsEveryByteOfEveryFile() throws Exception {
    file("0-0.rec", 10 * 1024);
    file("0-1.rec", 64 * 1024 + 7);
    file("archive.catalog", 1);

    final ArchivePrefetcher.Result result = ArchivePrefetcher.start(dir.toFile(), 2).join();

    assertEquals(3, result.filesRead());
    assertEquals(10 * 1024 + 64 * 1024 + 7 + 1, result.bytesRead());
    assertEquals(0, result.filesSkipped());
  }

  @Test
  void singleThreadReadsAll() throws Exception {
    file("a.rec", 4096);
    file("b.rec", 512);

    final ArchivePrefetcher.Result result = ArchivePrefetcher.start(dir.toFile(), 1).join();

    assertEquals(2, result.filesRead());
    assertEquals(4096 + 512, result.bytesRead());
  }

  @Test
  void missingDirectoryCompletesAsNoOp() {
    final File absent = new File(dir.toFile(), "absent");

    final ArchivePrefetcher.Result result = ArchivePrefetcher.start(absent, 4).join();

    assertEquals(0, result.filesRead());
    assertEquals(0, result.bytesRead());
    assertEquals(0, result.filesSkipped());
  }

  @Test
  void emptyDirectoryCompletesAsNoOp() {
    final ArchivePrefetcher.Result result = ArchivePrefetcher.start(dir.toFile(), 4).join();

    assertEquals(0, result.filesRead());
    assertEquals(0, result.bytesRead());
  }

  @Test
  void unreadableFileIsSkippedWhileOthersComplete() throws Exception {
    file("readable-1.rec", 2048);
    final File locked = file("locked.rec", 2048);
    file("readable-2.rec", 1024);
    // Running as root would see through the permission bit; the case needs a real EACCES.
    assumeTrue(locked.setReadable(false) && !locked.canRead());

    final ArchivePrefetcher.Result result = ArchivePrefetcher.start(dir.toFile(), 2).join();

    assertEquals(2, result.filesRead());
    assertEquals(2048 + 1024, result.bytesRead());
    assertEquals(1, result.filesSkipped());
  }

  @Test
  void subdirectoriesAreIgnored() throws Exception {
    file("top.rec", 128);
    final Path sub = Files.createDirectory(dir.resolve("sub"));
    Files.write(sub.resolve("nested.rec"), new byte[64]);

    final ArchivePrefetcher.Result result = ArchivePrefetcher.start(dir.toFile(), 2).join();

    assertEquals(1, result.filesRead());
    assertEquals(128, result.bytesRead());
  }

  @Test
  void threadCountBelowOneIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> ArchivePrefetcher.start(dir.toFile(), 0));
  }
}
