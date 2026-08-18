# Podman compose for a local cluster

**What** — one command that brings up a working cluster locally: a media driver, the
cluster nodes, and a gateway. Then the system can be run and poked at without a manual
multi-terminal startup dance.

**Why** — a three-node Aeron Cluster means three JVMs with distinct member ids, ports,
archive directories, and a start order. Doing that by hand every time is the kind of
friction that stops a system from being exercised at all.

## Blocked by

There is no Aeron dependency yet and no `ClusteredService`. Every package holds a
hello-world placeholder. A compose file today would orchestrate nothing.

Build this after the first real cluster node exists.

## Traps

### 1. `--cpuset-cpus` breaks the CPU pinning design

This is the one that matters. `.claude/rules/trading-latency.md` requires each critical
thread to pin itself at runtime through the FFM `sched_setaffinity` call, and states the
constraint directly: a cpuset cgroup is a kernel-enforced wall, and `sched_setaffinity`
fails with `EINVAL` outside it.

Podman's `--cpuset-cpus` creates exactly that cgroup. Set it to the housekeeping cores and
every runtime pin fails. If a cpuset is needed for other reasons, it must contain both the
housekeeping and the isolated cores.

The failure mode is the dangerous kind: a cluster that looks healthy, starts cleanly, and
quietly misses every latency budget.

### 2. The default `/dev/shm` is far too small

Aeron keeps its CnC file and log buffers in shared memory. Containers default to 64 MB of
`/dev/shm`. An Aeron log buffer is three terms plus metadata, and the IPC term length
defaults to 64 MB — so a single IPC publication wants roughly 192 MB, three times the
whole container default.

Needs `--shm-size`, or the aeron directory mounted from the host. Aeron Cluster uses IPC
between the consensus module and the service container, so the IPC defaults are the ones
that apply, not the smaller network ones.

### 3. Components sharing a media driver must share the aeron directory

The `ConsensusModule` and `ClusteredServiceContainer` talk over Aeron IPC, which means the
same aeron directory. Either co-locate them in one container or share a volume. Decide
deliberately: it changes what a single container failure takes down.

### 4. Rootless networking is not root networking

Aeron is UDP-heavy. Rootless podman routes through pasta or slirp4netns, whose UDP
behaviour and buffer limits differ from root networking. Verify rather than assume.

## Open question — which tool

Not `podman-compose` by reflex. Three candidates, checked on this machine (podman 4.9.3):

| Option | Notes |
|---|---|
| `podman compose` | Shells out to `/snap/bin/docker-compose` here. Standard compose semantics, external dependency. |
| `podman-compose` | Installed at `/usr/bin/podman-compose`. Third-party Python reimplementation; drifts from the compose spec. |
| Quadlet | Native to podman 4.4+. systemd units, no compose file. Strongest ordering and restart control, least familiar. |

Start order is a real requirement — a cluster node that starts before the media driver
fails. Compose `depends_on` waits for container start, not readiness, so it does not
actually solve this without healthchecks. That argues for Quadlet.

## Not a latency tool

Any number measured inside this stack is a functional smoke test, not a latency result.
Latency numbers need the machine, JVM version, and flags recorded alongside them, per the
measurement rules. Do not let a convenient local cluster become the place benchmarks get
run.
