#!/usr/bin/env bash
# Machine-layer CPU isolation for latency measurement (openspec change: core-isolation).
#
#   scripts/isolation.sh check   verify the recorded layout; exit nonzero naming missing layers
#   scripts/isolation.sh apply   (root) set the per-boot parts: IRQ affinity, workqueue mask,
#                                governor. The GRUB edit below is a user action, never scripted.
#
# Recorded layout (design.md, user-confirmed 2026-08-25, Intel Core Ultra 7 255HX, 20 CPUs):
#   isolated cores : 4,6           (the favored 5300 MHz P-cores)
#   housekeeping   : 0-3,5,7-19    (hex mask fffaf)
#   GRUB_CMDLINE_LINUX_DEFAULT += "isolcpus=domain,managed_irq,4,6 nohz_full=4,6 rcu_nocbs=4,6 nmi_watchdog=0"
#   then: sudo update-grub && reboot. Revert by removing the parameters and repeating.
#
# Reference measured invocation (launch layer + runtime layer):
#   taskset -c 0-3,5,7-19 bazel-bin/cluster-node/tape-replay <archive> <manifest> - \
#       --warmup <warmup-archive> --latency --pin 4
# taskset starts every JVM thread (GC, JIT, VM) on the housekeeping set; the applying thread
# then moves itself onto CPU 4 via --pin. taskset, never a cpuset cgroup — a cgroup makes
# sched_setaffinity fail with EINVAL and the runtime layer stops working.
set -euo pipefail

ISOLATED_LIST="4,6"
HOUSEKEEPING_LIST="0-3,5,7-19"
HOUSEKEEPING_MASK=$((0xfffaf))
ISOLATED_RE='(^|[,-])(4|6)($|[,-])'   # matches 4 or 6 inside an smp_affinity_list value
# Legacy timer/cascade IRQs whose affinity the kernel refuses to change.
UNMOVABLE_IRQS="0 2"

mode=${1:?usage: isolation.sh check|apply}
fail=0

miss() { echo "MISSING  $1"; fail=1; }
ok()   { echo "ok       $1"; }

check_kernel() {
  local cmdline param
  cmdline=$(cat /proc/cmdline)
  for param in "isolcpus=domain,managed_irq,$ISOLATED_LIST" "nohz_full=$ISOLATED_LIST" \
               "rcu_nocbs=$ISOLATED_LIST" "nmi_watchdog=0"; do
    if [[ " $cmdline " == *" $param "* ]]; then ok "kernel: $param"; else miss "kernel: $param"; fi
  done
  if [[ "$(cat /sys/devices/system/cpu/isolated)" == "$ISOLATED_LIST" ]]; then
    ok "kernel: /sys/devices/system/cpu/isolated = $ISOLATED_LIST"
  else
    miss "kernel: /sys/devices/system/cpu/isolated is '$(cat /sys/devices/system/cpu/isolated)'"
  fi
  if grep -q '^CONFIG_NO_HZ_FULL=y' "/boot/config-$(uname -r)" 2>/dev/null; then
    ok "kernel: CONFIG_NO_HZ_FULL=y"
  else
    miss "kernel: CONFIG_NO_HZ_FULL not set in /boot/config-$(uname -r)"
  fi
}

check_irqs() {
  local irq name offenders=""
  for irq in /proc/irq/[0-9]*; do
    name=${irq##*/}
    [[ " $UNMOVABLE_IRQS " == *" $name "* ]] && continue
    if grep -Eq "$ISOLATED_RE" "$irq/smp_affinity_list" 2>/dev/null; then
      offenders+=" $name"
    fi
  done
  if [[ -z "$offenders" ]]; then
    ok "interrupts: no IRQ targets an isolated core"
  else
    miss "interrupts: IRQs still targeting $ISOLATED_LIST:$offenders"
  fi
}

check_workqueue() {
  local mask
  mask=$(tr -d ',\n' < /sys/devices/virtual/workqueue/cpumask)
  if [[ $((16#$mask)) -eq $HOUSEKEEPING_MASK ]]; then
    ok "workqueue: cpumask = $HOUSEKEEPING_LIST"
  else
    miss "workqueue: cpumask is 0x$mask, want $(printf '0x%x' "$HOUSEKEEPING_MASK")"
  fi
}

check_governor() {
  local cpu gov
  for cpu in 4 6; do
    gov=$(cat "/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor")
    if [[ "$gov" == "performance" ]]; then
      ok "governor: cpu$cpu = performance"
    else
      miss "governor: cpu$cpu is $gov"
    fi
  done
}

apply() {
  [[ $EUID -eq 0 ]] || { echo "apply needs root" >&2; exit 1; }
  local irq moved=0 stuck=0
  printf '%x' "$HOUSEKEEPING_MASK" > /proc/irq/default_smp_affinity
  for irq in /proc/irq/[0-9]*; do
    if echo "$HOUSEKEEPING_LIST" > "$irq/smp_affinity_list" 2>/dev/null; then
      moved=$((moved + 1))
    else
      stuck=$((stuck + 1))
    fi
  done
  echo "interrupts: $moved IRQs moved to $HOUSEKEEPING_LIST, $stuck refused (managed/unmovable)"
  printf '%x' "$HOUSEKEEPING_MASK" > /sys/devices/virtual/workqueue/cpumask
  echo "workqueue: cpumask set"
  for cpu in 4 6; do
    echo performance > "/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor"
  done
  echo "governor: performance on cpus $ISOLATED_LIST"
}

case "$mode" in
  check)
    check_kernel
    check_irqs
    check_workqueue
    check_governor
    exit "$fail"
    ;;
  apply)
    apply
    ;;
  *)
    echo "usage: isolation.sh check|apply" >&2
    exit 1
    ;;
esac
