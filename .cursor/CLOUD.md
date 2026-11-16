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
3. **Terminals** — `backend` (`./gradlew bootRun` with an explicit fixture `ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64`), `frontend` (`npm run dev` on `:5173`).

Default DB/Redis env matches `deploy/compose/.env.example` (`archops` / `archops` on localhost ports).

编码入口是根目录 `AGENTS.md`。人给出目标、边界、怎样算做完。测试和类型检查是反馈。

## 在这台机器上跑

- 现行控制面是 `control-plane/`，现行 Host Agent 是 `host-agent/`。角色存活和一步修复：`cd control-plane && pytest`，`cd host-agent && go test ./...`。
- 留存 Java 树的健康检查：`GET /api/health`。测试：`cd backend && ./gradlew test`（不少 HTTP 测试用内嵌 Postgres）。
- 前端：Vite `:5173` 把 `/api` 代理到 `:8080`。`agent/` 里的 Python 心跳是留存 stub。
- 不要假设开发者的 Windows 机器、SteamTools 证书或本机 Docker Desktop。

## Secrets

Put API keys (e.g. AI 编排层 egress, ADR-0044) in [Cursor Dashboard → Cloud Agents → Secrets](https://cursor.com/dashboard/cloud-agents), not in git. Do not put model keys on the control plane.  
Do not commit `.env` files.

## After changing this config

1. Commit + push `.cursor/*` and scripts to the branch Cloud Agents use (usually `main`).
2. Trigger / wait for a new Environment **Build** in the Cloud Agents dashboard so the snapshot picks up the Dockerfile/`install` changes.
3. See also: [Cloud Agent setup](https://cursor.com/docs/cloud-agent/setup).
