## 1. Pin the build tool

- [x] 1.1 Add `.bazelversion` matching the version used locally
- [x] 1.2 Confirm the local build still works with the file present

## 2. CI configuration

- [x] 2.1 Add a `ci` config to `.bazelrc` with disk and repository cache locations
- [x] 2.2 Confirm a local build is unchanged, since the config is inert without `--config=ci`

## 3. Workflow

- [x] 3.1 Add `.github/workflows/ci.yml` triggering on pull requests and pushes to `main`
- [x] 3.2 Route `bazel` through Bazelisk so `.bazelversion` governs
- [x] 3.3 Assert the running Bazel version equals `.bazelversion`, failing with both named
- [x] 3.4 Run the test suite with `--nocache_test_results` so a pass means it executed
- [x] 3.5 Run the lint gate; do not run the rewriting formatter
- [x] 3.6 Cache the disk and repository caches across runs
- [x] 3.7 Pin action versions to current majors, checked rather than recalled
- [x] 3.8 Set least-privilege permissions and cancel superseded runs

## 4. Verification

- [x] 4.1 Confirm the workflow file is valid YAML
- [x] 4.2 Push and confirm the workflow runs and reports a status
- [x] 4.3 Confirm the version assertion passes on the runner
- [x] 4.4 Confirm the run reports tests as executed, not cached

## 5. Branch protection

Blocked until this change is on `main`. PRs #7 and #8 carry no workflow, so they report zero
check runs; requiring `gate` before the workflow lands would make them permanently unmergeable.
Apply immediately after merge.

- [x] 5.1 Read the actual check name from the first completed run: `gate`
- [ ] 5.2 Apply branch protection on `main` requiring that check
- [ ] 5.3 Verify protection is present and names the reporting check

## 6. Documentation

- [x] 6.1 Update `process.md` where it says the gate rests on discipline
- [x] 6.2 Add the CI command to the `CLAUDE.md` command table if it belongs there
