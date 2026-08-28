# tape-replay — delta

## ADDED Requirements

### Requirement: App replay can walk fat chains zero-copy with identical outputs

With `--zero-copy`, app-mode replay SHALL deliver each fragmented session message as payload
slices in log order without a reassembly copy, checksum the slices in place with an
incremental checksum bit-identical to the service's contiguous one, and report a `Result`
identical to the copy path — same counts, same sequenced timestamps, same checksums — so the
existing golden gate applies unchanged. Unfragmented entries SHALL still be applied through
the real service. `--zero-copy` SHALL be off by default and rejected in combination with
`--latency`. Every existing chain-contract violation SHALL still throw in slice mode.

#### Scenario: Golden equivalence on a real fat tape

- **GIVEN** the `local-fatheartbeats-1m` tape and its full two-value goldens
- **WHEN** replayed with `--zero-copy`
- **THEN** the run reports `REPLAY OK` — every sequenced timestamp and every checksum equals
  the goldens the real service produced at record time

#### Scenario: Split-equivalence of the incremental checksum

- **GIVEN** every buffer length up to several words and every split of it into slices
- **WHEN** the incremental checksum consumes the slices
- **THEN** it equals the contiguous `PayloadChecksum` of the whole buffer, including the
  pinned hand constants and the zero-padded tail rule
