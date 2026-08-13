# Shared docker build-args for archops:latest (sourced by build-images*.sh).
# Always passed so `docker build "${DOCKER_IMAGE_BUILD_ARGS[@]}"` is safe with `set -u`.
# DOCKER_HUB_MIRROR — Hub prefix for node/temurin (default DaoCloud library).
# GRADLE_DISTRIBUTION_URL — optional override of wrapper distributionUrl.
DOCKER_IMAGE_BUILD_ARGS=(
  --build-arg "DOCKER_HUB_MIRROR=${DOCKER_HUB_MIRROR:-docker.m.daocloud.io/library}"
  --build-arg "GRADLE_DISTRIBUTION_URL=${GRADLE_DISTRIBUTION_URL:-}"
)
