# Tasks — pipelined-recording

- [ ] 1. Spec: proposal, design, cluster-gateway delta; `openspec validate pipelined-recording`
- [ ] 2. Failing tests: `SendWindowTest` (boundaries, FIFO RTT matching, ack-without-send),
      windowed ack/print path in `FatHeartbeatRoundTripTest`
- [ ] 3. Implement `SendWindow`; rework `FatHeartbeatRoundTrip.run` to the windowed loop;
      wire `oms.gateway.window` in `GatewayMain`
- [ ] 4. Record 100k closed-loop and 100k windowed same-day; both recovery-verified;
      record rates in `measurements.md`
- [ ] 5. `bazel test //...` green; format + lint; PR
