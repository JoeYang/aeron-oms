# aeron-oms

An order management system built on **Aeron Cluster**.

Aeron Cluster supplies consensus, ordered ingress, the replicated log, snapshots, and leader
failover. This project supplies the `ClusteredService` — a deterministic state machine — and
the processes around it. Because every input is sequenced through the cluster, the log is a
complete description of a run, and replaying it reproduces state exactly.

## Packages

| Target | Role |
|---|---|
| `//core` | Shared library: value types, codecs. No entry point. |
| `//cluster-service` | The `ClusteredService` — deterministic state machine. |
| `//cluster-node` | Hosts `ConsensusModule` and `ClusteredServiceContainer`. |
| `//gateway` | `AeronCluster` client and protocol adapters. |
| `//driver` | Standalone `MediaDriver` launcher. |

`//cluster-service` is visible only to `//cluster-node`. A dependency from anywhere else
fails the build.

## Build

Requires Bazel. The JDK is pinned in the build as `remotejdk_25` and is not taken from `PATH`.

```bash
bazel build //...
bazel test //...
bazel run //cluster-node:cluster-node
```

## Status

Skeleton. The build, the JUnit 5 test wiring, and the package boundaries work end to end.
There is no Aeron dependency and no OMS behaviour yet — each package holds a placeholder that
exists to prove the toolchain. Development follows a spec-first flow; see
`openspec/` and `.claude/rules/process.md`.
