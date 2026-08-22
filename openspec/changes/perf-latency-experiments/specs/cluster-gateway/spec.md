# cluster-gateway — delta for perf-latency-experiments

## ADDED Requirements

### Requirement: Optional IPC mode

With `-Doms.ipc=true` the gateway SHALL attach to the node's media driver (under the shared
data directory) instead of launching an embedded driver, and use `aeron:ipc` for ingress
and egress. The default SHALL remain the embedded driver over UDP.

#### Scenario: IPC round trips complete

- **WHEN** the gateway runs in IPC mode against an IPC-enabled node on the same host
- **THEN** the heartbeat stream completes as over UDP

### Requirement: Bench mode measures correctly

With `-Doms.gateway.warmup=N` (default 0) the gateway SHALL discard the first N round
trips and, after the run, print min, p50, p90, p99 and max of the measured remainder.

#### Scenario: Warmup is excluded

- **WHEN** the gateway runs with a warmup count
- **THEN** the printed percentiles cover only the samples after warmup

### Requirement: Pinned launch

`scripts/run.sh` SHALL honour an `OMS_TASKSET` environment variable by launching the
process under `taskset -c` with the given core list; unset, launch is unchanged.

#### Scenario: Pinning is opt-in

- **WHEN** `OMS_TASKSET=4-11 scripts/run.sh cluster-node` is invoked
- **THEN** every thread of the process starts confined to cores 4–11
