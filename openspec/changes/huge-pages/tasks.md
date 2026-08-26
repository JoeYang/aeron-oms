# Tasks — huge pages

## 1. Memory-advice port (interfaces before implementation)

- [x] 1.1 Failing tests: advising a valid mapped range succeeds; an unmapped address raises
      an error naming errno
- [x] 1.2 `MemoryAdvice` port in `//core` (own commit)
- [x] 1.3 Linux FFM `madvise` implementation with captured errno

## 2. Huge-page replay

- [x] 2.1 Failing tests: `--huge` parses; `TapeWalker` advises when asked (observable via a
      recording fake of the port); smaps summing parses a fixture
- [x] 2.2 Thread the advice parameter through `TapeWalker.map`/`TapeReplay`; wire `--huge`
      and the verification report line in `TapeReplayMain`

## 3. Measure

- [x] 3.1 Gate on `scripts/isolation.sh check`; identical protocol (prefault ×2, discard 1,
      3 measured); record the huge-pages verification line with every run
- [x] 3.2 In-place result was 0 kB (ext4 file THP unavailable; MADV_COLLAPSE foreclosed by
      kernel config) — decided with user: tmpfs hosting with huge=always; verification
      extended to ShmemPmdMapped; add a tmpfs-without-huge control point
- [x] 3.3 Record results vs core-isolation point (c) in measurements.md; verdict with
      machine, kernel, flags
