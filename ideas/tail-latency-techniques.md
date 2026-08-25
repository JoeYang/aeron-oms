# Tail-latency techniques — parked levers

**Context** — measured on the warmed 100M replay (2026-08-25, PR #36):
`apply-latency: p50=16 p90=17 p99=36 p99.9=62 max=428234 ns`. Each percentile names its
own noise source, and each lever below targets one. Two levers were approved as separate
initiatives and are NOT parked: **core isolation (four layers)** — see
`ideas/cpu-pinning-layers.md`, its revisit trigger has fired — and **huge pages for the
tape mapping** (TLB pressure: 12.8 GB = 3.3M 4 KB pages vs ~1.5k TLB entries; the
16→36→62 ns percentile ladder is the TLB-miss ladder). What follows is the rest.

## Frequency governor (attacks: run-to-run jitter, mid-run drift)

The box runs `powersave` on a hybrid part with 4.5–6.3 GHz cores: the same 80-cycle
apply costs 13 or 18 ns depending on the instantaneous clock, and frequency transitions
stall the core for microseconds with no OS event. `performance` on the measurement
cores is one sysfs write, no reboot. Cheapest lever here; fold it into the isolation
initiative's machine-config step rather than doing it alone.

## Prefault the mapping (attacks: fault spikes in max; the E2 stall class)

First touch of a mapped page traps to the kernel; under memory pressure the fault can
include reclaim — the mechanism behind the 100–150 ms time-to-safepoint stalls found in
experiment E2. `MappedByteBuffer.load()` takes every fault upfront, outside the
measured window. Trade-off: wiring 12.8 GB on a pressured box can evict what others
need, and it may interact badly with huge pages. Revisit trigger: fault-attributed
spikes still visible in `max`/p99.99 after isolation + huge pages land.

## Epsilon GC for count-only replay (attacks: the possibility of GC, not its presence)

Zero collections were measured, but G1's threads exist and can still request a
safepoint. Epsilon creates no GC threads: no GC safepoint is possible, and allocation
past the heap crashes — converting "allocates nothing" from an observation into an
enforced property, the same philosophy as the Bazel visibility walls. Revisit trigger:
when the replay harness becomes a latency regression gate and needs structural
guarantees; also a candidate for the node once real state exists and is proven
allocation-free.

## Report p99.99 (attacks: blindness to the deep tail)

With 100M samples, p99.99 rests on the 10,000 worst events — statistically solid, and
it is the percentile the isolation work will actually move. One line in
`TapeReplayMain`'s report. Do this at the START of the isolation initiative, before
any fix, so the improvement is visible against a pre-recorded baseline.
