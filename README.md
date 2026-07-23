# ArchOps AI Platform

[中文文档](README.zh-CN.md) | English

[![CI](https://github.com/kamineayaka/ArchOps/actions/workflows/ci.yml/badge.svg)](https://github.com/kamineayaka/ArchOps/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**ArchOps AI Platform** is a B/S cloud-native intelligent operations control plane for Linux server fleets. It provides a unified web interface with AI-assisted operations, Web SSH terminal, in-process agent tool registry, knowledge-base RAG, RBAC-tiered approval workflow, and tamper-evident audit logging.

Designed for deployment on any Linux server — from a single VPS to a production cluster — and for other developers to self-host and extend.

## Features

| Module | Description |
|---|---|
| **User & RBAC** | JWT auth, role-based access (ADMIN / OPERATOR / VIEWER), single-session kick-out |
| **Asset Management** | Server/cluster/service inventory, AES-256-GCM encrypted SSH credentials |
| **SSH Connection Pool** | Server-side pooled SSH sessions per user/asset; warm API; reused by terminal and `ssh_exec` |
| **Web SSH Terminal** | Browser terminal (xterm.js + MINA SSHD) over pooled connections, PTY resize |
| **AI Agent** | ReAct tool-calling loop; pin target assets per conversation so `ssh_exec` needs no repeated `assetId` |
| **Built-in Tool Registry** | Extensible in-process agent tools (`ssh_exec`, `list_assets`, ...) |
| **Approval Workflow** | RBAC-tiered risk classification (LOW / MEDIUM / HIGH) with human gate |
| **Knowledge Base** | Architecture snapshot + work logs + pgvector RAG semantic retrieval |
| **Audit Center** | Append-only log with SHA-256 hash chain for tamper detection |
| **Observability** | Prometheus metrics, Grafana dashboards, Loki log aggregation |

## Quick Start (Docker Compose)

### Prerequisites

- Docker 24+ and Docker Compose v2
- 2 CPU cores, 4 GB RAM minimum (8 GB recommended with observability stack)
- An OpenAI-compatible or Anthropic API key (configure in admin UI → AI Settings; optional `OPENAI_API_KEY` env seed)
- Node.js 22+ for frontend development

### Deploy

```bash
git clone https://github.com/kamineayaka/ArchOps.git
cd ArchOps

# Configure environment (JWT/credentials auto-generate if left empty)
cp deploy/compose/.env.example deploy/compose/.env
# Optional: set OPENAI_API_KEY for seed migration, or configure providers in AI Settings after deploy

# Start platform
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d --build

# (Optional) Start observability stack
docker compose -f deploy/compose/compose.observability.yaml up -d
```

Open **http://your-server-ip** and log in with:

- Username: `admin`
- Password: `admin123`

**Change the default password immediately after first login.**

After the first deploy, run `POST /api/knowledge/reindex` as admin to initialize the RAG vector index.

### SSH pool & AI targets

1. Register assets and save SSH credentials under **Assets**.
2. In **AI Ops**, select **Target assets** for the conversation (connections are warmed automatically).
3. Ask naturally (e.g. “check disk usage”) — the agent runs `ssh_exec` against pinned targets without you specifying `assetId` each time.
4. **Web Terminal** reuses the same pool; optional warm: `POST /api/ssh/pool/{assetId}/warm`.

See [docs/ssh-connection-pool-design.md](docs/ssh-connection-pool-design.md) for pool semantics and API details.

## Development

```bash
# Start dependencies only
docker compose -f deploy/compose/compose.yaml up -d postgres redis minio

# Backend (port 8080)
cd backend && ./mvnw spring-boot:run

# Frontend (port 5173, requires Node.js 22+)
cd frontend && npm install && npm run dev
```

## Project Structure

```
ArchOps/
├── backend/           Spring Boot 3 (Java 21), modular packages
├── frontend/          Vue 3 + Naive UI + TypeScript
├── deploy/
│   ├── compose/       Docker Compose (single-node + observability)
│   └── helm/          Kubernetes Helm Chart
├── docker/            Shared Dockerfiles and infra config
├── docs/              Architecture and deployment guides
└── .github/workflows/ CI pipeline
```

## Deployment Options

| Method | Use Case | Guide |
|---|---|---|
| Docker Compose | Single server / MVP / small production | [docs/deployment.md](docs/deployment.md) |
| Kubernetes Helm | Production HA (chart scaffold) | [deploy/helm/](deploy/helm/) |

## Tech Stack

| Layer | Choices |
|---|---|
| Backend | Java 21, Spring Boot 3, Flyway, PostgreSQL + pgvector, Redis |
| Frontend | Vue 3, Naive UI, Pinia, vue-i18n |
| AI | OpenAI-compatible API / Ollama, in-process agent tools, pgvector RAG |
| Deploy | Docker Compose, Nginx, Prometheus / Grafana / Loki |

Product vision: [docs/product-vision.md](docs/product-vision.md).  
Mainline plan (living Architecture): [docs/mainline-implementation-plan.md](docs/mainline-implementation-plan.md) — agent prompt: [docs/mainline-implementation-prompt.md](docs/mainline-implementation-prompt.md).  
Engineering architecture: [docs/architecture.md](docs/architecture.md).

## Security

See [SECURITY.md](SECURITY.md) for the vulnerability reporting process and production hardening checklist.

Before going to production:

- Rotate `JWT_SECRET`, `CREDENTIALS_MASTER_KEY`, and other default secrets
- Change the default admin password
- Expose only ports 80/443 and enable TLS
- Restrict access to `/actuator/prometheus` and other monitoring endpoints as needed

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)
