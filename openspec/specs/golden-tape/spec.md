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

### Requirement: Recording supports profile flags and optional goldens

The recording script SHALL pass `NODE_FLAGS` and `GW_FLAGS` environment values
through to the node and gateway, SHALL always supply the gateway with the recording
data directory so IPC ingress works, and with `SKIP_GOLDENS=1` SHALL omit the
golden-outputs file while still verifying the recorded message count and noting the
omission in the manifest.

#### Scenario: Record a tuned tape without goldens

- **WHEN** the script runs with tuned NODE_FLAGS/GW_FLAGS and SKIP_GOLDENS=1
- **THEN** it records, count-verifies, recovery-verifies, and writes a manifest that
  records the skipped goldens — with no golden-outputs file

### Requirement: Recording supports a FatHeartbeat scenario

`record-tape.sh` SHALL support a fat-message mode producing a tape of FatHeartbeats with
two-value goldens (`<timestamp> <checksum>`), recorded under a new tape name; existing tapes
and their formats are untouched. The recorder SHALL verify the configured Aeron limits admit
the fat message size before sending, failing fast otherwise.

#### Scenario: Record the fat tape

- **WHEN** a fat recording of 1M messages completes
- **THEN** the manifest holds the count and observed rate, and the goldens hold 1M
  timestamp+checksum lines

