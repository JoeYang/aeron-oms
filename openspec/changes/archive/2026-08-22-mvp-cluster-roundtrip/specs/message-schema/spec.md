# message-schema — delta for mvp-cluster-roundtrip

## ADDED Requirements

### Requirement: Heartbeat reserves room for growth

The Heartbeat message SHALL declare an explicit `blockLength="32"`, larger than the 8 bytes
its fields occupy, so future fields can be appended without the block length ever moving.

#### Scenario: The reserved length is on the wire

- **WHEN** a Heartbeat is encoded
- **THEN** the header reports block length 32 and the encoded length is header + 32

#### Scenario: The pinned identity records the reservation

- **WHEN** the wire-identity test runs
- **THEN** it asserts block length 32, so any later drift fails the suite

### Requirement: Timestamps exclude the null sentinel

`TimestampNanos` SHALL declare `minValue="0"`, so the SBE null value (`Long.MIN_VALUE`)
can never be a legal field value and "field absent in an older message" stays permanently
distinguishable from "field sent".

#### Scenario: The legal range starts at zero

- **WHEN** the generated codec is inspected
- **THEN** its minimum value accessor reports 0 while the null value remains `Long.MIN_VALUE`
