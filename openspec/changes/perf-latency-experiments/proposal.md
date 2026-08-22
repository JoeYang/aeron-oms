# Latency experiments — four independent optimizations, measured

## Why

The MVP baseline round trip measures p50 241.8 µs / p99 4,055 µs under a closed-loop burst
(and ~4.3 ms when poked once per second — the parked-pipeline artifact). The latency rules
demand measurement before belief: each candidate optimization lands on its own branch, is
measured under one identical protocol, and the numbers decide what merges.

## What Changes

Four independent branches, four PRs, none stacked; each measured against the same baseline:

1. `feat/perf-busyspin` — `-Doms.lowlatency=true`: DEDICATED threading for driver and
   archive, busy-spin idle strategies for driver sender/receiver, consensus module and
   service container, both node and gateway sides.
2. `feat/perf-ipc` — `-Doms.ipc=true`: the co-located gateway attaches to the node's
   MediaDriver and uses `aeron:ipc` ingress and egress; no UDP loopback hops.
3. `feat/perf-taskset` — `OMS_TASKSET` env in `scripts/run.sh`: launch-layer CPU pinning
   via `taskset` (the only pinning layer available before the FFM runtime layer exists).
4. `feat/perf-bench` — `-Doms.gateway.warmup=N`: gateway bench mode that discards warmup
   samples and prints min/p50/p90/p99/max — the correct measurement, in-repo.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `cluster-hosting`: optional low-latency threading profile; optional IPC ingress.
- `cluster-gateway`: optional IPC mode; bench mode with warmup and percentiles; pinned
  launch support in the run script.

## Impact

- `cluster-node` and `gateway` configuration surfaces; `scripts/run.sh`; no schema change,
  no new dependencies. Defaults everywhere are today's behaviour — every experiment is
  opt-in by flag, so an unmerged experiment leaves `main` untouched.
- Measurement protocol (out of repo, session scratchpad): fresh journal, 3,000 closed-loop
  round trips on port 22102, first 2,000 discarded, percentiles over the last 1,000.
  Machine: ThinkPad P16 Gen 3, 20 hardware threads, default governor, no core isolation.
