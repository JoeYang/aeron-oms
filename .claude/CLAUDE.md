# aeron-oms

An order management system built around an Aeron-based sequencer. Inbound commands are
assigned a single total order by the sequencer and published as an event stream over Aeron;
downstream services consume that stream to rebuild state deterministically.

## Status

Skeleton. The Bazel build works and `bazel test //...` is green. Aeron Cluster 1.52.2 is
declared and proven to run — a test starts an embedded `MediaDriver` and connects a client —
but no package uses Aeron beyond that test, and there is no OMS behaviour yet. The packages
hold placeholders that prove the toolchain, not domain types. Keep the command table below
honest: a listed command that does not run is worse than no table.

Agrona reads `jdk.internal.misc.Unsafe`, so `.bazelrc` carries
`--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`. It is mandatory, not tuning: without
it the first buffer allocation throws `IllegalAccessError`.

Target runtime is **JDK 25**, pinned in the build as `remotejdk_25` rather than inherited from
`PATH`. The choice is load-bearing: CPU pinning uses the FFM API
(`java.lang.foreign`) to call `sched_setaffinity`, which removes any need for JNI, a
third-party affinity library, or a C++ toolchain. See @.claude/rules/trading-latency.md.

## Pace

This project is deliberately slow. The limiting resource is the user's understanding, not
throughput. Work that outruns it has negative value — it has to be un-learned later.

- **One issue or feature at a time.** Do not start the next until the current one is finished
  and closed. No parallel workstreams, no "while we're here" additions.
- **Explain trade-offs before implementing, not alongside.** Every technical decision gets its
  options, their costs, and a recommendation — including what the choice forecloses.
- **One decision per exchange.** Do not stack several unrelated decisions into one response.
  If a task surfaces three choices, present the first, settle it, then move to the second.
- **Wait for explicit confirmation** on design decisions. Silence is not agreement, and a
  question answered is not the same as a decision made.
- **Depth over coverage.** A small change that is fully understood beats a large one that is
  merely accepted.
- **Do not scaffold ahead.** Build what the current issue needs. Speculative structure for
  imagined future features is cost with no reader.
- If understanding has not been reached, stop and explain it differently. Do not proceed and
  leave the explanation for later.

This is why the commit rules are small and sequential, and it is the main argument for
adopting stacked PRs — see @.claude/rules/stacked-prs.md.

## Commands

| Action | Command |
|---|---|
| Build | `scripts/build.sh` (wraps `bazel build //...`) |
| Test | `scripts/test.sh` (wraps `bazel test //...`) |
| Test (single) | `scripts/test.sh //core:core_test` |
| Run a process | `scripts/run.sh cluster-node \| gateway \| driver` |
| Format | `scripts/format.sh` (google-java-format, rewrites files) |
| Lint | `scripts/lint.sh` (format check + Checkstyle, read-only) |
| SBE — change a message | `/sbe-gen` (regenerate, inspect, diff wire identity, test) |
| Spec — start initiative | `openspec new change <name>` |
| Spec — validate | `openspec validate <name>` |
| Spec — archive when merged | `openspec archive <name>` |

## Architecture

Aeron Cluster. Dependency direction is enforced by Bazel `visibility`, not convention — see
@.claude/rules/architecture.md.

```
//sbe              the message schema, XML only. Changing it changes the log format
//sbe-java         Java codecs generated from //sbe at build time; nothing committed
//core             shared library: value types and ports (e.g. the clock). No entry point.
//cluster-service  ClusteredService — the deterministic state machine
//cluster-node     hosts ConsensusModule + ClusteredServiceContainer
//gateway          AeronCluster client + protocol adapters (FIX, SBE)
//driver           standalone MediaDriver launcher
```

`//cluster-service` is the replay-critical core: pure, deterministic, no I/O, no ambient clock.
Its visibility is restricted to `//cluster-node`, so a forbidden dependency fails the build
rather than warning. Aeron Cluster sequences ingress — this project does not write a sequencer.

## Boundaries

### Always do
- Start every initiative with an OpenSpec change proposal — see @.claude/rules/process.md
- Run `bazel test //...` before reporting work complete
- Run google-java-format before committing
- Work in a git worktree on a feature branch — see @.claude/rules/git-workflow.md
- Keep the design described in `README.md` identical to the code that actually exists

### Ask first
- Adding dependencies to `MODULE.bazel` or `BUILD.bazel`, especially anything on the hot path
- Changing SBE schemas, Aeron stream/channel ids, or the journal format
- Changing the sequencer's ordering semantics
- Changing the CPU layout, thread pinning, or `jvm_flags`
- Introducing a database or any external store

### Never do
- Push to `main`
- Open a PR while `bazel test //...` is failing
- Write code or tests before the OpenSpec proposal validates
- Introduce non-determinism into `domain/` — wall-clock reads, `Random`, unordered map
  iteration, or thread scheduling that affects output
- Allocate on the hot path once steady state is reached
- Disable or skip existing tests
- Commit credentials, `.env` files, or venue certificates

## Rules

| File | Scope |
|---|---|
| @.claude/rules/process.md | OpenSpec spec-first workflow, TDD order, the PR gate |
| @.claude/rules/architecture.md | Tier layout, import direction, determinism rule |
| @.claude/rules/design.md | SOLID, bound to this repo's seams |
| @.claude/rules/java.md | Java, Aeron, and Agrona conventions |
| @.claude/rules/testing.md | TDD, JUnit 5, determinism and failure injection |
| @.claude/rules/security.md | Strict: OWASP baseline + cryptography + audit |
| @.claude/rules/trading-latency.md | Latency budgets, hot-path and JVM constraints |
| @.claude/rules/api-design.md | SBE, FIX, and control-plane interface design |
| @.claude/rules/git-workflow.md | Worktrees and feature branches |
| @.claude/rules/stacked-prs.md | Stacked PR approaches — decision pending |
| @.claude/rules/foundry.md | Project registry integration |
