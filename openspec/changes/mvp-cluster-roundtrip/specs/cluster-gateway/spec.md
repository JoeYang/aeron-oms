# cluster-gateway — new capability

## ADDED Requirements

### Requirement: A client completes measured round trips

The gateway SHALL connect to the cluster and send a configurable number of Heartbeats at a
configurable interval (default: 10, one per second). For each it SHALL print the sequenced
timestamp and the measured round-trip time, then exit cleanly — a visible stream of messages
through the log, one line per message.

#### Scenario: One line per Heartbeat

- **WHEN** the gateway runs against a live node
- **THEN** it prints one line per Heartbeat carrying the sequenced timestamp and round-trip
  time, and exits cleanly after the configured count

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
