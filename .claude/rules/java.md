---
paths: ["src/**/*.java"]
---
# Java conventions

## Language level and style

- Target JDK 25, pinned in `MODULE.bazel` — never rely on the ambient `java` on `PATH`
- google-java-format is authoritative; do not hand-tune formatting it will rewrite
- `final` by default on fields and parameters; prefer immutable value types off the hot path
- Package-private is the default visibility — widen only with a reason

## Aeron and Agrona

- Use `DirectBuffer`/`MutableDirectBuffer` flyweights rather than copying into `byte[]` or
  `ByteBuffer`
- Never retain a buffer passed into a `FragmentHandler` beyond the callback — it is reused.
  Copy out what you need, or you will read someone else's message later.
- Always check the return of `Publication.offer()`. A negative value is a real, expected
  condition (`BACK_PRESSURED`, `NOT_CONNECTED`, `ADMIN_ACTION`, `CLOSED`), not a rare edge
  case, and each needs a deliberate response.
- Choose an `IdleStrategy` per thread deliberately and document why: `BusySpinIdleStrategy`
  for the latency-critical duty cycle, `BackoffIdleStrategy` for background agents. A
  busy-spin strategy on a non-critical thread burns a core for nothing.
- Close Aeron resources in reverse construction order (`Publication`/`Subscription` →
  `Aeron` → `MediaDriver`); use try-with-resources where the lifetime is scoped

## Threads

- Duty cycles run on platform threads. Never a virtual thread — it does not own an OS
  thread, so CPU pinning does not survive the next mount.
- Virtual threads are fine off the hot path: admin endpoints, housekeeping, startup work
- A `ThreadFactory` body runs on the *creating* thread. Anything that must apply to the new
  thread — CPU pinning, thread-local setup — goes inside the `Runnable`, not the factory.
- Name every thread. An unnamed thread in a stack dump or `perf` output costs debugging time.

## Native access (FFM)

CPU pinning calls `sched_setaffinity` through the JDK 25 FFM API (`java.lang.foreign`).
That is the only sanctioned native path in this project. Do not add JNI, and do not add a
third-party affinity library — either would pull in a toolchain or a dependency the project
does not otherwise need.

- `--enable-native-access=ALL-UNNAMED` belongs in the Bazel `jvm_flags`. Without it the JVM
  warns on every restricted call, and the JDK is moving toward making that an error.
- Hold FFM downcall handles in `static final` fields. Building a handle per call is
  expensive and defeats the purpose.
- `Arena.ofConfined()` for short-lived masks. A confined arena closes on the thread that
  opened it and must never be shared across threads.
- Check the return code of every downcall. `sched_setaffinity` returns `0` on success; a
  non-zero result throws, it does not log and continue.
- Keep the native surface small and in one class. FFM spread across the codebase is a design
  smell — the JVM should be doing the work.

## Error handling

- No swallowed exceptions — a `catch` block that cannot act should not exist
- Checked exceptions do not cross tier boundaries; translate at the seam
- On the hot path prefer status codes and pre-allocated error objects over throwing

## Things that quietly cost latency

- Autoboxing in collections — use Agrona primitive collections (`Long2ObjectHashMap`,
  `Int2IntHashMap`) instead of `Map<Long, ...>`
- `String` concatenation and `String.format` in logging — guard the call or pre-encode
- Capturing lambdas — they allocate; hoist to fields on hot paths
- `Optional` on the hot path — it allocates; reserve it for API boundaries
- Defensive copies of buffers "just in case" — decide ownership explicitly instead
