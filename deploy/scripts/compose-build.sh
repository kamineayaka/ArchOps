#!/usr/bin/env bash
# Build ArchOps Compose images with deploy-friendly defaults.
#
# Usage (from repo root or deploy/compose):
#   ./deploy/scripts/compose-build.sh
#   USE_CN_MIRRORS=1 LOWMEM=1 ./deploy/scripts/compose-build.sh
#   PREBUILT=1 ./deploy/scripts/compose-build.sh
#
# Why this exists:
# - docker compose build (buildx) often ignores dockerd registry-mirrors → prefetch via docker pull
# - npm/Maven official registries are painful on China ECS → USE_CN_MIRRORS / .env build args
# - ≤2GiB hosts OOM if Maven+npm build in parallel → LOWMEM serializes + frees RAM first
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="${COMPOSE_DIR:-$ROOT/deploy/compose}"
LOWMEM="${LOWMEM:-0}"
PREBUILT="${PREBUILT:-0}"
USE_CN_MIRRORS="${USE_CN_MIRRORS:-0}"
NPM_REGISTRY="${NPM_REGISTRY:-}"
MAVEN_MIRROR="${MAVEN_MIRROR:-}"

cd "$COMPOSE_DIR"
if [ ! -f .env ]; then
  cp .env.example .env
fi

if [ "$USE_CN_MIRRORS" = "1" ]; then
  NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
  MAVEN_MIRROR="${MAVEN_MIRROR:-https://maven.aliyun.com/repository/public}"
fi

upsert_env() {
  local key="$1" val="$2"
  [ -z "$val" ] && return 0
  if grep -q "^${key}=" .env; then
    sed -i.bak "s|^${key}=.*|${key}=${val}|" .env && rm -f .env.bak
  else
    printf '%s=%s\n' "$key" "$val" >> .env
  fi
}

upsert_env NPM_REGISTRY "$NPM_REGISTRY"
upsert_env MAVEN_MIRROR "$MAVEN_MIRROR"

if [ "$PREBUILT" != "1" ]; then
  if [ ! -f "$ROOT/frontend/package-lock.json" ]; then
    echo "ERROR: frontend/package-lock.json missing — Docker frontend build runs npm ci and will fail." >&2
    echo "       Run: cd frontend && npm install && git add package-lock.json" >&2
    exit 1
  fi
  if command -v npm >/dev/null 2>&1; then
    echo "==> Preflight: npm ci --dry-run (lockfile sync check)"
    (cd "$ROOT/frontend" && npm ci --dry-run --no-audit --no-fund) \
      || {
        echo "ERROR: package-lock.json out of sync with package.json." >&2
        echo "       Run: cd frontend && npm install && commit package-lock.json" >&2
        exit 1
      }
  fi
fi

if [ -z "${NPM_REGISTRY}" ] && [ -z "${MAVEN_MIRROR}" ] \
  && ! grep -qE '^NPM_REGISTRY=.+' .env 2>/dev/null \
  && ! grep -qE '^MAVEN_MIRROR=.+' .env 2>/dev/null; then
  echo "WARN: no NPM_REGISTRY / MAVEN_MIRROR configured."
  echo "      On China / slow networks this often means 30–60+ minutes of downloads."
  echo "      Use: USE_CN_MIRRORS=1 $0   or set them in deploy/compose/.env"
fi

FILES=(-p archops -f compose.yaml)
[ "$PREBUILT" = "1" ] && FILES+=(-f compose.prebuilt.yaml)
[ "$LOWMEM" = "1" ] && FILES+=(-f compose.lowmem.yaml)

BASE_IMAGES=(
  nginx:1.27-alpine
  eclipse-temurin:21-jre
  pgvector/pgvector:pg16
  redis:7-alpine
)
if [ "$PREBUILT" != "1" ]; then
  BASE_IMAGES+=(node:22-alpine maven:3.9.9-eclipse-temurin-21)
fi

echo "==> Prefetch base images via dockerd (honors registry-mirrors; buildx often does not)"
for img in "${BASE_IMAGES[@]}"; do
  docker pull "$img" || echo "WARN: docker pull failed for $img (continuing)"
done

if [ "$LOWMEM" = "1" ]; then
  echo "==> LOWMEM: stopping app containers to free RAM before build"
  docker compose "${FILES[@]}" --env-file .env stop backend frontend 2>/dev/null || true
fi

# Prefer dockerd-local layers after prefetch; avoid buildx re-pulling FROM images.
export DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-0}"
export COMPOSE_DOCKER_CLI_BUILD="${COMPOSE_DOCKER_CLI_BUILD:-0}"

echo "==> Building images (BUILDKIT=${DOCKER_BUILDKIT}, LOWMEM=${LOWMEM}, PREBUILT=${PREBUILT})"
if [ "$LOWMEM" = "1" ]; then
  # Serial: Maven and npm competing for 1.6–2GiB RAM → OOM.
  docker compose "${FILES[@]}" --env-file .env build --pull=false backend
  docker compose "${FILES[@]}" --env-file .env build --pull=false frontend
else
  docker compose "${FILES[@]}" --env-file .env build --pull=false
fi

echo "==> Build finished"
