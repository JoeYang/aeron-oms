# cluster-gateway — delta

## ADDED Requirements

### Requirement: Fat sender can pipeline sends within a bounded window

With `oms.gateway.window=N` (default 1), the fat sender SHALL keep at most N messages
outstanding: it sends while outstanding < N, polls egress otherwise, and prints one ack line
per message — `sequenced=<timestamp> checksum=<checksum>` — in sequenced order, preserving
the golden-extraction format. Ack-to-send matching SHALL be FIFO over the single cluster
session, and an ack arriving with no outstanding send SHALL fail loudly. The recording SHALL
fail if 10 s pass with neither a successful offer nor an ack. At window 1 the sender SHALL
behave as the existing closed loop.

#### Scenario: Windowed recording passes recovery verification

- **GIVEN** `record-tape.sh` with `TYPE=fat` and `GW_FLAGS` carrying
  `oms.gateway.window=64`
- **WHEN** 100,000 messages are recorded
- **THEN** the gateway prints 100,000 golden lines in sequenced order, the tape passes the
  script's recovery verification, and the recording rate materially exceeds the closed-loop
  rate measured the same day

#### Scenario: Ack without an outstanding send

- **GIVEN** a `SendWindow` with zero outstanding messages
- **WHEN** an ack is matched
- **THEN** the window throws rather than silently mis-accounting
