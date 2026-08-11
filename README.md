# ArchOps

运维关系真相控制平面。领域合同已冻结（`CONTEXT.md`、ADR-0039）；技术栈已冻结（ADR-0043）。  
实现策略：K1 推倒（ADR-0040）、同仓重写（ADR-0042）。

**脚手架状态**：已按 ADR-0043 重建可启动最小骨架。竖切按工单推进。  
**AI Agent 入口**：[`AGENTS.md`](./AGENTS.md) · [`CLAUDE.md`](./CLAUDE.md)

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

### 2. 后端

需要 **JDK 21**。Windows 本机若遇 Gradle wrapper / Docker / TLS 问题，先跑：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\fix-windows-dev-env.ps1
```

（会对齐 `JAVA_HOME`、拉起 Docker Desktop、补齐 Gradle wrapper 缓存，并把 SteamTools/Watt Toolkit 的 MITM 根证书同步进用户级 Java truststore。）

若 `./gradlew` 仍从 `services.gradle.org` 拉取失败，可将
`backend/gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 临时改为镜像，例如
`https://mirrors.cloud.tencent.com/gradle/gradle-8.12.1-bin.zip`。

Docker Hub 若因 DNS 污染拉不到镜像，确认 Docker Desktop → Docker Engine 已配置 registry-mirrors（脚本不改引擎 UI；本机可编辑 `%USERPROFILE%\.docker\daemon.json`），然后重启 Docker Desktop。

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

> Windows 本机：Docker Desktop 路径多为 `%LOCALAPPDATA%\Programs\DockerDesktop\`。若 `docker.io` DNS 被污染，用 `scripts/fix-windows-dev-env.ps1` + DaoCloud 直拉镜像（见脚本末尾提示）。

## 明确不做（脚手架）

策展 / 观测 / 冲突 / 计划 / 协作 API、AI 出站业务、SSH 工作台、Neo4j、Maven、JPA 地基、Vue、LangChain。竖切见 `docs/mvp-vertical-slice.md`，另开对话实现。

## 合同与接手

- 术语：`CONTEXT.md`
- 栈真相：`docs/adr/0043-tech-stack.md`
- 接手：`docs/dev-handoff.md`
- 脚手架 prompt：`docs/scaffold-bootstrap-prompt.md`
