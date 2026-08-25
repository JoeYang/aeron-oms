# Scale tapes — fast recording and warmed replay

## Why

The first 100M-message tape exposed two gaps. Recording at the default profile takes
~12 hours (measured: 1M in ~7 minutes closed-loop), and golden outputs at that scale
are a 2 GB file nothing needs. Separately, replay throughput numbers include JIT
warmup; the measurement rules require warmup to be excluded, and today it cannot be.

## What Changes

- `record-tape.sh` accepts `NODE_FLAGS`/`GW_FLAGS` env passthrough (tuned-profile
  recording, ~2.5 µs round trips) and `SKIP_GOLDENS=1` for local scale tapes; the
  gateway always receives the data dir so IPC mode works from the script.
- Cluster replay report gains a warmup window: `-Doms.replay.warmup=N` starts the
  timing after the first N applies (the run self-warms, the report excludes it).
- App-mode `tape-replay` gains `--warmup <archive-dir>` (replay a warmup tape in the
  same JVM first, report only the main tape) and accepts `-` for the golden-outputs
  argument (count-only verification, for tapes recorded with `SKIP_GOLDENS`).
- `replay-cluster.sh` passes `NODE_FLAGS` through so the warmup property is reachable.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `golden-tape`: recording supports profile flags and optional golden outputs.
- `tape-replay`: both replay modes support a warmup window excluded from the report.

## Impact

- `ReplayReportingService` (warmup-aware window), `ClusterNodeMain` (one property),
  `TapeReplayMain` (two arguments); `record-tape.sh`, `replay-cluster.sh`.
- No schema, wire, or dependency change. Committed golden tapes unaffected.
