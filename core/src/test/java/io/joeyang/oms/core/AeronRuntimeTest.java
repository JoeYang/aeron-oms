package io.joeyang.oms.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Aeron dependency runs, rather than merely resolving.
 *
 * <p>Resolution is not the interesting failure. Agrona reaches into {@code jdk.internal.misc},
 * which JDK 25 does not export by default, so a build that downloads every artifact still throws
 * {@code IllegalAccessError} at the first buffer allocation. Only starting a driver proves the JVM
 * configuration in {@code .bazelrc} is right.
 */
class AeronRuntimeTest {

  /**
   * The narrowest check that fails without {@code --add-exports
   * java.base/jdk.internal.misc=ALL-UNNAMED}. Agrona initialises its unsafe accessor in a static
   * initialiser reached by the first buffer construction, so this fails before any Aeron class
   * loads.
   */
  @Test
  void agronaAllocatesAnOffHeapBuffer() {
    final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(64));

    buffer.putLong(0, 0xCAFEBABEL);

    assertEquals(0xCAFEBABEL, buffer.getLong(0));
    assertEquals(64, buffer.capacity());
  }

  /**
   * Starts a real media driver and connects a real client to it. This is the assertion that the
   * dependency is usable end to end on the pinned JDK.
   *
   * @param tempDir a per-test directory, so the driver never touches the shared default location
   */
  @Test
  void mediaDriverStartsAndClientConnects(@TempDir final Path tempDir) {
    final Path aeronDir = tempDir.resolve("aeron");

    final MediaDriver.Context driverContext =
        new MediaDriver.Context()
            .aeronDirectoryName(aeronDir.toString())
            .threadingMode(ThreadingMode.SHARED)
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true);

    try (MediaDriver driver = MediaDriver.launchEmbedded(driverContext);
        Aeron aeron =
            Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()))) {

      assertNotNull(driver.aeronDirectoryName());
      assertTrue(
          Files.isDirectory(Path.of(driver.aeronDirectoryName())), "driver directory exists");
      assertTrue(aeron.clientId() >= 0, "client received an id from the driver");
      assertFalse(aeron.isClosed(), "client is live");
      assertNotNull(aeron.countersReader(), "client mapped the driver's counters");
    }
  }
}
