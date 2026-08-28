# Design — FatHeartbeat

## Context

Heartbeat is 128 bytes end to end and every entry fits one log frame, so `TapeWalker` could
afford to treat a fragmented frame as corruption. A 32 KB message breaks that assumption at
every hop: Aeron fragments it on the ingress channel, in the cluster log, and therefore in
the recorded journal. The walker, the goldens, and the recording tooling all need to grow —
the state machine, deliberately, barely.

## Goals / Non-Goals

**Goals:**
- One fat message type, end to end: gateway → cluster → journal → both replay modes.
- Payload integrity provable from the goldens (checksum echoed and recorded).
- Measurements comparable with the thin-tape numbers (same protocol, same machine).

**Non-Goals:**
- Order-management semantics — FatHeartbeat is still an echo, just a heavy one.
- Tuning fat-path performance (MTU, term buffers): this change measures defaults first.
- Fat egress: the echo stays small (timestamp + checksum), the payload is never sent back.

## Decisions

1. **Schema: var-length payload, not a fixed 32 KB block.** `timestampNanos` (int64) plus a
   `<data>` payload with uint16 length (caps at ~64 KB — a deliberate one-way door, recorded
   per the sizing rule; fatter than that is a different message). Var-length matches how
   real order payloads behave and lets future scenarios vary size per message without schema
   changes. Appended message, new template id, `sinceVersion` bumped — append-only per
   `/sbe-gen` rules; wire identity of existing messages must not move.
2. **Payload content is a deterministic pattern** derived from the message sequence (e.g.
   repeating the sequence bytes), never random: a tape must mean one exact byte stream. The
   pattern makes checksums predictable and corruption visible.
3a. **The echo rides a dedicated `FatHeartbeatAck`** (`timestampNanos`, `payloadChecksum`),
   appended in the same version bump — the review and original design named the echo's
   values but no message to carry them. A distinct ack keeps ingress and egress concerns
   separate instead of overloading Heartbeat's reserve bytes.

3. **Checksum: rotate-left-1-and-XOR over little-endian longs** (tail bytes zero-padded into
   the final long): order-sensitive unlike a plain sum, two ops per 8 bytes so ~4k steps for
   32 KB (~1–2 µs — memory-honest without drowning the measurement in multiply latency).
   Pinned in tests two independent ways: a byte-wise reference implementation that must agree
   with the long-wise production code, and hard constants for tiny hand-checkable payloads.
   Implemented in `//cluster-service` with no dependencies — not a CRC library (no new deps
   on the hot path, "ask first") and not cryptographic (integrity-of-replay, not security).
   Must read every byte — the point is that the apply pays the honest memory cost.
4. **Walker reassembly with a preallocated scratch buffer** sized to the max message
   (64 KB, matching the uint16 door). Unfragmented frames take the existing zero-copy path;
   only BEGIN/…/END chains copy. A chain that ends mid-way (truncated tape) throws, same as
   today's torn-frame rule. The `SessionMessageHeader` lives in the first fragment only.
   Chains never straddle a term — Aeron's term appender claims space for every fragment of
   a message at once and rotates with padding when they do not fit — and segments hold whole
   terms, so no cross-segment state exists. That is Aeron's contract, not ours: the walker
   still throws defensively on a chain left open at segment end, and the fragmented-fixture
   test verifies the contract empirically. `tape-cat` shares the walker and inherits
   reassembly for free; `check-journals.sh` is unaffected (local-* tapes are skipped).
5. **Goldens format for fat tapes: `<timestamp> <checksum>` per line.** 1M lines is a small
   file; full goldens return (the 100M thin tape was count-only out of necessity, not
   preference). App-mode replay compares both columns; a checksum mismatch is a payload
   integrity failure, which count-only could never catch.
6. **Smoke before scale.** A 1k-message fat tape (~32 MB) is recorded first: it proves a
   single 32 KB `offer` round-trips ingress and the log before 32 GB is committed to disk,
   and it is the TDD fixture for reassembly. The startup limit check verifies max message
   length on **both** the ingress and log channels — they are configured independently.

7. **Comparability is count-only.** Fat throughput is measured with the count-only golden
   (`-`), the same code path as every thin-tape number; the full checksum-verify run is the
   integrity gate, reported separately, never compared against count-only thin figures.
   Goldens are captured from what the service echoes, so the checksum *algorithm* is pinned
   by hand-computed values in unit tests — a wrong algorithm must fail a test, not become
   the golden.

8. **Recording knob, not a new script**: `record-tape.sh <name> [count]` gains a
   `TYPE=fat` environment switch the gateway understands. New tape `local-fatheartbeats-1m`,
   git-ignored like the other `local-*` tapes; committed golden tapes untouched.

## Risks / Trade-offs

- [32 KB vs cluster ingress/log limits] → Aeron's max message is term-length/8; defaults
  give comfortable headroom over 32 KB, but the recorder verifies at startup and fails fast
  rather than assuming.
- [Recording is disk-bandwidth-bound] → expected and part of the experiment; the manifest
  records the observed rate.
- [32 GB extracted tape ≈ page-cache capacity] → replay becomes streaming-bandwidth-bound;
  record `MemAvailable` with every measurement, as the protocol already requires.
- [Fragment reassembly cost lands in the walker] → the copy is once per fat entry and the
  scratch buffer is preallocated; the timed apply sees only the assembled buffer.

## Open Questions

None — scale (1M × 32 KB) and apply semantics (checksum + echo) were decided with the user.
