# Cursor Cloud Agent — ArchOps

Cloud Agents run on **isolated Ubuntu VMs**, not the developer's laptop.  
Repo config: [`.cursor/environment.json`](./environment.json) + [`.cursor/Dockerfile`](./Dockerfile).

## What the VM provides

| Tool | Purpose |
|---|---|
| JDK 21 | Gradle / Spring Boot |
| Node 22 | React / Vite |
| Python 3 | Agent stub |
| Docker Engine + Compose | Postgres 16 + Redis 7（Compose 默认 DaoCloud 镜像名） |

## Lifecycle

1. **Build (`install`)** — `scripts/cloud-install.sh`: warm Gradle + `npm ci` (idempotent, no long-running services).
2. **Agent start (`start`)** — `scripts/cloud-start.sh`: start dockerd, `docker compose … up -d postgres redis`.
3. **Terminals** — `backend` (`./gradlew bootRun`), `frontend` (`npm run dev` on `:5173`).

Default DB/Redis env matches `deploy/compose/.env.example` (`archops` / `archops` on localhost ports).

Matt workflow skills are **vendored in the repo** (Cloud Agents do not inherit laptop `~/.agents/skills/`):

| Path | Who reads it |
|---|---|
| `.cursor/skills/` | Cursor Cloud Agent (plus ArchOps `add-*` skills) |
| `.agents/skills/` | skills.sh / desktop Cursor |
| `docs/agents/` | `/setup-matt-pocock-skills` output: tracker, labels, domain layout |

## Acceptance in Cloud

- Primary seam: HTTP API (`GET /api/health`, then ticket acceptance tests).
- `/implement` drives `/tdd`: **red → green → refactor**, witnessed red per cycle. Overlay: [`docs/agents/tdd.md`](../docs/agents/tdd.md).
- Backend unit/HTTP tests: `cd backend && ./gradlew test` (embedded Postgres available for many tests).
- Manual UI: Vite `:5173` proxies `/api` → `:8080`. UI after that ticket's HTTP cycles are green.
- Do **not** assume the developer's Windows machine, SteamTools certs, or local Docker Desktop.

## Secrets

Put API keys (e.g. AI 编排层 egress, ADR-0044) in [Cursor Dashboard → Cloud Agents → Secrets](https://cursor.com/dashboard/cloud-agents), not in git. Do not put model keys on the control plane.  
Do not commit `.env` files.

## After changing this config

1. Commit + push `.cursor/*` and scripts to the branch Cloud Agents use (usually `main`).
2. Trigger / wait for a new Environment **Build** in the Cloud Agents dashboard so the snapshot picks up the Dockerfile/`install` changes.
3. See also: [Cloud Agent setup](https://cursor.com/docs/cloud-agent/setup).
