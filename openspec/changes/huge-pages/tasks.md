# Tasks — huge pages

## 1. Memory-advice port (interfaces before implementation)

- [ ] 1.1 Failing tests: advising a valid mapped range succeeds; an unmapped address raises
      an error naming errno
- [ ] 1.2 `MemoryAdvice` port in `//core` (own commit)
- [ ] 1.3 Linux FFM `madvise` implementation with captured errno

## 2. Huge-page replay

- [ ] 2.1 Failing tests: `--huge` parses; `TapeWalker` advises when asked (observable via a
      recording fake of the port); smaps summing parses a fixture
- [ ] 2.2 Thread the advice parameter through `TapeWalker.map`/`TapeReplay`; wire `--huge`
      and the verification report line in `TapeReplayMain`

## 3. Measure

- [ ] 3.1 Gate on `scripts/isolation.sh check`; identical protocol (prefault ×2, discard 1,
      3 measured); record the huge-pages verification line with every run
- [ ] 3.2 If FilePmdMapped is 0: one in-place escalation with MADV_COLLAPSE, re-verify
- [ ] 3.3 Record results vs core-isolation point (c) in measurements.md; verdict with
      machine, kernel, flags
