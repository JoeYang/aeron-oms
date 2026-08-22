# cluster-hosting — delta for perf-latency-experiments

## ADDED Requirements

### Requirement: An opt-in low-latency threading profile

With `-Doms.lowlatency=true` the node SHALL run the media driver and archive in dedicated
threading modes with busy-spin idle strategies on the message path (driver sender and
receiver, consensus module, service container). The default SHALL remain the shared,
backoff profile.

#### Scenario: The profile is opt-in

- **WHEN** the node starts without the flag
- **THEN** threading and idle behaviour are unchanged from the MVP

#### Scenario: A low-latency node still round-trips

- **WHEN** the node starts with the flag and a client offers a Heartbeat
- **THEN** the sequenced echo arrives as before

### Requirement: Optional IPC ingress

With `-Doms.ipc=true` the node SHALL accept ingress over `aeron:ipc` for clients attached
to its media driver. The default SHALL remain UDP.

#### Scenario: An IPC client round-trips

- **WHEN** the node runs with IPC ingress and a client on the same driver offers a Heartbeat
- **THEN** the sequenced echo arrives over IPC egress
