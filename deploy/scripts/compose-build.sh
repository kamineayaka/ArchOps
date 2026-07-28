#!/usr/bin/env bash
# Build ArchOps Compose images with deploy-friendly defaults.
#
# Usage (from repo root or deploy/compose):
#   bash deploy/scripts/compose-build.sh
#   USE_CN_MIRRORS=1 LOWMEM=1 bash deploy/scripts/compose-build.sh
#   PREBUILT=1 bash deploy/scripts/compose-build.sh
#   RESUME=frontend LOWMEM=1 bash deploy/scripts/compose-build.sh   # after backend already built
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
# RESUME=backend|frontend|"" — skip completed services after a partial LOWMEM failure
RESUME="${RESUME:-}"
# SKIP_PREFLIGHT=1 — skip npm ci --dry-run (Docker build still validates lockfile)
SKIP_PREFLIGHT="${SKIP_PREFLIGHT:-0}"
PULL_RETRIES="${PULL_RETRIES:-5}"
DEPLOY_LOG="${DEPLOY_LOG:-$COMPOSE_DIR/deploy-build.log}"

ts() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }
log() { echo "[$(ts)] $*"; echo "[$(ts)] $*" >>"$DEPLOY_LOG"; }
die() { log "ERROR: $*"; exit 1; }

cd "$COMPOSE_DIR"
: >"$DEPLOY_LOG"
log "==> compose-build start (LOWMEM=$LOWMEM PREBUILT=$PREBUILT RESUME=${RESUME:-none} USE_CN_MIRRORS=$USE_CN_MIRRORS SKIP_PREFLIGHT=$SKIP_PREFLIGHT)"

if [ ! -f .env ]; then
  cp .env.example .env
fi

# Auto-enable China mirrors when unset and TZ/locale looks like CN, or USE_CN_MIRRORS=auto
if [ "$USE_CN_MIRRORS" = "auto" ] || { [ "$USE_CN_MIRRORS" = "0" ] && [ -z "${NPM_REGISTRY}" ] && [ -z "${MAVEN_MIRROR}" ]; }; then
  TZ_NAME="${TZ:-$(cat /etc/timezone 2>/dev/null || true)}"
  if echo "${TZ_NAME:-}${LANG:-}" | grep -qiE 'Asia/Shanghai|Asia/Chongqing|zh_CN|PRC'; then
    USE_CN_MIRRORS=1
    log "==> Auto-detected China locale/TZ → USE_CN_MIRRORS=1"
  fi
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

force_env() {
  local key="$1" val="$2"
  if grep -q "^${key}=" .env; then
    sed -i.bak "s|^${key}=.*|${key}=${val}|" .env && rm -f .env.bak
  else
    printf '%s=%s\n' "$key" "$val" >> .env
  fi
}

upsert_env NPM_REGISTRY "$NPM_REGISTRY"
upsert_env MAVEN_MIRROR "$MAVEN_MIRROR"

# ≤2GiB / LOWMEM: never leave graph half-on from a stale .env
if [ "$LOWMEM" = "1" ]; then
  MEM_MB="$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null || echo 0)"
  if [ "${MEM_MB}" -gt 0 ] && [ "${MEM_MB}" -lt 3072 ]; then
    if grep -qE '^ARCHOPS_GRAPH_ENABLED=true' .env 2>/dev/null; then
      log "WARN: low memory (${MEM_MB}MiB) but ARCHOPS_GRAPH_ENABLED=true in .env — forcing false"
      force_env ARCHOPS_GRAPH_ENABLED false
    fi
  fi
fi

