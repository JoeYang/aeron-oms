# cluster-gateway — delta

## ADDED Requirements

### Requirement: Gateway streams FatHeartbeats with deterministic payloads

In fat mode the gateway SHALL send FatHeartbeat messages whose 32,000-byte payloads are a
deterministic pattern derived from the message sequence — never random — so a recorded tape
means one exact byte stream and expected checksums are computable at record time.

#### Scenario: Fat send mode

- **WHEN** the gateway runs in fat mode for N messages
- **THEN** N FatHeartbeats with sequence-derived payloads are sequenced and their
  timestamp+checksum echoes are received
