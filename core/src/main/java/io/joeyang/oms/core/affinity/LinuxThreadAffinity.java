package io.joeyang.oms.core.affinity;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * {@link ThreadAffinity} for Linux, bound with the JDK 25 FFM API — no JNI, no third-party affinity
 * library, per the project's toolchain decision.
 *
 * <p>{@code sched_setaffinity(0, ...)} with pid 0 acts on the calling thread, which is exactly the
 * pin-from-inside-the-Runnable rule. The syscall's errno is captured on the downcall so a rejected
 * CPU ({@code EINVAL} — the id is not on this machine) is reported as a different failure from an
 * operational error. The pin is then read back with {@code sched_getaffinity} and compared to the
 * requested single-CPU mask; a pin that cannot be verified throws instead of reporting success.
 *
 * <p>The mask is sized to glibc's {@code cpu_set_t} (1024 bits). Bit layout is an array of native
 * longs; byte-level addressing below is correct on little-endian x86-64, the only platform this
 * class targets.
 */
public final class LinuxThreadAffinity implements ThreadAffinity {

  private static final int CPU_SET_BYTES = 128;
  private static final int EINVAL = 22;

  private static final Linker LINKER = Linker.nativeLinker();
  private static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
  private static final VarHandle ERRNO =
      CAPTURE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));
  private static final FunctionDescriptor AFFINITY_DESCRIPTOR =
      FunctionDescriptor.of(
          ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
  private static final MethodHandle SCHED_SETAFFINITY =
      LINKER.downcallHandle(
          LINKER.defaultLookup().findOrThrow("sched_setaffinity"),
          AFFINITY_DESCRIPTOR,
          Linker.Option.captureCallState("errno"));
  private static final MethodHandle SCHED_GETAFFINITY =
      LINKER.downcallHandle(
          LINKER.defaultLookup().findOrThrow("sched_getaffinity"),
          AFFINITY_DESCRIPTOR,
          Linker.Option.captureCallState("errno"));

  @Override
  public void pinCurrentThread(final int cpu) {
    if (cpu < 0 || cpu >= CPU_SET_BYTES * 8) {
      throw new IllegalArgumentException(
          "CPU " + cpu + " is outside [0, " + CPU_SET_BYTES * 8 + ")");
    }
    try (Arena arena = Arena.ofConfined()) {
      final MemorySegment captured = arena.allocate(CAPTURE_LAYOUT);
      final MemorySegment requested = arena.allocate(CPU_SET_BYTES);
      requested.set(ValueLayout.JAVA_BYTE, cpu / 8, (byte) (1 << (cpu % 8)));

      final int setResult =
          (int) SCHED_SETAFFINITY.invokeExact(captured, 0, (long) CPU_SET_BYTES, requested);
      if (setResult != 0) {
        final int errno = (int) ERRNO.get(captured, 0L);
        if (errno == EINVAL) {
          throw new IllegalArgumentException("CPU " + cpu + " does not exist on this machine");
        }
        throw new IllegalStateException(
            "sched_setaffinity(cpu=" + cpu + ") failed with errno " + errno);
      }

      final MemorySegment actual = arena.allocate(CPU_SET_BYTES);
      final int getResult =
          (int) SCHED_GETAFFINITY.invokeExact(captured, 0, (long) CPU_SET_BYTES, actual);
      if (getResult != 0) {
        final int errno = (int) ERRNO.get(captured, 0L);
        throw new IllegalStateException("sched_getaffinity failed with errno " + errno);
      }
      for (int i = 0; i < CPU_SET_BYTES; i++) {
        if (actual.get(ValueLayout.JAVA_BYTE, i) != requested.get(ValueLayout.JAVA_BYTE, i)) {
          throw new IllegalStateException(
              "pin to CPU " + cpu + " did not verify: affinity mask differs at byte " + i);
        }
      }
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Throwable t) {
      throw new IllegalStateException("affinity downcall failed", t);
    }
  }
}
