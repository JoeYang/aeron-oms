# Stacked pull requests

**Status: undecided.** Three approaches are documented below; none is yet the project default.
Try one, then record the decision and replace this status line in the same commit.

A stack is a chain of small PRs where each is based on the one below it, so a large change
merges sequentially instead of arriving as one unreviewable diff. It pairs directly with the
global 200-line commit target: the commit rules already force the split, stacking is how the
split gets reviewed.

## Option A — `gh` CLI with manual bases

```bash
git checkout -b feat/part-1 main
# ...work, commit...
gh pr create --base main --head feat/part-1

git checkout -b feat/part-2 feat/part-1
# ...work, commit...
gh pr create --base feat/part-1 --head feat/part-2
```

After `part-1` merges into `main`, rebase `part-2` off the merged base and rewire its PR:

```bash
git rebase --onto main feat/part-1 feat/part-2
gh pr edit feat/part-2 --base main
git push --force-with-lease
```

- Needs nothing beyond `git` and `gh`, which is already authenticated here
- Every mechanism is visible — the best way to learn what stacking actually is
- Restacking and base rewiring are manual on every merge, and get error-prone past ~3 PRs

## Option B — Graphite (`gt`)

```bash
gt create -m "feat: part 1"     # creates branch + commit
gt create -m "feat: part 2"     # stacks on top of part 1
gt submit --stack               # opens or updates every PR in the stack
gt sync                         # after a merge: restacks and rewires bases
```

- Auto-restacks the whole stack and fixes PR bases when something merges
- Renders the stack in each PR description so reviewers see the order
- Requires installing `gt` and authorizing a third-party GitHub app; free-tier limits apply
  to private repos. Adopting it is a dependency decision — flag it before adding, per the
  global rule on new tooling.

## Option C — `spr` (one PR per commit)

```bash
git commit -m "feat: part 1"
git commit -m "feat: part 2"
git spr update                  # one PR per commit, stacked automatically
```

- No branch management at all; maps cleanly onto the small-commit rule
- Rewrites commit SHAs on every update, which breaks anything pinned to a SHA
- Smaller community, extra install

## Choosing

Work through these in order:

1. **How deep do stacks get?** Two or three PRs — Option A is fine. Regularly five or more —
   A's manual restacking stops being worth it.
2. **Is a third-party GitHub app acceptable** for this repo? If not, B is out.
3. **Does anything depend on stable commit SHAs** — CI caching, release tags, external
   references? If yes, C is out.
4. **Solo or shared review?** Shared review benefits from B's stack visualisation; solo work
   rarely needs it.

Record the outcome as a decision (foundry `add_decision`) with the rationale, and update the
status line above.

## Rules that hold regardless of tooling

- Each PR in a stack must build and pass `bazel test //...` **on its own**. A PR that only
  works once the one above it lands is a broken split, not a stack.
- Split along the global commit rules: interfaces before implementation, refactor separate
  from feature, schema changes alone
- Merge strictly bottom-up — never merge an upper PR first
- Rebase; never merge `main` into a stacked branch. Merge commits corrupt the chain.
- Always `--force-with-lease`, never a bare `--force` — a bare force-push on a shared stack
  discards someone else's restack
