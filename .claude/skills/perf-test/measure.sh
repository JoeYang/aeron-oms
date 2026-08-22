#!/usr/bin/env bash
# One measurement arm under the fixed protocol. Run it once per (arm, repetition).
#
#   measure.sh <workdir> <label> <focus> [node-flags] [gw-flags]
#
#   workdir     repo checkout to measure — the tree under test, never a parked branch
#   label       short arm name: baseline, tuned, my-change
#   focus       latency | throughput | gc | jit
#   node-flags  extra "--jvm_flag=-D..." strings for the node, space-separated
#   gw-flags    same, for the gateway
#
# Environment overrides: OUT_BASE (default /tmp/oms-perf), PORT (default 22102),
# COUNT, WARMUP. The defaults are the protocol; override them only with a reason,
# and then for every arm equally.
set -euo pipefail

WORKDIR=$1; LABEL=$2; FOCUS=$3
NODE_FLAGS=${4:-}; GW_FLAGS=${5:-}
PORT=${PORT:-22102}

case $FOCUS in
  latency | gc | jit) COUNT=${COUNT:-3000}; WARMUP=${WARMUP:-2000} ;;
  throughput) COUNT=${COUNT:-50000}; WARMUP=${WARMUP:-10000} ;;
  *) echo "usage: measure.sh <workdir> <label> latency|throughput|gc|jit" >&2; exit 2 ;;
esac

OUT="${OUT_BASE:-/tmp/oms-perf}/$LABEL-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
DATA="$OUT/data"   # fresh journal per run; never delete a journal in place

case $FOCUS in
  gc)
    NODE_FLAGS="$NODE_FLAGS --jvm_flag=-Xlog:gc*:file=$OUT/node-gc.log"
    GW_FLAGS="$GW_FLAGS --jvm_flag=-Xlog:gc*:file=$OUT/gateway-gc.log" ;;
  jit)
    # File output, not -XX:+PrintCompilation: compiler threads writing to stdout
    # interleave with and corrupt the gateway's measurement lines.
    NODE_FLAGS="$NODE_FLAGS --jvm_flag=-Xlog:jit+compilation=debug:file=$OUT/node-jit.log"
    GW_FLAGS="$GW_FLAGS --jvm_flag=-Xlog:jit+compilation=debug:file=$OUT/gateway-jit.log" ;;
esac

cd "$WORKDIR"

# A number without its machine, tree, and flags means nothing (trading-latency.md).
{
  date -Is; git rev-parse HEAD; git status --short | head -20; uname -r
  lscpu | grep -E 'Model name|^CPU\(s\)'
  cat /proc/cmdline
  sort -u /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2>/dev/null
  echo "no_turbo=$(cat /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null || echo n/a)"
  echo "focus=$FOCUS count=$COUNT warmup=$WARMUP port=$PORT"
  echo "node-flags=$NODE_FLAGS"
  echo "gw-flags=$GW_FLAGS"
} > "$OUT/meta.txt" 2>&1

# Build outside the measurement, then exec the binaries directly — `bazel run`
# holds the workspace lock for the process lifetime and would serialize the arms.
bazel build //cluster-node:cluster-node //gateway:gateway > "$OUT/build.log" 2>&1

# shellcheck disable=SC2086
bazel-bin/cluster-node/cluster-node \
  "--jvm_flag=-Doms.data.dir=$DATA" --jvm_flag=-Doms.cluster.clean=true \
  "--jvm_flag=-Doms.cluster.port=$PORT" \
  $NODE_FLAGS > "$OUT/node.log" 2>&1 &
NODE_PID=$!
stop_node() {
  kill "$NODE_PID" 2>/dev/null || true
  for _ in 1 2 3 4 5; do kill -0 "$NODE_PID" 2>/dev/null || return 0; sleep 1; done
  kill -9 "$NODE_PID" 2>/dev/null || true
}
trap stop_node EXIT

for _ in $(seq 1 150); do
  grep -q "cluster-node up" "$OUT/node.log" && break
  sleep 0.2
done
grep -q "cluster-node up" "$OUT/node.log" \
  || { echo "FAIL: node did not start"; tail -5 "$OUT/node.log"; exit 1; }

# shellcheck disable=SC2086
bazel-bin/gateway/gateway \
  "--jvm_flag=-Doms.gateway.count=$COUNT" --jvm_flag=-Doms.gateway.interval.ms=0 \
  "--jvm_flag=-Doms.cluster.port=$PORT" \
  $GW_FLAGS > "$OUT/gateway.log" 2>&1

stop_node
trap - EXIT

python3 - "$OUT" "$LABEL" "$FOCUS" "$COUNT" "$WARMUP" <<'EOF'
import re, sys
out, label, focus, count, warmup = sys.argv[1:6]
count, warmup = int(count), int(warmup)
text = open(f"{out}/gateway.log").read()
lines = re.findall(r'sequenced=(\d+) ns .* rtt=([0-9.]+) us', text)
if len(lines) < count:
    print(f"FAIL: only {len(lines)}/{count} samples — see {out}/gateway.log")
    sys.exit(1)
window = lines[warmup:]
rtts = sorted(float(r) for _, r in window)
def pct(p): return rtts[min(len(rtts) - 1, int(p / 100 * len(rtts)))]
print(f"RESULT {label}: n={len(rtts)} min={rtts[0]:.1f} p50={pct(50):.1f} "
      f"p90={pct(90):.1f} p99={pct(99):.1f} max={rtts[-1]:.1f} (us)")
if focus == "throughput":
    span_s = (int(window[-1][0]) - int(window[0][0])) / 1e9
    print(f"RATE {label}: {len(window) - 1} round trips in {span_s:.3f} s "
          f"= {(len(window) - 1) / span_s:,.0f} msg/s (closed-loop: rate == 1/RTT)")
elif focus == "gc":
    for side in ("node", "gateway"):
        try:
            pauses = re.findall(r'\[([0-9.]+)s\].*Pause', open(f"{out}/{side}-gc.log").read())
        except FileNotFoundError:
            pauses = None
        if pauses is None:
            print(f"GC {label} {side}: no gc log written")
        else:
            last = f", last at {pauses[-1]}s uptime" if pauses else ""
            print(f"GC {label} {side}: {len(pauses)} pauses{last}")
elif focus == "jit":
    for side in ("node", "gateway"):
        comps = re.findall(r'\[([0-9.]+)s\]', open(f"{out}/{side}-jit.log").read())
        last = f", last at {comps[-1]}s VM uptime" if comps else ""
        print(f"JIT {label} {side}: {len(comps)} compile events{last}")
print(f"logs: {out}")
EOF
