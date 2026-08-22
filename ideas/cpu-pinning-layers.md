# CPU pinning — the remaining layers

**What** — per-thread pinning of the hot duty cycles onto isolated cores: the FFM
`sched_setaffinity` runtime layer plus kernel isolation (`isolcpus`, IRQ affinity), on top
of the launch-layer `taskset` already prototyped.

**Why parked** — measured twice, on two configurations, launch-layer pinning **alone** has
no material benefit on this machine and hurts the tail (closed PR #25, code on branch
`feat/perf-taskset`):

| configuration | p50 | p90 | p99 (µs) |
|---|---|---|---|
| baseline (backoff) | 241.8 | 389.7 | 4,055.4 |
| baseline + taskset | 253.5 | 3,893.4 | 5,365.6 |
| lowlatency (busy-spin) | 7.6 | 9.4 | 30.5 |
| lowlatency + taskset | 7.5 | 9.8–10.3 | 247–526 |

Confining threads to fewer, non-isolated cores queues them behind whatever the scheduler
also puts there. The mechanism itself is verified: every JVM thread — GC, JIT, VM, duty
cycles — inherits the launch mask (`/proc/<pid>/task/*/status` showed all 23 threads
confined). The launch layer works; it is aimed at the wrong half of the problem alone.

## What "investigate later" means concretely

The four layers of `trading-latency.md`, and what exists today:

| Layer | Mechanism | Status |
|---|---|---|
| Kernel | `isolcpus=`, `nohz_full=`, IRQ affinity | missing — needs boot parameters |
| Launch | `taskset -c <housekeeping>` on the JVM | prototyped on `feat/perf-taskset` |
| Runtime | FFM `sched_setaffinity(0, ...)` from inside each hot thread | missing — the JDK 25 FFM work |
| Verification | read affinity back at startup, fail fast | missing |

The launch layer's real job is the opposite of the experiment: hold the *housekeeping*
threads (GC, JIT — which application code can never pin from inside) on housekeeping
cores, while each hot thread moves itself onto its own isolated core via FFM. A thread may
legally leave the `taskset` mask; a `cpuset` cgroup would forbid that with `EINVAL` —
which is why `taskset`, never `cpuset`. Aeron's plug points are ready:
`senderThreadFactory()`, `receiverThreadFactory()`, `conductorThreadFactory()` on the
driver context, `threadFactory()` on the consensus module and container contexts, with the
pin call inside the thread's own `Runnable`.

Revisit when: the FFM affinity work is scheduled, on a machine whose boot parameters we
control. Expected payoff is the tail (the 17–30 µs p99 and the max outliers), not the
2.5 µs median.
