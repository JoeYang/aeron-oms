# message-schema — delta

## ADDED Requirements

### Requirement: FatHeartbeat carries a variable-length payload

The schema SHALL define a `FatHeartbeat` message — `timestampNanos` plus a variable-length
payload with a uint16 length — appended after all existing messages with a new template id
and the schema version incremented. The wire identity (schema id, template ids, block
lengths) of every existing message MUST NOT change.

#### Scenario: Append-only addition

- **WHEN** the schema gains FatHeartbeat and codecs are regenerated
- **THEN** the wire-identity diff shows only the new message and the version bump
