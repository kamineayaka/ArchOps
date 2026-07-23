# Deployment Guide

## Docker Compose (Recommended for Single Server)

For turning a small Linux VPS into a dedicated test/deploy host (SSH key, swap, low-memory overlay, remote sync scripts), see [test-deploy-server.md](./test-deploy-server.md).

### 1. Server Requirements

| Resource | Minimum | Recommended |
|---|---|---|
| CPU | 2 cores | 4 cores |
| RAM | 4 GB | 8 GB |
| Disk | 40 GB | 100 GB SSD |
| OS | Ubuntu 22.04+ / Debian 12+ / CentOS Stream 9+ | |

### 2. Install Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
```

### 3. Configure Environment

```bash
cp deploy/compose/.env.example deploy/compose/.env

# JWT_SECRET and CREDENTIALS_MASTER_KEY are optional — leave empty to auto-generate
# on first boot and persist to the archops_secrets volume.

# Optional: set OPENAI_API_KEY for one-time seed migration to default AI Provider.
# After deploy, configure providers in the admin UI: Settings → AI Settings.
# Edit deploy/compose/.env and set:
# - CORS_ALLOWED_ORIGINS=http://your-server-ip
```

### 4. Start Platform

```bash
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d --build
```

Verify health:

```bash
curl http://localhost/actuator/health
```

### 5. Post-Install Checklist

- [ ] Log in and change the default `admin` password
- [ ] Add your server assets under **资产管理**
- [ ] Configure SSH credentials for each asset
- [ ] Set `OPENAI_API_KEY` or connect Ollama
- [ ] Run initial RAG index: `POST /api/knowledge/reindex` (admin JWT) after first deploy
- [ ] Put Nginx behind TLS (reverse proxy or cloud load balancer)
- [ ] Restrict firewall: only expose port 80/443

### 6. Observability Stack (Optional)

```bash
docker compose -f deploy/compose/compose.observability.yaml up -d
```

- Grafana: http://your-server:3000 (admin / admin)
- Prometheus: http://your-server:9090

## Kubernetes (Helm)

```bash
# Edit values
cp deploy/helm/values.yaml deploy/helm/values-prod.yaml
# Set secrets, ingress host, storage class

helm install archops deploy/helm -f deploy/helm/values-prod.yaml -n archops --create-namespace
```

See [deploy/helm/README.md](../deploy/helm/README.md) for full Helm configuration reference.

## Backup

### PostgreSQL

```bash
docker exec archops-postgres pg_dump -U archops archops > backup_$(date +%Y%m%d).sql
```

### Redis

Redis uses AOF persistence (`appendonly yes`). Data is in the `redis_data` Docker volume.

### Restore

```bash
cat backup_20260101.sql | docker exec -i archops-postgres psql -U archops archops
```

## Secret rotation (`JWT_SECRET` / `CREDENTIALS_MASTER_KEY`)

Platform secrets resolve in priority order: environment variables → secrets file (`archops.secrets.path`, default `./data/secrets.properties` in Compose) → auto-generated on first boot.

### Impact summary

| Secret | What it protects | Rotation impact |
|--------|------------------|-----------------|
| `JWT_SECRET` | Signs access/refresh JWTs | **All active sessions invalidated.** Users must sign in again. Existing tokens in browsers/clients stop working immediately after restart with the new value. |
| `CREDENTIALS_MASTER_KEY` | AES key for encrypted SSH credentials and AI provider API keys in PostgreSQL | **Existing ciphertext cannot be decrypted** with the new key. SSH credentials and stored provider API keys appear missing until re-entered. RAG embeddings already written to the DB are unaffected, but embedding API calls need valid provider keys again. |

Auto-generated secrets (empty env + empty file) are written once to the secrets volume. Rotating them later has the same impact as setting new values manually.

### Recommended rotation procedure

1. **Plan a maintenance window** — rotation requires backend restart and user re-login.
2. **Back up PostgreSQL** (see [Backup](#backup)) before rotating `CREDENTIALS_MASTER_KEY`.
3. **Generate new values** (at least 32 random bytes; base64-encoded is fine), e.g. `openssl rand -base64 32`.
4. **Update configuration:**
   - **Compose:** set `JWT_SECRET` and/or `CREDENTIALS_MASTER_KEY` in `deploy/compose/.env`, or edit the persisted file on the `archops_secrets` volume (`jwt.secret`, `credentials.master-key`).
   - **Kubernetes:** update the Helm Secret / external secret manager and roll the backend Deployment.
5. **Restart the backend** (`docker compose up -d` or `kubectl rollout restart`).
6. **After `CREDENTIALS_MASTER_KEY` rotation:**
   - Re-enter SSH credentials for each asset under **Assets**.
   - Re-enter API keys for each AI provider under **Settings → AI Settings** (masked keys cannot be recovered).
   - Run `POST /api/knowledge/reindex` if you changed embedding provider or dimensions in the same maintenance window.
7. **Communicate** that all users need to sign in again when `JWT_SECRET` changes.

### What you do *not* need to re-run

- Flyway migrations
- Asset host/port inventory (still in PostgreSQL)
- Platform AI settings rows (provider IDs, RAG toggles) — only encrypted fields need re-entry

## Upgrading

```bash
git pull
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d --build
```

Flyway migrations run automatically on backend startup.
