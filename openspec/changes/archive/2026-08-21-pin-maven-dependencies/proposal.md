## Why

`maven.install` resolves without a lock file. `MODULE.bazel.lock` does not close this: it
records extension fingerprints, not resolved artifact versions. Two machines, or one machine on
two dates, can resolve differently while every declared version string stays identical.

The `todo/` entry deferring this named its own trigger:

> A second machine or a CI runner builds this repository. Unpinned resolution is invisible while
> exactly one machine ever builds.

CI now builds the repository on GitHub runners. The trigger has fired.

This matters more than usual here because Aeron's version governs cluster log and wire
compatibility, so a resolution that quietly moves is not a cosmetic difference.

## What Changes

- Generate and commit `maven_install.json` via `bazel run @maven//:pin`.
- Reference it from `MODULE.bazel` so the build fails when the lock and the declared artifacts
  disagree, rather than silently re-resolving.
- Remove the `todo/` entry, since promotion deletes the file that tracked it.

Not in scope: changing any dependency version. This pins what already resolves.

## Capabilities

### Modified Capabilities
- `build-toolchain`: gains a requirement that dependency resolution is reproducible.

## Impact

- `maven_install.json` — new, generated, large. It lands in its own commit and is exempt from
  the 400-line limit on the same basis as generated documentation.
- `MODULE.bazel` — references the lock file
- `todo/pin-maven-dependencies.md` — deleted
