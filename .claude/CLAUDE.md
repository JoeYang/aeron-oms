# aeron-oms

An order management system built around an Aeron-based sequencer. Inbound commands are
assigned a single total order by the sequencer and published as an event stream over Aeron;
downstream services consume that stream to rebuild state deterministically.

## Status

Genesis — the repo contains only `README.md`. There is no `src/`, `MODULE.bazel`,
`BUILD.bazel`, or `tools/` yet. The commands below are targets to build toward, not commands
that currently pass. Each becomes real in the commit that creates it; keep this table honest,
because a listed command that does not run is worse than no table.

Target runtime is **JDK 25**. The choice is load-bearing: CPU pinning uses the FFM API
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
| Build | `bazel build //...` |
| Test | `bazel test //...` |
| Test (single) | `bazel test //src/test/java/io/joeyang/oms/<pkg>:<Class>` |
| Format | `java -jar tools/google-java-format.jar --replace $(find src/ -name '*.java')` |
| Lint | `bazel build //... --aspects=@rules_lint//java:checkstyle.bzl%checkstyle` |
| Spec — start initiative | `openspec new change <name>` |
| Spec — validate | `openspec validate <name>` |
| Spec — archive when merged | `openspec archive <name>` |

## Architecture

Target tier layout. Import direction is enforced — see @.claude/rules/architecture.md.

```
gateway/    protocol adapters (FIX, SBE) — decode/encode only, no business logic
sequencer/  Aeron cluster: total ordering, consensus, journal, replay
domain/     deterministic state machine: order lifecycle, matching, risk, positions
egress/     outbound publication of acks, fills, drops, snapshots
common/     SBE codecs, buffer flyweights, shared value objects
```

`domain/` is the replay-critical core: pure, deterministic, no I/O, no ambient clock.

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
| @.claude/rules/exchange.md | Venue sessions and order lifecycle conventions |
| @.claude/rules/api-design.md | SBE, FIX, and control-plane interface design |
| @.claude/rules/git-workflow.md | Worktrees and feature branches |
| @.claude/rules/stacked-prs.md | Stacked PR approaches — decision pending |
| @.claude/rules/foundry.md | Project registry integration |
