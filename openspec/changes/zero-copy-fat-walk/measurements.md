# Measurements — zero-copy fat walk

Machine: Intel Core Ultra 7 255HX, CPUs 4,6 isolated (`isolation.sh check` passed), 64 GB,
kernel 6.17.0-1032-oem, JDK 25. Tape `local-fatheartbeats-1m` (1M × 32 KB, 32.9 GB) hosted
RAM-resident on tmpfs (prefault verified at 10.3 GB/s). Count-only, `taskset` housekeeping +
`--pin 4`, 4 runs per mode, same session.

## Throughput, copy vs zero-copy

| mode | runs (msg/s) | band |
|---|---|---|
| copy (default) | 232.5k / 237.3k / 234.5k / 236.0k | ~7.7 GB/s |
| `--zero-copy` | 213.8k / 221.3k / 218.2k / 226.1k | ~7.2 GB/s |

**The lever is falsified: zero-copy is 5–7% slower.** The hypothesis ("drop one full 32 GB
pass") assumed the reassembly copy costs a DRAM pass. It does not: the scratch buffer is
128 KB and stays cache-resident, its lines re-dirtied in place message after message, so the
copy costs L2 bandwidth the pipeline had to spare — while slice delivery pays ~23 virtual
calls per message plus carry handling at every fragment boundary. The mapped bytes are read
once either way; that was always the real pass.

## Correctness (the part that survives)

Zero-copy replay of the full tape against its two-value goldens: **REPLAY OK** — all
1,000,000 sequenced timestamps and in-place incremental checksums equal what the real
service echoed at record time, on top of the exhaustive split-equivalence unit tests.

## Verdict

Do not merge this for performance; the honest outcome is that the copy path is already
optimal for this pipeline shape. What the change proves and leaves behind if merged:
a slice-capable walker and a boundary-carrying incremental checksum, both fully tested —
the building blocks a future gather-based path (e.g. a checksum fused into a cluster-side
assembler) would need. Either decision is reasonable; the measurement, not the PR, should
make it.