if [ "$PREBUILT" != "1" ]; then
  if [ ! -f "$ROOT/frontend/package-lock.json" ]; then
    die "frontend/package-lock.json missing — Docker frontend build runs npm ci and will fail. Run: cd frontend && npm install && git add package-lock.json"
  fi
  if [ "$SKIP_PREFLIGHT" = "1" ]; then
    log "==> Preflight skipped (SKIP_PREFLIGHT=1) — Docker npm ci will still fail fast on lock drift"
  else
    log "==> Preflight: lockfile sync check"
    if command -v npm >/dev/null 2>&1; then
      (cd "$ROOT/frontend" && npm ci --dry-run --no-audit --no-fund) \
        || die "package-lock.json out of sync with package.json. Run: cd frontend && npm install && commit package-lock.json"
    elif command -v docker >/dev/null 2>&1; then
      # Prefer alpine: Dockerfile builds on node:22-alpine (musl); host glibc npm can miss optional deps.
      docker run --rm \
        -v "$ROOT/frontend:/app" -w /app \
        ${NPM_REGISTRY:+-e "npm_config_registry=$NPM_REGISTRY"} \
        node:22-alpine \
        sh -c "npm ci --dry-run --no-audit --no-fund" \
        || die "package-lock.json out of sync (docker npm ci --dry-run failed on node:22-alpine)"
    else
      log "WARN: neither npm nor docker available for lockfile preflight"
    fi
  fi
fi

if [ -z "${NPM_REGISTRY}" ] && [ -z "${MAVEN_MIRROR}" ] \
  && ! grep -qE '^NPM_REGISTRY=.+' .env 2>/dev/null \
  && ! grep -qE '^MAVEN_MIRROR=.+' .env 2>/dev/null; then
  log "WARN: no NPM_REGISTRY / MAVEN_MIRROR configured."
  log "      On China / slow networks this often means 30–60+ minutes of downloads."
  log "      Use: USE_CN_MIRRORS=1 $0   or set them in deploy/compose/.env"
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

pull_with_retry() {
  local img="$1"
  local attempt=1
  local delay=5
  while [ "$attempt" -le "$PULL_RETRIES" ]; do
    log "==> docker pull $img (attempt $attempt/$PULL_RETRIES)"
    if docker pull "$img"; then
      docker image inspect "$img" >/dev/null 2>&1 \
        || die "pulled $img but docker image inspect failed"
      return 0
    fi
    log "WARN: pull failed for $img — retry in ${delay}s"
    sleep "$delay"
    delay=$((delay * 2))
    if [ "$delay" -gt 120 ]; then delay=120; fi
    attempt=$((attempt + 1))
  done
  die "docker pull failed for $img after $PULL_RETRIES attempts — aborting before build"
}

log "==> Prefetch base images via dockerd (honors registry-mirrors; buildx often does not)"
for img in "${BASE_IMAGES[@]}"; do
  pull_with_retry "$img"
done

if [ "$LOWMEM" = "1" ]; then
  log "==> LOWMEM: stopping app containers to free RAM before build"
  docker compose "${FILES[@]}" --env-file .env stop backend frontend 2>/dev/null || true
fi

# Prefer dockerd-local layers after prefetch; avoid buildx re-pulling FROM images.
export DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-0}"
export COMPOSE_DOCKER_CLI_BUILD="${COMPOSE_DOCKER_CLI_BUILD:-0}"

build_one() {
  local svc="$1"
  log "==> Building $svc (BUILDKIT=${DOCKER_BUILDKIT})"
  docker compose "${FILES[@]}" --env-file .env build --pull=false "$svc"
  log "==> Built $svc OK"
}

log "==> Building images (BUILDKIT=${DOCKER_BUILDKIT}, LOWMEM=${LOWMEM}, PREBUILT=${PREBUILT}, RESUME=${RESUME:-none})"
if [ "$LOWMEM" = "1" ]; then
  case "$RESUME" in
    frontend)
      build_one frontend
      ;;
    backend)
      build_one backend
      ;;
    *)
      build_one backend
      build_one frontend
      ;;
  esac
else
  case "$RESUME" in
    frontend) build_one frontend ;;
    backend) build_one backend ;;
    *)
      docker compose "${FILES[@]}" --env-file .env build --pull=false
      log "==> Built all services OK"
      ;;
  esac
fi

log "==> Build finished"
echo "==> Build finished (log: $DEPLOY_LOG)"
