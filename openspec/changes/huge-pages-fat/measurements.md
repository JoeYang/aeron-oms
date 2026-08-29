# Measurements — huge pages for the fat tape

Machine: Intel Core Ultra 7 255HX, CPUs 4,6 isolated (`isolation.sh check` passed), 64 GB,
kernel 6.17.0-1032-oem, JDK 25, uptime ~2 days. Tape `local-fatheartbeats-1m` (1M × 32 KB,
32.9 GB) on the `huge=always` tmpfs, per-size shmem knob `always`. Pinned (`taskset` +
`--pin 4`), count-only for throughput, `--latency` with the 1k warmup for percentiles,
4 runs per mode, same session.

## Delivery: fragmentation is the new gate (finding #5)

The four gates from #40 were all open (tmpfs hosting, 2 MB writes, per-size knob, prctl
clear) — and delivery still capped at **12.3 GB PMD-mapped of 32.1 GB requested (~38%)**:
after ~2 days of uptime, physical memory is too fragmented to allocate 10k+ contiguous 2 MB
folios. Three strategies tried (in-place `dd bs=2M` re-blow ×2, bulk-free then a fresh
`tar --to-command='dd bs=2M'` extraction); all plateaued at ~12 GB. Without root
(`compact_memory`, or boot-time allocation) that is the ceiling on this box tonight. The
read-back machinery reported it on every run — exactly what it exists for.

## Throughput (small vs ~38%-huge hosting, same session)

| mode | runs (msg/s) | band |
|---|---|---|
| small pages | 239.4k / 236.2k / 241.2k / 237.3k | 7.8 GB/s |
| `--huge` (38% PMD) | 261.4k / 262.6k / 260.5k / 263.5k | **8.6 GB/s** |

**+9.6% at 38% delivery** — non-overlapping bands, larger per mapped byte than #40's thin
result (+9% at 99.3%). If the effect is linear in mapped fraction, full delivery projects
to +20-25%; that is a hypothesis for a fresh-boot run, not a claim.

## Apply latency

Unchanged: p50 1568–1632 ns, p99.9 1728–3712, p99.99 6.0–9.2 µs, max 12–274 µs — the same
bands as small pages, re-confirming the fat-heartbeat rerun's finding that the timed apply
reads the L2-hot scratch and never pays the mapping's faults or TLB misses; those are
absorbed by the untimed walk, which is exactly where the throughput gain shows up.

## Thin-tape rerun (review follow-up, 2026-08-29)

Same protocol on `local-heartbeats-100m` (100M × 128 B, 12.9 GB): control tar-extracted
onto the tmpfs (ShmemHugePages flat — small folios confirmed), huge hosting via the
`dd bs=2M` extraction, both prefaulted resident, pinned, 4 runs per mode. Fragmentation
allowed **70% PMD coverage** this time (8.9 of 12.6 GB requested, read back every run).

| mode | throughput runs (msg/s) | apply latency |
|---|---|---|
| small pages | 52.8M / 47.3M / 53.5M / 54.0M | p50 17, p99.9 70–80, p99.99 188–204 ns |
| `--huge` (70% PMD) | 65.9M / 65.5M / 65.8M / 65.8M | p50 17–19, p99.9 68–92, p99.99 184–236 ns |

**+22–27% at 70% coverage, latency bands overlapping — unchanged.** The thin effect is
larger than the fat one (+9.6%) and the mechanism says why: thin applies read the mapped
bytes directly, with no reassembly copy to absorb translation misses, so the TLB cost is a
larger fraction of a 17 ns apply pipeline than of a 1.6 µs one. Also notable: the huge
runs are far tighter (65.5–65.9M) than the small-page spread (47.3–54.0M) — removing
translation overhead removes a throughput noise source too.

## Verdict

The lever is real on fat tapes (+9.6% under partial delivery) and the machinery is honest
about what it delivered. The operational lesson joins the four gates from #40: huge pages
on a long-running box need a compaction or boot-time story, or the folios simply are not
there to allocate.
