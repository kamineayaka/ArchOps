#!/usr/bin/env bash
# Cloud Agent session start: Docker daemon + Postgres/Redis via Compose.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Some Cloud VMs leave /var/run as 0700 (or dockerd creates a private dir).
# Without traverse perms, docker-group users cannot reach docker.sock.
ensure_docker_sock_reachable() {
  if [[ -e /var/run/docker.sock ]] || [[ -d /var/run ]]; then
    sudo chmod 755 /var/run 2>/dev/null || true
    sudo chmod 755 /var/run/docker 2>/dev/null || true
  fi
}

docker_ready() {
  ensure_docker_sock_reachable
  docker info >/dev/null 2>&1
}

wait_for_docker() {
  local seconds="${1:-60}"
  local _
  for _ in $(seq 1 "${seconds}"); do
    if docker_ready; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# Restart a hung dockerd (stale socket / pid file) without `pkill -f`.
restart_hung_dockerd() {
  local pid
  while read -r pid; do
    [[ -n "${pid}" ]] || continue
    sudo kill "${pid}" 2>/dev/null || true
  done < <(ps -eo pid,cmd | awk '$2 == "/usr/bin/dockerd" || $2 == "dockerd" { print $1 }')
  sleep 1
  while read -r pid; do
    [[ -n "${pid}" ]] || continue
    if [[ -d "/proc/${pid}" ]]; then
      sudo kill -9 "${pid}" 2>/dev/null || true
    fi
  done < <(ps -eo pid,cmd | awk '$2 == "/usr/bin/dockerd" || $2 == "dockerd" { print $1 }')
  sudo rm -f /var/run/docker.pid
}

echo "==> Starting Docker daemon"
if docker_ready; then
  echo "==> docker already ready"
else
  if command -v service >/dev/null 2>&1; then
    sudo service docker start || true
  fi
  if ! wait_for_docker 15; then
    echo "==> docker not serving; replacing hung daemon if present"
    restart_hung_dockerd
    sudo dockerd >/tmp/dockerd.log 2>&1 &
    if ! wait_for_docker 60; then
      echo "ERROR: docker did not become ready" >&2
      tail -n 40 /tmp/dockerd.log >&2 || true
      exit 1
    fi
  fi
fi

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
