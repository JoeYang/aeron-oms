# Design — cold-read prefetch

## Decision 1: userspace prefetcher, not `read_ahead_kb`, not `O_DIRECT`

| option | pros | cons |
|---|---|---|
| Parallel userspace prefetch (chosen) | no privileges; travels with the node; flag-gated; testable in JUnit | duplicates work the kernel could do; burns idle cores briefly |
| `read_ahead_kb` bump | one sysfs write; helps every cold read | root; machine-wide side effects; invisible to the repo — exactly the kind of silent machine dependency the isolation work scripts against |
| `O_DIRECT`/io_uring archive reads | highest ceiling (3.1 GB/s QD1 measured, more with depth) | bypasses the page cache, so it cannot *warm* anything — it would mean replacing the Archive's own read path, a fork of Aeron internals |

`O_DIRECT` is disqualified on a mechanism point worth recording: direct I/O does not
populate the page cache, so a direct-read *prefetcher* warms nothing. Direct I/O only helps
if the actual consumer uses it, and the consumer here is Aeron's Archive, whose read path is
not ours to rewrite. The prefetcher's job is to get bytes into the cache; buffered parallel
streams are the only way to do that from userspace.

## Decision 2: full-speed, unbounded window

The prefetcher reads as fast as it can rather than throttling to stay a bounded distance
ahead of the replayer. Warm machinery consumes at 5.8 GB/s (measured) — faster than the
~2.5–3 GB/s the parallel streams can deliver from this drive — so the replayer trails the
prefetcher closely and the freshly warmed window stays small relative to memory. Tracking
the replayer's position would need consensus-module internals for, on this evidence, no
benefit. Recorded risk: on a machine where the tape dwarfs RAM, an unbounded prefetch can
evict its own tail; the flag stays off by default and the measurement notes the working-set
assumption.

## Decision 3: stripe whole files across threads

Each thread takes every Nth file (sorted), reading sequentially within a file with a 16 MB
buffer. Per-file sequential access keeps each stream readahead-friendly; striping across
files gets the parallelism that a single stream cannot. Splitting *within* a file was
rejected: it turns each stream into a seek pattern the readahead heuristic distrusts.

## Threading

Startup work, not a duty cycle: plain named daemon platform threads ("archive-prefetch-N"),
per the thread rules — no idle strategy, they run to completion and exit. Buffer allocation
happens once per thread at start; this path ends before steady state begins, so the hot-path
allocation rule is not in play.
