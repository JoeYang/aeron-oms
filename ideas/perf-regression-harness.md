# Performance benchmark and regression harness

**What** — a repeatable way to measure the hot path and to detect when a change makes it
slower, rather than finding out in production.

**Why** — `.claude/rules/trading-latency.md` sets budgets (order entry under 50 µs,
sequencer round trip under 20 µs, tick-to-trade under 500 µs) and says never to assume a
change improves performance. Neither is actionable without a harness that produces numbers
the same way twice.

## Blocked by

There is no hot path yet. A benchmark today measures `Greeting.greet()`.

Partially blocked, not fully: the *format* of a stored result is worth deciding early,
because history is only comparable if it was recorded the same way from the first run.

## Split the two things being conflated

This is the decision that shapes everything else. "Benchmark" means two different jobs with
different requirements, and merging them produces either false alarms or false confidence.

| | Regression check | Budget verification |
|---|---|---|
| Question | Did this change make it slower? | Do we meet the 50 µs budget? |
| Comparison | Relative, A vs B | Absolute, against a number |
| Machine | Any, if A and B run on it together | Only the isolated, tuned machine |
| Frequency | Every change | Rarely, deliberately |

An absolute latency number from an untuned dev box cannot be compared to the budgets at
all. A relative comparison on the same box in the same session is still useful.

## Prefer counting gates over timing gates

The strongest recommendation here. Timing is noisy; counting is not.

- **Allocations in steady state** — the rules already require zero. An allocation counter
  is near-deterministic, so it makes a gate that fails only when something really broke.
- **GC events during a run** — should be zero once steady state is reached.
- **Syscalls on the hot path** — should be zero.

These catch most latency regressions at their cause, and they do not flake. Build these
first. Add timing assertions later, and only where a counting gate cannot express the
requirement.

## Traps

### This machine cannot produce trustworthy absolute numbers

Checked, not assumed:

```
20 cores, Intel Core Ultra 7 255HX
distinct max frequencies: 4500000 5100000 5300000 6300000 Hz
governor: powersave      turbo: on
/proc/cmdline: no isolcpus, no nohz_full      nohz_full: (null)
```

Four frequency tiers means this is a hybrid part — performance cores, efficiency cores, and
favoured turbo bins. An unpinned thread can land on a 4.5 GHz core or a 6.3 GHz one. That
is a **1.4x spread from placement alone**, with no code change involved, run to run.

Add `powersave` scaling, turbo, and an OEM kernel on mobile silicon that thermally
throttles, and none of the four isolation layers the rules require are present here.

Consequence: pin the benchmark thread even for relative runs, or core placement noise will
swamp the effect being measured.

### Bazel caches test results, so a "passing" benchmark may not have run

`bazel test` returns a cached result when inputs are unchanged. A benchmark reported as
passing may not have executed at all. Benchmarks also do not belong in `bazel test //...`,
which is the PR gate and must stay fast.

Use `tags = ["manual"]`, or a `java_binary` invoked explicitly, and
`--nocache_test_results` if it ever does become a test target.

### Coordinated omission

If the load generator waits for a response before sending the next request, then a stall
causes it to send *fewer* requests — so the stall is under-counted and the tail looks far
better than it is. This is the classic way an OMS latency chart lies.

Either drive from a fixed schedule independent of responses, or use HdrHistogram's
expected-interval recording.

### Sequential A-then-B confounds drift with the change

Thermal state and background load drift over minutes. Measure the baseline, then the
candidate, and the drift is inside the difference.

Interleave instead — A, B, A, B — so drift hits both arms equally. Run enough repetitions
to compare distributions rather than two single numbers; one p99 from one run is noise.

### A micro-benchmark improvement need not be an end-to-end improvement

JMH numbers do not compose. A 20% faster codec is invisible if the codec is not the
bottleneck. Confirm at the end-to-end level before believing a win.

## Open question — where results live

A regression monitor needs history, and history has to live somewhere.

Committing result files to the repo creates merge conflicts and grows the tree. A separate
branch or repo avoids that but adds machinery. For solo work, on-demand A/B with no stored
history may be enough, and is far simpler.

Worth deciding before the first result is written, not after.
