# tape-cat — golden tape viewer

## Why

A tape is currently opaque: verifying it is automated (`check-journals.sh`), but a
human diagnosing a mismatch — the one moment judgement is needed — has no way to see
what the journal actually contains. A viewer turns "outputs diverged" into "entry 1501
diverged, and here it is".

## What Changes

- A `tape-cat` binary decodes a tape's recorded log entry by entry: position, entry
  kind (session message, timer, cluster event), sequenced timestamp, and the decoded
  Heartbeat payload. Two output modes: human-readable lines and JSONL (one JSON object
  per line, for `jq`/`grep`).
- The frame-walking logic moves out of `TapeReplay` into a shared `TapeWalker` so the
  replayer and the viewer decode one way (refactor commit, behaviour-preserving).
- `scripts/tape-cat.sh <name> [--json]` unpacks a tape from `journal/` and runs it.
- Read-only throughout — nothing writes; editing options stay parked in
  `ideas/journal-tool.md`.

## Capabilities

### New Capabilities

- `tape-view`: decoding and displaying a tape's log entries, human and JSONL modes.

### Modified Capabilities

<!-- none — replay behaviour is unchanged; the walker refactor is internal -->

## Impact

- `//cluster-node`: new `TapeWalker` (extracted), `TapeCat` + `TapeCatMain`, third
  `java_binary`. No new dependencies, no schema or wire change.
- New script `scripts/tape-cat.sh`; command table row.
