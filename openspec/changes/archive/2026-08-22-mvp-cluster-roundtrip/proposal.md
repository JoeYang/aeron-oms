# MVP cluster round trip

## Why

Every part of the toolchain is proven, but no message has ever travelled through a real
consensus log — the sequencer, the journal, the deterministic service and the client exist
only as placeholders. The smallest end-to-end slice (one Heartbeat in, one sequenced echo
out) turns the architecture from a diagram into a running system, produces the first real
journal, and gives the latency budgets their first measured baseline. Approved 2026-08-22
via the MVP review artifact.

## What Changes

- **BREAKING (pre-deployment):** `sbe/message-schema.xml` — Heartbeat gains an explicit
  reserved `blockLength="32"` (was implicit 8) and `TimestampNanos` gains `minValue="0"`.
  No journal exists anywhere, so the never-deployed v0 is amended in place rather than
  versioned; the `wireIdentityIsPinned` constant moves 8 → 32 in the same commit. This is
  the explicitly decided change that test exists to force into the open.
- `//cluster-service` gains `OmsClusteredService`: decodes committed Heartbeats, latches the
  cluster timestamp into `SequencedClock`, echoes a Heartbeat stamped with sequenced time.
- `//cluster-node` gains a single-node launcher: MediaDriver + Archive + ConsensusModule +
  ClusteredServiceContainer in one process, journal directories set explicitly, state kept
  across restarts by default.
- `//gateway` gains `HeartbeatRoundTrip`: connects, offers one Heartbeat, prints the
  sequenced echo and the round-trip time.
- `scripts/run.sh cluster-node` and `scripts/run.sh gateway` become real commands.

## Capabilities

### New Capabilities

- `cluster-state-machine`: the deterministic service — applies only committed log messages,
  takes time only from the log, produces egress; constructible and testable without Aeron
  running.
- `cluster-hosting`: hosting a cluster member — component wiring, journal directory layout,
  replay-on-restart behaviour, dev reset semantics.
- `cluster-gateway`: client access to the cluster — ingress offer, egress reception, and the
  round-trip measurement.

### Modified Capabilities

- `message-schema`: Heartbeat block length is reserved at 32 bytes and `TimestampNanos`
  excludes the null sentinel from its legal range (`minValue="0"`). Both are wire-contract
  requirements, decided before the first kept journal makes them permanent.

## Impact

- Bazel: `//cluster-service`, `//cluster-node`, `//gateway` gain real sources and
  `//sbe-java` / `//core` deps; `//cluster-service` visibility stays restricted to
  `//cluster-node`. No new external dependencies — everything used is already pinned.
- Schema: `sbe/message-schema.xml` (own commit, reviewed via `/sbe-gen`);
  `HeartbeatCodecTest.wireIdentityIsPinned` updated in that commit.
- Scripts: `scripts/run.sh` gains the two working targets.
- Docs: `README.md` and `.claude/rules/architecture.md` command/package tables updated in
  the same PRs that make them true.
- Deliberate non-goals: multi-node HA, snapshot content, hot-path optimisation, new
  messages, FIX.
