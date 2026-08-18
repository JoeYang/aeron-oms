## ADDED Requirements

### Requirement: Pinned JDK toolchain

The build SHALL compile and run against JDK 25 declared in the build configuration, and SHALL
NOT depend on the `java` executable found on `PATH`.

#### Scenario: Build uses the pinned JDK

- **WHEN** `bazel build //...` runs on a machine whose `PATH` `java` is not JDK 25
- **THEN** the build succeeds and the compiled classes target JDK 25

#### Scenario: Java version is visible in the build files

- **WHEN** a reader opens `MODULE.bazel`
- **THEN** the required Java version is stated there explicitly, not implied

### Requirement: Reproducible build and test commands

The commands documented in `CLAUDE.md` SHALL execute successfully from a clean checkout.

#### Scenario: Full build passes

- **WHEN** `bazel build //...` runs
- **THEN** it exits zero with every target built

#### Scenario: Full test suite passes

- **WHEN** `bazel test //...` runs
- **THEN** it exits zero and reports at least one executed test

### Requirement: JUnit 5 test execution

Tests SHALL be written against JUnit 5 and SHALL be executed by Bazel's test runner.

#### Scenario: A passing test is reported as passing

- **WHEN** `bazel test //...` runs against a correct implementation
- **THEN** the JUnit 5 test executes and the target reports `PASSED`

#### Scenario: A failing assertion fails the build

- **WHEN** the implementation under test is changed to violate its assertion
- **THEN** `bazel test //...` exits non-zero and names the failing test

### Requirement: Package layout for Aeron Cluster processes

The build SHALL provide one Bazel package per deployable Aeron Cluster process, plus one
shared library package.

#### Scenario: Required packages exist

- **WHEN** `bazel query //...` runs
- **THEN** targets exist under `core`, `cluster-service`, `cluster-node`, `gateway`, and `driver`

#### Scenario: Shared library has no entry point

- **WHEN** the `core` package is inspected
- **THEN** it declares a `java_library` and no `java_binary`

### Requirement: Enforced dependency direction

Package dependencies SHALL be constrained by Bazel `visibility` so that a forbidden dependency
fails the build rather than producing a warning.

#### Scenario: Gateway cannot depend on the clustered service

- **WHEN** a dependency on `//cluster-service` is added to `//gateway`
- **THEN** `bazel build //...` fails with a visibility error

#### Scenario: Every package may depend on core

- **WHEN** any package declares a dependency on `//core`
- **THEN** the build succeeds

### Requirement: Developer entry point scripts

The repository SHALL provide shell entry points for build, test, and run, so that a newcomer
does not need to know Bazel target syntax to get started.

#### Scenario: Scripts work from any directory

- **WHEN** `scripts/test.sh` is invoked from a subdirectory of the repository
- **THEN** it runs against the whole repository, not the subdirectory

#### Scenario: Failure propagates

- **WHEN** the underlying Bazel command fails
- **THEN** the script exits non-zero rather than reporting success

#### Scenario: Unknown process is rejected

- **WHEN** `scripts/run.sh` is given a name that is not a declared process
- **THEN** it exits non-zero and lists the valid process names

### Requirement: Formatting and linting are enforceable

The repository SHALL provide a formatter that rewrites sources and a read-only check that
fails on unformatted or non-conforming code, so the check can gate a PR.

#### Scenario: Formatter and linter resolve through the build

- **WHEN** a developer runs the format or lint script on a clean checkout
- **THEN** the tools are fetched by the build system, with no jar committed to the repository
  and no download performed by the script itself

#### Scenario: Unformatted code fails the check

- **WHEN** a source file does not match the formatter's output
- **THEN** `scripts/lint.sh` exits non-zero and names the offending file

#### Scenario: Style violations fail the check

- **WHEN** a source file violates the configured style rules
- **THEN** `scripts/lint.sh` exits non-zero, rather than printing warnings and succeeding

#### Scenario: The check never modifies files

- **WHEN** `scripts/lint.sh` runs against unformatted sources
- **THEN** the working tree is unchanged afterwards

### Requirement: Native access flag present

Binary and test targets SHALL run with native access enabled, so that the FFM calls required
for CPU pinning do not later emit restricted-method warnings.

#### Scenario: Flag is applied to executable targets

- **WHEN** a `java_binary` or `java_test` target is executed by Bazel
- **THEN** the JVM runs with `--enable-native-access=ALL-UNNAMED`
