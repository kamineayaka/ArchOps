# Deployment Guide

## Docker Compose (Recommended for Single Server)

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
# on first boot and persist to the cloudops_secrets volume.

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

helm install cloudops deploy/helm -f deploy/helm/values-prod.yaml -n cloudops --create-namespace
```

See [deploy/helm/README.md](../deploy/helm/README.md) for full Helm configuration reference.

## Backup

### PostgreSQL

```bash
docker exec cloudops-postgres pg_dump -U cloudops cloudops > backup_$(date +%Y%m%d).sql
```

### Redis

Redis uses AOF persistence (`appendonly yes`). Data is in the `redis_data` Docker volume.

### Restore

```bash
cat backup_20260101.sql | docker exec -i cloudops-postgres psql -U cloudops cloudops
```

## Upgrading

```bash
git pull
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d --build
```

Flyway migrations run automatically on backend startup.
