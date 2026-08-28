# Zero-copy fat walk

## Why

App-mode replay of the fat tape moves every payload byte three times: the walker touches the
mapped segment, copies each fragment chain into a scratch buffer, and the service's checksum
reads the copy. The fat-heartbeat measurements put the pipeline at 7.9 GB/s,
byte-bandwidth-bound — so removing one full pass over 32 GB is the largest app-mode lever
left (`ideas/fat-message-levers.md`, "zero-copy fat walk"). The copy exists only to hand the
service one contiguous buffer; the payload bytes already sit in the mapped segment,
interrupted every ~1376 bytes by fragment headers.

## What Changes

- `IncrementalPayloadChecksum` in `cluster-service`: the same rotate-left-1 XOR over
  little-endian longs as `PayloadChecksum`, but consumable in slices with byte-level carry at
  slice boundaries, so a payload split at arbitrary fragment edges checksums to the identical
  value. Pinned by equivalence tests against the contiguous implementation across exhaustive
  small-buffer splits.
- `TapeWalker` gains a slice-delivery mode: with a `ChainSliceHandler` supplied, a fragment
  chain that is a session message is delivered as its payload slices in log order — no
  scratch copy. Unfragmented entries, non-session chains, and every existing chain-contract
  check are unchanged; a chain whose first fragment cannot hold the headers falls back to
  the copy path.
- `TapeReplay.replayZeroCopy`: fat chains are checksummed in place via the slice path and
  captured exactly as the service's ack would report them (sequenced timestamp + checksum);
  unfragmented entries still go through the real `OmsClusteredService`. The `Result` shape
  is identical, so the golden gate applies unchanged.
- `tape-replay --zero-copy` flag; mutually exclusive with `--latency` (per-apply timing has
  a different meaning when the apply is fused into the walk).

## Why this is still honest replay

The zero-copy path does not verify itself: golden outputs were captured from the real
cluster service at record time, so a zero-copy replay that reports `REPLAY OK` has proven
its in-place checksum equals what the service computed, for every message on the tape. The
copy path remains the default and remains what the golden CI gate runs.

## Capabilities

- `tape-replay` — app replay MAY walk fat chains zero-copy behind an explicit flag, with
  identical outputs.

## Acceptance

- Incremental ≡ contiguous checksum across exhaustive small-length/split-point cases and
  the pinned hand constants.
- Zero-copy replay of a synthetic fragmented tape produces the identical `Result` as the
  copy path (unit).
- Zero-copy replay of `local-fatheartbeats-1m` with full goldens reports `REPLAY OK`.
- Measured count-only throughput compared against the copy path on the same tape, same day.
