# golden-tape — delta

## ADDED Requirements

### Requirement: Recording supports a FatHeartbeat scenario

`record-tape.sh` SHALL support a fat-message mode producing a tape of FatHeartbeats with
two-value goldens (`<timestamp> <checksum>`), recorded under a new tape name; existing tapes
and their formats are untouched. The recorder SHALL verify the configured Aeron limits admit
the fat message size before sending, failing fast otherwise.

#### Scenario: Record the fat tape

- **WHEN** a fat recording of 1M messages completes
- **THEN** the manifest holds the count and observed rate, and the goldens hold 1M
  timestamp+checksum lines
