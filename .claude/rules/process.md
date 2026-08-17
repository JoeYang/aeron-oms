# Process — spec first, then tests, then code

Every initiative follows the same order. No step is skipped. No step runs in parallel with
the one before it.

```
spec  →  failing tests  →  implementation  →  green suite  →  PR  →  merge  →  archive spec
```

## 1. Spec first (OpenSpec)

Every initiative starts as an OpenSpec change proposal, before any test and before any code.

| Action | Command |
|---|---|
| Initialize (once per repo) | `openspec init --tools claude` |
| Start an initiative | `openspec new change <name>` |
| List open changes | `openspec list` |
| List current specs | `openspec list --specs` |
| Inspect one | `openspec show <name>` |
| Check artifact completeness | `openspec status` |
| Validate before implementing | `openspec validate <name>` |
| Fold into the main specs when merged | `openspec archive <name>` |

Rules:

- No code and no tests until the proposal is written and `openspec validate` passes
- **One open change at a time.** This follows directly from the Pace section of CLAUDE.md.
- The spec states behaviour and acceptance criteria, not implementation
- When implementation shows the spec was wrong, update the spec first, then the code. A spec
  that has drifted from the code is worse than no spec.
- `openspec archive` runs only after the change is merged and the suite is green

## 2. Tests before code (TDD)

Inherited from the global config and not negotiable here. Write the failing test, watch it
fail, then implement.

Two kinds of test are required:

- **Unit tests** — JUnit 5, per class, for logic and boundary conditions
- **Journal tests** — replay-based, for anything touching sequenced state

Both are defined in @.claude/rules/testing.md.

## 3. The PR gate

Once a test pipeline exists, no PR may be opened until the full suite passes. The rule is
absolute. The local enforcement is deliberately not.

- `bazel test //...` must be green before `gh pr create`
- A `PreToolUse` hook checks this and **warns**. It does not block. It activates by itself
  once `MODULE.bazel` exists, and stays silent until then.
- A warning is not permission. Opening a PR over a failing suite is a deliberate act, and
  the reason belongs in the PR description.
- Real enforcement is a required status check on `main` in branch protection, added when CI
  exists. A local hook guards one machine and can be bypassed; branch protection guards the
  repository and cannot. Until that check exists, this gate rests on discipline.

## 4. A complete initiative

1. `openspec new change <name>`, write the proposal, `openspec validate <name>`
2. Create a worktree and feature branch — @.claude/rules/git-workflow.md
3. Write the failing tests, unit and journal
4. Implement until they pass
5. `bazel test //...` fully green
6. Open the PR; stack it if the change is large — @.claude/rules/stacked-prs.md
7. Merge, then `openspec archive <name>`
