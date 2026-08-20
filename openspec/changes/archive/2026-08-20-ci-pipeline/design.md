## Context

The repository is public, so Actions minutes are free. Bazel downloads `remotejdk_25` itself, so
the runner's own JDK is irrelevant and JDK 25 needs no setup step.

`main` is unprotected today: `GET /branches/main/protection` returns 404.

## Goals / Non-Goals

**Goals:**
- The completion gate runs on every pull request, not only on one machine
- A failing gate blocks a merge rather than warning about it
- CI runs the same Bazel version as a developer checkout, verifiably

**Non-Goals:**
- Release, publish, or deployment workflows
- Matrix builds across JDKs or operating systems — one pinned toolchain is the point
- A remote build cache

## Decisions

### One job, two steps

Test and lint share a Bazel server and output base, so the lint step reuses what the test step
built. Splitting them into separate jobs would duplicate the whole Bazel setup for the second.
Step names attribute failures well enough without paying that cost twice.

### `bazel` must be routed through Bazelisk, then verified

The runner ships Bazel 9.2.0 and Bazelisk 1.28.1. `.bazelversion` only governs when the `bazel`
on `PATH` is Bazelisk, so the workflow symlinks it and then asserts the running version equals
the file. Without the assertion this silently degrades to "whatever the runner had" — the
failure mode is a green CI testing a different toolchain, which is worse than no CI.

### `--nocache_test_results` on the gate

Bazel serves cached test results when inputs are unchanged, so a target can report as passing
without executing. For a completion gate that is the wrong default: the suite starts a real
`MediaDriver`, and the point of running it on CI hardware is to actually run it there. The suite
takes under a second, so the cost is nothing.

Build outputs are still cached. Only test *results* are forced to re-run.

### Cache flags live in an inert `ci` config

`.bazelrc` gains `build:ci --disk_cache` and `--repository_cache` lines. They do nothing unless
`--config=ci` is passed, so a local build is bit-for-bit unaffected. The alternative — putting a
disk cache in the unconditional `build` lines — would change every developer's local behaviour
and disk usage to serve CI.

`scripts/test.sh` passes extra arguments through but does **not** append `//...` when given any,
so the workflow passes the target explicitly: `scripts/test.sh //... --config=ci`.

### Branch protection is applied after the first successful run

A required status check whose name never reports blocks every merge permanently. The check name
is therefore read back from the first real run before protection is applied, rather than guessed
from the workflow file.

`enforce_admins` is left off. Protection blocks the normal path; an admin override remains
possible as a deliberate act, which matches how `process.md` already treats the local gate —
"a warning is not permission". Turning it on is one API call if that proves too soft.

Required reviews are not enabled: this is solo work, and requiring a reviewer who does not exist
would block every merge.

## Risks / Trade-offs

- **`AeronRuntimeTest` starts a real media driver** → Aeron's driver and client timeouts are the
  most likely source of a flaky CI on a contended shared runner. `ThreadingMode.SHARED` and a
  per-test temporary directory already reduce the exposure. If it flakes, raise the driver
  timeout for CI rather than deleting the test — a dependency proven only on a developer laptop
  is the gap this test exists to close.

- **A required check can block all merges if it stops reporting** → mitigated by applying
  protection only after a real run, and by leaving admin override available.

- **No cache on the first runs** → the initial builds download the JDK and every Maven artifact.
  Acceptable: the repository is public so minutes are free, and the cache warms after one run.

## Open Questions

- Whether to require the check on pull requests only, or also to forbid direct pushes to `main`.
  The branching rules already forbid pushing to `main`, so protection here restates an existing
  rule rather than adding one.
