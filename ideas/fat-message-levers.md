# Fat-message levers — outcomes

**Context** — measured on the fat-heartbeat initiative (2026-08-28, PR #42, tape
`local-fatheartbeats-1m`: 1M × 32 KB): app-mode replay 7.9 GB/s, apply p50 1.6 µs,
cluster recovery 5.8 GB/s warm / 1.0 GB/s cold-disk. Every lever below was then built
and measured in the 2026-08-29 PR batch (#44–#49); this file records what each one
turned out to be worth. One lever was promoted and merged (huge pages, PR #47 — its
section is deleted per the promotion rule; see the archived
`2026-08-29-huge-pages-fat` change for the +9.6% fat / +22–27% thin numbers). The
rest are recorded here with their closed PRs, whose branches carry the full code,
tests, and measurements.

## Cold-read prefetch — measured, closed unmerged (PR #44)

Built and measured on `feat/cold-read-prefetch`: parallel buffered readers striping the
archive segments beat the per-stream kernel readahead cap (~1.2 GB/s) and took clean-cold
fat recovery from 43.9 s to 21.8 s (4 threads) / 17.8 s (8 threads) — 2.0–2.5× same-day.
Latency evidence: the prefetch threads exit before recovery completes (t=13 s vs t=26 s
report; structural — the prefetcher leads the replayer over the same bytes), and live RTT
through a freshly recovered node is indistinguishable with the flag on or off. Closed in
review with the rest of the batch; flag-gated machinery preserved on the branch. Revisit
trigger: cold recovery wall time matters operationally. The machine-layer alternative
(`read_ahead_kb`) remains untried.

## Recovery-path machinery tuning — measured, nothing promoted (PR #49)

Matrix on `feat/recovery-tuning` (warm fat recovery, mirrored order):
`aeron.cluster.log.fragment.limit=512` and the `oms.lowlatency` busy-spin profile each
land inside the defaults' ±2% noise band, and their combination is reproducibly *worse*
(−8 to −23%) — the thin-tape "DEDICATED hurt recovery" caveat held. The 93%-busy
archive-conductor is the real limit; closing the remaining ~27% to app-mode means
changing its per-fragment work, not its poll cadence. Untried: pinning the
archive/service agents onto isolated cores.

## Multi-lane checksum (attacks: apply p50, 1.6 µs) — still parked, untried

The rotate-xor checksum is a serial dependency chain: 2 cycles per 8 bytes ≈ 20 GB/s at
5.1 GHz — exactly the measured apply rate, so arithmetic, not cache bandwidth, is the
floor. Eight independent accumulators folded at the end break the chain (~8× headroom;
apply p50 toward ~0.4 µs). One-way door: the checksum definition is baked into recorded
goldens — a change means a new tape name or a versioned algorithm field. Revisit trigger:
a real fat-message apply that must do more than checksum inside its budget.

## Zero-copy fat walk — falsified (PR #46, closed)

Built properly on `feat/zero-copy-fat-walk` (incremental carry checksum pinned by
exhaustive split-equivalence, slice-mode walker, golden run REPLAY OK over all 1M real
messages) and measured 5–7% *slower* than the copy path. The predicted "extra 32 GB
pass" never existed at DRAM level: the reassembly scratch is 128 KB of cache-resident
lines re-dirtied in place, so the copy costs only spare L2 bandwidth, while slicing pays
~23 virtual calls per message plus a carry at every fragment boundary. Lesson recorded:
measure which level of the memory hierarchy a "saved pass" actually lives in. The tested
building blocks (slice walker, carry checksum) survive on the branch if a gather-based
path is ever wanted.

## Gateway pipelining — measured and rejected (PR #45, closed)

A `SendWindow` of N outstanding sends took 100k-message fat recording from 4.8k msg/s
(RTT-bound) to 62k msg/s at window 64 — 13×, saturating ~2 GB/s of journal ingest;
window 256 bought nothing more. The cost is inherent, not tunable: per-message RTT grows
exactly per Little's law (211 µs closed-loop → 850 µs at window 64 → 3.6 ms at 256)
because each message queues behind the rest of the window. **Rejected on principle**:
latency must not deteriorate when a lever is engaged, even in tooling, and a
windowed-send pattern should not live in this codebase where it could migrate toward the
hot path. Revisit trigger: recording time actually blocks an initiative *and* the latency
principle is consciously waived for the recording tool — both, explicitly.

## Snapshotting (attacks: recovery cost scaling itself) — still parked, untried

The structural answer: with snapshots, recovery replays only the tail since the last
snapshot, and the recovery bill stops scaling with journal bytes entirely. Everything
above optimizes the constant; this removes the linearity. Largest initiative on the
list, needs its own spec (snapshot content, cadence, determinism proof via
golden-tape-through-snapshot), and is eventually inevitable for any real OMS state.
Revisit trigger: the state machine holds real order state worth snapshotting.

## Residual apply-tail: attributed to thermal interrupts (PR #48, closed)

The p99.99 ≈ 6 µs band is **thermal event interrupts (TRM) landing on the isolated
core**. Evidence from instrumented fat latency replays (pinned, RAM-resident tape):
the JVM is exonerated (`-Xlog:gc*` zero pauses; `-Xlog:safepoint*` exactly one exit-Halt
per run, max sync 0 ns); the CPU-4 interrupt census is dominated by TRM at 191–519 per
run — ~2× the package rate on the busy 5.3 GHz core, package ~71 °C under bench load;
the landing-probability arithmetic reproduces the observed tail count (~310 TRM in the
window × ~38% chance of hitting a timed 1.6 µs apply ≈ the ~100 events above the knee);
and per-run TRM deltas correlate monotonically with p99.99 (191 → 5.9 µs, 425 → 6.7,
519 → 6.9). The same event rate sits beyond p99.9999 on 100M thin samples, which is why
thin p99.99 stays clean at 155–240 ns — compare event *rates*, not percentiles, across
workloads. Still unattributed: the max band (77–624 µs, a handful of events per run);
next tool is `perf sched` or MSR thermal-status reads, both root. Mitigations if the
band ever matters: cooling/package power, or accepting it as this machine's thermal
noise floor. Trap kept: the fault-band explanation was once written down without
evidence — attribute before narrating.
