# Foundry Integration Rules

Foundry is the local registry that tracks Claude Code projects — status, decisions, todos, and next steps — so work survives context switches and compaction.

## Session start

- Call `mcp__foundry__get_project` to load the project's recorded status, open decisions, and todos **before** starting work. Treat the loaded status and next-step as the source of truth for "where we left off."
- If it returns not-found, the project isn't registered — register it once with `mcp__foundry__upsert_project` (name + description).

## During work

- `mcp__foundry__heartbeat` at the start of a work session and after meaningful progress, so the registry reflects active work.
- `mcp__foundry__set_status` when the project's state changes (e.g. active, blocked, paused, done).
- `mcp__foundry__set_next_step` whenever you reach a natural stopping point — record the single most useful next action so the next session resumes instantly.

## Decisions and todos

- `mcp__foundry__add_decision` for every significant decision, with the rationale (the *why*, matching the commit-message standard). Use `mcp__foundry__supersede_decision` when a later decision overrides an earlier one — never rewrite history.
- `mcp__foundry__add_todo` for follow-ups discovered mid-work; `mcp__foundry__update_todo` to close them. Don't let todos live only in conversation — they vanish on compaction.
- `mcp__foundry__add_note` for context that is neither a decision nor a todo but is worth preserving.

## Boundaries

- Foundry records project state; it is not a substitute for git history or in-repo docs. Do not duplicate code structure or commit logs into it.
- Do not store secrets, credentials, or PII in foundry entries.
- If this project includes a `project-coordinator` agent, that agent owns foundry interactions — other agents should not call foundry directly.

<!-- TODO: not yet registered — foundry MCP was not connected at bootstrap. Call mcp__foundry__upsert_project on first session where it is available. -->
