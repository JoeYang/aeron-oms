# Tasks — mvp-cluster-roundtrip

Stacked PRs, each green under `bazel test //...` on its own. TDD order inside every group:
failing test first. Schema work is its own commit, before any code that uses it.

## 1. Schema hardening (PR 1, first commit — own commit per commit rules)

- [x] 1.1 Extend `HeartbeatCodecTest` first: block length 32 asserted in
      `wireIdentityIsPinned` and `encodedLengthIsHeaderPlusBlock`; add the minValue
      scenario. Watch the suite fail against the current schema.
- [x] 1.2 Edit `sbe/message-schema.xml`: `blockLength="32"` on Heartbeat,
      `minValue="0"` on `TimestampNanos`. Review via `/sbe-gen`; suite green.

## 2. State machine (PR 1, remainder)

- [x] 2.1 Write failing unit tests for `OmsClusteredService` with a fake `ClientSession`
      honouring real semantics (`BACK_PRESSURED`, `NOT_CONNECTED`, `CLOSED`): echo carries
      sequenced time, unknown template ignored, back-pressure retried, one echo total.
- [x] 2.2 Implement `OmsClusteredService` in `//cluster-service`; constructible with no
      Aeron running; empty remaining callbacks. Suite green.
- [x] 2.3 Update `.claude/rules/architecture.md` table row for `cluster-service` in the
      same PR that makes it true. Open PR 1.

## 3. Node hosting (PR 2, stacked on PR 1)

- [x] 3.1 Write the failing integration test: launch a single-node cluster in a temp dir,
      connect an in-process client, assert the sequenced echo and epoch-nanos timestamp.
- [x] 3.2 Implement `SingleNodeCluster` in `//cluster-node`: explicit journal dirs under
      `-Doms.data.dir`, `NanosecondClusterClock`, delete flags default false with
      `-Doms.cluster.clean`, fail-fast errorHandler, `replicationChannel` set.
- [x] 3.3 Add the restart test: stop, restart without clean flag, assert replay applies
      prior messages; with clean flag, assert empty log. Suite green. Open PR 2.

## 4. Gateway and scripts (PR 3, stacked on PR 2)

- [ ] 4.1 Write the failing test for the gateway encode path: outbound timestamp comes
      through the `Clock` port.
- [ ] 4.2 Implement `HeartbeatRoundTrip` in `//gateway`: connect, offer, poll egress,
      print sequenced timestamp and round-trip time.
- [ ] 4.3 Wire `scripts/run.sh cluster-node` and `scripts/run.sh gateway` with the
      required JVM flags; verify the two-terminal round trip by hand.
- [ ] 4.4 Sync `README.md` command table and docs; open PR 3.

## 5. Close out

- [ ] 5.1 All PRs merged bottom-up; `bazel test //...` green on `main`.
- [ ] 5.2 `openspec archive mvp-cluster-roundtrip`; delete the resolved risk rows from the
      lavish artifact or retire the page.
