# aeron-oms

An order management system (OMS) built around an Aeron-based sequencer.

Inbound commands are funnelled through a sequencer that assigns them a single
total order and publishes the resulting event stream over Aeron. Downstream
services consume that stream to rebuild state deterministically, so every
component sees the same events in the same order.

## Status

Early stage — no implementation yet. The overview above states intent, and will
be kept identical to the code as components land.
