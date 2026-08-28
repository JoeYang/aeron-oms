# Pipelined gateway recording

## Why

Fat-tape recording is RTT-bound, not system-bound: the gateway sends one message, spins for
its ack, then sends the next — ~3.7k msg/s at ~270 µs RTT, so recording the 1M fat tape took
~4.5 minutes and a 10M tape would take ~45. The cluster itself journals fat bytes far faster
(warm recovery replays them at 5.8 GB/s). This is the "gateway pipelining" lever from
`ideas/fat-message-levers.md`: allow a window of outstanding messages so the ingress pipe
stays full. Tooling only — the gateway's product path is not a bulk loader — but recording
large fat tapes becomes practical.

## What Changes

- A `SendWindow` accounting type in the gateway: tracks sent vs acked, admits a send only
  while outstanding < window, and matches each ack to its send time (FIFO — a single cluster
  session's acks arrive in sequenced order) so per-message RTT survives pipelining.
- `FatHeartbeatRoundTrip` takes the window size; the send loop offers while the window has
  room, polls egress otherwise, and prints one `sequenced=<t> checksum=<c>` line per ack in
  sequenced order — the exact format golden extraction greps today.
- `oms.gateway.window` (default **1**) read in `GatewayMain`. At the default the loop
  degenerates to the existing closed-loop behavior: one in flight, ack before next send.
- Timeout becomes progress-based: 10 s without a successful offer or an ack fails the
  recording, replacing the per-message deadline (equivalent at window 1).
- No script changes: `record-tape.sh` already forwards `GW_FLAGS`.
- Measure: record two 100k fat tapes same-day — closed loop and windowed — and compare
  rates; both must pass the script's built-in recovery verification.

## Capabilities

- `cluster-gateway` — fat sender MAY pipeline sends within a bounded window; golden line
  format and per-line order unchanged.

## Acceptance

- Window 1 reproduces today's behavior and output format exactly.
- Windowed recording of `local-fatheartbeats-100k-w64` passes recovery verification with
  100,000 golden lines in sequenced order, at a materially higher rate than closed loop.
- Unit tests cover the window accounting boundaries and the ack/print path.
