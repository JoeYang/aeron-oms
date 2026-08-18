## Why

The repository has no build. Every command in `CLAUDE.md` currently fails, the PR gate is
dormant because it is guarded on `MODULE.bazel` existing, and no behaviour can be specified
or tested until something compiles.

This change is also the first concrete test of the Aeron Cluster decision. The module layout
either matches how Aeron Cluster applications are actually deployed, or it does not, and that
is cheaper to discover now than after a state machine exists.

## What Changes

- `MODULE.bazel` with a pinned JDK 25 toolchain, so builds never depend on the ambient `java`
- JUnit 5 available to tests, with a runner that Bazel's `java_test` can drive
- Five Bazel packages reflecting the Aeron Cluster topology, one per deployable process plus
  a shared library
- A hello-world binary and a passing unit test, proving compile, run, and test end to end
- `.claude/rules/architecture.md` corrected: under Aeron Cluster the consensus module performs
  sequencing, so the `sequencer/` tier described there is not code this project writes

## Capabilities

### New Capabilities

- `build-toolchain`: A reproducible Bazel build on a pinned JDK 25, executing JUnit 5 tests,
  with a package layout that expresses the Aeron Cluster process topology and enforces the
  dependency direction between those packages.

### Modified Capabilities

None. No specs exist yet.

## Impact

- **New files**: `MODULE.bazel`, `.bazelrc`, `BUILD.bazel` per package, `src/` trees
- **Modified**: `.claude/rules/architecture.md` — the tier table is replaced with the Cluster
  topology, and the import-direction rule is restated against the new package names
- **Side effect**: creating `MODULE.bazel` activates the PR-gate hook in `.claude/settings.json`,
  which is guarded on that file. From this change onward, `gh pr create` warns when
  `bazel test //...` is failing.
- **Not affected**: no dependency on Aeron itself is added yet. This change proves the
  toolchain, not the transport.
