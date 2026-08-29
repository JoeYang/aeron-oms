# Huge pages for the fat tape mapping

## Why

This revives closed PR #40 (`feat/huge-pages`, parked unmerged) with its target moved to the
fat tape, per the revisit trigger in `ideas/fat-message-levers.md`. The plain-language case:

The replayer maps tape segments into memory and reads them through the CPU's address
translation cache (TLB). With ordinary 4 KB pages, a 32 GB fat tape is ~8 million pages —
each 32 KB message touches 8 of them, and the TLB holds only ~1.5k entries. With 2 MB huge
pages the same tape is ~16k pages: one entry covers 64 messages. #40 measured this exact
streaming pattern at **+9% throughput** on the thin tape, with the latency percentiles
unchanged (the reassembly copy pre-touches each page in untimed decode, so the timed apply
never pays a miss).

#40 also paid for four hard-won findings, preserved in its design notes and re-verified
here rather than re-discovered: huge pages must be *verified*, not assumed — (1) ext4-backed
files cannot get them on this kernel, so the tape lives on tmpfs; (2) folio size is decided
at *write* time, so a tar-extracted tape has small folios and must be re-written in 2 MB
blocks; (3) a per-size shmem sysfs knob can veto the mount option; (4) the process itself
can carry an inherited `PR_SET_THP_DISABLE` that vetoes everything.

## What Changes

Ports the #40 machinery onto current main, unchanged in design:

- `MemoryAdvice` port + `LinuxMemoryAdvice` (FFM `madvise(MADV_HUGEPAGE)`, named errors,
  alignment checked; `clearProcessThpDisable()` via FFM `prctl`) in `//core`.
- `TapeWalker`/`TapeReplay` accept optional advice applied to each segment mapping at map
  time.
- `tape-replay --huge`: clears the inherited THP veto, advises the mappings, and prints the
  read-back — `huge-pages: N kB PMD-mapped of M kB requested` — because advice is a request,
  not a guarantee, and a silently small-paged run must not report as a huge-page result.
- Measure the fat tape, copy path, pinned: throughput and apply latency, small vs huge,
  same session; PMD-mapped read-back recorded with every number.
- Ports the `ideas/tail-latency-techniques.md` correction from the parked branch (the
  thin-tape "TLB-miss ladder" attribution was falsified by #40's measurements) so that
  finding is not lost with the branch.

## Capabilities

- `tape-replay` — app replay MAY advise huge pages on the tape mapping behind an explicit
  flag, with mandatory read-back verification.

## Acceptance

- `--huge` off: byte-identical behavior.
- `--huge` on a hosting that cannot deliver huge pages: replay still correct, read-back
  reports ~0 PMD-mapped — loud honesty, not failure.
- Measured fat-tape numbers recorded with their PMD-mapped evidence.
