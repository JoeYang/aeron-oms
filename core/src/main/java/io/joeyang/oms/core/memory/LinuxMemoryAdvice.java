package io.joeyang.oms.core.memory;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * {@link MemoryAdvice} for Linux, bound with the JDK 25 FFM API — no JNI, per the project's
 * toolchain decision and matching {@code LinuxThreadAffinity}.
 *
 * <p>{@code madvise(addr, len, MADV_HUGEPAGE)} asks the kernel to back the range with transparent
 * huge pages. errno is captured on the downcall so the two interesting failures are named: {@code
 * ENOMEM} (the range is not mapped) and {@code EINVAL} (the advice is invalid for this mapping —
 * including a kernel that cannot apply THP to it). Alignment is checked upfront because the
 * kernel's own EINVAL for it would be indistinguishable from the unsupported-mapping case.
 */
public final class LinuxMemoryAdvice implements MemoryAdvice {

  private static final int MADV_HUGEPAGE = 14;
  private static final int EINVAL = 22;
  private static final int ENOMEM = 12;
  private static final long PAGE_SIZE = 4096;

  private static final Linker LINKER = Linker.nativeLinker();
  private static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
  private static final VarHandle ERRNO =
      CAPTURE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));
  private static final MethodHandle MADVISE =
      LINKER.downcallHandle(
          LINKER.defaultLookup().findOrThrow("madvise"),
          FunctionDescriptor.of(
              ValueLayout.JAVA_INT,
              ValueLayout.ADDRESS,
              ValueLayout.JAVA_LONG,
              ValueLayout.JAVA_INT),
          Linker.Option.captureCallState("errno"));

  @Override
  public void adviseHugePages(final long address, final long length) {
    if (address % PAGE_SIZE != 0) {
      throw new IllegalArgumentException(
          "address " + address + " is not page-aligned (" + PAGE_SIZE + ")");
    }
    if (length <= 0) {
      throw new IllegalArgumentException("length must be positive: " + length);
    }
    try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
      final MemorySegment captured = arena.allocate(CAPTURE_LAYOUT);
      final int result =
          (int)
              MADVISE.invokeExact(
                  captured, MemorySegment.ofAddress(address), length, MADV_HUGEPAGE);
      if (result != 0) {
        final int errno = (int) ERRNO.get(captured, 0L);
        if (errno == ENOMEM) {
          throw new IllegalArgumentException(
              "madvise(MADV_HUGEPAGE) failed with ENOMEM: range ["
                  + address
                  + ", +"
                  + length
                  + ") is not mapped");
        }
        throw new IllegalStateException(
            "madvise(MADV_HUGEPAGE) failed with errno "
                + errno
                + (errno == EINVAL ? " (EINVAL)" : ""));
      }
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Throwable t) {
      throw new IllegalStateException("madvise downcall failed", t);
    }
  }
}
