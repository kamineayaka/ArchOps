#!/usr/bin/env bash
# Wrapper: run functional smoke against a host (default http://127.0.0.1).
# Examples:
#   bash deploy/scripts/smoke-functional.sh
#   ARCHOPS_BASE_URL=http://10.0.0.2 bash deploy/scripts/smoke-functional.sh
#   bash deploy/scripts/smoke-functional.sh http://127.0.0.1
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ $# -ge 1 ]]; then
  export ARCHOPS_BASE_URL="$1"
fi
export ARCHOPS_BASE_URL="${ARCHOPS_BASE_URL:-http://127.0.0.1}"
exec node "$ROOT/deploy/scripts/smoke-functional.mjs"
