#!/usr/bin/env bash
# 从源码构建 ArchOps backend/frontend 镜像并打上交付 tag。
#
# 用法（在仓库根目录）：
#   bash deploy/scripts/build-images.sh
#   ARCHOPS_VERSION=v1.0.0 bash deploy/scripts/build-images.sh
#   ARCHOPS_IMAGE_PREFIX=ghcr.io/myorg/archops bash deploy/scripts/build-images.sh
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

export ARCHOPS_IMAGE_PREFIX="${ARCHOPS_IMAGE_PREFIX:-ghcr.io/kamineayaka/archops}"
export ARCHOPS_VERSION="${ARCHOPS_VERSION:-latest}"

echo "==> Building images:"
echo "    ${ARCHOPS_IMAGE_PREFIX}/backend:${ARCHOPS_VERSION}"
echo "    ${ARCHOPS_IMAGE_PREFIX}/frontend:${ARCHOPS_VERSION}"

cd "$COMPOSE_DIR"
docker compose -f compose.yaml -f compose.build.yaml build "$@"

echo "==> Done. Next:"
echo "    bash deploy/scripts/push-images.sh"
echo "    # or offline: bash deploy/scripts/package-offline.sh"
