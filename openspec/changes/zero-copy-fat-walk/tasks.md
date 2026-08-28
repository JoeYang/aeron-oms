# Tasks — zero-copy-fat-walk

- [x] 1. Spec: proposal, design, tape-replay delta; `openspec validate zero-copy-fat-walk`
- [x] 2. Failing tests, then `IncrementalPayloadChecksum` (cluster-service): exhaustive
      split-equivalence vs `PayloadChecksum`, pinned hand constants
- [x] 3. Failing tests, then `TapeWalker` slice mode: slices concatenate to the copy-path
      entry, contract violations still throw, fallback for short/non-session chains
- [x] 4. Failing tests, then `TapeReplay.replayZeroCopy` + `--zero-copy` flag: synthetic
      tape Result equality, flag exclusivity with `--latency`
- [x] 5. Measure: count-only copy vs zero-copy on `local-fatheartbeats-1m`, same day,
      pinned; golden run with `--zero-copy` reports REPLAY OK; recorded in
      `measurements.md` — outcome negative: zero-copy is 5-7% slower
- [x] 6. `bazel test //...` green; format + lint; PR
