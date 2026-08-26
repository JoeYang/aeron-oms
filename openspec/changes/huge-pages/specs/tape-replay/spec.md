# tape-replay — delta

## ADDED Requirements

### Requirement: Count-only replay maps the tape with huge pages on request

With `--huge`, count-only replay SHALL advise every segment mapping `MADV_HUGEPAGE` at map
time via the FFM `madvise` binding, and after the measured walk SHALL report the verified
huge-page extent (`FilePmdMapped` plus `ShmemPmdMapped` from `/proc/self/smaps`, covering
disk-backed and tmpfs-hosted tapes) against the requested extent.
A zero extent MUST be reported explicitly — an unverifiable optimisation is never reported
as present.

#### Scenario: Huge-page replay on a THP-madvise kernel

- **WHEN** tape-replay runs count-only with --huge
- **THEN** the report contains a huge-pages line stating verified PMD-mapped kB and
  requested kB

#### Scenario: Kernel declines huge pages

- **WHEN** the kernel backs no advised range with huge pages
- **THEN** the replay still completes and the huge-pages line states 0 kB verified
