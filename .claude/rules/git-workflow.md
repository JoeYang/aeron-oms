# Git workflow — worktrees and feature branches

## Never work on main

`main` is integration-only. A `PreToolUse` hook blocks file edits while `main` or `master` is
checked out. Every task starts on its own branch, in its own worktree.

## Worktree per task

```bash
# from the primary checkout
git worktree add ../aeron-oms-wt/<task-slug> -b feat/<task-slug> main
cd ../aeron-oms-wt/<task-slug>
```

Conventions:

- Worktrees live in a sibling directory (`../aeron-oms-wt/`), never nested inside the repo.
  A nested worktree gets swept up by Bazel globs and by `find src/ -name '*.java'` in the
  format command.
- One worktree per task, named after its branch
- Bazel shares an output base across worktrees by default. If concurrent builds interfere,
  give each worktree its own with `--output_base=/tmp/bazel-<task-slug>`.
- `git worktree list` shows what is live — check it before creating another

Tear down once merged:

```bash
git worktree remove ../aeron-oms-wt/<task-slug>
git branch -d feat/<task-slug>
git worktree prune
```

## Branch naming

| Prefix | Use |
|---|---|
| `feat/` | New capability |
| `fix/` | Bug fix — starts with a failing regression test |
| `refactor/` | Behaviour-preserving change; never carries a feature |
| `chore/` | Tooling, build, config |
| `docs/` | Documentation only |

## Integration

- Rebase onto `main` before merging — never merge `main` into a feature branch
- Conventional Commits; under 200 lines per commit, hard cap 400 (global rule)
- Interfaces and types land in their own commit, before implementation
- Schema changes (SBE, journal format) are always their own commit
- Force-push only with `--force-with-lease`

For splitting one change across several PRs that merge in order, see
@.claude/rules/stacked-prs.md.
