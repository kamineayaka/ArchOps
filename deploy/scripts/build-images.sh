#!/usr/bin/env bash
# 发布方：从源码构建 ArchOps backend/frontend 镜像并打上交付 tag。
# 使用方不跑此脚本，只 docker compose up -d（或离线 load）。
#
# 用法（在仓库根目录）：
#   bash deploy/scripts/build-images.sh
#   ARCHOPS_VERSION=v1.0.0 bash deploy/scripts/build-images.sh
#   ARCHOPS_IMAGE_PREFIX=ghcr.io/myorg/archops bash deploy/scripts/build-images.sh
#   MAVEN_MIRROR=https://maven.aliyun.com/repository/public \
#     NPM_REGISTRY=https://registry.npmmirror.com bash deploy/scripts/build-images.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_DIR="$ROOT/deploy/compose"

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

MAVEN_MIRROR="${MAVEN_MIRROR:-}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmjs.org}"

echo "==> Building images:"
echo "    $BACKEND"
echo "    $FRONTEND"

docker build \
  --build-arg "MAVEN_MIRROR=${MAVEN_MIRROR}" \
  -t "$BACKEND" \
  "$ROOT/backend"

docker build \
  --build-arg "NPM_REGISTRY=${NPM_REGISTRY}" \
  -t "$FRONTEND" \
  "$ROOT/frontend"

echo "==> Done. Next:"
echo "    bash deploy/scripts/push-images.sh"
echo "    # or offline: bash deploy/scripts/package-offline.sh"
