# Tasks — FatHeartbeat

## 1. Schema (own commit, /sbe-gen review)

- [x] 1.1 Capture wire identity before; add FatHeartbeat (append-only, uint16 payload
      length, sinceVersion bump); regenerate; diff identity — existing messages unmoved
- [x] 1.2 Codec tests: round trip, boundary payload sizes (0, 1, 32000, max), non-zero
      offset, wire-identity pin for the new message

## 2. State machine (TDD)

- [x] 2.1 Failing tests: checksum pinned against hand-computed values for known payloads;
      determinism; allocation-free apply
- [x] 2.2 FatHeartbeat apply: checksum + timestamp echo

## 3. Walker reassembly (TDD)

- [ ] 3.1 Failing tests: fragmented entry reassembled whole; truncated chain throws;
      thin tapes still walk the zero-copy path (existing golden tape green)
- [ ] 3.2 Implement BEGIN/…/END reassembly with a preallocated 64 KB scratch buffer

## 4. Gateway + goldens (TDD)

- [ ] 4.1 Failing tests: sequence-derived payload pattern; two-value golden parsing and
      comparison, mismatch fails naming the position
- [ ] 4.2 Fat send mode; replay-side two-value golden verification

## 5. Record and measure

- [ ] 5.0 Smoke: record `local-fatheartbeats-1k` (~32 MB) — proves one 32 KB message
      round-trips ingress and log; becomes the reassembly test fixture
- [ ] 5.1 `record-tape.sh` fat mode with startup limit check on both ingress and log
      channels; record `local-fatheartbeats-1m` (32 GB); manifest with observed rate
- [ ] 5.2 Replay app-mode: count-only throughput (comparable with thin numbers) plus one
      full checksum-verify run (integrity gate), and the `--latency` ladder; replay
      cluster-mode recovery; identical protocol (prefault, discard, 3 runs, MemAvailable)
- [ ] 5.3 Record results and verdict vs the thin-tape numbers in measurements.md
