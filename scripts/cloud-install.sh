#!/usr/bin/env bash
# Idempotent Cloud Agent Build install (disk warm-up only — no long-running services).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Java"
java -version
echo "==> Node"
node -v
npm -v
echo "==> Python"
python3 --version
python3 -c 'import sys; assert sys.version_info >= (3, 12), sys.version'

echo "==> Matt + ArchOps skills (Cloud Agent discovery)"
ls -1 "$ROOT/.cursor/skills"
test -f "$ROOT/.cursor/skills/to-spec/SKILL.md"
test -f "$ROOT/.cursor/skills/to-tickets/SKILL.md"
test -f "$ROOT/.cursor/skills/setup-matt-pocock-skills/SKILL.md"
test -f "$ROOT/docs/agents/issue-tracker.md"

echo "==> Warm Gradle dependencies / compile (skip tests)"
cd "$ROOT/backend"
chmod +x gradlew || true
./gradlew --no-daemon classes testClasses -x test

echo "==> npm ci (frontend)"
cd "$ROOT/frontend"
if [[ -f package-lock.json ]]; then
  npm ci --no-audit --no-fund
else
  npm install --no-audit --no-fund
fi

echo "==> cloud-install done"
