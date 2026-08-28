# cluster-state-machine — delta

## ADDED Requirements

### Requirement: FatHeartbeat applies by checksumming its payload

On a FatHeartbeat the state machine SHALL read every payload byte, compute a deterministic
64-bit checksum, and echo the sequenced timestamp together with the checksum. The apply MUST
NOT allocate and MUST NOT skip payload bytes — the echoed checksum is the proof the payload
survived sequencing and replay intact.

#### Scenario: Deterministic checksum echo

- **WHEN** the same FatHeartbeat payload is applied twice (live and via replay)
- **THEN** both applies echo the identical timestamp and checksum
