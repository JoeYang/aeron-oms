# FatHeartbeat — a 32 KB message through the whole pipeline

## Why

Every number so far was earned with 128-byte heartbeats. Real order flow carries fat
payloads, and fat messages change the physics: they exceed Aeron's MTU (so they fragment on
ingress, in the log, and in the journal), they turn recording into a disk-bandwidth problem,
and they make the apply cost memory-bandwidth-real. A 1M-message, 32 GB fat tape (scale
confirmed with the user; 100M × 32 KB = 3.2 TB does not fit) tells us how the system handles
all three before real order payloads exist.

## What Changes

- **New SBE message `FatHeartbeat`** (append-only schema change, own commit, `/sbe-gen`
  review): `timestampNanos` plus a variable-length payload; the gateway sends 32,000-byte
  deterministic patterned payloads (never random — replay is the product).
- **State machine applies it by reading it** (decided with the user): checksum all payload
  bytes deterministically, echo sequenced timestamp + checksum. Payload integrity becomes
  end-to-end verifiable and the timed apply reflects the honest cost of touching fat data.
- **`TapeWalker` learns fragment reassembly**: a 32 KB entry spans multiple log frames
  (BEGIN/…/END); the walker currently throws on fragmented frames by design. Reassembly uses
  a preallocated scratch buffer; truncated fragment chains still fail loudly.
- **Golden outputs carry two values** for fat tapes: `<timestamp> <checksum>` per line — 1M
  lines is small enough to record with full goldens, unlike the count-only 100M thin tape.
- **Recording and gateway gain a message-type knob** so `record-tape.sh` can produce
  `local-fatheartbeats-1m` (git-ignored local tape; new scenario = new name, existing tapes
  untouched).
- **Measure**: record the tape, replay app-mode (throughput + `--latency` ladder) and
  cluster-mode recovery; report alongside the thin-tape numbers.

## Capabilities

### Modified Capabilities
- `message-schema`: adds the FatHeartbeat message (append-only; wire identity of existing
  messages unchanged).
- `cluster-state-machine`: applies FatHeartbeat by checksumming the payload and echoing
  timestamp + checksum.
- `cluster-gateway`: can stream FatHeartbeats with deterministic patterned payloads.
- `tape-replay`: walker reassembles fragmented entries; fat-tape goldens verify checksums.
- `golden-tape`: recording can produce a FatHeartbeat scenario with two-value goldens.

## Impact

- `sbe/message-schema.xml` (append-only; own commit), `//cluster-service` (apply),
  `//cluster-node` (walker, replay verification), `//gateway` (send mode),
  `scripts/record-tape.sh` (type knob).
- Disk: 32 GB extracted tape + compressed archive; recording is disk-bandwidth-bound
  (~2 GB/s → tens of seconds).
- No change to existing tapes, stream ids, or the journal format itself — fragmentation is
  already Aeron's wire format; we only teach the reader to honour it.
