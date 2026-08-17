# Trading latency rules

## Latency budgets

Targets for this OMS. Treat them as budgets to defend, and revise them from measurements
rather than opinion:

- Order entry path: < 50 microseconds (wire to ack)
- Sequencer round trip: < 20 microseconds (ingress to sequenced position)
- End-to-end tick-to-trade: < 500 microseconds

## Hot path rules

The critical path — ingress decode, sequencing, state machine apply, egress publish — must:

- **Not allocate** once steady state is reached: pre-allocate buffers and objects at startup
- **Not lock**: single-threaded ownership per agent, or Agrona lock-free structures
- **Not make blocking syscalls**: no synchronous logging, file I/O, or network calls
- **Not throw**: use status codes and pre-allocated error objects

## JVM specifics

- GC is the dominant tail-latency risk. Choose the collector deliberately — ZGC or Shenandoah
  for low pause, or an allocation-free steady state that never triggers a collection — and pin
  the flags in the Bazel `jvm_flags`. Never rely on defaults.
- Verify the allocation-free steady state; do not assume it. `-Xlog:gc*` and JFR allocation
  profiling are the evidence.
- Account for JIT warmup. Benchmarks and latency assertions run after warmup, and production
  startup should warm the hot path before taking live flow.
- Pre-touch heap pages (`-XX:+AlwaysPreTouch`) so page faults do not surface as jitter
- Keep duty-cycle loops short — long counted loops delay safepoints and show up as pauses
  in something unrelated

## CPU pinning

Busy-spin without pinning is wasted effort. A spinning thread the scheduler migrates pays
exactly the cache and TLB cost it was spinning to avoid.

Pinning is done from Java, using the JDK 25 FFM API to call `sched_setaffinity`. No JNI, no
third-party affinity library, and no C++ toolchain. See @.claude/rules/java.md for the
binding rules.

### The four layers

All four are required. No single layer is sufficient on its own.

| Layer | Mechanism | Purpose |
|---|---|---|
| Kernel | `isolcpus=`, `nohz_full=`, `rcu_nocbs=` | Remove cores from scheduler balancing; stop the timer tick |
| Interrupts | `/proc/irq/*/smp_affinity`, `irqbalance` ban list | Move device interrupts off the isolated cores |
| Launch | `taskset -c <housekeeping>` on the JVM | Start every JVM thread — including GC and JIT — on the housekeeping set |
| Runtime | FFM `sched_setaffinity(0, ...)` called by the thread itself | Move each critical thread onto its isolated core |

The launch layer works by inheritance: a new thread copies the mask of the thread that
created it. This is the only way to place JVM-internal threads (GC, JIT compiler, VM
thread), because application code never runs on them and cannot pin them directly.

### `taskset`, not `cpuset`

Use `taskset` or `numactl` at launch. Do not put the JVM in a `cpuset` cgroup that excludes
the isolated cores.

- `taskset` sets a starting mask; a thread may move itself out of it
- A `cpuset` cgroup is a kernel-enforced wall. `sched_setaffinity` fails with `EINVAL`
  outside it, and the runtime layer above stops working.

If a cgroup is required for other reasons, its cpuset must contain both core sets.

### Rules

- Pin platform threads only. A virtual thread does not own an OS thread, so any affinity set
  on it is lost at the next mount.
- Pin from inside the thread's own `Runnable`. A `ThreadFactory` body runs on the creating
  thread and would pin the wrong one.
- Use `ThreadingMode.DEDICATED` when pinning, and supply a pinning `ThreadFactory` to
  `conductorThreadFactory()`, `senderThreadFactory()`, and `receiverThreadFactory()`
- Verify the pin at startup and fail fast: read the affinity back and compare it to the
  request. A silently unpinned duty cycle looks healthy and misses every budget.
- Core assignment is configuration, not a constant. Record the CPU layout each budget
  assumes — a budget without a stated machine is not reproducible.

## Measurement discipline

- Never assume a change improves performance — always measure before and after
- Benchmark with realistic journal replay, not synthetic uniform load
- JMH for micro-benchmarks, HdrHistogram for end-to-end latency capture
- Track percentiles (p50, p99, p99.9, max), never averages — tail latency is the product
- Record machine, JVM version, and flags alongside any number, or the number means nothing
