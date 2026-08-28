package io.joeyang.oms.cluster.node;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streams every file in an archive directory through the page cache so a cold recovery reads warm
 * pages instead of waiting on per-stream kernel readahead (~1.2 GB/s measured against 3.1 GB/s the
 * drive delivers — see the cold-read-prefetch change).
 *
 * <p>Files are striped whole across named daemon threads: sequential access within a file keeps
 * each stream readahead-friendly, parallelism across files gets past the per-file cap. Startup
 * work, not a duty cycle — the threads run to completion and exit, and the node never joins the
 * future, so no prefetch failure can propagate into recovery.
 */
final class ArchivePrefetcher {

  private static final int READ_BUFFER_BYTES = 16 * 1024 * 1024;

  private ArchivePrefetcher() {}

  /** What the prefetch touched; observable in tests, ignored by the node. */
  record Result(int filesRead, long bytesRead, int filesSkipped) {}

  /**
   * Starts the prefetch and returns immediately.
   *
   * @param archiveDir directory whose regular files are read; missing directory is a no-op
   * @param threads number of prefetch threads, at least 1
   * @return future completed when every thread has finished
   */
  static CompletableFuture<Result> start(final File archiveDir, final int threads) {
    if (threads < 1) {
      throw new IllegalArgumentException("threads must be >= 1: " + threads);
    }
    final File[] entries = archiveDir.listFiles(File::isFile);
    final CompletableFuture<Result> done = new CompletableFuture<>();
    if (entries == null || entries.length == 0) {
      done.complete(new Result(0, 0, 0));
      return done;
    }
    Arrays.sort(entries);

    final AtomicInteger filesRead = new AtomicInteger();
    final AtomicLong bytesRead = new AtomicLong();
    final AtomicInteger filesSkipped = new AtomicInteger();
    final AtomicInteger remaining = new AtomicInteger(threads);
    for (int t = 0; t < threads; t++) {
      final int stripe = t;
      final Thread thread =
          new Thread(
              () -> {
                try {
                  final ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_BYTES);
                  for (int i = stripe; i < entries.length; i += threads) {
                    readThrough(entries[i], buffer, filesRead, bytesRead, filesSkipped);
                  }
                  if (remaining.decrementAndGet() == 0) {
                    done.complete(new Result(filesRead.get(), bytesRead.get(), filesSkipped.get()));
                  }
                } catch (final Throwable error) {
                  done.completeExceptionally(error);
                }
              },
              "archive-prefetch-" + t);
      thread.setDaemon(true);
      thread.start();
    }
    return done;
  }

  private static void readThrough(
      final File file,
      final ByteBuffer buffer,
      final AtomicInteger filesRead,
      final AtomicLong bytesRead,
      final AtomicInteger filesSkipped) {
    long fileBytes = 0;
    try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
      while (true) {
        buffer.clear();
        final int n = channel.read(buffer);
        if (n < 0) {
          break;
        }
        fileBytes += n;
      }
      filesRead.incrementAndGet();
      bytesRead.addAndGet(fileBytes);
    } catch (final IOException e) {
      // Containment is the contract: an unreadable segment must not fail recovery. The skip
      // count is the record of it.
      filesSkipped.incrementAndGet();
    }
  }
}
