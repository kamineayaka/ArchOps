# Shared docker build-args for archops:latest (sourced by build-images*.sh).
# Always passed so `docker build "${DOCKER_IMAGE_BUILD_ARGS[@]}"` is safe with `set -u`.
# ARCHOPS_CN_MIRRORS=1 — Tencent Gradle zip + Aliyun Maven (Docker layer only).
# GRADLE_DISTRIBUTION_URL — override wrapper distributionUrl (implies URL validation off).
DOCKER_IMAGE_BUILD_ARGS=(
  --build-arg "ARCHOPS_CN_MIRRORS=${ARCHOPS_CN_MIRRORS:-0}"
  --build-arg "GRADLE_DISTRIBUTION_URL=${GRADLE_DISTRIBUTION_URL:-}"
)
