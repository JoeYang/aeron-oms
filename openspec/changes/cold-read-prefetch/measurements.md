# Measurements — cold-read prefetch

Machine: Intel Core Ultra 7 255HX, 64 GB, kernel 6.17.0-1032-oem, JDK 25, Samsung
PM9A1-class NVMe. Tape `local-fatheartbeats-1m` (1M × 32 KB, 32.9 GB extracted). Protocol
per run: fresh extraction → `sync` + `posix_fadvise(DONTNEED)` eviction → node recovery via
the `replay-cluster.sh` invocation with `oms.replay.report=true`; disk volume from
`/proc/diskstats` confirms every run read all 32.9 GB from disk (no cache pollution).

## Cold recovery, same day, same drive state (2026-08-29)

| condition | wall | msg/s | delivery | jvm cpu |
|---|---|---|---|---|
| prefetch off (baseline) | 43.9 s | 22.8k | 0.75 GB/s | 20.5 s |
| prefetch on, 4 threads (default) | 21.8 s | 45.9k | 1.5 GB/s | 29.7 s |
| prefetch on, 8 threads | 17.8 s | 56.1k | 1.85 GB/s | 31.4 s |

**2.0× at the default, 2.5× at 8 threads.** The replay report counted 1,000,000 messages in
every run — prefetch changes delivery, not output.

## Honesty notes

- **Cold-disk delivery varies with drive state.** The previous day's clean-cold baseline
  (fat-heartbeat attribution) ran 32.5 s at 1.0 GB/s; today's identical protocol gave
  43.9 s at 0.75 GB/s, and even `sync` during eviction crawled — the drive was still doing
  internal housekeeping after absorbing the 31 GB extraction write. Against the *best*
  observed baseline (32.5 s), 8-thread prefetch is 1.8×; against the same-day baseline,
  2.5×. Both numbers are real; the same-day pair is the controlled comparison.
- The 3× ceiling from the `O_DIRECT` probe (3.1 GB/s) was not reached: parallel buffered
  streams delivered 1.5–1.9 GB/s in this drive state. The prefetcher removes the
  readahead cap; it cannot make the drive healthy.
- JVM CPU rises with prefetch on (20.5 → ~30 s) — the prefetch threads' own read work,
  spent on otherwise-idle cores while recovery was disk-starved anyway.
- Working-set assumption: tape (32.9 GB) vs RAM (64 GB). An unbounded prefetch on a tape
  larger than memory can evict its own tail; the flag defaults off (design.md, decision 2).
