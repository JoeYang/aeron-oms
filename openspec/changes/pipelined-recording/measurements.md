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

## Reproduction (later the same night)

Fresh tapes, identical protocol: window 1 → **4,765 msg/s** (first run: 4,767 — dead-on);
window 64 → **62,236**; window 256 → **63,240**. The speedup reproduces at 13.1× and the
saturation plateau holds (64 ≈ 256, ~2.0 GB/s ingest); the windowed runs came out ~6-11%
faster than the first set, consistent with a quieter machine. All three recordings passed
recovery verification with full goldens.

## Per-message RTT under pipelining (reproduction runs)

| window | p50 | p90 | p99 | p99.9 | max |
|---|---|---|---|---|---|
| 1 | 211 µs | 229 µs | 291 µs | 461 µs | 5.5 ms |
| 64 | 850 µs | 948 µs | 2.6 ms | 9.0 ms | 12.4 ms |
| 256 | 3.6 ms | 3.9 ms | 10.1 ms | 17.8 ms | 19.9 ms |

Throughput is bought with queueing delay, exactly per Little's law (RTT ≈ outstanding ÷
throughput: 64 ÷ 62.2k/s ≈ 1.0 ms, 256 ÷ 63.2k/s ≈ 4.0 ms — the measured medians sit just
under those bounds). For a recording tool this is the right trade — nothing consumes these
RTTs — and it is the second reason window 64 beats 256: identical throughput, 4× lower
per-message delay. The state machine's apply latency is untouched by this change; the tape
contents and goldens are identical whatever the window.

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
