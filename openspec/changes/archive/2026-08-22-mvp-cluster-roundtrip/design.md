# Design — mvp-cluster-roundtrip

## Context

All toolchain pieces are proven (build, codecs, clock port, CI); no process runs. Aeron
Cluster supplies the sequencer (ConsensusModule), the journal (Archive) and commit
semantics; this change writes only the three thin components around it. API signatures were
verified with `javap` against the pinned 1.52.2 jars, and the launcher configuration was
verified by an independent review that ran a real single-node cluster from those jars.
Full review surface: `.lavish/mvp-topology.html` (decisions resolved 2026-08-22).

## Goals / Non-Goals

**Goals:** first message through a real consensus log; first journal on disk;
`SequencedClock` used as designed; runnable `scripts/run.sh` targets; first latency number.

**Non-Goals:** multi-node HA, snapshot content, hot-path optimisation (allocation audit,
pinning, warmup), new messages, FIX, bounded back-pressure policy.

## Decisions

- **Single member, quorum of one, real everything.** Alternative: fake the cluster with
  direct calls. Rejected — the point is exercising the real log, election, commit and
  replay paths; multi-node later changes only who acknowledges commit.
- **Reuse Heartbeat in both directions.** Alternative: introduce a Ping/Pong pair.
  Rejected — new messages are gated on the venue-semantics decision; the echo proves the
  same pipeline with zero schema growth.
- **Amend the layout pre-deployment** (`blockLength="32"`, `minValue="0"`) **with a version
  bump to 1**. The layout change is legal only because no journal exists anywhere — that
  window closes at the first kept journal, which is why both were decided now. The version
  still increments: `api-design.md` and the `/sbe-gen` rules require a bump on every schema
  change with no pre-deployment exemption, and the bump costs nothing on the wire. No
  `sinceVersion` markers — no field is new. `wireIdentityIsPinned` moves block length
  8 → 32 and version 0 → 1 in the same commit.
- **`NanosecondClusterClock`.** Alternative: default millisecond clock plus conversion.
  Rejected — verified live that the nanosecond clock returns epoch nanos, the exact
  `Clock.timeNanos()` contract; a conversion is a standing unit-bug risk.
- **Journal directories explicit under `-Doms.data.dir`;** container shares the module's
  cluster dir. Aeron's defaults are relative paths and land in Bazel runfiles.
- **Delete flags default false; `-Doms.cluster.clean=true` is the reset.** Alternative:
  wipe-on-start for convenience. Rejected — wiping masks replay bugs and contradicts the
  audit rule; with false, every restart exercises replay (verified live).
- **Fail-fast errorHandler.** Aeron's default logs and continues, silently skipping the
  message — undetectable divergence. Alternative (validate-and-ignore inside the service)
  rejected: it converts an invariant violation into silence.
- **Egress back-pressure: retry with the cluster idle strategy.** Replay cannot hang — the
  reviewer verified replayed offers return a mocked positive result. Bounded policy later.

## Risks / Trade-offs

- [Retry-forever spins if the session closes mid-retry] → accepted for MVP, stated in spec.
- [`fileSyncLevel=0`: power loss can drop the un-flushed tail] → accepted; durability comes
  from the quorum once HA exists; revisit at multi-node.
- [Empty `onTakeSnapshot`] → acceptable while state is a clock latch, re-derived by replay;
  snapshot design is its own initiative.
- [Integration test flakiness (ports, timing)] → temp dirs per test, ephemeral/high ports,
  generous connect timeouts, single test that owns the node lifecycle.

## Migration Plan

None — no deployed system, no journal to migrate. The schema amendment is safe only
because of that, and the spec records it.

## Open Questions

None blocking. The venue-semantics decision (what a command looks like on the wire)
deliberately stays outside this change.
