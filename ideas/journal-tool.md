# Journal viewing and authoring tools — parked options

**What** — tooling around golden tapes beyond the V1 viewer (`tape-cat`, built 2026-08-24
as change `tape-cat-viewer`). These are the options considered and deferred, with the
reasoning, so revisiting starts warm.

**Ground rule that shaped all of them** — in-place editing of a journal is forbidden.
The journal is the append-only audit record and tapes are immutable. "Edit" therefore
always means *derive a new tape*, never mutate an existing one.

## V2 — schema-driven generic decode (SBE OTF)

Replace `tape-cat`'s hardcoded Heartbeat decoding with SBE's OTF (on-the-fly) decoding
driven by the schema IR (intermediate representation), so any future message type
displays with zero tool changes. This is the read half of [[sbe-log-tool]] —
see `ideas/sbe-log-tool.md` for the earlier notes (nested framing, performance).

- Revisit trigger: the second message type in `sbe/message-schema.xml`. With one
  message, OTF is machinery without a reader.
- Natural shape: an upgrade inside `tape-cat`, not a second tool.

## V4 — richer viewing surfaces

A TUI browser, or an HTML render (lavish) of a tape as a filterable table. Nice for
demos; poor effort/value while a tape holds one message type. Revisit only if tapes
become something reviewed by eye regularly.

## E1 — scenario composer (strongest next candidate)

A small input format — CSV or a few-line script ("3 heartbeats, 1 garbage frame,
2 heartbeats") — that drives a real node + gateway to record a new tape through the
existing `record-tape.sh` pipeline. Positions, terms, catalog, and golden outputs stay
correct *by construction* because the real cluster writes them.

- This is how mixed valid/invalid and future multi-type scenarios become tapes.
- Revisit trigger: the first time a test needs a tape that is not "N identical
  heartbeats" — likely alongside the first real order-command message.

## E2 — byte-level tape surgery (rejected, recorded so it stays rejected)

Decode → modify → re-encode frames, recomputing `recording.log` and the archive
catalog. Rejected: frame alignment, positions, and catalog must be kept mutually
consistent by hand; the output is a journal that never actually happened — a fixture
that can lie. High effort, high foot-gun, and against the spirit of the audit rules.
If a need appears that seems to require this, look again at E1 or E3 first.

## E3 — tape slicing

Derive a prefix tape ("first N messages of heartbeats-v1") for bisecting a replay
divergence. Implement safely as replay-then-re-record through a real cluster (E1
machinery), never as byte surgery (E2). Revisit trigger: the first
`check-journals.sh` mismatch that needs localizing.
