## Why

`process.md` states the PR gate rests on discipline until a required status check exists:

> Real enforcement is a required status check on `main` in branch protection... A local hook
> guards one machine and can be bypassed; branch protection guards the repository and cannot.

There is no CI at all today, and `main` is unprotected. The local `PreToolUse` hook only warns,
and only on this machine. So the rule the project treats as absolute is currently enforced by
nothing.

## What Changes

- Add a GitHub Actions workflow running the test suite and the lint gate on every pull request
  and every push to `main`.
- Pin the Bazel version in `.bazelversion`, and assert in CI that the pinned version is the one
  that actually ran.
- Add an inert `ci` config to `.bazelrc` carrying the cache flags, so local builds are unchanged.
- Enable branch protection on `main` requiring the check to pass before merge.

**Format is not a separate step.** `scripts/format.sh` rewrites files, so running it in CI would
mutate a runner and discard the result, proving nothing. `scripts/lint.sh` is already the
read-only equivalent: format check plus Checkstyle. CI runs the check, never the rewrite.

Not in scope: a release or publish workflow, matrix builds across JDKs or platforms, and a
remote build cache.

## Capabilities

### New Capabilities
- `continuous-integration`: the completion gate runs on the repository rather than on one
  developer's machine, and blocks a merge when it fails.

## Impact

- `.github/workflows/ci.yml` — new
- `.bazelversion` — new, pinning the Bazel version
- `.bazelrc` — a `ci` config that is inert unless `--config=ci` is passed
- Repository settings — branch protection on `main`, applied after the check has reported once
  so a check that never runs cannot permanently block merges

### Version drift found while scoping this

The runner image ships **Bazel 9.2.0**; this project builds with **9.1.0**. Without
`.bazelversion`, CI would silently test a different Bazel than the one used locally, which is
the class of difference that produces a green CI and a broken checkout. Bazelisk 1.28.1 is also
on the image, so pinning works — provided `bazel` resolves to Bazelisk rather than to the real
9.2.0 binary, which the workflow arranges and then verifies.
