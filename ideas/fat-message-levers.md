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

## Gateway pipelining — measured and rejected (2026-08-29, PR #45 closed)

Built and measured on `feat/pipelined-recording` (branch and full measurements preserved
on the closed PR): a `SendWindow` of N outstanding sends took 100k-message fat recording
from 4.8k msg/s (RTT-bound) to 62k msg/s at window 64 — 13×, saturating ~2 GB/s of
journal ingest; window 256 bought nothing more. The cost is inherent, not tunable:
per-message RTT grows exactly per Little's law (211 µs closed-loop → 850 µs at window 64
→ 3.6 ms at window 256) because each message queues behind the rest of the window.

**Rejected on principle**: latency must not deteriorate when a lever is engaged, even in
tooling, and a windowed-send pattern should not live in this codebase where it could
migrate toward the hot path. Revisit trigger: recording time actually blocks an
initiative *and* the latency principle is consciously waived for the recording tool —
both, explicitly.

## Snapshotting (attacks: recovery cost scaling itself)

The structural answer: with snapshots, recovery replays only the tail since the last
snapshot, and the recovery bill stops scaling with journal bytes entirely. Everything
above optimizes the constant; this removes the linearity. Largest initiative on the
list, needs its own spec (snapshot content, cadence, determinism proof via
golden-tape-through-snapshot), and is eventually inevitable for any real OMS state.
Revisit trigger: the state machine holds real order state worth snapshotting.

## Residual apply-tail attribution (attacks: p99.99 ≈ 6 µs, max 14–400 µs)

The band survives a fully RAM-resident tape, so it is not paging. ~100 events per run at
p99.99 is enough to catch with `perf` on the isolated core (or JFR safepoint/compilation
logs) during a latency replay. Same shape as the parked thin-tape residual (p99.99
≈ 200 ns there); one attribution session likely explains both. Trap: the fault-band
explanation was already written down once without evidence — attribute before narrating.
