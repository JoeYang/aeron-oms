## 1. Build configuration

- [x] 1.1 Create `MODULE.bazel` with `rules_java` and `rules_jvm_external`
- [x] 1.2 Pin the JDK 25 toolchain; fall back to a locally registered JDK if no remote JDK 25 resolves
- [x] 1.3 Create `.bazelrc` with the Java language level and `--enable-native-access=ALL-UNNAMED`
- [x] 1.4 Declare the JUnit 5 Maven dependencies and verify they resolve

## 2. Package layout

- [x] 2.1 Create `core` as a `java_library` with no entry point
- [x] 2.2 Create `cluster-service`, depending on `core` only
- [x] 2.3 Create `cluster-node`, depending on `cluster-service` and `core`
- [x] 2.4 Create `gateway`, depending on `core` only
- [x] 2.5 Create `driver`, depending on `core` only
- [x] 2.6 Set `visibility` on each target so the direction above is enforced by the build

## 3. Hello world and test

- [x] 3.1 Add a small shared type in `core` with real behaviour to assert against
- [x] 3.2 Add a `java_binary` that runs and prints, proving the binary path works
- [x] 3.3 Add a JUnit 5 test covering the `core` type, including a boundary case
- [x] 3.4 Verify the test genuinely fails when the implementation is broken, then restore it

## 4. Verification

- [x] 4.1 `bazel build //...` exits zero
- [x] 4.2 `bazel test //...` exits zero and reports the executed test
- [x] 4.3 Confirm a forbidden dependency (`gateway` on `cluster-service`) fails the build
- [x] 4.4 Confirm the running JVM reports JDK 25

## 5. Documentation

- [x] 5.1 Correct `.claude/rules/architecture.md`: replace the five-tier table with the Aeron Cluster topology and remove the `sequencer/` tier
- [x] 5.2 Update the layering hook in `.claude/settings.json` to match the new package names
- [x] 5.3 Update `CLAUDE.md`: architecture block, and remove the genesis Status caveat for commands that now work
- [x] 5.4 Update `README.md` so its design description matches what exists

## 6. Developer entry points

- [x] 6.1 Add `scripts/build.sh` wrapping `bazel build`, with pass-through arguments
- [x] 6.2 Add `scripts/test.sh` wrapping `bazel test`, with pass-through arguments
- [x] 6.3 Add `scripts/run.sh` to launch a named process binary, validating the name
- [x] 6.4 Make the scripts runnable from any working directory
- [x] 6.5 Verify each script exits non-zero when the underlying Bazel command fails
- [x] 6.6 List the scripts in `CLAUDE.md` and `README.md`

## 7. Formatting and linting

- [x] 7.1 Resolve google-java-format and Checkstyle through Bazel, not a committed jar
- [x] 7.2 Add the `--add-exports` JVM flags google-java-format needs on a modern JDK
- [x] 7.3 Add `scripts/format.sh`, rewriting sources in place
- [x] 7.4 Add `scripts/lint.sh` as a read-only gate: format check plus Checkstyle
- [x] 7.5 Promote Checkstyle severity to error so the gate fails rather than warns
- [x] 7.6 Fix the violations the gate exposes
- [x] 7.7 Point the auto-format hook at the built launcher so it no longer no-ops
- [x] 7.8 Verify the gate fails on a misformatted file, then restore it
