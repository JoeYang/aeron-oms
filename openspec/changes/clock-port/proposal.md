## Why

Nothing in this repository reads time yet, which makes now the cheapest moment to make
reading it impossible except through one port. `.claude/rules/design.md` already names the
time source as a port — "supplied, never ambient" — and `CLAUDE.md` forbids wall-clock reads
in deterministic code. Neither is enforceable while there is no interface to enforce.

The rule matters because replay is the product. A state machine that reads a clock produces
a different result on every replay of the same log, so every journal test built on it is
flaky and the audit reconstruction the security rules require becomes impossible.

## What Changes

- Add a `Clock` port to `//core`: one method, nanoseconds since the Unix epoch.
- Add `SequencedClock`, advanced only by a timestamp that arrived as sequenced input. This
  is the implementation deterministic code uses.
- Add `SystemClock`, reading the wall clock, for code outside the replay boundary.
- Add `FixedClock` as a test double that never advances.
- No production code is converted to use the port, because no production code reads time yet.

Not in scope: wiring the clock into a `ClusteredService` (there is none), choosing the
cluster's `timeUnit`, and the ingress timestamp used for latency measurement.

## Capabilities

### New Capabilities
- `time-source`: time is obtained through a single port, with one implementation that is
  deterministic under replay and one that is not, distinguishable at the type level.

## Impact

- `core/` — new `io.joeyang.oms.core.time` package and its tests
- `core/BUILD.bazel` — no new dependency. `SequencedClock` receives a `long`, so the port and
  every implementation compile without Aeron on the path.
- `todo/` — records that `SystemClock` is not yet mechanically barred from deterministic code

### Fact check on the Aeron message header

The request proposed reading the 8-byte reserved value from the Aeron message header.
Verified against Aeron 1.52.2: the field is real and is exactly 8 bytes —
`DataHeaderFlyweight.RESERVED_VALUE_OFFSET = 24`, `DATA_OFFSET = 32`, readable through
`Header.reservedValue()` and writable through a `ReservedValueSupplier`.

It is nevertheless the wrong source for this clock. Aeron Cluster passes a timestamp directly
into every service callback — `onSessionMessage(ClientSession, long timestamp, ...)` — and
that value is in the replicated log, so replay reproduces it exactly. The reserved value is
set by the publisher, and the `Header` a service receives belongs to the log fragment rather
than the client's ingress frame.

The reserved value remains the natural carrier for the *ingress* timestamp that the
wire-to-ack latency budget and the audit requirements need. That is a separate concern from
this port and is not built here.
