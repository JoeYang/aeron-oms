# tape-view Specification

## Purpose
TBD - created by archiving change tape-cat-viewer. Update Purpose after archive.
## Requirements
### Requirement: tape-cat decodes every entry of a tape

The viewer SHALL walk a tape's recording and emit one line per log entry — position,
entry kind, sequenced timestamp, and decoded Heartbeat fields for session messages —
without writing to the tape.

#### Scenario: View the golden tape

- **WHEN** tape-cat runs over the committed golden tape
- **THEN** it emits one session-message line per manifest message, in log order, with
  timestamps equal to the golden outputs, plus lines for each non-session entry

#### Scenario: Unknown entry kinds do not fail the view

- **WHEN** the log contains an entry whose template the viewer does not know by name
- **THEN** the entry prints with its numeric template id and the walk continues

### Requirement: JSONL mode streams to standard tooling

With `--json`, the viewer SHALL emit one flat JSON object per line, parseable
independently of every other line.

#### Scenario: Pipe the golden tape through jq

- **WHEN** tape-cat runs with --json over the golden tape
- **THEN** every emitted line parses as a standalone JSON object carrying position,
  kind, and timestamp fields

