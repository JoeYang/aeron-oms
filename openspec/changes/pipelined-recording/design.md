# Design — pipelined recording

## Decision 1: window in the gateway, not batching in the protocol

| option | pros | cons |
|---|---|---|
| Outstanding-send window (chosen) | no schema change, no cluster change; per-message acks and goldens unchanged | throughput still bounded by ingress path, not RTT — which is the point |
| Batch message (many payloads per SBE frame) | fewer frames | schema + state-machine + golden format change; a recording lever is not worth a wire change |
| Fire-and-forget (no ack tracking) | simplest | loses the ack-to-send match, so no RTT and no per-message integrity check at record time |

## Decision 2: FIFO ack matching

`FatHeartbeatAck` carries no sequence number, so pipelining relies on ordering: one cluster
session, totally ordered ingress, egress in sequenced order — the k-th ack answers the k-th
send. That invariant is exactly what the cluster guarantees and what the golden file already
assumes (line order = sequenced order). The `SendWindow` keeps a ring of send timestamps
sized to the window; an ack beyond the sends is a hard error, never a silent skip.

## Decision 3: progress-based timeout

A per-message 10 s deadline made sense when each message completed before the next began.
Pipelined, a single stall anywhere should fail the recording, but 100k messages must not
share one budget: the deadline advances on every successful offer and every ack. At window 1
this is behaviorally the old per-message deadline.

## Decision 4: default window 1

Recording behavior is part of tape provenance. Existing tapes were recorded closed-loop;
the default stays closed-loop, and a windowed recording is a deliberate choice recorded in
the tape's manifest command line. The interval sleep (`oms.gateway.interval.ms`) still
applies after each send, degenerating identically at window 1.
