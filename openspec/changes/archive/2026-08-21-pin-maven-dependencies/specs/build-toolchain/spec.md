## ADDED Requirements

### Requirement: Dependency resolution is reproducible

The build SHALL resolve every external Java dependency to a version and integrity hash recorded
in the repository, so that two machines building the same commit obtain identical artifacts.

#### Scenario: Resolved versions are recorded

- **WHEN** the repository is inspected
- **THEN** a lock file records each resolved artifact with its version and integrity hash

#### Scenario: Drift between declaration and lock fails the build

- **WHEN** a declared artifact does not match the lock file
- **THEN** the build fails and names the mismatch, rather than resolving silently

#### Scenario: The pinned build still passes

- **WHEN** the full suite runs against the pinned resolution
- **THEN** it passes, confirming the pin captured a working set rather than changing it
