# cluster-hosting — new capability

## ADDED Requirements

### Requirement: One process hosts a complete member

The node launcher SHALL start MediaDriver, Archive, ConsensusModule and the
ClusteredServiceContainer in a single process, forming a working single-member cluster.

#### Scenario: A round trip completes against the node

- **WHEN** the node is running and a client offers a Heartbeat
- **THEN** the client receives the sequenced echo

### Requirement: The journal location is explicit

The archive directory and the cluster directory SHALL be set explicitly under one
configurable base (`-Doms.data.dir`, default `data/`), and the container SHALL share the
module's cluster directory. Aeron's relative-path defaults SHALL never be relied on.

#### Scenario: Journal files land under the base

- **WHEN** the node runs and sequences a message
- **THEN** recording segments exist under `<base>/node-0/archive/` and the RecordingLog
  under `<base>/node-0/consensus/`

### Requirement: State survives restart by default

A restart SHALL replay the existing journal; wiping state SHALL require the explicit flag
`-Doms.cluster.clean=true`.

#### Scenario: Restart replays history

- **WHEN** the node is restarted without the clean flag
- **THEN** previously sequenced messages are re-applied before any new message

#### Scenario: The clean flag is an explicit reset

- **WHEN** the node starts with `-Doms.cluster.clean=true`
- **THEN** prior journal state is removed and the log starts empty

### Requirement: Cluster time is epoch nanoseconds

The consensus module SHALL use `NanosecondClusterClock`, so the timestamp delivered to the
service matches the `Clock.timeNanos()` contract with no conversion.

#### Scenario: Sequenced timestamps are epoch nanos

- **WHEN** a message is sequenced
- **THEN** its cluster timestamp is epoch nanoseconds, close to wall time

### Requirement: A callback failure terminates the node

An exception thrown while applying a message SHALL terminate the node rather than be
logged and skipped, because a silently skipped message is undetectable divergence.

#### Scenario: A throwing service stops the node

- **WHEN** the service throws during message application
- **THEN** the node shuts down; it does not continue to the next message
