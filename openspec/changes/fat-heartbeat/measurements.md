# Measurements — FatHeartbeat (1M × 32 KB)

Machine: Intel Core Ultra 7 255HX, CPUs 4,6 isolated (`isolation.sh check` passed for every
pinned run), 64 GB, kernel 6.17.0-1032-oem, JDK 25. Tape `local-fatheartbeats-1m`: 1M
messages, 31 GB extracted, 350 MB tar.gz (~91:1 — patterned payloads compress), 40 MB of
two-value goldens. Recording: closed-loop over IPC, ~4.5 min wall (~3.7k msg/s, ~120 MB/s
into the journal — each message waits for its ack, so this is RTT-bound, not disk-bound).
`MemAvailable` ~40 GB: the 31 GB tape is only partially cache-resident (prefault passes
~25 s each); the discarded first run absorbs the disk work.

## App-mode throughput (count-only, `taskset` + `--pin 4`, discard 1, 3 measured)

| run | msg/s | GB/s |
|---|---|---|
| 1 | 219,627 | 7.0 |
| 2 | 244,908 | 7.8 |
| 3 | 247,999 | 7.9 |

**The pipeline is byte-bandwidth-bound, not message-bound**: fat ~7.9 GB/s vs thin
65M msg/s × 128 B ≈ 8.3 GB/s — a 250× larger message costs almost exactly 250× the time,
no more. Fragment reassembly (one copy per fat entry) and the checksum (reads every byte)
are both inside that budget.

## Apply latency (`--latency`, warmed by the fat 1k tape, pinned, discard 1, 3 measured)

| run | p50 | p90 | p99 | p99.9 | p99.99 | max (ns) |
|---|---|---|---|---|---|---|
| 1 | 1600 | 1632 | 1728 | 1792 | 6016 | 47,166 |
| 2 | 1568 | 1632 | 1696 | 1760 | 7680 | 299,065 |
| 3 | 1568 | 1632 | 1728 | 1792 | 5888 | 17,974 |

The design's predicted "~1–2 µs honest cost of touching fat data" measured: **p50 = 1.6 µs**,
which is 32 KB read at ~20 GB/s — cache-speed, because the walker's reassembly copy leaves
the payload hot for the checksum. The distribution is remarkably tight: p99.9 sits within
~200 ns of the median on the isolated core. p99.99 (6–8 µs) and max (18–300 µs) are the
fault band of a partially-resident 31 GB mapping.

## Integrity gate (full two-value goldens, one run)

All 1,000,000 timestamps **and** checksums match: payload integrity is proven through
gateway → sequencing → fragmented journal → reassembly → checksum → golden. `REPLAY OK`.

## Cluster-mode recovery (no tricks, 2 runs)

44.85 s and 47.20 s → **22.3k / 21.2k msg/s ≈ 0.7 GB/s**. The cluster path's fat penalty is
~11× versus app-mode, where thin messages paid only ~2× — recovery through the real cluster
pays per-fragment costs (archive reads via Aeron, log adapter handling, copies) that scale
with bytes. Practical reading: a 1M-fat-message day recovers in ~45 s versus ~3.3 s for 100M
thin messages — **journal bytes, not message count, size the recovery bill**, and this is
where a future fat-path initiative (or snapshotting) would aim.

## Verdict

The system handles 32 KB messages end to end at full honesty: they fragment at every hop,
the walker reassembles them, the apply pays 1.6 µs to read every byte, and the goldens prove
integrity for all million. The app-mode pipeline treats message size as free per byte
(~8 GB/s either way); the cluster recovery path does not, and is the first place to look
if fat recovery time ever matters.
