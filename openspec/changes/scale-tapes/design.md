# Design — scale tapes

## Context

JIT warmth lives inside one JVM process, so "warm up with the 1M tape, then measure
the 100M tape" means different mechanics per mode: app-mode can replay two tapes in
one JVM; cluster-mode recovery is one-shot per process, so its warmup must be a
skip-the-first-N window inside the same run — the same discard convention the perf
protocol already uses.

## Goals / Non-Goals

**Goals:** record 100M-class tapes in minutes; report replay rates with warmup
excluded; keep committed-tape semantics untouched.

**Non-Goals:** pipelined recording (still closed-loop); gateway quiet mode (the
per-line log at 100M is ~10 GB of temp disk — acceptable, deleted with the workdir);
changing the CI gate.

## Decisions

- **D1 — warmup as a count, not a tape, in cluster mode.** `-Doms.replay.warmup=N`
  skips the first N applies from the timing window. Passing an actual warmup tape is
  impossible there (recovery replays exactly one journal per process). Default 0
  keeps the report byte-identical to today.
- **D2 — warmup as a tape in app mode.** `--warmup <archive-dir>` replays it in the
  same JVM, unreported, before the measured tape — literally the requested
  methodology, correct where a JVM can host two replays.
- **D3 — goldens optional only at record time, count-only only when asked.**
  `SKIP_GOLDENS=1` writes no outputs file and notes it in the manifest; `tape-replay`
  compares outputs unless the golden argument is `-`. Committed tapes keep full
  verification; nothing in the gate changes.

## Risks / Trade-offs

- A tape shorter than the warmup count reports 0 messages at rate 0 — visible, not
  wrong.
- Env-var flag passthrough is stringly typed; it mirrors the existing `measure.sh`
  convention rather than inventing a new one.
