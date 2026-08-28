# Fat-message levers — parked

**Context** — measured on the fat-heartbeat initiative (2026-08-28, PR #42, tape
`local-fatheartbeats-1m`: 1M × 32 KB): app-mode replay 7.9 GB/s, apply p50 1.6 µs,
cluster recovery 5.8 GB/s warm / 1.0 GB/s cold-disk. The attribution run (see
`openspec/changes/fat-heartbeat/measurements.md`) showed the feared "11× cluster
penalty" was ~85% disk; the machinery itself is only 1.4× behind app-mode. One lever
(attribution) was executed as part of the initiative; what follows is the rest, parked.

## Cold-read delivery (attacks: cold recovery wall time — the dominant real cost)

Buffered streaming reads deliver ~1.2 GB/s under the default `read_ahead_kb=128`, while
`O_DIRECT` 64 MB reads pull 3.1 GB/s from the same file on the same drive. The archive
replayer eats whatever the kernel hands it, so raising delivery is a ~3× lever on cold
recovery without touching cluster code. Options, cheapest first: raise `read_ahead_kb`
(machine config, like the isolation fixtures); a prefetcher thread that streams the next
archive segments through the page cache ahead of the replayer; `O_DIRECT`/io_uring reads
in a custom archive read path (largest gain, largest surface). Revisit trigger: cold
recovery time ever matters operationally.

## Recovery-path machinery tuning (attacks: the remaining 1.4× vs app-mode)

De-prioritized by the attribution: warm machinery is 5.8 vs 7.9 GB/s, so the whole lever
is worth at most ~27%. If ever pursued: `archive-conductor` runs at 93% — the archive
replay feed, not the service, is the top of the funnel. Fragment-limit per poll, idle
strategies, and pinning the archive/service agents onto isolated cores are the knobs.
Caveat from the thin-tape work: DEDICATED threading made recovery *slower* there —
measure, don't assume.

## Multi-lane checksum (attacks: apply p50, 1.6 µs)

The rotate-xor checksum is a serial dependency chain: 2 cycles per 8 bytes ≈ 20 GB/s at
5.1 GHz — exactly the measured apply rate, so arithmetic, not cache bandwidth, is the
floor. Eight independent accumulators folded at the end break the chain (~8× headroom;
apply p50 toward ~0.4 µs). One-way door: the checksum definition is baked into recorded
goldens — a change means a new tape name or a versioned algorithm field. Revisit trigger:
a real fat-message apply that must do more than checksum inside its budget.

## Zero-copy fat walk (attacks: app-mode replay throughput)

The walker copies every fragmented entry into scratch before apply; checksumming across
fragments in place would drop one full 32 GB pass (+20–30% app-mode). Cost: breaks the
"payload is one contiguous DirectBuffer" service contract — the apply would need a
gather-style view. Real design cost for a replay-tool-only win; the cluster path has its
own reassembly and would not benefit. Revisit trigger: app-mode replay wall time blocks
iteration on big tapes.

## Huge pages for the fat tape (attacks: replay throughput and the fault band)

The parked huge-pages machinery (PR #40, closed unmerged; branch `feat/huge-pages`
preserved) applies to this tape as-is: +9% measured on exactly this streaming pattern,
and 2 MB mappings cut page faults from 8 per 32 KB message to 1 per 64 messages, which
is aimed at the measured p99.99 (6–8 µs) / max fault band. Revisit trigger: the user
wants to understand and un-park #40.

## Gateway pipelining (attacks: recording wall time — tooling only)

Recording is closed-loop, one message in flight: ~3.7k msg/s is pure RTT, not a system
limit. A window of N outstanding sends multiplies recording speed by ~N until another
limit binds. No product value — the gateway's product path is not a bulk loader — but
recording 10M+ fat tapes becomes practical. Revisit trigger: tape recording time blocks
an initiative.

## Snapshotting (attacks: recovery cost scaling itself)

The structural answer: with snapshots, recovery replays only the tail since the last
snapshot, and the recovery bill stops scaling with journal bytes entirely. Everything
above optimizes the constant; this removes the linearity. Largest initiative on the
list, needs its own spec (snapshot content, cadence, determinism proof via
golden-tape-through-snapshot), and is eventually inevitable for any real OMS state.
Revisit trigger: the state machine holds real order state worth snapshotting.

## Residual apply-tail: attributed to thermal interrupts (2026-08-29)

The p99.99 ≈ 6 µs band is **thermal event interrupts (TRM) landing on the isolated core**.
Evidence, from instrumented fat latency replays (pinned, RAM-resident tape, `isolation.sh
check` passed):

- **The JVM is exonerated.** `-Xlog:gc*`: zero GC pauses. `-Xlog:safepoint*`: exactly one
  safepoint per run — the exit Halt — with "Maximum sync time 0 ns". Not GC, not
  safepoints, not compilation stalls.
- **Interrupt census on CPU 4 per run window (~6 s):** TRM 191–519 (dominant), CAL ~30,
  LOC ~21, TLB ~14, RES ~8. The pinned core draws roughly 2× the package TRM rate — the
  busy 5.3 GHz core crosses thermal thresholds most (package ~71 °C under bench load; the
  `performance` governor is precisely what heats it).
- **The arithmetic closes.** ~310 TRM in the 4.2 s measured window × ~38% chance of landing
  inside a timed 1.6 µs apply ≈ ~118 ≈ the ~100 observed events above the p99.99 knee.
- **Per-run correlation is monotone:** TRM 191 → p99.99 5.9 µs; 425 → 6.7; 519 → 6.9.
- **It reconciles the thin tape:** the same ~20–80 events/s sit at p99.99 for 1M fat
  samples over 4.2 s but beyond p99.9999 for 100M thin samples over 1.5 s — which is why
  thin p99.99 measures a clean 155–240 ns on the same machine.

Still unattributed: the max band (77–624 µs, a handful of events per run) — too rare for
the interrupt census to pin; next tool is `perf sched record` or MSR thermal-status reads,
both root. Mitigations if the 6 µs band ever matters: cooling/package-power (hardware
reality on a laptop), or accepting it as this machine's thermal noise floor. Trap kept
from the first attempt: the fault-band explanation was once written down without evidence
— attribute before narrating.
