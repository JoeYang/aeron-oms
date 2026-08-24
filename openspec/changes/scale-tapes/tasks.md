# Tasks — scale-tapes

## 1. Spec

- [x] 1.1 Artifacts written; `openspec validate scale-tapes` passes.

## 2. Scripts

- [x] 2.1 `record-tape.sh`: NODE_FLAGS/GW_FLAGS passthrough, gateway gets the data
      dir, SKIP_GOLDENS=1, longer recovery-verify wait for scale tapes; verified by
      a small tuned goldenless recording.
- [x] 2.2 `replay-cluster.sh`: NODE_FLAGS passthrough.

## 3. Warmup (TDD)

- [x] 3.1 Failing unit test: ReplayReportingService with warmup 2 and 5 applies
      reports 3; implement; `-Doms.replay.warmup` wired in ClusterNodeMain.
- [x] 3.2 `TapeReplayMain`: `--warmup <archive>` and golden `-` (count-only);
      verified live against the 1M tape.

## 4. Measure and close

- [ ] 4.1 Record `local-heartbeats-100m` (tuned, SKIP_GOLDENS); report cluster
      recovery with warmup=1,000,000 and app replay warmed by the 1M tape.
- [ ] 4.2 Suite green uncached; PR; after merge `openspec archive scale-tapes`.
