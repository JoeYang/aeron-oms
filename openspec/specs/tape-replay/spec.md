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

