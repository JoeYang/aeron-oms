## Context

Empty repository apart from `README.md` and the `.claude/` harness. Bazel 9.1.0 and JDK
25.0.3 are installed locally (`/usr/lib/jvm/java-25-openjdk-amd64`). Bazel 9 uses bzlmod, so
there is no `WORKSPACE` file.

Aeron Cluster was chosen over raw Aeron. That decision is what drives the package list: the
framework supplies consensus, ordered ingress, the log via Aeron Archive, snapshots, and
leader failover. What this project writes is a `ClusteredService` and the clients around it.

## Goals / Non-Goals

**Goals:**

- `bazel build //...` and `bazel test //...` both pass from a clean checkout
- The JDK is pinned in the build, not inherited from `PATH`
- One Bazel package per deployable Aeron Cluster process, plus one shared library
- Dependency direction between packages enforced by Bazel `visibility`, not convention
- A test that genuinely fails when the code is wrong, so the suite is worth gating on

**Non-Goals:**

- No Aeron dependency yet. Adding Aeron, Agrona, or SBE is a separate change.
- No OMS behaviour. No orders, no matching, no state machine.
- No cluster configuration, ports, or deployment topology.
- No CI. The PR gate stays local and advisory until CI exists.

## Decisions

**Package layout — one package per deployable process, plus `core`.**
Aeron Cluster deployments separate the cluster node (consensus module plus service container)
from the clients that talk to it. Modelling that split in the build now means the boundary is
enforced from the first commit rather than retrofitted.

- `core` — shared library: value types, codecs later. No `main`.
- `cluster-service` — the `ClusteredService` implementation. Deterministic, no I/O.
- `cluster-node` — hosts `ConsensusModule` and `ClusteredServiceContainer`. Depends on
  `cluster-service`.
- `gateway` — `AeronCluster` client plus protocol adapters. Never depends on `cluster-service`.
- `driver` — `MediaDriver` launcher.

Alternative considered: a single package until behaviour exists. Rejected because the user
explicitly asked for the sub-project structure, and because the `gateway` must-not-depend-on
`cluster-service` rule is the one worth having mechanically enforced before code accumulates.

**JDK 25 pinned via a Bazel toolchain, not `PATH`.**
Pros: reproducible, and the version is visible in the build files. Cons: adds toolchain
configuration, and remote JDK availability for a given version varies by `rules_java` release.
If no remote JDK 25 is available, fall back to a locally registered JDK pointing at the
installed path — this is recorded as a risk below.

**JUnit 5, not JUnit 4.**
Bazel's native `java_test` drives the JUnit 4 runner by default. JUnit 5 needs the platform
console launcher on the test classpath and an explicit main class. The extra wiring is worth
it: the rest of the config already specifies JUnit 5, and migrating later is worse.

**`--enable-native-access=ALL-UNNAMED` in the test and binary `jvm_flags` now.**
It costs nothing today and is required the moment FFM CPU pinning lands. Setting it now means
the flag is already in place and proven rather than discovered later as a warning.

## Risks / Trade-offs

- **Remote JDK 25 toolchain may not exist** in the `rules_java` version resolved by Bazel 9.1.0.
  Mitigation: register the local JDK explicitly. This makes the build machine-dependent, which
  is a real cost and should be replaced with a remote toolchain when one is available.
- **JUnit 5 under `java_test` is fiddly.** If the console-launcher wiring proves brittle, the
  fallback is a small `java_junit5_test` macro in `tools/`. Either way the wiring is written
  once and reused.
- **Five packages with almost no code in them.** This is scaffolding ahead of need, which the
  Pace rule warns against. Accepted deliberately because the layout was explicitly requested,
  but each package holds a placeholder only — no invented domain types.
- **Creating `MODULE.bazel` activates the PR gate.** From this change on, a failing suite
  produces a warning on `gh pr create`. That is intended, but it is a behaviour change that
  arrives as a side effect rather than as its own decision.
