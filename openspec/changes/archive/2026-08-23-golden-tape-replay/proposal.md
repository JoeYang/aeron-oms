# Golden tape record and replay

## Why

The journal is the audit record and the recovery mechanism, but nothing preserves a
known-good journal or proves it replays correctly. A golden tape — a recorded heartbeat
run frozen as a fixture — turns replay from an assumption into a regression check, and a
schema-v1 tape recorded now becomes the permanent compatibility proof that old journals
replay through future code. That recording cannot be recreated later.

## What Changes

- A recording script runs the node plus gateway on an isolated port, then freezes the
  journal (`archive/` and `consensus/` directories) as `journal/<name>.tar.gz` with a
  manifest (message count, schema version, commit, machine) and a golden-outputs file.
- A replay tool applies the tape directly to a bare `OmsClusteredService` — no cluster,
  no media driver — by walking the recording's data frames and unwrapping the cluster's
  session-message framing, asserting count and echoed outputs against the golden files.
- The cluster node gains an opt-in replay report: a delegating wrapper counts applies
  during restart-recovery and prints count and duration once the node reaches leader.
- A benchmark script replays the tape both ways and reports messages, wall time, and
  msgs/sec per mode, with machine metadata.

## Capabilities

### New Capabilities

- `golden-tape`: recording a journal fixture and its manifest and golden outputs;
  the immutability rule (a tape is preserved, never regenerated).
- `tape-replay`: replaying a tape against the bare state machine and against the
  single-node cluster, with correctness assertions and a throughput benchmark.

### Modified Capabilities

<!-- none — existing cluster behaviour (restart recovery) is unchanged; the counting
     wrapper is observational and off by default -->

## Impact

- New top-level `journal/` folder holding committed binary fixtures (a few hundred KiB,
  sparse-tar). New scripts: record, replay-app, replay-cluster, benchmark.
- `//cluster-node` gains a second `java_binary` (the app-mode replayer) and an opt-in
  system property for the replay report. `//cluster-service` visibility is untouched.
- No schema change, no wire change, no new dependencies.
