# Tasks — tape-cat-viewer

## 1. Spec

- [x] 1.1 Proposal, design, specs, tasks; `openspec validate tape-cat-viewer` passes.

## 2. Walker refactor (behaviour-preserving, own commit)

- [x] 2.1 Extract `TapeWalker.walk(archiveDir, handler)` from `TapeReplay`; replay
      tests stay green untouched.

## 3. Viewer (TDD)

- [x] 3.1 Failing tests first: golden tape → 3000 session lines in order with golden
      timestamps + non-session lines; `--json` lines each parse standalone; unknown
      template id prints, does not throw.
- [x] 3.2 Implement `TapeCat` + `TapeCatMain`; third `java_binary`; tests green.
- [x] 3.3 `scripts/tape-cat.sh <name> [--json]`; command-table row; journal/README
      pointer.

## 4. Close

- [ ] 4.1 Suite green uncached; PR; after merge `openspec archive tape-cat-viewer`.
