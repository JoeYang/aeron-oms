# Tasks — pipelined-recording

- [x] 1. Spec: proposal, design, cluster-gateway delta; `openspec validate pipelined-recording`
- [x] 2. Failing tests: `SendWindowTest` (boundaries, FIFO RTT matching, ack-without-send),
      windowed ack/print path in `FatHeartbeatRoundTripTest`
- [x] 3. Implement `SendWindow`; rework `FatHeartbeatRoundTrip.run` to the windowed loop;
      wire `oms.gateway.window` in `GatewayMain`
- [x] 4. Record 100k closed-loop and 100k windowed same-day; both recovery-verified;
      record rates in `measurements.md`
- [x] 5. `bazel test //...` green; format + lint; PR
