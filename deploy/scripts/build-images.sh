#!/usr/bin/env bash
# Build the single control-plane image tagged archops:latest (ADR-0043).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_TAG="${ARCHOPS_IMAGE_TAG:-archops:latest}"

echo "Building ${IMAGE_TAG} from ${ROOT_DIR}"
docker build -t "${IMAGE_TAG}" -f "${ROOT_DIR}/Dockerfile" "${ROOT_DIR}"
echo "Done: ${IMAGE_TAG}"
