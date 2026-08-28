# Measurements — recovery tuning

Machine: Intel Core Ultra 7 255HX, 64 GB, kernel 6.17.0-1032-oem, JDK 25. Warm fat
recovery: `local-fatheartbeats-1m` (32.9 GB) RAM-resident on tmpfs (prefault 10.0 GB/s),
same node invocation as `replay-cluster.sh`, one shared extraction, mirrored run order
(A B C D D C B A) so recovery-appended drift cannot bias a condition.

| condition | runs (msg/s) |
|---|---|
| A — defaults | 171.4k / 169.4k |
| B — `aeron.cluster.log.fragment.limit=512` | 168.9k / 173.1k |
| C — `oms.lowlatency` (DEDICATED driver + busy-spin idles) | 169.9k / 171.2k |
| D — B + C combined | **132.6k / 157.3k** |

## Verdict

**No knob is promoted.** B and C sit inside the defaults' run-to-run band (~±2%); the
combination is reproducibly *worse* (−8 to −23%), echoing the thin-tape finding that
DEDICATED threading can hurt recovery — the busy-spinning threads and larger polls appear
to contend rather than help. The 93%-busy archive-conductor is the actual limit, and none
of these knobs change its per-fragment work; closing the remaining ~27% to app-mode would
mean changing what the archive replay path does per fragment, not how often it polls.
This matches the lever's de-prioritized status in `ideas/fat-message-levers.md`, which now
records the measured outcome.
