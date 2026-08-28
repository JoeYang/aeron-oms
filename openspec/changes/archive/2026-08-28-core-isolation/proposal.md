# Core isolation

## Why

The warmed 100M replay measures `p50=16 p90=17 p99=36 p99.9=62 max=428234 ns`. The median is
the state machine; the max is the operating system — scheduler migrations, device interrupts,
and timer ticks landing on the core mid-apply. Isolation is the approved lever aimed at the
deep tail (`max`, p99.99), per `ideas/tail-latency-techniques.md` and
`ideas/cpu-pinning-layers.md` (revisit trigger fired: FFM affinity work is now scheduled, on a
machine whose boot parameters we control). Launch-layer pinning alone was measured twice and
hurts the tail (closed PR #25); the full stack is required.

## What Changes

- Add **p99.99** to the `--latency` report line, before any isolation work, and re-record the
  100M baseline so the improvement is visible against it.
- **Machine layer** (documented and scripted; the user applies it — GRUB edit plus reboot):
  `isolcpus`, `nohz_full`, `rcu_nocbs` for the chosen isolated P-cores; device IRQs
  (interrupt requests) moved off those cores; `performance` governor on them. Recorded as
  configuration with the exact CPU layout, per the trading-latency rule.
- **Launch layer**: replay runs start under `taskset -c <housekeeping>` so every JVM thread —
  GC, JIT, VM — inherits the housekeeping mask.
- **Runtime layer**: a thread-affinity port in `//core`, implemented with the JDK 25 FFM API
  calling `sched_setaffinity`; the replay hot thread pins itself onto an isolated core from
  inside its own `Runnable`.
- **Verification layer**: read the affinity back after pinning and fail fast on mismatch. A
  silently unpinned thread looks healthy and misses every budget.
- Measure before/after on the 100M tape with identical protocol; report the percentile table.

## Capabilities

### New Capabilities
- `cpu-isolation`: thread-to-core pinning via FFM `sched_setaffinity`, affinity verification
  with fail-fast, and the documented machine-layer isolation configuration.

### Modified Capabilities
- `tape-replay`: the `--latency` report gains p99.99; count-only replay can pin its hot
  thread onto a configured core.

## Impact

- `//core`: new affinity port and Linux FFM implementation (`java.lang.foreign`, no JNI).
- `//cluster-node`: `LatencyHistogram` p99.99 query; `TapeReplayMain` pin flag and report line.
- `scripts/`: machine-layer setup/check script; replay invocation gains the taskset wrapper.
- Build: FFM restricted methods need `--enable-native-access=ALL-UNNAMED` on the runner —
  a `jvm_flags` change, which is "ask first"; this proposal is that ask.
- Machine: one GRUB kernel-cmdline edit and one reboot, performed by the user.
- No SBE, journal-format, or stream-id changes. Tapes untouched.
