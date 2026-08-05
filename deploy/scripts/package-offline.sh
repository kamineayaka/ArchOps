#!/usr/bin/env bash
# 打包离线交付物：backend/frontend + 依赖官方镜像 → 单个 .tar
#
# 用法：
#   bash deploy/scripts/build-images.sh
#   bash deploy/scripts/package-offline.sh
#   OUT=archops-offline-v1.0.0.tar bash deploy/scripts/package-offline.sh
#
# 交付给对方：生成的 .tar + deploy/compose/{compose.yaml,.env.example}
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
OUT="${OUT:-$ROOT/archops-images-${VERSION}.tar}"

IMAGES=(
  "${PREFIX}/backend:${VERSION}"
  "${PREFIX}/frontend:${VERSION}"
  "pgvector/pgvector:pg16"
  "redis:7-alpine"
  "neo4j:5.26-community"
)

echo "==> Ensuring dependency images are present locally..."
for img in "${IMAGES[@]}"; do
  if ! docker image inspect "$img" >/dev/null 2>&1; then
    echo "    pulling $img"
    docker pull "$img"
  else
    echo "    ok $img"
  fi
done

echo "==> Saving to $OUT"
docker save -o "$OUT" "${IMAGES[@]}"

echo "==> Done. Ship to recipient:"
echo "    $OUT"
echo "    deploy/compose/compose.yaml"
echo "    deploy/compose/.env.example"
echo "Recipient:"
echo "    bash deploy/scripts/load-offline.sh $OUT"
echo "    cd deploy/compose && cp .env.example .env && docker compose up -d"
