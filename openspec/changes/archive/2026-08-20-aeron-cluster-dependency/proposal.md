## Why

Every package in this repository holds a placeholder that exists to prove the toolchain.
None of them can reference an Aeron type, because Aeron is not on the build path. No cluster
work — `ClusteredService`, ingress adapters, snapshots, journal replay tests — can begin
until the dependency is declared and proven to run.

Proving it matters more than declaring it. Agrona reaches into `jdk.internal.misc`, which
JDK 25 does not export by default, so a build that resolves the artifacts cleanly still
fails at the first buffer allocation. The dependency is not "sorted" until something
actually starts a media driver.

## What Changes

- Declare `io.aeron` 1.52.2 in `MODULE.bazel` as granular artifacts, not the `aeron-all`
  fat jar, so each Bazel package depends only on the layer it uses.
- Add `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` to the build configuration.
  This is mandatory, not a tuning choice: without it Agrona throws `IllegalAccessError`
  on the first `UnsafeBuffer` construction.
- Add a test that launches an embedded `MediaDriver` and connects an `Aeron` client, so the
  suite fails if the dependency is present but unusable.
- Attach the Aeron dependency only where code actually uses it — the proof test. Packages
  holding placeholders get no Aeron dependency, because a declared dependency nothing calls
  is scaffolding, and the tier wiring cannot be judged before the code that needs it exists.
- Record the deferred `maven_install.json` pinning as a tracked debt rather than silently
  leaving it open.

Not in scope: any `ClusteredService`, SBE schema, ingress protocol, or CPU pinning work.
This change makes Aeron available and proves it runs. It does not use it.

## Capabilities

### New Capabilities
- `aeron-runtime`: the Aeron Cluster libraries are declared, resolvable, and demonstrably
  functional on the pinned JDK — including the JVM configuration Agrona requires.

### Modified Capabilities
- `build-toolchain`: gains a requirement that the JVM configuration exports the internal
  package Agrona needs, alongside the existing native-access requirement.

## Impact

- `MODULE.bazel` — new `maven.install` artifacts (`io.aeron:aeron-cluster`,
  `aeron-archive`, `aeron-driver`, `aeron-client`, `org.agrona:agrona`)
- `.bazelrc` — one added JVM option, applying to every binary and test
- A new test target exercising the driver and client. No existing package BUILD file
  changes, because no existing package has code that uses Aeron yet.
- `todo/` — records the unpinned `maven_install.json` gap

Dependency risk is real and worth stating: this is a hot-path dependency, and its version
governs cluster log and wire compatibility. 1.52.2 was released 2026-07-10 and has had no
follow-up patch in the five weeks since, following three releases in the preceding week.
