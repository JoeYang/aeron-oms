# tape-replay — delta

## ADDED Requirements

### Requirement: The walker reassembles fragmented entries

Entries larger than one log frame SHALL be reassembled from their BEGIN/…/END fragment chain
into a preallocated scratch buffer before the handler is invoked; unfragmented entries keep
the zero-copy path. A chain that ends without its final fragment SHALL fail loudly, like any
torn frame.

#### Scenario: Fat entry spans frames

- **WHEN** app-mode replay walks a tape holding 32 KB entries
- **THEN** each entry is delivered to the state machine whole, and the replayed count and
  checksums match the manifest and goldens

#### Scenario: Truncated fragment chain

- **WHEN** a tape ends mid-chain
- **THEN** replay fails with an error rather than applying a partial payload

### Requirement: Fat-tape goldens verify checksums

For tapes whose goldens carry `<timestamp> <checksum>` lines, app-mode replay SHALL compare
both values per message and fail on any mismatch.

#### Scenario: Payload corruption is caught

- **WHEN** a replayed payload produces a checksum differing from the golden line
- **THEN** replay exits nonzero naming the position
