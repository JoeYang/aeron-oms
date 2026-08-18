# Exchange simulator

**What** — a venue simulator with a limit order book and price-time priority matching, so
the OMS has something to trade against before real venue connectivity exists.

**Why** — without a counterparty there is nothing to test an order lifecycle against.
Fills, partial fills, rejects, and cancels all need something on the other side that
behaves like a venue. A controlled dependency is cheaper and far more deterministic than
booking time on a venue test environment.

## Blocked by

Nothing structural. This can start as soon as there is a `ClusteredService` to model it on.

It does need the wire protocol question below to be at least provisionally settled, or it
will encode a guess.

## Open question — cluster app, or cluster-shaped service?

Recorded as "another Aeron Cluster app". That is worth splitting into two decisions,
because they have different answers.

**The service** — yes. Shape it as a `ClusteredService`: deterministic, no I/O, no ambient
clock. That discipline is what makes the book replayable, and `.claude/rules/design.md`
already requires a state machine to be constructible in a unit test with no media driver
running.

**The hosting** — less obvious. A three-node cluster means three JVMs, a consensus module,
and an archive per test run. That is heavy for a test fixture, and slow tests get skipped.
Consensus buys fault tolerance the simulator does not need: it is not the system under
test, and if it dies the test should simply fail.

| Hosting | Cost | Buys |
|---|---|---|
| In-process, no Aeron | Milliseconds | Unit tests of matching logic |
| Single-node cluster | Seconds | Real ingress and log path |
| Three-node cluster | Slowest | Failover behaviour under test |

These are not exclusive — one deterministic service, three ways to host it. The decision is
which one the default test path uses.

## Traps

### Time priority forces the deferred "time through the sequencer" decision

Price-time priority needs a time ordering. If the matcher reads a clock, two replays of the
same log produce different books and every test built on it goes flaky. If time arrives as
sequenced input, priority is deterministic by construction.

This is the first concrete thing that requires that design to be settled. It cannot be
deferred past this point.

Related, and cheaper to get wrong: a book keyed by `HashMap` iterates in unspecified order.
`CLAUDE.md` already bans unordered map iteration in deterministic code. The book is exactly
where that bites.

### A simulator sharing `//core` codecs cannot catch codec bugs

If both sides encode with the same SBE codec, a bug in that codec makes both sides agree
and the test passes. The simulator validates matching logic, not the wire format. Either
accept that limit knowingly, or exercise the wire independently.

### An invented protocol teaches the gateway the wrong lessons

If the simulator speaks a made-up protocol, the gateway adapter is written against fiction
and gets rewritten on day one of real connectivity. Model a real venue's semantics early,
even a small FIX subset.

This is the fake-`Publication` rule from `.claude/rules/design.md` applied one level up: a
fake that only honours the success path asserts a contract the real thing does not.
Simulate rejects, session errors, and throttling, not just fills.

### Amend semantics are the classic source of divergence

Whether an amend keeps time priority depends on what changed, and the convention varies by
venue. Pick a rule, write it down, test it. Getting this wrong yields a simulator that is
subtly not the venue it claims to model — the worst kind, because it passes.

## Scope

Small enough to stay a dependency rather than become a project.

- **In**: one instrument, limit orders, new/cancel/amend, price-time matching, rejects.
- **Out until asked for**: icebergs, self-trade prevention, tick and price banding,
  auctions, multiple instruments.

Bazel `testonly = True` makes the boundary mechanical — production code that depends on the
simulator fails the build, the same way visibility already enforces the tier layout.
