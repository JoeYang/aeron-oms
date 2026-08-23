# golden-tape Specification

## Purpose
TBD - created by archiving change golden-tape-replay. Update Purpose after archive.
## Requirements
### Requirement: Recording produces a self-describing tape fixture

The recording script SHALL run a cluster node and gateway on an isolated port, drive a
configured number of heartbeats, stop the node cleanly, and freeze the journal as
`journal/<name>.tar.gz` (sparse tar of the `archive/` and `consensus/` directories,
excluding `*-mark.dat`), beside `journal/<name>.manifest.txt` and
`journal/<name>.golden-outputs.txt`.

#### Scenario: Record a heartbeat tape

- **WHEN** the recording script runs with a name and a message count
- **THEN** it produces the tarball, a manifest holding message count, SBE schema
  version, git commit, date, and machine identity, and a golden-outputs file holding
  the ordered sequenced timestamps — and the tarball unpacks to a journal a cluster
  node can recover from

### Requirement: A tape is immutable

A committed tape SHALL never be regenerated, edited, or overwritten. A new scenario is
a new tape under a new name.

#### Scenario: Re-recording an existing name

- **WHEN** the recording script is invoked with the name of an existing tape
- **THEN** it refuses and exits non-zero without touching the existing files

