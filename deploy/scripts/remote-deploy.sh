#!/usr/bin/env bash
# Sync ArchOps to a remote host and start Docker Compose (lowmem overlay by default).
# Usage:
#   ./deploy/scripts/remote-deploy.sh root@HOST
#   LOWMEM=0 ./deploy/scripts/remote-deploy.sh root@HOST   # full resources
#   SKIP_BUILD=1 ./deploy/scripts/remote-deploy.sh root@HOST  # use images already on host
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET="${1:?usage: $0 user@host}"
REMOTE_DIR="${REMOTE_DIR:-/opt/archops}"
LOWMEM="${LOWMEM:-1}"
SKIP_BUILD="${SKIP_BUILD:-0}"
LOAD_IMAGES="${LOAD_IMAGES:-0}"
PREBUILT="${PREBUILT:-0}"

SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$TARGET")
RSYNC_EXCLUDES=(
  --exclude '.git/'
  --exclude 'node_modules/'
  --exclude '.cursor/'
  --exclude '*.log'
  --exclude '.env'
  --exclude 'deploy/compose/.env'
)
if [ "$PREBUILT" != "1" ]; then
  RSYNC_EXCLUDES+=(--exclude 'frontend/dist/' --exclude 'backend/target/')
fi

# Prefer OpenSSH keepalive on slow links (e.g. cross-region VPS).
RSYNC=(rsync -az --delete -e "ssh -o BatchMode=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=120 -o Compression=yes" "${RSYNC_EXCLUDES[@]}")

echo "==> Syncing sources → ${TARGET}:${REMOTE_DIR} (PREBUILT=$PREBUILT)"
"${RSYNC[@]}" "$ROOT/" "${TARGET}:${REMOTE_DIR}/"

if [ ! -f "$ROOT/deploy/compose/.env" ]; then
  echo "WARN: local deploy/compose/.env missing; remote .env will be created from example if absent"
fi

if [ -f "$ROOT/deploy/compose/.env" ]; then
  scp -o BatchMode=yes "$ROOT/deploy/compose/.env" "${TARGET}:${REMOTE_DIR}/deploy/compose/.env"
fi

if [ "$LOAD_IMAGES" = "1" ]; then
  ARCHIVE="${IMAGE_ARCHIVE:-/tmp/archops-images.tar.gz}"
  echo "==> Loading prebuilt images from $ARCHIVE"
  scp -o BatchMode=yes "$ARCHIVE" "${TARGET}:/tmp/archops-images.tar.gz"
  "${SSH[@]}" 'docker load -i /tmp/archops-images.tar.gz && rm -f /tmp/archops-images.tar.gz'
fi

echo "==> Starting stack on remote (LOWMEM=$LOWMEM SKIP_BUILD=$SKIP_BUILD PREBUILT=$PREBUILT)"
"${SSH[@]}" "bash -s" -- "$REMOTE_DIR" "$LOWMEM" "$SKIP_BUILD" "$PREBUILT" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
LOWMEM="$2"
SKIP_BUILD="$3"
PREBUILT="$4"
cd "$REMOTE_DIR/deploy/compose"
if [ ! -f .env ]; then
  cp .env.example .env
  HOST_IP=$(curl -fsS --max-time 3 ifconfig.me || hostname -I | awk '{print $1}')
  sed -i "s|CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=http://${HOST_IP},http://localhost|" .env
fi
FILES=(-p archops -f compose.yaml)
[ "$PREBUILT" = "1" ] && FILES+=(-f compose.prebuilt.yaml)
[ "$LOWMEM" = "1" ] && FILES+=(-f compose.lowmem.yaml)
if [ "$SKIP_BUILD" = "1" ]; then
  docker compose "${FILES[@]}" --env-file .env up -d
else
  docker compose "${FILES[@]}" --env-file .env up -d --build
fi
docker compose "${FILES[@]}" ps
echo "Health:"
for i in $(seq 1 40); do
  if curl -fsS http://127.0.0.1/actuator/health >/dev/null 2>&1; then
    curl -fsS http://127.0.0.1/actuator/health || true
    echo
    exit 0
  fi
  sleep 5
done
echo "WARN: health check did not pass within timeout; check: docker compose logs"
docker compose "${FILES[@]}" logs --tail=80 backend || true
exit 1
REMOTE

echo "==> Deploy finished"
