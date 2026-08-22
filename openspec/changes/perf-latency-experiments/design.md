# Design — perf-latency-experiments

## Context

Baseline (main at #22, burst protocol): min 14.7 / p50 241.8 / p90 389.7 / p99 4,055.4 /
max 6,012.9 µs. The 1-per-second demo's ~4.3 ms average was dominated by parked backoff
idle strategies waking, not by work. The p99 tail is where that cost remains under load.

## Goals / Non-Goals

**Goals:** attribute latency to its causes by measuring four candidate optimizations
independently, under one protocol, and rank them by impact.

**Non-Goals:** kernel bypass, kernel core isolation (`isolcpus` — needs boot parameters),
the FFM per-thread pinning feature, merging any experiment before the numbers are read.

## Decisions

- **One variable per branch, all off `main`, none stacked.** A stacked comparison cannot
  attribute impact. Interactions (e.g. busy-spin + IPC) are a follow-up once single-variable
  effects are known.
- **Every experiment is an opt-in flag with today's behaviour as default**, so a losing
  experiment can merge harmlessly or close unmerged, and CI semantics never change.
- **One measurement protocol, external to all branches** (session scratchpad script): fresh
  journal, port 22102, 3,000 closed-loop round trips, first 2,000 discarded, percentiles
  over the last 1,000. Per-line printing stays on in every run — a constant bias shared by
  all measurements, acceptable for ranking, removed later by the bench mode.
- **Launch-layer pinning only (`taskset`).** The runtime layer needs the FFM
  `sched_setaffinity` feature; the kernel layer needs boot parameters. Both out of scope.

## Risks / Trade-offs

- [Laptop noise: turbo, C-states, scheduler] → rank by p50 and p99 together; rerun any
  surprising result before believing it.
- [Busy-spin burns ~7 cores] → opt-in flag; never the default on shared hardware.
- [Independent branches conflict at merge time (same Config record)] → expected; the
  summary decides what merges and the loser rebases or closes.

## Migration Plan

None. All flags default off.

## Results (2026-08-22, ThinkPad P16 Gen 3, 20 threads, no isolation)

| experiment | min | p50 | p90 | p99 | max (µs) |
|---|---|---|---|---|---|
| baseline | 14.7 | 241.8 | 389.7 | 4,055.4 | 6,012.9 |
| busyspin (#23) | **5.9** | **6.8** | **8.5** | **18.3** | **508.5** |
| ipc (#24) | 13.8 | 162.6 | 223.8 | 291.3 | 475.0 |
| taskset (#25) | 73.2 | 253.5 | 3,893.4 | 5,365.6 | 7,855.2 |
| bench mode (#26) | 21.1 | 252.9 | 395.9 | 3,933.3 | 6,187.5 |

Ranking: **busy-spin dominates** — 35× at p50, 220× at p99; the parked duty cycles were
the latency. IPC's main effect is the tail (p99 14×) by removing the second driver and
both UDP hops. Taskset alone is reproducibly harmful at the tail (two runs) — pinning a
backoff system onto fewer shared cores queues its sleepers; it pays only combined with
busy-spin and isolated cores. Bench mode changes nothing and measures it correctly, which
is its job; its summary matched the external parser exactly.

## Results, round 2 (post-#23 merge, experiments rebased and combined with the profile)

| configuration | min | p50 | p90 | p99 | max (µs) |
|---|---|---|---|---|---|
| lowlatency (merged #23, fresh ref) | 6.2 | 7.6 | 9.4 | 30.5 | 691.8 |
| lowlatency + ipc (#24) | **1.9** | **2.4** | **3.6** | **23.7** | 1,070.9 |
| lowlatency + taskset (#25, run 1) | 6.3 | 7.5 | 9.8 | 526.1 | 6,018.8 |
| lowlatency + taskset (#25, run 2) | 6.6 | 7.5 | 10.3 | 247.0 | 1,323.4 |

The open question is answered: **the combination pays** — busy-spin removes the wakeups,
IPC then removes the UDP hops that were the remaining floor: p50 2.4 µs, 100× the original
baseline, no kernel bypass. Pinning still buys nothing without kernel isolation — p50
unchanged, tail reproducibly worse — because `taskset` confines the spinners to cores the
scheduler still shares. The pinning layer waits for `isolcpus` + the FFM runtime layer.

## Open Questions

None. #23 and #24 are merged — `-Doms.lowlatency=true -Doms.ipc=true` is the co-located
operating point at p50 2.4 µs. Remaining: #26 (the measurement tool, this PR), and #25 as
a dormant building block or closed — its taskset layer only pays once the FFM per-thread
pinning and kernel isolation exist.
