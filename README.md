# ArchOps

只运维大数据平台本身。第一版是虚机上的 HDFS 与 YARN：集群、服务角色、主机，角色不在时告警，以及 Host Agent 上的一步修复。

跑角色的主机装 Go Host Agent。它上报 NameNode、DataNode、ResourceManager、NodeManager 是否在，并执行 Python 控制面一次发来的一步修复。Agent 不自己选下一步。失败就停。修复输出不写成告警证据；角色恢复靠下一次采集。不走执行引擎 SSH。

**入口**：[`AGENTS.md`](./AGENTS.md) · [`CLAUDE.md`](./CLAUDE.md) · 产品词 [`CONTEXT.md`](./CONTEXT.md)  
**边界**：ADR-0050、ADR-0051、ADR-0052。  
**Cloud Agent 环境**：[`.cursor/environment.json`](./.cursor/environment.json) · [`.cursor/CLOUD.md`](./.cursor/CLOUD.md)

## 目录

| 路径 | 说明 |
|---|---|
| `control-plane/` | 现行控制面：Python、FastAPI、PostgreSQL |
| `host-agent/` | 现行 Host Agent：Go。采集角色存活，执行一步修复 |
| `frontend/` | React + TypeScript + Vite + Ant Design |
| `backend/` | 留存的 Java 控制面 |
| `agent/` | 留存的 Python 心跳 stub |
| `deploy/` | 本地 Compose（Postgres、Redis）。Java 镜像仍可在这里起 |
| `docs/` | 产品说明、ADR、Spec。`docs/archive/` 是历史笔记 |
| `Dockerfile` | 留存 Java 控制面的多阶段构建，镜像 tag `archops:latest` |

## 现行控制面与 Host Agent

Postgres 用下面的 Compose。控制面默认库是 `archops_platform`（用户和密码都是 `archops`）。

```bash
docker compose -f deploy/compose/compose.yaml up -d postgres
export ARCHOPS_PLATFORM_DATABASE_URL=postgresql://archops:archops@127.0.0.1:5432/archops_platform
cd control-plane
python3 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt
PYTHONPATH=src python -m archops_control
```

控制面听 `8081`。Host Agent 上报后，若控制面已经发出一步，这次进程只执行这一步：

```bash
cd host-agent
go run . --control-plane http://127.0.0.1:8081 --host-id <host-id> --role DataNode
```

库还不存在时，控制面测试会创建 `archops_platform`。测试：

```bash
(cd control-plane && . .venv/bin/activate && pytest)
(cd host-agent && go test ./...)
```

## 留存的 Java 控制面

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
export ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64="$(openssl rand -base64 32)"
./gradlew bootRun
# Windows: .\gradlew.bat bootRun
```

`ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64` is required for boot (AES key, Base64, 16/24/32 bytes). There is no source-code default. Cloud Agent `backend` terminal injects an explicit fixture key.

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

### 4. 留存的 Python 心跳（可选）

现行采集和修复在 `host-agent/`。这个 stub 仍对着 Java 控制面的 `POST /api/agent/heartbeat`。

```bash
python agent/heartbeat.py --interval 0
```

## 镜像构建与本地 Compose

交付目标是三个 Kubernetes Deployment（控制面、执行引擎、AI 编排层，ADR-0049）。下面的 `docker compose up` 只起本地开发用的 Postgres、Redis、控制面和执行引擎，不是交付目标。Host Agent 留在主机上。

```bash
bash deploy/scripts/build-images.sh
# 产出 archops:latest

# Set ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64 in deploy/compose/.env
# (copy from .env.example; generate with openssl rand -base64 32).
# Hub and executor use the same value. Blank is refused at boot.
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

## 明确不做

第一版不做对象存储、Spark on K8s、Flink on K8s、编排层和模型文案。HDFS/YARN 修复不走执行引擎 SSH。不把策展/观测/冲突判断当现行做法。

## 合同与接手

- 现行产品词：`CONTEXT.md`
- 边界：ADR-0050、ADR-0051、ADR-0052
- 行为：`docs/specs/big-data-platform.md`
- 做过什么：`docs/dev-handoff.md`
