#!/usr/bin/env bash
# Build archops image; on failure write a full plain-progress log for diagnosis.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_TAG="${ARCHOPS_IMAGE_TAG:-archops:latest}"
LOG_DIR="${ARCHOPS_BUILD_LOG_DIR:-${HOME}/logs}"
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="${LOG_DIR}/build-${STAMP}.log"
TMP_LOG="$(mktemp)"

mkdir -p "${LOG_DIR}"

cleanup() {
  rm -f "${TMP_LOG}"
}
trap cleanup EXIT

echo "Building ${IMAGE_TAG} from ${ROOT_DIR}"
echo "Live output below; failure log (if any): ${LOG_FILE}"

set +e
# plain progress keeps the full npm/docker layer output readable in the log
DOCKER_BUILDKIT=1 docker build \
  --progress=plain \
  -t "${IMAGE_TAG}" \
  -f "${ROOT_DIR}/Dockerfile" \
  "${ROOT_DIR}" \
  2>&1 | tee "${TMP_LOG}"
status=${PIPESTATUS[0]}
set -e

if [[ "${status}" -eq 0 ]]; then
  echo "Done: ${IMAGE_TAG}"
  exit 0
fi

{
  echo "===== ArchOps image build FAILED ====="
  echo "time:        $(date -Is)"
  echo "image_tag:   ${IMAGE_TAG}"
  echo "root_dir:    ${ROOT_DIR}"
  echo "exit_code:   ${status}"
  echo "host:        $(hostname)"
  echo
  echo "===== free -h ====="
  free -h || true
  echo
  echo "===== recent kernel OOM (if any) ====="
  dmesg -T 2>/dev/null | grep -iE 'oom|killed process|out of memory' | tail -30 || true
  echo
  echo "===== docker build log (progress=plain) ====="
  cat "${TMP_LOG}"
} > "${LOG_FILE}"

echo
echo "Build failed (exit ${status}). Full log written to:"
echo "  ${LOG_FILE}"
echo "Tail of failure:"
tail -n 40 "${LOG_FILE}" || true
exit "${status}"
