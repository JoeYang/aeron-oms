---
paths: ["*/src/main/java/**", "**/BUILD.bazel", "MODULE.bazel"]
---
# Architecture — packages and dependency direction

The project runs on **Aeron Cluster**. The framework supplies consensus, ordered ingress, the
log via Aeron Archive, snapshots, and leader failover. This project does not write a sequencer:
the consensus module performs sequencing.

## Packages

| Package | Bazel target | Responsibility | May depend on |
|---|---|---|---|
| sbe | `//sbe` | The message schema, XML only. No code. Changing it changes the log format. | (nothing) |
| sbe-java | `//sbe-java` | Java codecs **generated** from `//sbe` at build time. No committed source. | `sbe` |
| core | `//core` | Shared library: value types, ports such as the clock. No entry point. | `sbe-java` |
| cluster-service | `//cluster-service` | The `ClusteredService` implementation — the deterministic state machine driven by the consensus module. | `core`, `sbe-java` |
| cluster-node | `//cluster-node` | Hosts `ConsensusModule` and `ClusteredServiceContainer`. | `cluster-service`, `core` |
| gateway | `//gateway` | `AeronCluster` client plus protocol adapters (FIX, SBE). | `core`, `sbe-java` |
| driver | `//driver` | Standalone `MediaDriver` launcher. | `core` |

One Bazel package per deployable process, plus `core` as the shared library and `sbe`/`sbe-java`
as the message definition and its generated codecs.

Codecs live in their own package rather than in `core` because they change on a different
cadence and to a different standard: `core` is hand-written and reviewed line by line, while
`sbe-java` is machine output that is never read in review. Keeping them apart stops generated
code being edited by hand, and keeps `core` free of anything the schema drags in.

## Dependency direction is enforced, not documented

`//cluster-service` declares `visibility = ["//cluster-node:__pkg__"]`. Only the process that
hosts the service container may depend on it.

This is mechanical. Adding `//cluster-service` to `//gateway`'s deps produces:

```
ERROR: in java_binary rule //gateway:gateway: Visibility error:
target '//cluster-service:cluster-service' is not visible from target '//gateway:gateway'
```

The build aborts. A gateway that depended on the clustered service would reach the state
machine directly instead of through the cluster, bypassing ordering — so this is the boundary
worth enforcing before any code accumulates.

A boundary that exists only in this document is not a boundary. Every rule in the table above
is backed by a `visibility` declaration.

## The determinism rule

`cluster-service` is the replay-critical core. Given the same ordered input it MUST produce
identical state, because Aeron Cluster replays the log to rebuild state after a restart and to
bring a follower up to date. Inside `cluster-service`:

- No wall-clock reads — time arrives from the cluster, on the sequenced message or from the
  cluster clock handed to the service
- No `Random`, no generated UUIDs — any seed or identifier comes from the sequenced message
- No iteration over `HashMap`/`HashSet` where order affects output — use ordered or Agrona
  collections with deterministic iteration
- No I/O, no blocking logging, no threads
- No dependency on `gateway`, `cluster-node`, or `driver`

A determinism break is a correctness bug, not a style preference: replay diverges, and a
follower reaches a different state from the leader.

## Everything enters through the cluster

The governing principle: **all inputs are sequenced.** Not only order flow, but also market
data, reference data, configuration, and time.

The consequence is that the cluster log becomes a complete description of a run. Nothing
reaches the state machine from outside the ordered stream, so replay reproduces the run
exactly, and journal testing (@.claude/rules/testing.md) can serve as the primary test method.

> **Design deferred.** The principle is recorded here; the mechanism is not designed yet.
> Open questions include how reference-data and config updates are framed as cluster messages,
> how time is injected and at what granularity, and what all of this costs on the ingress path.
> Do not implement against this section until that discussion has happened.

## Adding a package

A new package needs a row in the table above and a matching Bazel `visibility` declaration, in
the same commit. Do not create a package that no table row describes.
