## 1. Declare the dependency

- [x] 1.1 Add the Aeron version as a single named value in `MODULE.bazel`
- [x] 1.2 Declare `aeron-cluster`, `aeron-archive`, `aeron-driver`, `aeron-client` and
      `org.agrona:agrona` as granular artifacts
- [x] 1.3 Confirm the artifacts resolve with `bazel build //...`

## 2. JVM configuration

- [x] 2.1 Add `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` to `.bazelrc`
- [x] 2.2 Confirm no other `--add-exports` or `--add-opens` is needed, using
      `jdeps --jdk-internals` across every Aeron and Agrona jar

## 3. Proof test (write first, watch it fail)

- [x] 3.1 Add a test that constructs an Agrona buffer — the assertion that fails without
      the export flag
- [x] 3.2 Extend it to launch an embedded `MediaDriver` and connect an `Aeron` client
- [x] 3.3 Place the aeron directory under the test's own temporary directory, with delete
      on start and on shutdown
- [x] 3.4 Verify the test fails with the export flag removed, then restore it
- [x] 3.5 Verify the test passes twice in succession, proving no leaked state

## 4. Verification

- [x] 4.1 `bazel test //...` exits zero and the proof test is reported as executed, not
      served from cache
- [x] 4.2 `scripts/lint.sh` exits zero
- [x] 4.3 Confirm no placeholder package gained an Aeron dependency

## 5. Deferred work

- [x] 5.1 Create `todo/` with a README stating how it differs from `ideas/`
- [x] 5.2 Record the unpinned `maven_install.json` gap as a todo, with the trigger that
      closes it

## 6. Documentation

- [x] 6.1 Update `README.md` and `CLAUDE.md` Status if the described state changed
