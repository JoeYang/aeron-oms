#!/usr/bin/env bash
#
# Read-only quality check. Never modifies files.
#
#   scripts/lint.sh
#
# Two gates:
#   1. formatting matches google-java-format
#   2. Checkstyle (Google style) reports no violations
#
# Exits non-zero if either fails, so this is safe to gate a PR on.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
readonly ROOT="${PWD}"

if ! command -v bazel >/dev/null 2>&1; then
  echo "error: bazel not found on PATH" >&2
  exit 127
fi

mapfile -t sources < <(find . -name '*.java' -not -path './bazel-*' -printf "${ROOT}/%P\n")

if [ "${#sources[@]}" -eq 0 ]; then
  echo "no Java sources found"
  exit 0
fi

readonly BAZEL_QUIET=(--ui_event_filters=-info,-debug --noshow_progress)
status=0

echo "== formatting =="
if bazel run "${BAZEL_QUIET[@]}" //tools:google-java-format -- \
  --dry-run --set-exit-if-changed "${sources[@]}"; then
  echo "ok: all files match google-java-format"
else
  echo "FAIL: files above are not formatted. Run scripts/format.sh" >&2
  status=1
fi

echo
echo "== checkstyle =="
if bazel run "${BAZEL_QUIET[@]}" //tools:checkstyle -- \
  -c /google_checks.xml "${sources[@]}"; then
  echo "ok: no Checkstyle violations"
else
  echo "FAIL: Checkstyle reported violations" >&2
  status=1
fi

exit "${status}"
