#!/usr/bin/env bash
# 将已构建的 ArchOps backend/frontend 镜像推送到镜像仓库。
#
# 用法：
#   docker login ghcr.io   # 或你的私有仓库
#   bash deploy/scripts/push-images.sh
#   ARCHOPS_VERSION=v1.0.0 bash deploy/scripts/push-images.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_DIR="$ROOT/deploy/compose"

# shellcheck disable=SC1091
if [[ -f "$COMPOSE_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$COMPOSE_DIR/.env"
  set +a
fi

PREFIX="${ARCHOPS_IMAGE_PREFIX:-ghcr.io/kamineayaka/archops}"
VERSION="${ARCHOPS_VERSION:-latest}"

BACKEND="${PREFIX}/backend:${VERSION}"
FRONTEND="${PREFIX}/frontend:${VERSION}"

echo "==> Pushing:"
echo "    $BACKEND"
echo "    $FRONTEND"

docker push "$BACKEND"
docker push "$FRONTEND"

echo "==> Done. Recipients can run (no source tree required):"
echo "    cd deploy/compose && cp .env.example .env && docker compose up -d"
