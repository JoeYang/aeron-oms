# Measurements — pipelined recording

Machine: Intel Core Ultra 7 255HX, 64 GB, kernel 6.17.0-1032-oem, JDK 25. Same day, same
protocol per run: `record-tape.sh` with `TYPE=fat`, IPC on both sides, 100,000 × 32 KB
messages; the rate is the gateway's own `fat-stream` report (send-to-last-ack window, which
excludes build, node startup, tape freeze, and recovery verification). Every recording
passed the script's built-in recovery verification with full two-value goldens.

| window | stream time | rate | journal ingest |
|---|---|---|---|
| 1 (closed loop, baseline) | 20.98 s | 4,767 msg/s | ~0.15 GB/s |
| 64 | 1.70 s | 58,696 msg/s | ~1.9 GB/s |
| 256 | 1.76 s | 56,879 msg/s | ~1.9 GB/s |

**12.3× at window 64.** 256 buys nothing more — the pipe saturates near 1.9 GB/s, so past
~64 outstanding the binding constraint moves off the RTT and onto the ingest path
(archive write / IPC flow control), which is where it should sit for a recording tool.

Notes:

- The closed-loop baseline here (4.8k msg/s) is faster than the 1M recording's ~3.7k from
  the fat-heartbeat change — smaller run, no interleaved journal pressure from 31 GB of
  accumulated segments. Same-day pairs are the honest comparison, as with the prefetch
  measurements.
- Practical consequence: the 1M fat tape that took ~4.5 minutes of streaming now takes
  ~17 s; a 10M tape moves from ~45 minutes to ~3.
- Tapes `local-fatheartbeats-100k{,-w64,-w256}` are local (git-ignored) measurement
  artifacts in this worktree's journal, regenerable by the commands above — new names,
  never reused, per the immutability rule.
