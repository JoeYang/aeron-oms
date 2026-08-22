# cluster-gateway — new capability

## ADDED Requirements

### Requirement: A client completes a measured round trip

The gateway SHALL connect to the cluster, offer one Heartbeat, receive the sequenced echo,
and report both the sequenced timestamp and the measured round-trip time.

#### Scenario: One Heartbeat, one echo

- **WHEN** the gateway runs against a live node
- **THEN** it prints the echo's sequenced timestamp and a round-trip time, then exits cleanly

### Requirement: The gateway takes time from the Clock port

Outbound timestamps SHALL come from the `Clock` port (`SystemClock` here), not from
scattered ambient time reads, so the time source stays swappable in tests.

#### Scenario: The outbound stamp comes through the port

- **WHEN** the gateway encodes its Heartbeat
- **THEN** the timestamp is obtained via the `Clock` interface

### Requirement: The run scripts are real

`scripts/run.sh cluster-node` and `scripts/run.sh gateway` SHALL launch the node and the
client respectively, with the JVM flags the runtime requires.

#### Scenario: The documented commands work

- **WHEN** `scripts/run.sh cluster-node` is running and `scripts/run.sh gateway` is invoked
- **THEN** the gateway completes its round trip against the node
