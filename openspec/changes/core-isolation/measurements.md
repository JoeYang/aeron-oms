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

## Point (b) — `--pin` CPU 4 + `performance` on 4,6, no isolation (2026-08-25)

`MemAvailable`: 16,484,284 kB. Launch: `taskset -c 0-3,5,7-19`, runtime `--pin 4`
(affinity verified each run). Binary at commit a81f19e.

| run | p50 | p90 | p99 | p99.9 | p99.99 | max (ns) |
|---|---|---|---|---|---|---|
| 1 | 14 | 16 | 18 | 63 | 848 | 473,901 |
| 2 | 17 | 19 | 20 | 63 | 960 | 368,966 |
| 3 | 15 | 16 | 23 | 59 | 816 | 381,700 |

Medians p50/p90/p99/p99.9: **15 / 16 / 20 / 63 ns**. Per-run p99.99: **848, 960, 816 ns**.
Versus (a): p99 nearly halved (38→20) — the frequency-transition/migration band; p99.9
76→63; p99.99 down ~15%; `max` unchanged (fault-class, as design.md predicts).

## Point (c) — full isolation + `--pin` CPU 4, gated (2026-08-25, after reboot)

Kernel 6.17.0-1032-oem (the reboot pulled an update; (a)/(b) ran on 1028). Gate:
`scripts/isolation.sh check` exit 0, all four layers verified. `MemAvailable`:
54,776,228 kB — fresh boot. Prefault pass 2 took 1.2 s versus ~9 s at (a)/(b): the tape
is fully cache-resident here, which it was not before. That conditions difference favours
(c) on fault-class spikes and is inseparable from the isolation effect in `max`; the
p99.99 band is dominated by scheduler/IRQ noise, which is isolation's own work.

| run | p50 | p90 | p99 | p99.9 | p99.99 | max (ns) |
|---|---|---|---|---|---|---|
| 1 | 15 | 17 | 18 | 56 | 160 | 37,192 |
| 2 | 15 | 16 | 18 | 57 | 164 | 43,798 |
| 3 | 15 | 16 | 17 | 39 | 148 | 39,871 |

Medians p50/p90/p99/p99.9: **15 / 16 / 18 / 56 ns**. Per-run p99.99: **160, 164, 148 ns**.

## Verdict

| lever | band it moved | evidence |
|---|---|---|
| pin + `performance` (b) | p99: 38 → 20 ns | frequency transitions + migrations |
| kernel isolation (c) | p99.99: ~850 → ~155 ns; max: ~400 µs → ~40 µs | scheduler/IRQ removal; `max` shares credit with fresh-boot cache residency |

The isolated layout's ladder is now p50=15, p99=18, p99.9≈56, p99.99≈155 ns, max≈40 µs.
What remains in p99.9/p99.99 is the TLB/fault class — initiative B (huge pages for the
tape mapping) targets exactly that. Re-measuring (c) under normal desktop memory load
would separate the cache-residency share of the `max` improvement if it ever matters.
