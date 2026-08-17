# Venue and session conventions

## Completion gate

Until a dedicated end-to-end harness exists, `bazel test //...` is the completion gate — no
task is done without a passing run. When a smoke harness lands, replace this section with the
harness command **in the same commit that adds it**.

The eventual end-to-end run must validate: gateway session establishment, order entry and ack,
sequencing and journal append, matching and fill generation, egress publication, and replay of
the journal to identical state.

> Adapted from the harness library rule, which gates on `./smoke_test_all.sh <exchange>`.
> That script belongs to an exchange-simulator repo and does not exist here; this repo is the
> client-side OMS.

## Session conventions

- APAC venue sessions follow their local trading calendars and timezones — never assume UTC
  business dates
- Use venue-native instrument symbology on the wire; normalize only at the gateway boundary
- Handle session phases explicitly — pre-open, continuous trading, closing auction, halt.
  Order handling differs per phase; encode the phase as state, do not infer it from the clock.
- Reject rather than silently queue orders received outside an accepting session phase
- Log all gateway messages at DEBUG during development so sessions can be replayed

## Order lifecycle

- Every inbound order gets a venue-independent internal id at the gateway, mapped to the
  venue's id. Internal ids never leak to the venue; venue ids never reach `domain`.
- Amend and cancel are ordered operations: they pass through the sequencer like any other
  command, never short-circuiting against local state
- Unsolicited venue messages — venue-initiated cancel, session reset, mass status — are
  first-class inputs with their own handlers, not error cases
- A fill is terminal state for the quantity it covers; partial fills accumulate against the
  order rather than replacing prior state
