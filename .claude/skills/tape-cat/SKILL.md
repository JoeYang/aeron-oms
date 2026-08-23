---
name: tape-cat
description: Use when inspecting a golden tape — viewing what a journal contains, reading or explaining tape-cat output, diagnosing a check-journals mismatch, or finding a specific entry in the recorded log.
---

# tape-cat — golden tape viewer

Read-only. Decodes a tape from `journal/` and prints one line per recorded log entry.
It never modifies a tape; tapes are immutable by rule.

## Run it

```bash
scripts/tape-cat.sh <name>            # human-readable, e.g. scripts/tape-cat.sh heartbeats-v1
scripts/tape-cat.sh <name> --json     # JSONL: one JSON object per line, for jq/grep
```

The argument is the tape name from `journal/` — no path, no `.tar.gz`. The script
unpacks to a temp dir and builds `//cluster-node:tape-cat` if needed.

## Read a line

```
       256  session-message  t=1787429407462746339  Heartbeat timestampNanos=1787429407461166568
```

| Part | Meaning |
|---|---|
| `256` | **Log position, in bytes** — the entry's frame offset in the recorded log. Not a counter. |
| `session-message` | Entry kind. Application message; everything else is a cluster event. |
| `t=` | The **sequenced** cluster timestamp — stamped by the sequencer, echoed by the service, recorded in golden outputs. |
| `timestampNanos=` | The payload: the **gateway's send-time** stamp — earlier and different. |

Two facts people trip on:

- **There is no sequence number, by design.** The log itself is the total order; an
  entry's identity is its byte position. "Message N" = the Nth session-message line.
  Heartbeat entries sit 128 bytes apart (104-byte entry aligned to 32).
- **`t=` minus `timestampNanos` is the per-message sequencing delay** (gateway send →
  sequenced apply). On `heartbeats-v1` it starts ~1.2–1.8 ms cold and settles to
  ~60–90 µs warm — the tape recorded the warmup curve.

Entry kinds seen on a heartbeat tape: `new-leadership-term`, `session-open`,
`session-message` × N, `session-close`. Timers appear as `timer-event`; anything the
viewer does not know by name prints as `template-<id>` and the walk continues.

## Recipes

```bash
# per-message sequencing delay, microseconds
scripts/tape-cat.sh <name> --json \
  | jq -r 'select(.kind=="session-message") | (.timestamp - .timestampNanos) / 1000'

# number the messages (ordinal view)
scripts/tape-cat.sh <name> --json | jq -c 'select(.kind=="session-message")' | nl -ba

# only the cluster events
scripts/tape-cat.sh <name> | grep -v session-message

# the entry at a known position (e.g. from a divergence report)
scripts/tape-cat.sh <name> --json | jq 'select(.position==384128)'
```

## Where it fits

`scripts/check-journals.sh` (the CI gate) says **whether** replay diverged; tape-cat
says **where and what** — that is the diagnosis moment. Divergence rule: the fault is
in the code, never in the tape — do not update golden files or re-record a tape to
make a gate pass.

## Limits

- Read-only; authoring/slicing options are parked in `ideas/journal-tool.md`.
- Decodes Heartbeat payloads only (schema has one message); unknown payloads print
  raw schema/template ids. Generic decode is the recorded V2 upgrade.
- `clusterSessionId` (which client session sent an entry) is in the log but not yet
  printed — single-gateway tapes make it redundant today.
- One recording per tape; truncated or fragmented frames throw — same strictness as
  replay, shared via `TapeWalker`.
