# time-source Specification

## Purpose
TBD - created by archiving change clock-port. Update Purpose after archive.
## Requirements
### Requirement: Time is obtained through a single port

The system SHALL expose exactly one interface for reading time, returning nanoseconds since
the Unix epoch.

#### Scenario: The port is substitutable

- **WHEN** code depends on the time port rather than a concrete implementation
- **THEN** any implementation, including a test double, can be supplied without changing that
  code

#### Scenario: The port needs no transport dependency

- **WHEN** the port and its implementations are compiled
- **THEN** they compile with no Aeron or Agrona dependency, and are constructible in a unit
  test with no media driver running

### Requirement: The deterministic clock advances only from sequenced input

A clock used by deterministic code SHALL derive its value solely from a timestamp supplied to
it, and SHALL NOT read any ambient time source.

#### Scenario: Reads reflect the last supplied timestamp

- **WHEN** a timestamp is supplied and the clock is then read
- **THEN** the value read equals the timestamp supplied

#### Scenario: Reading twice without an update returns the same value

- **WHEN** the clock is read repeatedly with no intervening update
- **THEN** every read returns an identical value

#### Scenario: Replaying the same timestamps reproduces the same readings

- **WHEN** an identical sequence of timestamps is supplied to two separate clock instances
- **THEN** the two instances yield identical readings at every step

### Requirement: Time may repeat but SHALL NOT go backwards

The deterministic clock SHALL accept a timestamp equal to its current value and SHALL reject
one that is smaller.

#### Scenario: A repeated timestamp is accepted

- **WHEN** a timestamp equal to the current value is supplied
- **THEN** the update succeeds and the value is unchanged

#### Scenario: A regressing timestamp is rejected

- **WHEN** a timestamp smaller than the current value is supplied
- **THEN** the update fails with an error naming both values, and the clock retains its
  previous value

#### Scenario: A negative timestamp is rejected

- **WHEN** a timestamp before the Unix epoch is supplied
- **THEN** the update fails and the clock retains its previous value

### Requirement: A wall-clock implementation exists for non-deterministic code

The system SHALL provide an implementation reading the host clock, for use outside the replay
boundary.

#### Scenario: It returns a plausible current time

- **WHEN** the wall-clock implementation is read
- **THEN** the value is within one minute of the host's current time expressed in epoch
  nanoseconds

#### Scenario: It does not go backwards across successive reads

- **WHEN** the wall-clock implementation is read many times in succession
- **THEN** no reading is smaller than the reading before it

### Requirement: A test double is available

The system SHALL provide a clock whose value is fixed at construction, so tests can assert on
an exact time without coordinating updates.

#### Scenario: The value never changes

- **WHEN** a fixed clock is read repeatedly over time
- **THEN** every read returns the value it was constructed with

