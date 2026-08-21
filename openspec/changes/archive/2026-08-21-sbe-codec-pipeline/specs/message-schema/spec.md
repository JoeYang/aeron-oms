## ADDED Requirements

### Requirement: Codecs are generated, never committed

Message codecs SHALL be produced from the schema by the build. No generated source SHALL be
stored in the repository.

#### Scenario: The schema is the only committed definition

- **WHEN** the repository is inspected
- **THEN** the schema is present and no generated codec source is committed

#### Scenario: Editing the schema changes the codecs

- **WHEN** a field is added to a message and the build runs
- **THEN** the regenerated codec reflects the change without any other file being edited

### Requirement: An invalid schema fails the build

Schema errors SHALL stop the build rather than produce partial or silently wrong codecs.

#### Scenario: A malformed type is rejected

- **WHEN** a field declares a type the encoder does not recognise
- **THEN** the build fails and names the offending type

#### Scenario: Warnings are treated as errors

- **WHEN** the generator emits a validation warning
- **THEN** the build fails, rather than generating code and reporting success

### Requirement: A message round-trips through its codec

An encoded message SHALL decode to the values that were encoded.

#### Scenario: Values survive the round trip

- **WHEN** a message is encoded and then decoded
- **THEN** every field equals the value encoded

#### Scenario: Boundary values survive

- **WHEN** the minimum and maximum values of a field's type are encoded
- **THEN** each decodes to the value encoded, so width and sign errors are caught

#### Scenario: Position within the buffer does not matter

- **WHEN** a message is encoded at a non-zero offset
- **THEN** it decodes correctly from that offset

### Requirement: Wire identity cannot change silently

Schema id, message id, schema version, and block length SHALL be asserted by a test, because
they are permanent once a message has been written to a cluster log.

#### Scenario: Renumbering fails the suite

- **WHEN** a schema id, message id, or version is changed
- **THEN** the test suite fails and names the field that changed

#### Scenario: A layout change fails the suite

- **WHEN** a field is added or removed such that the block length changes
- **THEN** the test suite fails, so a wire-format change cannot pass unnoticed
