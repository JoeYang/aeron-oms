# Design — tape-cat viewer

## Context

`TapeReplay` already contains a correct, strict frame walker (32-byte data headers,
padding skipped, truncation/fragmentation throw) proven against the committed golden
tape. The viewer needs exactly that walk with a different consumer.

## Goals / Non-Goals

**Goals:** one decode path shared by replayer and viewer; human and JSONL output;
read-only.

**Non-Goals:** generic multi-message decode (OTF — parked as V2 in
`ideas/journal-tool.md`); any form of tape authoring or editing (parked E1/E3,
rejected E2); pagination or interactivity.

## Decisions

### D1 — Extract `TapeWalker`, do not duplicate the walk

`TapeWalker.walk(archiveDir, handler)` with a two-method handler: session messages
(timestamp, payload, log position) and other entries (template id, position). The
strictness rules live in the walker once. Extraction is a behaviour-preserving
refactor in its own commit; existing replay tests prove it.

- Alternative — copy the loop into `TapeCat`: two decoders drift apart; the golden
  tape then guards only one of them. Rejected.

### D2 — Output: aligned text by default, JSONL behind `--json`

Human mode: one line per entry — position, kind, timestamp, payload fields. JSONL
mode: one flat JSON object per line, no wrapper array, so `jq`/`grep` stream it.
Known cluster template ids map to names (timer, session open/close, new-leadership
term); unknown ids print as `template-<id>` rather than failing — viewing must not be
stricter than replaying.

### D3 — The binary lives in `//cluster-node`

Same reasoning as `tape-replay`: it decodes the app payload, and this is the one
package allowed near the service tier's codecs. Third `java_binary`, no visibility
change.

## Risks / Trade-offs

- Heartbeat-specific payload decode: acceptable while the schema has one message; V2
  (OTF) is the recorded upgrade path.
- JSON assembled by string formatting (no JSON library dependency): fields are
  numeric or fixed enum-like strings, so escaping is a non-issue today; revisit if a
  string-typed field ever appears.
