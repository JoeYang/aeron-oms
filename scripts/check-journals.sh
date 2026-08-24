#!/usr/bin/env bash
# Verify that every golden tape in journal/ still replays byte-for-byte to its recorded
# outputs. Deterministic and judgement-free: this script decides pass/fail; diagnosis of
# a mismatch is a separate, human/agent job. Runs in the CI gate on every PR and from
# the pre-PR hook, and picks up new tapes automatically.
#
#   scripts/check-journals.sh
#
# Env: JOURNAL_DIR overrides the tape directory (used for failure-injection testing);
# BAZEL_ARGS passes extra flags to the build (CI sets --config=ci).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="${JOURNAL_DIR:-$ROOT/journal}"

shopt -s nullglob
TAPES=("$DIR"/*.tar.gz)
if [ ${#TAPES[@]} -eq 0 ]; then
  echo "check-journals: no tapes in $DIR — nothing to verify"
  exit 0
fi

cd "$ROOT"
BUILD_LOG=$(mktemp)
# shellcheck disable=SC2086
bazel build //cluster-node:tape-replay ${BAZEL_ARGS:-} > "$BUILD_LOG" 2>&1 \
  || { echo "check-journals: build failed"; cat "$BUILD_LOG"; exit 1; }

STATUS=0
for tape in "${TAPES[@]}"; do
  name=$(basename "$tape" .tar.gz)
  case "$name" in
    local-*)
      # Machine-only experiment tapes (git-ignored); verify on demand with replay-app.sh.
      echo "skip $name: local tape, not part of the gate"
      continue
      ;;
  esac
  manifest="$DIR/$name.manifest.txt"
  golden="$DIR/$name.golden-outputs.txt"
  if [ ! -f "$manifest" ] || [ ! -f "$golden" ]; then
    echo "FAIL $name: missing manifest or golden-outputs beside the tape"
    STATUS=1
    continue
  fi
  work=$(mktemp -d)
  tar -xzf "$tape" -C "$work"
  if out=$(bazel-bin/cluster-node/tape-replay "$work/archive" "$manifest" "$golden" 2>&1); then
    echo "OK   $name: $(echo "$out" | head -1)"
  else
    echo "FAIL $name:"
    echo "$out" | sed 's/^/     /'
    STATUS=1
  fi
done

if [ $STATUS -ne 0 ]; then
  cat >&2 <<'EOF'

check-journals: a golden tape no longer replays to its recorded outputs.
This is a journal-compatibility regression in the code, not in the tape.
Do NOT update the golden files or re-record the tape to make this pass —
that converts a broken replay path into a green build. Investigate the
divergence (this is the point where Claude earns its keep).
EOF
fi
exit $STATUS
