#!/usr/bin/env bash
# Sync ArchOps to a remote host and start Docker Compose (lowmem overlay by default).
# Usage:
#   ./deploy/scripts/remote-deploy.sh root@HOST
#   LOWMEM=0 ./deploy/scripts/remote-deploy.sh root@HOST   # full resources
#   SKIP_BUILD=1 ./deploy/scripts/remote-deploy.sh root@HOST  # use images already on host
#   PREBUILT=1 ./deploy/scripts/remote-deploy.sh root@HOST    # use local JAR/dist
#
# China / slow networks (strongly recommended on Aliyun ECS etc.):
#   USE_CN_MIRRORS=1 ./deploy/scripts/remote-deploy.sh root@HOST
#
# Requires SSH key auth (BatchMode). First time: ssh-copy-id user@HOST
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET="${1:?usage: $0 user@host}"
REMOTE_DIR="${REMOTE_DIR:-/opt/archops}"
RELEASES_DIR="${RELEASES_DIR:-/opt/archops-releases}"
LOWMEM="${LOWMEM:-1}"
SKIP_BUILD="${SKIP_BUILD:-0}"
LOAD_IMAGES="${LOAD_IMAGES:-0}"
PREBUILT="${PREBUILT:-0}"
USE_CN_MIRRORS="${USE_CN_MIRRORS:-0}"
NPM_REGISTRY="${NPM_REGISTRY:-}"
MAVEN_MIRROR="${MAVEN_MIRROR:-}"

if [ "$USE_CN_MIRRORS" = "1" ]; then
  NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
  MAVEN_MIRROR="${MAVEN_MIRROR:-https://maven.aliyun.com/repository/public}"
fi

SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$TARGET")
RSYNC_EXCLUDES=(
  --exclude '.git/'
  --exclude 'node_modules/'
  --exclude '.cursor/'
  --exclude '*.log'
  --exclude '.env'
  --exclude 'deploy/compose/.env'
  --exclude 'VERSION'
)
if [ "$PREBUILT" != "1" ]; then
  RSYNC_EXCLUDES+=(--exclude 'frontend/dist/' --exclude 'backend/target/')
fi

RSYNC=(rsync -az --delete -e "ssh -o BatchMode=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=120 -o Compression=yes" "${RSYNC_EXCLUDES[@]}")

echo "==> Syncing sources → ${TARGET}:${REMOTE_DIR} (PREBUILT=$PREBUILT USE_CN_MIRRORS=$USE_CN_MIRRORS)"
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

DEPLOY_VERSION="$(git -C "$ROOT" describe --tags --always --dirty 2>/dev/null || date -u +%Y%m%dT%H%M%SZ)"
echo "==> Writing deploy stamp ${DEPLOY_VERSION} → ${RELEASES_DIR}/VERSION"
"${SSH[@]}" "mkdir -p '$RELEASES_DIR' && printf '%s\n' '$DEPLOY_VERSION' > '$RELEASES_DIR/VERSION' && ln -sfn '$RELEASES_DIR/VERSION' '$REMOTE_DIR/VERSION'"

if [ "$SKIP_BUILD" != "1" ] && [ -z "$NPM_REGISTRY" ] && [ -z "$MAVEN_MIRROR" ]; then
  echo "WARN: no npm/Maven mirrors set. On China ECS, dependency downloads alone can take 30–60+ min."
  echo "      Re-run with USE_CN_MIRRORS=1 for npmmirror + Aliyun Maven."
fi

echo "==> Starting stack on remote (LOWMEM=$LOWMEM SKIP_BUILD=$SKIP_BUILD PREBUILT=$PREBUILT)"
"${SSH[@]}" "bash -s" -- "$REMOTE_DIR" "$LOWMEM" "$SKIP_BUILD" "$PREBUILT" "$NPM_REGISTRY" "$MAVEN_MIRROR" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
LOWMEM="$2"
SKIP_BUILD="$3"
PREBUILT="$4"
NPM_REGISTRY="$5"
MAVEN_MIRROR="$6"

cd "$REMOTE_DIR/deploy/compose"
if [ ! -f .env ]; then
  cp .env.example .env
  HOST_IP=$(curl -fsS --max-time 3 ifconfig.me || hostname -I | awk '{print $1}')
  sed -i "s|CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=http://${HOST_IP},http://localhost|" .env
fi

FILES=(-p archops -f compose.yaml)
[ "$PREBUILT" = "1" ] && FILES+=(-f compose.prebuilt.yaml)
[ "$LOWMEM" = "1" ] && FILES+=(-f compose.lowmem.yaml)

if [ "$SKIP_BUILD" != "1" ]; then
  export LOWMEM PREBUILT NPM_REGISTRY MAVEN_MIRROR
  bash "$REMOTE_DIR/deploy/scripts/compose-build.sh"
fi

docker compose "${FILES[@]}" --env-file .env up -d
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

echo "==> Deploy finished (version $DEPLOY_VERSION)"
