#!/usr/bin/env bash
# 在目标机加载离线镜像包。
#
# 用法：
#   bash deploy/scripts/load-offline.sh archops-images-latest.tar
#
set -euo pipefail

TAR="${1:-}"
if [[ -z "$TAR" ]]; then
  echo "Usage: $0 <archops-images-*.tar>" >&2
  exit 1
fi
if [[ ! -f "$TAR" ]]; then
  echo "File not found: $TAR" >&2
  exit 1
fi

echo "==> Loading images from $TAR"
docker load -i "$TAR"
echo "==> Done. Next: cd deploy/compose && cp .env.example .env && docker compose up -d"
