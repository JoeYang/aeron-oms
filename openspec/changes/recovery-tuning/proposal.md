# Recovery machinery tuning

## Why

The fat-heartbeat attribution measured warm cluster recovery at 5.8 GB/s — a 2.8-core
pipeline with archive-conductor at 93% and clustered-service at 81% — against app-mode's
7.9 GB/s. `ideas/fat-message-levers.md` parks this lever as de-prioritized (the whole gap
is ~27%), with the caveat from the thin-tape work that DEDICATED threading *hurt* recovery
there: measure, don't assume. This change is that measurement, done as an experiment
matrix over knobs the node already exposes as system properties — no new code unless a
knob wins convincingly.

## What Changes

- Run warm fat recovery (RAM-resident tape, same protocol as the attribution) over a
  matrix: defaults; `aeron.cluster.log.fragment.limit` raised (the service log adapter's
  per-poll cap, default 50, against ~23M fragments on this tape); the node's existing
  `oms.lowlatency` profile (DEDICATED driver + busy-spin idles on archive, consensus, and
  container); and the combination. Mirrored run order so extraction drift cannot bias one
  condition.
- Record the matrix and verdict in `measurements.md`; update the parked-lever entry in
  `ideas/fat-message-levers.md` with the outcome.
- Promote a winning knob into the node or scripts only if it clears ~10% reproducibly;
  otherwise the deliverable is the recorded negative.

## Capabilities

- `tape-replay` — recovery measurement discipline only; no behavioral requirement changes
  unless a knob is promoted (decided by the measurements).

## Acceptance

- Each matrix cell has ≥2 runs, same session, with the replay report counting 1,000,000
  messages every time.
- The verdict states plainly whether any knob is worth promoting, against the ~27%
  theoretical ceiling.
