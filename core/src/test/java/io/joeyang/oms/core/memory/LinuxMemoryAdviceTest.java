package io.joeyang.oms.core.memory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

/**
 * Tests for the FFM {@code madvise} binding. The success path advises a live page-aligned anonymous
 * allocation; the failure paths hand the kernel ranges it must reject, proving the captured-errno
 * reporting rather than assuming it.
 */
class LinuxMemoryAdviceTest {

  private static final long PAGE = 4096;

  @Test
  void advisesLivePageAlignedRange() {
    try (Arena arena = Arena.ofConfined()) {
      final MemorySegment segment = arena.allocate(2 * 1024 * 1024, PAGE);
      assertDoesNotThrow(
          () -> new LinuxMemoryAdvice().adviseHugePages(segment.address(), segment.byteSize()));
    }
  }

  @Test
  void unmappedRangeIsRejectedWithTheErrnoName() {
    // The page at 4096 is never mapped in a normal process; madvise must fail with ENOMEM.
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new LinuxMemoryAdvice().adviseHugePages(PAGE, PAGE));
    assertTrue(e.getMessage().contains("ENOMEM"), e.getMessage());
  }

  @Test
  void misalignedAddressIsRejectedBeforeTheSyscall() {
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new LinuxMemoryAdvice().adviseHugePages(PAGE + 1, PAGE));
    assertTrue(e.getMessage().contains("aligned"), e.getMessage());
  }
}
