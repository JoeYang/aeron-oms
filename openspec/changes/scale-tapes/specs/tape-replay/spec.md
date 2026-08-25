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

### Requirement: App-mode replay reports apply-latency percentiles on request

With `--latency`, count-only app replay SHALL time each state-machine apply and report
p50/p90/p99/p99.9/max in nanoseconds over the measured tape, using a preallocated
log-linear histogram (no per-message allocation, bounded relative error), with the
sample count and a note that the timing itself adds two clock reads per apply.

#### Scenario: Latency view of a warmed replay

- **WHEN** tape-replay runs count-only with --latency and a --warmup tape
- **THEN** it prints the percentile line for the measured tape only, with sample count
  equal to the manifest count

### Requirement: App-mode replay supports count-only verification

`tape-replay` SHALL accept `-` in place of the golden-outputs path and then verify
the applied count against the manifest only — for tapes recorded with SKIP_GOLDENS.

#### Scenario: Replay a goldenless tape

- **WHEN** tape-replay runs with golden argument `-` on a tape without an outputs file
- **THEN** it verifies the count against the manifest and exits zero on match
