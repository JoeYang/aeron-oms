# aeron-oms

An order management system built on **Aeron Cluster**.

Aeron Cluster supplies consensus, ordered ingress, the replicated log, snapshots, and leader
failover. This project supplies the `ClusteredService` — a deterministic state machine — and
the processes around it. Because every input is sequenced through the cluster, the log is a
complete description of a run, and replaying it reproduces state exactly.

## Packages

| Target | Role |
|---|---|
| `//sbe` | The message schema, XML only. Changing it changes the log format. |
| `//sbe-java` | Java codecs generated from `//sbe` at build time. Nothing committed. |
| `//core` | Shared library: value types and ports, such as the clock. No entry point. |
| `//cluster-service` | The `ClusteredService` — deterministic state machine. |
| `//cluster-node` | Hosts `ConsensusModule` and `ClusteredServiceContainer`. |
| `//gateway` | `AeronCluster` client and protocol adapters. |
| `//driver` | Standalone `MediaDriver` launcher. |

`//cluster-service` is visible only to `//cluster-node`. A dependency from anywhere else
fails the build.

## Build

Requires Bazel. The JDK is pinned in the build as `remotejdk_25` and is not taken from `PATH`.

```bash
scripts/build.sh                 # bazel build //...
scripts/test.sh                  # bazel test //...
scripts/run.sh cluster-node      # gateway | driver
scripts/format.sh                # google-java-format, rewrites files
scripts/lint.sh                  # format check + Checkstyle, read-only
scripts/record-tape.sh <name>    # freeze a golden tape under journal/
scripts/replay-app.sh <name>     # replay a tape against the bare state machine
scripts/replay-cluster.sh <name> # replay a tape through cluster recovery
scripts/replay-bench.sh <name>   # benchmark both replay modes
```

The scripts run from any directory and pass extra arguments through to Bazel, so
`scripts/test.sh //core:core_test` works. Use Bazel directly when you need more control.

## Status

MVP round trip. A single-member cluster (Aeron Cluster 1.52.2) hosts the state machine —
currently an echo service that stamps each `Heartbeat` with the sequenced cluster time —
and the gateway streams heartbeats through it. Every input is journaled by the Archive
before it is applied.

Replay is proven, not assumed: `journal/` holds an immutable golden tape (a recorded
journal plus its expected outputs), and the suite replays it both against the bare state
machine and through cluster restart-recovery, asserting identical outputs each time. No
order-management behaviour exists yet beyond the heartbeat path.

Development follows a spec-first flow; see `openspec/` and `.claude/rules/process.md`.
Deferred work is tracked in `todo/`, and unbuilt options in `ideas/`.
