# tape-replay — delta

## ADDED Requirements

### Requirement: App replay can advise huge pages on the tape mapping, verified by read-back

With `--huge`, app-mode replay SHALL clear any inherited process-level THP disable, advise
`MADV_HUGEPAGE` on each segment mapping at map time, and report the kernel's read-back —
PMD-mapped kilobytes against requested kilobytes from `/proc/self/smaps`, summed only over
the measured archive's mappings — so a run whose hosting could not deliver huge pages is
loudly visible rather than silently small-paged. The flag SHALL default off with
byte-identical behavior, and advice failure or zero delivery SHALL never affect replay
correctness.

#### Scenario: Huge-page replay on a hugepage-capable tmpfs hosting

- **GIVEN** the fat tape re-written in 2 MB blocks onto a `huge=always` tmpfs
- **WHEN** replayed with `--huge`
- **THEN** the read-back reports a dominant fraction PMD-mapped and the replay outcome is
  identical to a small-page run

#### Scenario: Hosting that cannot deliver huge pages

- **GIVEN** a tar-extracted tape (small folios) or an ext4-backed extraction
- **WHEN** replayed with `--huge`
- **THEN** the replay is correct and the read-back reports approximately zero PMD-mapped
