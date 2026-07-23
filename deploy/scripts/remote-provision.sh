#!/usr/bin/env bash
# Provision a Linux host as ArchOps test/deploy platform.
# Usage: ./deploy/scripts/remote-provision.sh root@HOST
set -euo pipefail

TARGET="${1:?usage: $0 user@host}"
SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$TARGET")

echo "==> Checking connectivity: $TARGET"
"${SSH[@]}" 'echo connected; uname -a; free -h; df -h /'

echo "==> Ensuring swap (>=4G) for small-memory hosts"
"${SSH[@]}" 'bash -s' <<'REMOTE'
set -euo pipefail
NEED_SWAP_MB=4096
CURRENT_SWAP_MB=$(free -m | awk '/^Swap:/ {print $2}')
if [ "$CURRENT_SWAP_MB" -lt "$NEED_SWAP_MB" ]; then
  if [ -f /swapfile ]; then
    swapoff /swapfile || true
  fi
  fallocate -l 4G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=4096
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo "swap expanded to 4G"
else
  echo "swap already ${CURRENT_SWAP_MB}M"
fi
free -h
REMOTE

echo "==> Installing Docker if missing"
"${SSH[@]}" 'bash -s' <<'REMOTE'
set -euo pipefail
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker || true
docker --version
docker compose version
REMOTE

echo "==> Preparing /opt/archops"
"${SSH[@]}" 'mkdir -p /opt/archops /opt/archops-releases && chmod 755 /opt/archops /opt/archops-releases'

echo "==> Done. Next: ./deploy/scripts/remote-deploy.sh $TARGET"
