# golden-tape (delta)

## ADDED Requirements

### Requirement: Recording supports profile flags and optional goldens

The recording script SHALL pass `NODE_FLAGS` and `GW_FLAGS` environment values
through to the node and gateway, SHALL always supply the gateway with the recording
data directory so IPC ingress works, and with `SKIP_GOLDENS=1` SHALL omit the
golden-outputs file while still verifying the recorded message count and noting the
omission in the manifest.

#### Scenario: Record a tuned tape without goldens

- **WHEN** the script runs with tuned NODE_FLAGS/GW_FLAGS and SKIP_GOLDENS=1
- **THEN** it records, count-verifies, recovery-verifies, and writes a manifest that
  records the skipped goldens — with no golden-outputs file
