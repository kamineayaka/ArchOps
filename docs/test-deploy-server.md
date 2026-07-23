# Test & Deploy Server

This guide turns a single Linux VPS into the ArchOps **test / deploy** host (Docker Compose, low-memory overlay when RAM ≤ 2 GiB).

## Target host (current)

| Item | Value |
|---|---|
| Host | `8.138.118.78` |
| OS | Ubuntu 24.04 LTS |
| SSH | `root@8.138.118.78` (prefer **SSH key**, not password) |
| App path | `/opt/archops` |
| Public URL | `http://8.138.118.78` / `http://console.skycore.top` |

> Do **not** commit passwords or private keys. Use `ssh-copy-id` once, then disable password auth when ready.

## One-time provision

From a workstation that already has SSH key access:

```bash
# Install your public key (first time only)
ssh-copy-id root@8.138.118.78

# Expand swap, ensure Docker, create /opt/archops
./deploy/scripts/remote-provision.sh root@8.138.118.78
```

## Configure env

```bash
cp deploy/compose/.env.example deploy/compose/.env
# Edit CORS (and optional OPENAI_API_KEY):
# CORS_ALLOWED_ORIGINS=http://8.138.118.78,http://console.skycore.top
# For ≤2 GiB hosts also set:
# JAVA_OPTS=-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m
```

`deploy/compose/.env` is gitignored — keep it only on your machine / the server.

## Deploy / upgrade

```bash
# Default: low-memory overlay + remote source build
./deploy/scripts/remote-deploy.sh root@8.138.118.78

# Recommended on ≤2 GiB hosts: build JAR/dist on a stronger machine first
cd backend && ./mvnw -DskipTests package && cd ..
cd frontend && npm ci && npm run build && cd ..
PREBUILT=1 ./deploy/scripts/remote-deploy.sh root@8.138.118.78

# Full resources (when the VPS has ≥4 GiB RAM)
LOWMEM=0 ./deploy/scripts/remote-deploy.sh root@8.138.118.78
```

### Optional: build images elsewhere, load on VPS

On a machine with enough RAM/CPU:

```bash
docker compose -f deploy/compose/compose.yaml build
docker save archops-backend archops-frontend | gzip > /tmp/archops-images.tar.gz
LOAD_IMAGES=1 SKIP_BUILD=1 ./deploy/scripts/remote-deploy.sh root@8.138.118.78
```

Image repository names follow Compose project naming (`archops-*` when the project directory / `-p` name is `archops`).

## Verify

```bash
curl -fsS http://8.138.118.78/actuator/health
# Login: admin / admin123  — change immediately
```

## Notes for 1.6–2 GiB VPS

- Always use `compose.lowmem.yaml` (script default `LOWMEM=1`).
- Do **not** start `compose.observability.yaml` on this size of host.
- Keep at least 4 GiB swap; builds and JVM spikes will use it.
- Prefer loading prebuilt images if Maven/`npm` builds OOM on the VPS.
- If Docker Hub / mirrors fail (`TLS handshake timeout`), rebuild app images from any already-cached `compose-backend` / `compose-frontend` layers:

```bash
./deploy/scripts/rebuild-images-from-cache.sh
docker compose -p archops -f compose.yaml -f compose.images.yaml -f compose.lowmem.yaml --env-file .env up -d
```

## Current status (aliserver)

Provisioned and verified on `8.138.118.78`:

- SSH key auth for the deploy agent
- Swap expanded to 4 GiB
- Stack path: `/opt/archops`
- Health: `http://8.138.118.78/actuator/health` → `UP`
- UI: `http://8.138.118.78` and `http://console.skycore.top`

Default login remains `admin` / `admin123` — change it immediately after first login.
Rotate the root password that was shared out-of-band; prefer key-only SSH.