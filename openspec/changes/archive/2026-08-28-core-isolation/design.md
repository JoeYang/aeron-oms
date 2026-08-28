# Design — core isolation

## Context

The warmed 100M replay's deep tail (`max=428234 ns`) is OS noise on the applying core:
scheduler migrations, device IRQs, timer ticks. `trading-latency.md` defines a four-layer
stack (kernel, interrupts, launch, runtime) and states all four are required; PR #25 measured
the launch layer alone and it hurt the tail. The machine is an Intel Core Ultra 7 255HX:
P-cores 0-7, E-cores 8-19 (from `/sys/devices/cpu_core/cpus`), no HT, governor `powersave`,
no isolation on the current kernel cmdline.

## Goals / Non-Goals

**Goals:**
- Pin the replay hot thread onto an isolated P-core and measure the effect on p99.99/max.
- p99.99 in the report line before any isolation, with a re-recorded baseline.
- Machine-layer configuration documented, scripted, and checkable — reproducible, per the
  "a budget without a stated machine is not reproducible" rule.

**Non-Goals:**
- Pinning the cluster node, gateway, or media driver duty cycles (later initiative; the
  spare isolated core keeps that initiative reboot-free).
- Huge pages for the tape mapping (initiative B, measured separately).
- Any change to tapes, SBE schemas, or the journal format.

## Decisions

1. **Core layout: isolate P-cores 4 and 6** — confirmed by the user on 2026-08-25.
   Kernel line: `isolcpus=domain,managed_irq,4,6 nohz_full=4,6 rcu_nocbs=4,6 nmi_watchdog=0`.
   The `managed_irq` flag is required: kernel-managed device IRQs (NVMe queues) ignore
   `/proc/irq/*/smp_affinity` writes, so only the boot flag keeps them off the isolated
   cores. They are the two favored cores (5300 MHz vs 5100). The replay hot thread takes
   CPU 4; CPU 6 is the spare for the next pinning consumer, so one reboot covers both
   initiatives.
   - *Alternative: one core* — minimal, but forces a second GRUB edit + reboot later.
   - *Alternative: four cores* — no current consumer for the extra two; desktop loses 4 of 8
     P-cores for nothing. Rejected per "do not scaffold ahead."

2. **Affinity port lives in `//core`**, Linux implementation via FFM `sched_setaffinity` /
   `sched_getaffinity` with `pid=0` (the calling thread). `//core` is the shared port
   library; `gateway` and `cluster-node` both need pinning eventually. No JNI, no
   third-party affinity library — the JDK 25 FFM choice is load-bearing in CLAUDE.md.
   `//cluster-service` visibility is untouched; pinning is an adapter concern.
   The downcall handle uses `Linker.Option.captureCallState("errno")` so failures can be
   distinguished (`EINVAL` for a bad CPU vs anything else) and named in the error.

3. **Pin from inside the thread, verify, fail fast.** After `sched_setaffinity`, read the
   mask back and require it to equal the requested single-CPU set; on mismatch throw at
   startup (before the measured window — the no-throw hot-path rule is not violated).
   A silently unpinned run looks healthy and pollutes the measurement.

4. **`taskset`, not `cpuset`**, for the launch layer (rule already recorded): the replay
   wrapper starts the JVM under `taskset -c <housekeeping>`; the hot thread then legally
   moves itself out of that mask. A cpuset cgroup would make `sched_setaffinity` fail
   with `EINVAL`.

5. **Machine layer is scripted but user-applied.** `scripts/isolation.sh check` verifies
   cmdline flags (including `managed_irq` and `nmi_watchdog=0`), unmanaged IRQ affinity,
   workqueue cpumask, and governor against the recorded layout (exit nonzero, naming each
   missing layer); `scripts/isolation.sh apply` (root) sets the boot-volatile parts — IRQ
   masks, workqueue cpumask, governor — each boot. The GRUB edit itself is documented,
   never performed by tooling. **The point-(c) measurement is gated on `check` passing**:
   thread-affinity verification alone cannot detect a failed GRUB edit or a forgotten
   `apply`, so the script gate is what makes the measurement trustworthy. A systemd unit
   for `apply` was considered and rejected — the check-gate already protects the
   measurement, and a unit is machine scaffolding with no other consumer.

6. **Three measurement points, one protocol** (100M tape, `--warmup` 1M, `--latency`):
   a. baseline as-is (powersave, unpinned, no isolation) — re-recorded with p99.99;
   b. `--pin` CPU 4 + `performance` governor on 4 and 6 (no reboot);
   c. same as (b) after the isolation reboot, gated on `isolation.sh check`.
   (b)→(c) then has one variable: the kernel isolation layer. (a)→(b) bundles pin and
   governor deliberately — PR #25 already measured pin-alone and it hurt the tail.
   Cache preparation is explicit and identical at every point, because a reboot
   cold-starts the page cache and a fresh boot has more free RAM: sequentially read the
   extracted archive twice before the discarded run, and record `MemAvailable` with the
   results. Three measured runs per point; p50–p99.9 reported as medians, p99.99 and max
   reported per-run — a median of three maxima hides exactly the statistic under study.

## Risks / Trade-offs

- [Kernel `nohz_full` support] → resolved: `/boot/config-6.17.0-1028-oem` has
  `CONFIG_NO_HZ_FULL=y`; the check script still verifies it at runtime.
- [FFM restricted method warning/error] → `--enable-native-access=ALL-UNNAMED` in the replay
  binary's `jvm_flags` (this change is the "ask first" for that).
- [Fault-class spikes survive isolation] → expected: E2 traced the worst stalls to page
  faults, which initiative B (huge pages) targets. Isolation is measured on what it can
  move — scheduler/IRQ noise; the report says so explicitly.
- [Desktop loses 2 P-cores while isolated] → revert is deleting the GRUB parameters and
  rebooting; documented next to the setup.

## Open Questions

None. The core layout (Decision 1) was confirmed by the user; the kernel `nohz_full`
support question was resolved by inspection (`CONFIG_NO_HZ_FULL=y`).
