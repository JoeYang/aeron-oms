# Measurements — core isolation

Protocol per design.md Decision 6: extracted 100M archive prefaulted with two sequential
reads, one discarded run, three measured runs, `--warmup` 1M tape, `--latency`.
Machine: Intel Core Ultra 7 255HX, 64 GB, kernel 6.17.0-1028-oem, JDK 25 (Bazel remotejdk).
Binary: `bazel-bin/cluster-node/tape-replay` at commit 7907a0a.

## Point (a) — baseline: powersave, unpinned, no isolation (2026-08-25)

`MemAvailable` at run time: 17,074,592 kB. Note: both prefault passes took ~9 s
(~1.4 GB/s), so the page cache does not retain the full 12.8 GB tape under current
memory pressure — fault-class spikes remain present in `max` at this point.

| run | p50 | p90 | p99 | p99.9 | p99.99 | max (ns) |
|---|---|---|---|---|---|---|
| 1 | 16 | 18 | 38 | 76 | 1056 | 481,751 |
| 2 | 16 | 17 | 43 | 76 | 640 | 776,838 |
| 3 | 16 | 17 | 38 | 70 | 1056 | 479,635 |

Medians p50/p90/p99/p99.9: **16 / 17 / 38 / 76 ns**. Per-run p99.99: **1056, 640, 1056 ns**
(~65× the median — the scheduler/IRQ noise band this initiative targets).

## Point (b) — `--pin` CPU 4 + `performance` governor on 4,6 — pending

## Point (c) — full isolation, gated on `isolation.sh check` — pending
