#!/usr/bin/env bash
# Restore Docker bridge IPv4 (docker0 / orphan bridges) so containers can
# reach DNS and the internet again.
# Requires root: sudo bash deploy/scripts/fix-docker-bridge-dns.sh
#
# Default container DNS is public resolvers only. Do not hardcode a LAN
# nameserver (that breaks any host that is not on that network).
# Optional: sudo DOCKER_LAN_DNS=192.168.x.x bash deploy/scripts/fix-docker-bridge-dns.sh
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Please run with sudo:"
  echo "  sudo bash $0"
  exit 1
fi

echo "==> Before"
ip -4 addr show docker0 2>/dev/null || echo "docker0: missing or no IPv4"
ip -br link show type bridge || true

# UFW DROP on FORWARD breaks Docker NAT on many hosts; ACCEPT is the usual fix.
if [[ -f /etc/default/ufw ]]; then
  if grep -q '^DEFAULT_FORWARD_POLICY="DROP"' /etc/default/ufw; then
    echo "==> Setting UFW DEFAULT_FORWARD_POLICY=ACCEPT (was DROP)"
    sed -i 's/^DEFAULT_FORWARD_POLICY="DROP"/DEFAULT_FORWARD_POLICY="ACCEPT"/' /etc/default/ufw
    if grep -q '^ENABLED=yes' /etc/ufw/ufw.conf 2>/dev/null; then
      ufw --force reload || true
    fi
  else
    echo "==> UFW forward policy already non-DROP (or not set to DROP)"
  fi
fi

echo "==> Ensuring /etc/docker/daemon.json has bip + public DNS"
DOCKER_LAN_DNS="${DOCKER_LAN_DNS:-}" python3 - <<'PY'
import json
import os
from pathlib import Path

p = Path("/etc/docker/daemon.json")
data = {}
if p.exists() and p.read_text().strip():
    data = json.loads(p.read_text())
data.setdefault("bip", "172.17.0.1/16")
dns = ["223.5.5.5", "8.8.8.8"]
lan = os.environ.get("DOCKER_LAN_DNS", "").strip()
if lan:
    dns.append(lan)
data["dns"] = dns
p.write_text(json.dumps(data, indent=2) + "\n")
print(p.read_text())
PY

echo "==> Restarting Docker to recreate bridge IPv4 / iptables"
systemctl restart docker
sleep 2
systemctl is-active docker

echo "==> After restart"
ip -4 addr show docker0
ip -br link show type bridge || true

# Drop empty custom bridges that lost IPv4 (safe if unused)
echo "==> Recreating empty broken user bridges (if any)"
for net in archops_default; do
  if docker network inspect "$net" >/dev/null 2>&1; then
    containers="$(docker network inspect -f '{{len .Containers}}' "$net" 2>/dev/null || echo 0)"
    if [[ "$containers" == "0" ]]; then
      echo "    removing unused network: $net"
      docker network rm "$net" || true
    else
      echo "    skip $net (has $containers container(s))"
    fi
  fi
done

echo "==> Verify default bridge DNS + HTTPS"
docker run --rm alpine sh -c '
  set -e
  echo "resolv.conf:"; cat /etc/resolv.conf
  echo "gateway ping:"; ping -c1 -W3 "$(ip route | awk "/default/{print \$3}")"
  echo "dns:"; nslookup registry-1.docker.io || nslookup mirrors.cloud.tencent.com
  echo "http:"; wget -qO- --timeout=8 https://mirrors.cloud.tencent.com/ | head -c 120; echo
'
echo "==> OK: default bridge networking restored"
