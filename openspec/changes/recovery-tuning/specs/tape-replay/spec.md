# tape-replay — delta

## ADDED Requirements

### Requirement: Recovery tuning claims are matrix-measured before promotion

Any tuning knob proposed for the cluster recovery path SHALL first be measured in a warm,
RAM-resident recovery matrix — at least two runs per condition, mirrored run order so
extraction drift cannot bias a condition, every run's replay report counting the full tape
— and SHALL be promoted into node configuration or scripts only when it clears ~10%
reproducibly. An outcome below that bar SHALL be recorded as a negative in the change's
measurements and in the parked-lever entry, not silently dropped.

#### Scenario: A knob that does not clear the bar

- **GIVEN** the warm fat-recovery matrix over defaults, a raised log fragment limit, the
  low-latency profile, and their combination
- **WHEN** no condition improves on defaults by ~10% reproducibly
- **THEN** no knob is promoted, and the measured matrix with its verdict is recorded in
  `measurements.md` and reflected in `ideas/fat-message-levers.md`
