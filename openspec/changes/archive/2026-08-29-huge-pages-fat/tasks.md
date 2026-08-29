# Tasks — huge-pages-fat

- [x] 1. Spec: proposal, tape-replay delta; `openspec validate huge-pages-fat`
- [x] 2. Port from `feat/huge-pages` (closed PR #40): `MemoryAdvice`/`LinuxMemoryAdvice`
      + tests into `//core`; walker/replay advice seam; `--huge` flag with PMD read-back
      + tests; `//core` test dep
- [x] 3. Port the `ideas/tail-latency-techniques.md` TLB-ladder correction from the parked
      branch
- [x] 4. Measure fat tape, copy path, pinned: throughput + apply latency, small vs huge
      pages, PMD read-back recorded; `measurements.md` — +9.6% at 38% PMD delivery,
      fragmentation documented as the fifth gate
- [x] 5. `bazel test //...` green; format + lint; PR
