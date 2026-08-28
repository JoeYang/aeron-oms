# Tasks — zero-copy-fat-walk

- [ ] 1. Spec: proposal, design, tape-replay delta; `openspec validate zero-copy-fat-walk`
- [ ] 2. Failing tests, then `IncrementalPayloadChecksum` (cluster-service): exhaustive
      split-equivalence vs `PayloadChecksum`, pinned hand constants
- [ ] 3. Failing tests, then `TapeWalker` slice mode: slices concatenate to the copy-path
      entry, contract violations still throw, fallback for short/non-session chains
- [ ] 4. Failing tests, then `TapeReplay.replayZeroCopy` + `--zero-copy` flag: synthetic
      tape Result equality, flag exclusivity with `--latency`
- [ ] 5. Measure: count-only copy vs zero-copy on `local-fatheartbeats-1m`, same day,
      pinned; golden run with `--zero-copy` must report REPLAY OK; record in
      `measurements.md`
- [ ] 6. `bazel test //...` green; format + lint; PR
