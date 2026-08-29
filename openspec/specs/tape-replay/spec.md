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
p50/p90/p99/p99.9/p99.99/max in nanoseconds over the measured tape, using a preallocated
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

### Requirement: Count-only replay pins its hot thread on request

With `--pin <cpu>`, count-only replay SHALL pin the applying thread onto the given CPU
before the warmup tape runs, verify the pin by reading the affinity back, and exit nonzero
with a clear message if the pin cannot be verified. The report SHALL state the pinned CPU.

#### Scenario: Pinned replay on an isolated core

- **WHEN** tape-replay runs with --pin onto an online CPU
- **THEN** the replay proceeds and the report includes the pinned CPU id

#### Scenario: Pin to a CPU that does not exist

- **WHEN** tape-replay runs with --pin onto a CPU id absent from the machine
- **THEN** it exits nonzero naming the CPU, before replaying anything

### Requirement: The walker reassembles fragmented entries

Entries larger than one log frame SHALL be reassembled from their BEGIN/…/END fragment chain
into a preallocated scratch buffer before the handler is invoked; unfragmented entries keep
the zero-copy path. A chain that ends without its final fragment SHALL fail loudly, like any
torn frame.

#### Scenario: Fat entry spans frames

- **WHEN** app-mode replay walks a tape holding 32 KB entries
- **THEN** each entry is delivered to the state machine whole, and the replayed count and
  checksums match the manifest and goldens

#### Scenario: Truncated fragment chain

- **WHEN** a tape ends mid-chain
- **THEN** replay fails with an error rather than applying a partial payload

### Requirement: Fat-tape goldens verify checksums

For tapes whose goldens carry `<timestamp> <checksum>` lines, app-mode replay SHALL compare
both values per message and fail on any mismatch.

#### Scenario: Payload corruption is caught

- **WHEN** a replayed payload produces a checksum differing from the golden line
- **THEN** replay exits nonzero naming the position

### Requirement: App replay can advise huge pages on the tape mapping, verified by read-back

With `--huge`, app-mode replay SHALL clear any inherited process-level THP disable, advise
`MADV_HUGEPAGE` on each segment mapping at map time, and report the kernel's read-back —
PMD-mapped kilobytes against requested kilobytes from `/proc/self/smaps`, summed only over
the measured archive's mappings — so a run whose hosting could not deliver huge pages is
loudly visible rather than silently small-paged. The flag SHALL default off with
byte-identical behavior, and advice failure or zero delivery SHALL never affect replay
correctness.

#### Scenario: Huge-page replay on a hugepage-capable tmpfs hosting

- **GIVEN** the fat tape re-written in 2 MB blocks onto a `huge=always` tmpfs
- **WHEN** replayed with `--huge`
- **THEN** the read-back reports a dominant fraction PMD-mapped and the replay outcome is
  identical to a small-page run

#### Scenario: Hosting that cannot deliver huge pages

- **GIVEN** a tar-extracted tape (small folios) or an ext4-backed extraction
- **WHEN** replayed with `--huge`
- **THEN** the replay is correct and the read-back reports approximately zero PMD-mapped

