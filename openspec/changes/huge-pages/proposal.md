# Huge pages for the tape mapping

## Why

With isolation landed (#39), the remaining tail mass sits in p99.9≈56 ns and p99.99≈155 ns —
the TLB/fault class: 12.8 GB mapped as 3.3M 4 KB pages against ~1.5k TLB entries. 2 MB pages
cut the page count 512×. This is approved initiative B, stacked on `feat/core-isolation` and
measured with the identical protocol so the result is directly comparable to point (c).

## What Changes

- `tape-replay` gains `--huge`: each segment mapping is advised `MADV_HUGEPAGE` at map time
  through a new FFM `madvise` binding (no JNI, mirrors the `ThreadAffinity` pattern).
- **Verified, not assumed**: after the walk, the report states how many kB the kernel
  actually PMD-mapped (`FilePmdMapped` from `/proc/self/smaps`). Whether ext4 page cache
  produces PMD-mapped large folios on kernel 6.17 is an empirical question — the
  verification line is the experiment's readout, and the measurement is only meaningful
  alongside it.
- Measure on the isolated layout (gated on `isolation.sh check`), same protocol as
  core-isolation point (c); compare against the point-(c) baseline.

## Capabilities

### Modified Capabilities
- `tape-replay`: count-only replay can request huge-page backing for the tape mapping and
  reports the verified PMD-mapped extent.

## Impact

- `//core`: narrow `MemoryAdvice` port + Linux FFM `madvise` implementation.
- `//cluster-node`: `TapeWalker.map` advises when asked; `TapeReplayMain` `--huge` flag and
  verification report; smaps parser.
- No scripts, schema, journal-format, or machine-config changes. Tapes untouched.
