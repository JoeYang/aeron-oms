# Todo

Work that **must** happen, deliberately deferred, with the reason and the trigger recorded.

This is a debt register, not a backlog of nice-to-haves. Anything filed here was already
judged necessary. The only open question is when.

## How this differs from `ideas/`

| | `todo/` | `ideas/` |
|---|---|---|
| Already judged necessary | Yes | No |
| May be deleted unbuilt | No — only when done | Yes, freely |
| Has a trigger | Required | Not applicable |
| Cost of ignoring it | Accrues | None |

An idea you never build costs nothing. A todo you never do is a decision made by neglect.

Both folders sit outside `openspec/`. Neither is a commitment to a schedule, and neither
counts against the one-open-change rule. Promotion works the same way: pick the item,
`openspec new change <name>`, and delete the file in the commit that closes it.

## Format

One file per item, `kebab-case.md`. Each must answer:

- **What** — the work itself, in a sentence
- **Why deferred** — the reason it was not done at the time. Without this, a later reader
  cannot tell a deliberate deferral from an oversight.
- **Trigger** — the event that makes this due. A date, a milestone, or a condition.
- **Cost of delay** — what gets worse while it sits here

**Trigger** is the field that does the work. A todo without one is a wish, and it will sit
untouched until it becomes an incident.
