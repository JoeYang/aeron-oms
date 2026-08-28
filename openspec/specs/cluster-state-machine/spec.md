# cluster-state-machine Specification

## Purpose
TBD - created by archiving change mvp-cluster-roundtrip. Update Purpose after archive.
## Requirements
### Requirement: A committed Heartbeat is echoed with sequenced time

For each committed Heartbeat the service SHALL emit exactly one egress Heartbeat whose
timestamp is the sequenced clock's current time — the cluster timestamp from the log, never
a wall-clock read.

#### Scenario: The echo carries sequenced time

- **WHEN** a Heartbeat arrives with cluster timestamp T
- **THEN** exactly one egress Heartbeat is offered, stamped T

#### Scenario: Unknown templates are ignored without effect

- **WHEN** a message with an unrecognised template id arrives
- **THEN** the service emits nothing and its state is unchanged

### Requirement: The service is constructible without Aeron

The service SHALL be constructible and unit-testable with no media driver running and no
ambient time source — its only inputs are the callback parameters.

#### Scenario: Unit tests run without infrastructure

- **WHEN** the service is constructed in a unit test with a fake client session
- **THEN** message application and egress behaviour are fully testable with no Aeron process

#### Scenario: The fake session honours real failure semantics

- **WHEN** the fake session returns back-pressure, not-connected, or closed
- **THEN** the service's behaviour under each is asserted, not just the success path

### Requirement: Egress back-pressure is retried

When an egress offer is back-pressured the service SHALL retry until the offer is accepted.
(Bounded policies are a later, deliberate decision; the MVP policy is retry-forever and it
is stated, not hidden.)

#### Scenario: A back-pressured echo is eventually delivered

- **WHEN** the session rejects an offer with back-pressure and later accepts
- **THEN** the echo is offered again until accepted, exactly once in total

### Requirement: FatHeartbeat applies by checksumming its payload

On a FatHeartbeat the state machine SHALL read every payload byte, compute a deterministic
64-bit checksum, and echo the sequenced timestamp together with the checksum. The apply MUST
NOT allocate and MUST NOT skip payload bytes — the echoed checksum is the proof the payload
survived sequencing and replay intact.

#### Scenario: Deterministic checksum echo

- **WHEN** the same FatHeartbeat payload is applied twice (live and via replay)
- **THEN** both applies echo the identical timestamp and checksum

