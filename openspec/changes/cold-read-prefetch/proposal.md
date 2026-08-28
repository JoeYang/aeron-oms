# Cold-read prefetch for cluster recovery

## Why

The fat-heartbeat attribution (archived change `2026-08-28-fat-heartbeat`, measurements)
showed cold cluster recovery of the 1M × 32 KB tape is delivery-starved, not compute-bound:
32.9 GB read at ~1.0 GB/s with the JVM only 0.67 cores busy and no thread above 27%. The
same measurements localized the ceiling to kernel readahead — buffered streaming reads run
~1.2 GB/s under the default `read_ahead_kb=128`, while `O_DIRECT` 64 MB reads pull 3.1 GB/s
from the same file. Warm, the identical recovery finishes in 5.7 s at 5.8 GB/s. This is the
"cold-read delivery" lever from `ideas/fat-message-levers.md`: recovery time is bounded by
how fast archive bytes reach the page cache, and the kernel's per-stream readahead leaves
~3× on the table.

## What Changes

- A new cluster-node flag `oms.replay.prefetch` (default **off**). When set, the node starts
  named daemon threads at launch (`oms.replay.prefetch.threads`, default 4) that stream every
  file in the archive directory through the page cache in parallel, striped across threads,
  then exit. Parallel streams beat the per-file readahead cap because each file gets its own
  readahead pipeline; the replayer then reads warm pages at memory speed.
- Prefetch is contained: a missing archive directory is a no-op, a per-file read failure is
  counted and skipped, and no prefetch error can fail the node or alter recovery output.
- No script changes: `replay-cluster.sh` already forwards `NODE_FLAGS`, so
  `NODE_FLAGS="--jvm_flag=-Doms.replay.prefetch=true"` enables it.
- Measure cold fat recovery with the flag off and on, same eviction protocol as the
  attribution runs; record both in `measurements.md`.

The machine-layer alternative — raising `/sys/block/<dev>/queue/read_ahead_kb` — is
system-wide, needs root, and helps every cold read on the device; it is documented as an
operational option in `design.md` but not applied, because the userspace prefetcher is
self-contained, needs no privileges, and travels with the node.

## Capabilities

- `tape-replay` — cluster recovery MAY warm the archive ahead of the replayer; off by
  default; failure-contained.

## Acceptance

- With the flag off, node behavior and output are byte-identical to today.
- With the flag on, cold recovery of `local-fatheartbeats-1m` improves materially (target
  ≥2× on the 32.5 s clean-cold baseline); the replay report and goldens are unchanged.
- Unit tests cover: full read of a populated directory, missing directory, unreadable file
  skipped while others complete, and single-thread operation.
