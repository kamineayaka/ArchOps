#!/usr/bin/env bash
# Build the single control-plane image tagged archops:latest (ADR-0043).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_TAG="${ARCHOPS_IMAGE_TAG:-archops:latest}"
# shellcheck source=image-build-args.sh
source "${ROOT_DIR}/deploy/scripts/image-build-args.sh"

echo "Building ${IMAGE_TAG} from ${ROOT_DIR}"
echo "Docker Hub mirror: ${DOCKER_HUB_MIRROR:-docker.m.daocloud.io/library}"
docker build \
  "${DOCKER_IMAGE_BUILD_ARGS[@]}" \
  -t "${IMAGE_TAG}" \
  -f "${ROOT_DIR}/Dockerfile" \
  "${ROOT_DIR}"
echo "Done: ${IMAGE_TAG}"
