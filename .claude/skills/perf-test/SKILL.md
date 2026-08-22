---
name: perf-test
description: Use when measuring aeron-oms performance — rerunning the benchmark, comparing before/after a hot-path change, chasing a latency regression, or checking throughput, GC pauses, or JIT warmup.
---

# Performance test

One fixed protocol, four lenses. Every number this skill produces is comparable to every
other number it has produced, because the procedure never varies within a comparison.

## Step 1 — ask the focus

Ask with AskUserQuestion, one question, exactly these options:

> Which performance focus should this run measure?
> - **Latency** — round-trip percentiles; the default regression check
> - **Throughput** — sustained message rate through the sequencer
> - **GC** — collection pauses; verify the allocation-free steady state
> - **JIT** — compilation activity; verify warmup completes before measurement

Then run only that focus's section. Do not measure all four "while we're here".

## Step 2 — decide the arms

- **Testing a change**: arm A = `main`, arm B = the change branch, both in their own
  worktrees. Vary exactly one thing between arms.
- **No specific change**: arm A = default profile, arm B = tuned
  (`--jvm_flag=-Doms.lowlatency=true --jvm_flag=-Doms.ipc=true` on the node,
  `--jvm_flag=-Doms.ipc=true` on the gateway).

## Step 3 — run the protocol

`measure.sh` (in this skill's directory) runs one arm: builds, starts the node on an
isolated port with a fresh journal, drives closed-loop round trips, stops the node, and
prints one `RESULT` line plus focus-specific lines. Two processes total — the node JVM
(driver + archive + consensus + service container) and the gateway JVM.

```bash
SKILL_DIR=.claude/skills/perf-test
bash $SKILL_DIR/measure.sh <workdir> <label> <focus> [node-flags] [gw-flags]

# example: latency, tuned arm
bash $SKILL_DIR/measure.sh ~/clawd/aeron-oms tuned latency \
  "--jvm_flag=-Doms.lowlatency=true --jvm_flag=-Doms.ipc=true" \
  "--jvm_flag=-Doms.ipc=true"
```

Protocol invariants (the script enforces them — do not work around it):

| Invariant | Value | Why |
|---|---|---|
| Port | 22102 | never collides with a developer cluster on 9002 |
| Journal | fresh workdir + `-Doms.cluster.clean=true` | never `rm -rf` a data dir |
| Samples | 3000 round trips, first 2000 discarded | JIT/page-cache warmup out of the window |
| Statistics | min/p50/p90/p99/max, never averages | tail latency is the product |
| Repetitions | 3 per arm, interleaved A B A B A B | thermal drift lands between arms, not inside one |
| Metadata | `meta.txt` per run: commit, CPU, governor, cmdline, flags | a number without its machine means nothing |

Across repetitions report the median p50 and the **worst** p99.

## Focus: latency

Run both arms × 3, interleaved. Compare against the recorded table in
`ideas/perf-regression-harness.md` (2026-08-22, this machine: default p50 253.4 µs,
tuned p50 2.5 µs / p99 17.3 µs). A tuned p50 above ~5 µs or p99 above ~50 µs on this
machine is a regression signal — bisect it, single variable at a time.

## Focus: throughput

Same script, `focus=throughput` (50,000 round trips, 10,000 warmup). It prints a `RATE`
line computed from the sequenced timestamps of the measured window. **Read it honestly:
the gateway is closed-loop — one message in flight — so rate ≡ 1/RTT.** This measures
sequencer service rate under no queuing, not pipelined capacity. Pipelined load
generation is a code change; propose it before claiming a capacity number.

## Focus: GC

Same script, `focus=gc` — it adds `-Xlog:gc*` to both JVMs and prints pause counts with
the last pause's uptime. The rule (trading-latency.md): **zero collections in steady
state**. Pauses during warmup are acceptable; a pause after the warmup window is a
finding — grab allocation evidence next (JFR: `-XX:StartFlightRecording=...`) rather
than tuning collector flags blind. The RTT numbers from a GC-instrumented run carry
logging overhead — never mix them into a latency comparison.

## Focus: JIT

Same script, `focus=jit` — it adds `-Xlog:jit+compilation=debug` writing to a per-process
file (never `-XX:+PrintCompilation`: compiler threads on stdout corrupt the measurement
lines) and prints the compile-event count and the last compilation's VM uptime. Compare
the last-compile time with when the measured window began (the gateway log timestamps
each heartbeat): if compilation is still running inside the window, raise
`WARMUP=` for every arm equally and rerun. RTT numbers from an instrumented run are not
comparable to clean runs.

## Red flags — stop if you are about to

- Check out `feat/perf-bench` or any parked branch "because it has the bench tool" —
  the parser here works on `main`'s per-line output; measure the tree under test.
- `rm -rf` a data directory — the journal is append-only audit state; fresh workdir
  plus the `clean` flag is the reset.
- Design steps that need a human at a second terminal or a Ctrl+C — every step must be
  scriptable or the test is not rerunnable.
- Compare numbers across machines, days, or instrumented/clean runs.
- Report an average, or claim a budget (50 µs / 20 µs) is met from this untuned hybrid
  laptop — relative comparison only; see the traps in `ideas/perf-regression-harness.md`.

Results are reported in conversation and PR comments; do not commit result files
(storage is an open question recorded in `ideas/perf-regression-harness.md`).
