# Tasks — huge-pages-fat

- [ ] 1. Spec: proposal, tape-replay delta; `openspec validate huge-pages-fat`
- [ ] 2. Port from `feat/huge-pages` (closed PR #40): `MemoryAdvice`/`LinuxMemoryAdvice`
      + tests into `//core`; walker/replay advice seam; `--huge` flag with PMD read-back
      + tests; `//core` test dep
- [ ] 3. Port the `ideas/tail-latency-techniques.md` TLB-ladder correction from the parked
      branch
- [ ] 4. Measure fat tape, copy path, pinned: throughput + apply latency, small vs huge
      pages, PMD read-back recorded; `measurements.md`
- [ ] 5. `bazel test //...` green; format + lint; PR
