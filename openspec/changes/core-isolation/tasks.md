# Tasks — core isolation

## 1. p99.99 first (own commit, before any isolation)

- [x] 1.1 Failing test: `LatencyHistogram` reports p99.99 (extend `LatencyHistogramTest`)
- [x] 1.2 Implement p99.99 and add it to the `TapeReplayMain` report line
- [x] 1.3 Re-record the baseline: 100M tape, `--warmup` 1M, `--latency`, warm cache, discard
      first run, 3 measured runs — powersave, no isolation. Record medians in the PR.

## 2. Affinity port (interfaces commit before implementation)

- [x] 2.1 Failing tests: pin current thread to an online CPU and read the mask back;
      nonexistent CPU raises an error naming the CPU
- [x] 2.2 Port interface in `//core` (own commit)
- [x] 2.3 Linux FFM implementation of `sched_setaffinity`/`sched_getaffinity` (`pid=0`),
      verify-and-fail-fast; `--enable-native-access=ALL-UNNAMED` on the test and the
      replay binary only

## 3. Pinned replay

- [x] 3.1 Failing test: `--pin` with a bad CPU fails before replaying; report states the
      pinned CPU on success
- [x] 3.2 Wire `--pin <cpu>` into `TapeReplayMain` (pin before the warmup tape)
- [x] 3.3 Launch layer: replay invocation documented/wrapped with
      `taskset -c <housekeeping>`

## 4. Machine layer (user applies; tooling checks)

- [x] 4.1 `scripts/isolation.sh check` — cmdline (incl. `managed_irq`, `nmi_watchdog=0`),
      unmanaged IRQ affinity, workqueue cpumask, governor vs recorded layout; nonzero on
      drift, names each missing layer; detects `CONFIG_NO_HZ_FULL` presence
- [x] 4.2 `scripts/isolation.sh apply` — root; per-boot parts only (IRQ masks, workqueue
      cpumask, governor)
- [x] 4.3 Document the GRUB line for the confirmed layout
      (`isolcpus=domain,managed_irq,4,6 nohz_full=4,6 rcu_nocbs=4,6 nmi_watchdog=0`),
      and the revert

## 5. Measure

Every point: prefault the extracted archive with two sequential reads before the discarded
run; record `MemAvailable`; 3 measured runs; medians for p50–p99.9, per-run values for
p99.99 and max.

- [ ] 5.1 Point b: `--pin` CPU 4 + `performance` on CPUs 4 and 6 (no reboot)
- [ ] 5.2 User gate: apply the documented GRUB change, reboot, run `isolation.sh apply`
- [ ] 5.3 Point c: same as 5.1 — runs only if `scripts/isolation.sh check` passes
- [ ] 5.4 Compile the three-point percentile table and the verdict into the PR; state
      machine, kernel, and flags alongside every number
