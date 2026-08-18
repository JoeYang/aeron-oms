# Ideas

A parking area for things worth considering but not committed to. Reach in when the next
piece of work is not obvious.

Nothing here is a plan. There is no priority order, no schedule, and no promise that any of
it gets built. An idea may sit here for a year, or be deleted unbuilt. Both are fine.

## How this differs from `openspec/`

| | `ideas/` | `openspec/changes/` |
|---|---|---|
| Commitment | None | Committed work |
| How many open at once | Any number | Exactly one |
| Validated | No | `openspec validate` must pass |
| Allowed to be wrong | Yes — that is the point | No: fix the spec, then the code |

The path from one to the other:

1. Pick an idea.
2. `openspec new change <name>` and write the proposal properly. The idea file is raw
   material, not a substitute for the proposal.
3. Delete the idea file in the same commit.

Step 3 matters. An idea that survives its own promotion becomes a stale second copy of the
spec, and the two drift apart.

## Format

One file per idea, `kebab-case.md`. Keep it short enough to write in five minutes. Friction
here means ideas do not get captured at all, which is the only real failure mode of this
folder.

A useful idea file answers:

- **What** — one or two sentences
- **Why** — the problem it solves
- **Blocked by** — what must exist first, if anything
- **Traps** — anything already known that makes it harder than it looks

Skip any heading you have nothing to say under. A one-line idea is still worth keeping.

The **Traps** section is what makes this folder worth more than a notes app. It is where
you record what you already know today and will have forgotten by the time you pick the
idea up.
