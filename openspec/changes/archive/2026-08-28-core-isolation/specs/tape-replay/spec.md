# tape-replay — delta

## MODIFIED Requirements

### Requirement: App-mode replay reports apply-latency percentiles on request

With `--latency`, count-only app replay SHALL time each state-machine apply and report
p50/p90/p99/p99.9/p99.99/max in nanoseconds over the measured tape, using a preallocated
log-linear histogram (no per-message allocation, bounded relative error), with the
sample count and a note that the timing itself adds two clock reads per apply.

#### Scenario: Latency view of a warmed replay

- **WHEN** tape-replay runs count-only with --latency and a --warmup tape
- **THEN** it prints the percentile line for the measured tape only, with sample count
  equal to the manifest count

## ADDED Requirements

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
