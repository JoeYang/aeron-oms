# Tasks — perf-latency-experiments

## 1. Baseline

- [x] 1.1 Measure main under the burst protocol and record the numbers in design.md.

## 2. Experiments (independent branches off main, one PR each)

- [x] 2.1 `feat/perf-busyspin` — low-latency threading profile behind `-Doms.lowlatency`;
      integration test for the profile; measure.
- [x] 2.2 `feat/perf-ipc` — IPC ingress/egress behind `-Doms.ipc`; integration test over
      IPC; measure.
- [x] 2.3 `feat/perf-taskset` — `OMS_TASKSET` in run.sh; measure with node and gateway
      pinned to disjoint core sets.
- [x] 2.4 `feat/perf-bench` — gateway warmup/percentile bench mode with a unit test for
      the percentile summary; measure.

## 3. Summary

- [x] 3.1 One table: baseline vs four experiments, min/p50/p90/p99/max, with the ranking
      and a recommendation of what merges.
