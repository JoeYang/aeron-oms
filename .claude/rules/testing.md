---
paths: ["src/test/**/*.java"]
---
# Testing

TDD is the default: write the failing test, implement, pass, refactor, re-run.
Coverage expectations are inherited from the global config — every logical path, edge case,
and boundary condition, rather than a percentage target.

JUnit 5 + Mockito. One test class per source class, mirroring the `src/main/java/` structure
under `src/test/java/`. Run with `bazel test //...`.

## Journal tests

Journal testing is the primary method for anything touching sequenced state. It works
because every input enters through the sequencer and is recorded in order — order flow,
market data, reference data, config, and time alike (see @.claude/rules/architecture.md).
A recorded journal is therefore a complete, replayable description of a scenario.

A journal test does three things:

1. Loads a journal as its input fixture
2. Replays it through the state machine
3. Asserts both the resulting state and the emitted egress events

Required for every `domain/` change:

- **Replay determinism** — replay the same journal twice, assert identical final state
- **Snapshot round-trip** — snapshot, restore, continue; assert the same state as an
  uninterrupted run to the same position
- **Egress assertion** — assert the events published, not only internal state. A state
  machine that reaches the right state while emitting the wrong events is still broken.
- **Truncation** — replay a journal cut at an arbitrary offset. The result must be the valid
  state for that position, never a partly applied command.
- **No ambient time** — the test must fail if a wall-clock read is introduced

Journal fixtures are committed test data. Treat them as golden files: changing a fixture is
a deliberate, reviewed act, never a convenient way to make a test pass.

Prefer a journal recorded from a real run over a hand-built one. A hand-built journal encodes
the author's assumption about what a venue does; a recorded one encodes what it did.

## Failure injection

Required for every component that touches transport or state:

| Class | Cases |
|---|---|
| Transport | `offer()` returning `BACK_PRESSURED`, `NOT_CONNECTED`, `ADMIN_ACTION`, `CLOSED`; media driver absent; subscription with no image |
| Sequencer | Leader failover mid-stream; journal truncated at an arbitrary offset; duplicate or out-of-order ingress; replay from a corrupt snapshot |
| Inputs | Malformed SBE frames, truncated messages, unknown template id, wrong schema version, fields at min/max, zero and negative quantity and price |
| Resource | Term buffer exhausted, publication limit reached, image unavailable mid-fragment, disk full on journal append |
| Placement | Pin requested on a core that is absent or not isolated; `sched_setaffinity` returning `EINVAL` (a `cpuset` cgroup is blocking it); affinity read-back not matching the request; `--enable-native-access` missing |
| Concurrency | Producer outpacing consumer, slow-consumer eviction, agent thread death, shutdown mid-command |

Assert graceful degradation: a meaningful error, clean state, no partially applied command,
and no silent drop. A component that logs and continues past an unhandled failure fails review.

## Discipline

- Bug fixes start with a failing regression test that reproduces the bug
- Never disable, skip, or `@Disabled` an existing test — fix it
- Latency assertions belong in a benchmark harness, not unit tests. Unit tests assert
  behaviour; benchmarks assert time. A timing assertion in a unit test is a flake waiting
  for a busy CI box.
