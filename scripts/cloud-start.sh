#!/usr/bin/env bash
# Cloud Agent session start: Docker daemon + Postgres/Redis via Compose.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Starting Docker daemon"
if ! docker info >/dev/null 2>&1; then
  if command -v service >/dev/null 2>&1; then
    sudo service docker start || true
  fi
  if ! docker info >/dev/null 2>&1; then
    sudo dockerd >/tmp/dockerd.log 2>&1 &
    for _ in $(seq 1 30); do
      if docker info >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
  fi
fi

docker info >/dev/null

echo "==> Postgres + Redis (compose)"
docker compose -f deploy/compose/compose.yaml up -d postgres redis

echo "==> Waiting for health"
for _ in $(seq 1 60); do
  pg_ok="$(docker compose -f deploy/compose/compose.yaml ps --status running --services 2>/dev/null | grep -c postgres || true)"
  rd_ok="$(docker compose -f deploy/compose/compose.yaml ps --status running --services 2>/dev/null | grep -c redis || true)"
  if [[ "${pg_ok}" -ge 1 && "${rd_ok}" -ge 1 ]]; then
    if docker compose -f deploy/compose/compose.yaml exec -T postgres pg_isready -U archops -d archops >/dev/null 2>&1 \
      && docker compose -f deploy/compose/compose.yaml exec -T redis redis-cli ping 2>/dev/null | grep -qi PONG; then
      echo "==> postgres/redis ready"
      exit 0
    fi
  fi
  sleep 1
done

echo "WARN: postgres/redis may still be starting; check: docker compose -f deploy/compose/compose.yaml ps" >&2
exit 0
