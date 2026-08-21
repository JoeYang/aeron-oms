## 1. Dependency

- [x] 1.1 Add `uk.co.real-logic:sbe-tool` to `MODULE.bazel` at a version checked against
      maven-metadata.xml
- [x] 1.2 Repin the lock, confirming the enforcement added earlier rejects the stale lock first
- [x] 1.3 Confirm no second Agrona version enters the graph

## 2. Schema

- [x] 2.1 Create `sbe/` holding the schema XML and nothing else
- [x] 2.2 Define one message with a single fixed-width field

## 3. Generation

- [x] 3.1 Add `//sbe-java` with a generator binary and a genrule producing a srcjar
- [x] 3.2 Enable stop-on-error and warnings-fatal, which both default to off
- [x] 3.3 Build the generated library and confirm no source is committed

## 4. Tests

- [x] 4.1 Round-trip, boundary values, and non-zero offset
- [x] 4.2 Header carries schema id, template id, version, block length
- [x] 4.3 Pin the wire identity so renumbering fails loudly
- [x] 4.4 Verify a malformed schema fails the build
- [x] 4.5 Verify a wire-format change fails the suite

## 5. Documentation

- [x] 5.1 Add rows for `sbe` and `sbe-java` to `.claude/rules/architecture.md`
- [x] 5.2 Correct the `//core` row, which claims core holds SBE codecs
- [x] 5.3 Update `README.md` and `CLAUDE.md` package lists
