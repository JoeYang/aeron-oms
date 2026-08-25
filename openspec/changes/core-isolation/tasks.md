# Tasks — core isolation

## 1. p99.99 first (own commit, before any isolation)

- [ ] 1.1 Failing test: `LatencyHistogram` reports p99.99 (extend `LatencyHistogramTest`)
- [ ] 1.2 Implement p99.99 and add it to the `TapeReplayMain` report line
- [ ] 1.3 Re-record the baseline: 100M tape, `--warmup` 1M, `--latency`, warm cache, discard
      first run, 3 measured runs — powersave, no isolation. Record medians in the PR.

## 2. Affinity port (interfaces commit before implementation)

- [ ] 2.1 Failing tests: pin current thread to an online CPU and read the mask back;
      nonexistent CPU raises an error naming the CPU
- [ ] 2.2 Port interface in `//core` (own commit)
- [ ] 2.3 Linux FFM implementation of `sched_setaffinity`/`sched_getaffinity` (`pid=0`),
      verify-and-fail-fast; `--enable-native-access=ALL-UNNAMED` on the test and the
      replay binary only

## 3. Pinned replay

- [ ] 3.1 Failing test: `--pin` with a bad CPU fails before replaying; report states the
      pinned CPU on success
- [ ] 3.2 Wire `--pin <cpu>` into `TapeReplayMain` (pin before the warmup tape)
- [ ] 3.3 Launch layer: replay invocation documented/wrapped with
      `taskset -c <housekeeping>`

## 4. Machine layer (user applies; tooling checks)

- [ ] 4.1 `scripts/isolation.sh check` — cmdline, IRQ affinity, governor vs recorded layout;
      nonzero on drift, names each missing layer; detects `CONFIG_NO_HZ_FULL` presence
- [ ] 4.2 `scripts/isolation.sh apply` — root; per-boot parts only (IRQ masks, governor)
- [ ] 4.3 Document the GRUB line for the confirmed layout, and the revert

## 5. Measure

- [ ] 5.1 Point b: governor `performance` only (no reboot), same protocol as 1.3
- [ ] 5.2 User gate: confirm layout, apply GRUB change, reboot
- [ ] 5.3 Point c: full isolation + `--pin`, same protocol
- [ ] 5.4 Compile the three-point percentile table (p50…p99.99, max) and the verdict into
      the PR; state machine, kernel, flags alongside every number
