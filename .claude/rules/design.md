# Design principles (SOLID, bound to aeron-oms seams)

- **SRP** — one reason to change per module, sized to the 200-line commit target. A change
  that smears across `gateway`, `domain`, and `egress` means the seam is in the wrong place —
  most often protocol detail leaking into the state machine.

- **OCP** — new venues and new order types extend through existing ports. Adding a venue means
  a new `gateway` adapter, not a `switch` in `domain`. Adding an order type means a new handler
  registered with the state machine, not a branch in the sequencer.

- **LSP** — a fake `Publication`/`Subscription` used in tests must match Aeron's real failure
  semantics, including back-pressure (`BACK_PRESSURED`, `NOT_CONNECTED`, `ADMIN_ACTION`,
  `CLOSED`). A test that only exercises the success return is asserting a contract the real
  transport does not honour.

- **ISP** — keep ports narrow. This project's ports are: inbound command decoder, outbound
  event publisher, time source (supplied, never ambient), and snapshot store. Before adding a
  method to one, ask whether a second interface is cleaner.

- **DIP** — `domain` depends on interfaces it declares; `gateway`, `sequencer`, and `egress`
  supply the implementations. The concrete test: the state machine must be constructible in a
  unit test with no media driver running and no Aeron classes on the path.

**Performance caveat:** on the hot path, virtual dispatch and megamorphic call sites cost real
nanoseconds. Where profiling shows it matters, move substitutability to construction time — a
single implementation wired at startup, `final` classes, sealed hierarchies with exhaustive
switch — rather than adding runtime polymorphism. The principles hold; the vtable does not.
Do this where measurement justifies it, not pre-emptively.

Enforcement stack: this file → Bazel visibility and the import-direction hook (mechanical,
where checkable) → code review for the semantic SRP/OCP/ISP judgements, with authority to
bounce the change.
