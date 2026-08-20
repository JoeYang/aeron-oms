## ADDED Requirements

### Requirement: The gate runs on the repository

The test suite and the lint gate SHALL run automatically on every pull request and on every push
to the default branch.

#### Scenario: A pull request triggers the gate

- **WHEN** a pull request is opened or updated
- **THEN** the workflow runs the test suite and the lint gate, and reports a status

#### Scenario: A failing suite reports failure

- **WHEN** the test suite exits non-zero
- **THEN** the workflow fails and the reported status is a failure

#### Scenario: A lint violation reports failure

- **WHEN** a source file is unformatted or violates the style rules
- **THEN** the workflow fails, rather than reporting success with warnings

### Requirement: CI runs the pinned build tool

The build tool version SHALL be declared in the repository, and CI SHALL verify that the declared
version is the one that ran.

#### Scenario: The declared version is used

- **WHEN** the workflow runs on a runner whose preinstalled build tool is a different version
- **THEN** the declared version runs instead

#### Scenario: Version drift fails the build

- **WHEN** the running version does not match the declared version
- **THEN** the workflow fails with both versions named, rather than continuing

### Requirement: The gate executes tests rather than serving cached results

The workflow SHALL force test execution, so that a reported pass means the tests ran on the
runner.

#### Scenario: Tests are not served from cache

- **WHEN** the workflow runs with a warm build cache
- **THEN** test targets are executed rather than reported from cached results

### Requirement: Formatting is checked, never applied

CI SHALL run the read-only check and SHALL NOT run the rewriting formatter.

#### Scenario: The workflow does not modify sources

- **WHEN** the workflow runs against unformatted sources
- **THEN** it fails the check, and no step rewrites a file

### Requirement: Local builds are unaffected by CI configuration

Configuration added for CI SHALL be inert during an ordinary local build.

#### Scenario: A local build ignores the CI configuration

- **WHEN** a developer runs the build or test scripts without CI flags
- **THEN** no CI-specific cache location or option takes effect

### Requirement: A failing gate blocks a merge

The default branch SHALL require the gate to pass before a pull request can be merged.

#### Scenario: A red pull request cannot be merged normally

- **WHEN** the gate has failed on a pull request
- **THEN** the merge is blocked through the normal path

#### Scenario: Protection references a check that reports

- **WHEN** branch protection is configured
- **THEN** the required check name is one that the workflow has already reported, so protection
  cannot block every merge by naming a check that never runs
