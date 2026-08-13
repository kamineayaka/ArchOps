# ArchOps

运维关系真相控制平面。领域合同已冻结（`CONTEXT.md`、ADR-0039）；技术栈已冻结（ADR-0043）。  
实现策略：K1 推倒（ADR-0040）、同仓重写（ADR-0042）。

**脚手架状态**：已按 ADR-0043 重建可启动最小骨架。竖切按工单推进。  
**AI Agent 入口**：[`AGENTS.md`](./AGENTS.md) · [`CLAUDE.md`](./CLAUDE.md)  
**Cloud Agent 环境**：[`.cursor/environment.json`](./.cursor/environment.json) · [`.cursor/CLOUD.md`](./.cursor/CLOUD.md)

## 目录

| 路径 | 说明 |
|---|---|
| `backend/` | Java 21 / Spring Boot 3 / Gradle / MyBatis-Plus / Flyway |
| `frontend/` | React + TypeScript + Vite + Ant Design |
| `agent/` | Python 心跳 stub（交付主推 systemd，见 `agent/README.md`） |
| `deploy/` | Compose：`archops:latest` + Postgres 16 + Redis |
| `docs/` | 合同、ADR、竖切、脚手架 prompt |
| `Dockerfile` | 多阶段构建 → 镜像 tag `archops:latest` |

## 本地开发

### 1. 基础设施（Postgres + Redis）

```bash
docker compose -f deploy/compose/compose.yaml up -d postgres redis
```

默认：`localhost:5432` / `localhost:6379`，库用户密码均为 `archops`（可用 `deploy/compose/.env.example`）。  
Compose 默认从 DaoCloud 拉 `postgres:16` / `redis:7-alpine`（`POSTGRES_IMAGE` / `REDIS_IMAGE` 可改回官方名）。

### 2. 后端

需要 **JDK 21**。Windows 本机若遇 Gradle wrapper / Docker / TLS 问题，先跑：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\fix-windows-dev-env.ps1
```

（会对齐 `JAVA_HOME`、拉起 Docker Desktop、补齐 Gradle wrapper 缓存，并把 SteamTools/Watt Toolkit 的 MITM 根证书同步进用户级 Java truststore。）

依赖下载默认走国内镜像：Gradle zip 为腾讯云、Maven/插件为阿里云（官方 Central 作回退）、npm 为 npmmirror。wrapper 仍校验官方 SHA-256；读超时 120s。若本机已按官方 URL 缓存过 zip，换源后缓存目录会变，可重新让 wrapper 从腾讯云拉，或把同一 zip 放到 `~/.gradle/wrapper/dists/gradle-8.12.1-bin/830jys8xkckz69cgvf9t0ocan/`。

```bash
cd backend
./gradlew bootRun
# Windows: .\gradlew.bat bootRun
```

健康检查：`GET http://localhost:8080/api/health`  
期望：`{"success":true,"code":"OK","message":"ok","data":{"status":"UP"}}`

### 3. 前端

```bash
cd frontend
npm install
npm run dev
```

`frontend/.npmrc` 将 registry 固定为 `https://registry.npmmirror.com`。

打开 Vite 提示的地址（默认 `http://localhost:5173`）。页面标题 **ArchOps**，并代理 `/api` → `:8080` 展示 health 结果。

### 4. Agent stub（可选）

```bash
python agent/heartbeat.py --interval 0
```

当前无 ingest API，预期日志里出现 HTTP 404，属脚手架预期。

## 镜像构建与整栈

```bash
bash deploy/scripts/build-images.sh
# 产出 archops:latest

docker compose -f deploy/compose/compose.yaml up -d
```

多副本设计前提（ADR-0043）：本地可用  
`docker compose -f deploy/compose/compose.yaml up --scale archops=2`（端口映射冲突时需自行调整）。

`Dockerfile` 基础镜像默认也走 DaoCloud（`node` / `eclipse-temurin`）。容器里若解析不了镜像站：

```bash
# 可选：修复 docker0 / UFW FORWARD / 容器 DNS（默认只用公网 DNS，不要写死局域网地址）
# sudo bash deploy/scripts/fix-docker-bridge-dns.sh
# sudo DOCKER_LAN_DNS=192.168.x.x bash deploy/scripts/fix-docker-bridge-dns.sh

bash deploy/scripts/build-images-logged.sh
```

失败时完整 `progress=plain` 日志写到 `~/logs/build-*.log`。若必须改回 Docker Hub 官方名：

```bash
DOCKER_HUB_MIRROR=docker.io/library bash deploy/scripts/build-images.sh
POSTGRES_IMAGE=postgres:16 REDIS_IMAGE=redis:7-alpine \
  docker compose -f deploy/compose/compose.yaml up -d postgres redis
```

> `.cursor/Dockerfile`（Cloud Agent 底镜像）仍用 Ubuntu / NodeSource / Docker 官方 apt，因为那是美国区环境构建，不是应用依赖。Windows 本机 Docker Desktop 路径多为 `%LOCALAPPDATA%\Programs\DockerDesktop\`。

## 明确不做（脚手架）

策展 / 观测 / 冲突 / 计划 / 协作 API、AI 出站业务、SSH 工作台、Neo4j、Maven、JPA 地基、Vue、LangChain。竖切见 `docs/mvp-vertical-slice.md`，另开对话实现。

## 合同与接手

- 术语：`CONTEXT.md`
- 栈真相：`docs/adr/0043-tech-stack.md`
- 接手：`docs/dev-handoff.md`
- 脚手架 prompt：`docs/scaffold-bootstrap-prompt.md`
