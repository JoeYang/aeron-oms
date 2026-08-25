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

1. **Core layout: isolate P-cores 4 and 6** (`isolcpus=4,6 nohz_full=4,6 rcu_nocbs=4,6`).
   They are the two favored cores (5300 MHz vs 5100). The replay hot thread takes CPU 4;
   CPU 6 is the spare for the next pinning consumer, so one reboot covers both initiatives.
   - *Alternative: one core* — minimal, but forces a second GRUB edit + reboot later.
   - *Alternative: four cores* — no current consumer for the extra two; desktop loses 4 of 8
     P-cores for nothing. Rejected per "do not scaffold ahead."
   - **Needs user confirmation** — it is their machine and their reboot.

2. **Affinity port lives in `//core`**, Linux implementation via FFM `sched_setaffinity` /
   `sched_getaffinity` with `pid=0` (the calling thread). `//core` is the shared port
   library; `gateway` and `cluster-node` both need pinning eventually. No JNI, no
   third-party affinity library — the JDK 25 FFM choice is load-bearing in CLAUDE.md.
   `//cluster-service` visibility is untouched; pinning is an adapter concern.

3. **Pin from inside the thread, verify, fail fast.** After `sched_setaffinity`, read the
   mask back and require it to equal the requested single-CPU set; on mismatch throw at
   startup (before the measured window — the no-throw hot-path rule is not violated).
   A silently unpinned run looks healthy and pollutes the measurement.

4. **`taskset`, not `cpuset`**, for the launch layer (rule already recorded): the replay
   wrapper starts the JVM under `taskset -c <housekeeping>`; the hot thread then legally
   moves itself out of that mask. A cpuset cgroup would make `sched_setaffinity` fail
   with `EINVAL`.

5. **Machine layer is scripted but user-applied.** `scripts/isolation.sh check` verifies
   cmdline flags, IRQ affinity, and governor against the recorded layout (exit nonzero on
   drift); `scripts/isolation.sh apply` (root) sets the boot-volatile parts — IRQ masks and
   governor — each boot. The GRUB edit itself is documented, never performed by tooling.

6. **Three measurement points, one protocol** (100M tape, `--warmup` 1M, `--latency`,
   warm page cache, discard first run, 3 measured runs, median of percentiles):
   a. baseline as-is (powersave, no isolation) — re-recorded with p99.99;
   b. governor `performance` only (no reboot) — separates the governor's share;
   c. full isolation + pin (after reboot).
   Point (b) is nearly free and stops the governor and isolation effects being conflated.

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

- Core layout (Decision 1) — awaiting user confirmation before any machine change.
