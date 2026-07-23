#!/usr/bin/env bash
# BUG-02 regression: SPA routes like /assets must not use try_files $uri $uri/
# which collides with Vite's hashed /assets/* static directory (nginx 403).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fail=0
for f in "$ROOT/frontend/nginx.conf" "$ROOT/deploy/compose/nginx.conf"; do
  if grep -nE 'try_files \$uri \$uri/' "$f" >/dev/null; then
    echo "FAIL: $f still uses try_files \$uri \$uri/ (BUG-02)"
    fail=1
  fi
  if ! grep -nE 'try_files \$uri /index.html;' "$f" >/dev/null; then
    echo "FAIL: $f missing SPA try_files \$uri /index.html;"
    fail=1
  fi
  echo "OK: $f"
done
exit "$fail"
