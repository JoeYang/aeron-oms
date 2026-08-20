# aeron-runtime Specification

## Purpose
TBD - created by archiving change aeron-cluster-dependency. Update Purpose after archive.
## Requirements
### Requirement: Aeron Cluster artifacts are declared in the build

The build SHALL declare the Aeron Cluster libraries as granular Maven artifacts at a single
stated version, and SHALL NOT use the `aeron-all` fat jar.

#### Scenario: Artifacts resolve

- **WHEN** `bazel build //...` runs on a clean output base
- **THEN** the Aeron and Agrona artifacts resolve and the build exits zero

#### Scenario: The version is stated once

- **WHEN** a reader opens `MODULE.bazel`
- **THEN** the Aeron version appears as a single declared value, not repeated per artifact

#### Scenario: The fat jar is not used

- **WHEN** the declared artifact list is inspected
- **THEN** it names individual Aeron artifacts and does not include `aeron-all`

### Requirement: The runtime starts on the pinned JDK

The dependency SHALL be proven to run, not merely to resolve. The test suite SHALL exercise
the Aeron runtime against the JDK the build pins.

#### Scenario: A media driver starts and a client connects

- **WHEN** `bazel test //...` runs
- **THEN** a test launches an embedded media driver, connects an Aeron client to it, and
  passes

#### Scenario: Missing JVM configuration fails the suite

- **WHEN** the JVM option that exports the internal package Agrona requires is absent
- **THEN** the test fails rather than passing, so the gap cannot reach a green build

#### Scenario: The test leaves no shared state behind

- **WHEN** the test runs twice in succession
- **THEN** both runs pass, because the driver directory is created under the test's own
  temporary directory and removed afterwards

### Requirement: Aeron dependencies are attached only where used

A package SHALL NOT declare an Aeron dependency until it contains code that uses one.

#### Scenario: Placeholder packages stay clean

- **WHEN** the BUILD files of packages whose sources reference no Aeron type are inspected
- **THEN** they declare no Aeron dependency

