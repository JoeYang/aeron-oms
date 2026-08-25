# tape-replay Specification

## Purpose
TBD - created by archiving change golden-tape-replay. Update Purpose after archive.
## Requirements
### Requirement: App-mode replay applies the tape to the bare state machine

A replay tool SHALL read a tape's recording directly — no cluster, no media driver —
unwrap the cluster session-message framing, apply each heartbeat to a bare
`OmsClusteredService`, and report the applied count, the echoed outputs, wall time,
and msgs/sec.

#### Scenario: Replay the golden tape against the app

- **WHEN** the replay tool runs against the committed golden tape
- **THEN** the applied count equals the manifest count and the echoed timestamps equal
  the golden-outputs file, in order

#### Scenario: Replay is deterministic

- **WHEN** the same tape is replayed twice
- **THEN** both runs produce byte-identical outputs

#### Scenario: A truncated tape fails loudly

- **WHEN** the recording ends mid-frame or holds fewer messages than the manifest
- **THEN** the tool reports the shortfall and exits non-zero rather than passing on a
  partial replay

### Requirement: Cluster-mode replay reports recovery throughput

The cluster node SHALL, when started with `-Doms.replay.report=true` over an unpacked
tape (no clean flag), count service applies during recovery and print one summary line
— applied count, first-to-last apply duration, msgs/sec — once the node reaches leader
role. The property off SHALL leave behaviour unchanged.

#### Scenario: Recover the node from the golden tape

- **WHEN** the node starts over the unpacked golden tape with the report enabled
- **THEN** it reaches leader and the reported applied count equals the manifest count

### Requirement: The benchmark runs both modes with metadata

A benchmark script SHALL replay the same tape in app mode and cluster mode, and report
per mode the messages replayed, wall time, and msgs/sec, alongside machine metadata
(commit, CPU, governor), labelling the two numbers as answering different questions.

#### Scenario: Benchmark the golden tape

- **WHEN** the benchmark script runs against the committed golden tape
- **THEN** it prints one result line per mode and both modes' counts match the manifest

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

