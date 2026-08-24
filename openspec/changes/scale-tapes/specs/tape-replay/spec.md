# tape-replay (delta)

## ADDED Requirements

### Requirement: Replay reports exclude a warmup window

Cluster-mode replay SHALL, when `-Doms.replay.warmup=N` is set, start its timing
window after the first N applies and report the remaining count and rate, stating
the warmup in the report line; N=0 (default) SHALL leave the report unchanged.
App-mode replay SHALL, with `--warmup <archive-dir>`, replay that tape in the same
JVM unreported before replaying and reporting the measured tape.

#### Scenario: Cluster recovery with a warmup window

- **WHEN** a node recovers a tape of M messages with warmup N set
- **THEN** the report counts M minus N messages and its window starts at apply N+1

#### Scenario: App replay warmed by a second tape

- **WHEN** tape-replay runs with --warmup pointing at the 1M tape and measures the
  100M tape
- **THEN** the reported count and rate cover only the measured tape

### Requirement: App-mode replay supports count-only verification

`tape-replay` SHALL accept `-` in place of the golden-outputs path and then verify
the applied count against the manifest only — for tapes recorded with SKIP_GOLDENS.

#### Scenario: Replay a goldenless tape

- **WHEN** tape-replay runs with golden argument `-` on a tape without an outputs file
- **THEN** it verifies the count against the manifest and exits zero on match
