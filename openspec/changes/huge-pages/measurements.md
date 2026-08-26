# Measurements — huge pages

Machine: Intel Core Ultra 7 255HX, isolated CPUs 4,6 (`isolation.sh check` passed), 64 GB,
kernel 6.17.0-1032-oem, JDK 25. Tape: 100M heartbeats on tmpfs (`huge=always`, placed with
`dd bs=2M`), fully huge-folio-backed (`ShmemHugePages` +12.7 GB). All runs `taskset -c
0-3,5,7-19` + `--pin 4`. `MemAvailable` 42,187,940 kB. Verification every huge run:
**12,500,992 of 12,582,912 kB PMD-mapped (99.3%)** — the remainder is per-segment tail
extents, expected.

## Apply latency (`--latency`, `--warmup`, discard first, 3 measured)

| point | p50 | p90 | p99 | p99.9 | p99.99 (per run) | max ns (per run) |
|---|---|---|---|---|---|---|
| control: tmpfs, 4 KB PTEs | 15 | 16 | 17 | 68–78 | 196, 196, 224 | 66k, 104k, 33k |
| huge: tmpfs, 2 MB PMDs | 15–17 | 16–18 | 17–20 | 78–110 | 220, 204, 240 | 41k, 39k, 36k |

**No improvement — the TLB hypothesis for the timed apply is falsified.** The walker decodes
the frame header on the same page nanoseconds before the timed apply reads it, so the TLB
entry is already hot when the clock starts; the mapping's TLB misses are paid in the untimed
decode, not the measured window. The `16→36→62 ns TLB ladder` attribution recorded in
`ideas/tail-latency-techniques.md` was wrong for this window and is corrected in this change.

## Replay throughput (no `--latency`, interleaved A/B, 3 pairs)

| pair | control (msg/s) | huge (msg/s) |
|---|---|---|
| 1 | 64,175,283 | 70,398,537 |
| 2 | 65,242,487 | 70,426,893 |
| 3 | 65,361,458 | 71,789,272 |

**+9% (median 65.2M → 70.4M msg/s), non-overlapping, interleaved.** This is where the TLB
cost actually lives: the streaming decode walks every byte of 12.5 GB, and 2 MB pages cut
its page-walk overhead. The beneficiary is recovery/replay time, not steady-state apply
latency.

## Verdict

Huge pages are a **throughput lever, not an apply-latency lever**, for this workload. The
verified-backing machinery (`--huge`, the smaps read-back, the prctl clear) is what made a
trustworthy negative possible — without the verification line, gate 4 (inherited
`PR_SET_THP_DISABLE`) would have produced a silent sham comparison. The apply-latency
residual (p99.9≈70, p99.99≈200 ns on the isolated core) needs a different attribution —
candidates: the two `nanoTime` reads themselves, residual 1 Hz housekeeping tick, and
microarchitectural effects. Attribution is future work (`perf` on the isolated core).
