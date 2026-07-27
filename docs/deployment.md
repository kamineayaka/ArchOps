# 部署指南

## Docker Compose（推荐，单机）

低内存 VPS 的远程初始化与同步脚本用法，见 [test-deploy-server.md](./test-deploy-server.md)。API 一览见 [api.md](./api.md)。

### 1. 服务器要求

| 用途 | CPU | 内存 | 说明 |
|---|---|---|---|
| **仅运行**（PREBUILT / 加载镜像） | 2 核 | **≥1.5–2 GiB** + ≥4 GiB swap | 不要在目标机源码构建 |
| **本机构建**（Maven + npm） | 2 核 | **≥4 GiB** 推荐 8 GiB | 1.6–2 GiB 冷构建极慢且脆 |
| 推荐生产 | 4 核 | 8 GiB | SSD ≥40 GB |

> **警告**：在 ≤2 GiB 内存的机器上直接 `docker compose ... up -d --build` 会同时跑 Maven 与 npm，极易 OOM。小 VPS **默认应走 PREBUILT / 镜像加载**，不要在目标机冷构建。


### 2. 安装 Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
```

### 3. 配置环境变量

```bash
cp deploy/compose/.env.example deploy/compose/.env

# JWT_SECRET 与 CREDENTIALS_MASTER_KEY 可留空：首次启动自动生成，
# 并持久化到 archops_secrets 卷。

# 可选：设置 OPENAI_API_KEY，用于一次性种子迁移默认 AI Provider。
# 部署后也可在控制台「设置 → AI 设置」中配置。
# 编辑 deploy/compose/.env，例如：
# - CORS_ALLOWED_ORIGINS=http://你的服务器IP

# 国内 / 慢网构建（推荐写入 .env；.env.example 已默认填国内镜像）：
# NPM_REGISTRY=https://registry.npmmirror.com
# MAVEN_MIRROR=https://maven.aliyun.com/repository/public
```

### 4. 启动平台

优先用封装脚本（会预拉基础镜像并**失败即停**、可选国内源、低内存串行构建、可 `RESUME=` 续跑）：

```bash
# 国内 ECS 推荐
USE_CN_MIRRORS=1 bash deploy/scripts/compose-build.sh
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d

# ≤2 GiB：务必 PREBUILT（见 4b），不要在目标机源码构建
USE_CN_MIRRORS=1 LOWMEM=1 PREBUILT=1 bash deploy/scripts/compose-build.sh
docker compose -f deploy/compose/compose.yaml -f deploy/compose/compose.prebuilt.yaml \
  -f deploy/compose/compose.lowmem.yaml --env-file deploy/compose/.env up -d

# 半成功续跑（例如 backend 已成、frontend 失败）
RESUME=frontend USE_CN_MIRRORS=1 LOWMEM=1 bash deploy/scripts/compose-build.sh
```

#### 4a. 标准路径（≥4 GiB 内存，手写命令）

```bash
# 先用 dockerd 拉基础镜像（走 daemon.json registry-mirrors；
# docker compose build / buildx 常常不继承该加速器）
docker pull node:22-alpine nginx:1.27-alpine \
  maven:3.9.9-eclipse-temurin-21 eclipse-temurin:21-jre

# 关闭 BuildKit，复用 dockerd 本地层，避免 buildx 再慢吞吞拉一遍
DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0 \
  docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env \
  build --pull=false
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d
```

也可显式传 build arg（不必写进 .env）：

```bash
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env \
  build --pull=false \
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com \
  --build-arg MAVEN_MIRROR=https://maven.aliyun.com/repository/public
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d
```

#### 4b. 低内存机（≤2 GiB）：串行构建或预构建

```bash
# 方案 A：封装脚本（停 backend/frontend → 串行 build → 再 up）
USE_CN_MIRRORS=1 LOWMEM=1 ./deploy/scripts/compose-build.sh
docker compose -f deploy/compose/compose.yaml -f deploy/compose/compose.lowmem.yaml \
  --env-file deploy/compose/.env up -d

# 方案 B（推荐）：在较强机器上预构建 JAR/dist，再同步到小内存机
cd backend && ./mvnw -DskipTests package && cd ..
cd frontend && npm ci && npm run build && cd ..
# 然后用 remote-deploy.sh PREBUILT=1，或本地：
USE_CN_MIRRORS=1 PREBUILT=1 LOWMEM=1 ./deploy/scripts/compose-build.sh
docker compose -f deploy/compose/compose.yaml \
  -f deploy/compose/compose.prebuilt.yaml \
  -f deploy/compose/compose.lowmem.yaml \
  --env-file deploy/compose/.env up -d
```

健康检查（与 Compose `healthcheck` 对齐，用 **liveness / readiness**）：

```bash
curl -fsS http://localhost/actuator/health/liveness
curl -fsS http://localhost/actuator/health/readiness
# 可选：整体 /actuator/health（graph=false 时不应再因 Neo4j 变 DOWN）
curl -fsS http://localhost/actuator/health
```

### 5. 构建加速说明

| 问题 | 做法 |
|---|---|
| npm / Maven 下载极慢（国内常见 30–60min） | `USE_CN_MIRRORS=1` 或 `.env` 已默认的 `NPM_REGISTRY` / `MAVEN_MIRROR` |
| 配了 `registry-mirrors` 但 compose build 仍慢 | buildx 不继承 dockerd 加速器；先 `docker pull`（脚本会重试并失败即停），并用 `DOCKER_BUILDKIT=0` |
| ≤2GiB 上 `up --build` OOM / 过慢 | **不要**在目标机构建；`PREBUILT=1` 或 `LOAD_IMAGES=1` |
| 清空镜像后冷启动仅 prefetch 就 20min+ | 日常升级勿 `docker system prune -a`；可用 `docker save` / 私有 registry（见下） |
| frontend 半失败 | `RESUME=frontend LOWMEM=1 bash deploy/scripts/compose-build.sh` |

#### 保存 / 加载镜像层（避免小 VPS 反复拉 Hub）

```bash
# 在已构建成功的机器上
docker save archops-backend:latest archops-frontend:latest \
  nginx:1.27-alpine eclipse-temurin:21-jre \
  pgvector/pgvector:pg16 redis:7-alpine \
  | gzip > /tmp/archops-images.tar.gz

# 拷到目标机后
docker load -i /tmp/archops-images.tar.gz
LOAD_IMAGES=1 SKIP_BUILD=1 bash deploy/scripts/remote-deploy.sh root@HOST
```

### 6. 安装后检查清单

- [ ] 登录并修改默认 `admin` 密码
- [ ] 在 **资产管理** 中添加服务器资产
- [ ] 为各资产配置 SSH 凭证
- [ ] 配置 `OPENAI_API_KEY` 或接入 Ollama
- [ ] 首次部署后以管理员 JWT 调用 `POST /api/knowledge/reindex` 初始化 RAG
- [ ] 为 Nginx 配置 TLS（反向代理或云负载均衡）
- [ ] 防火墙仅开放 80/443

## 备份

### PostgreSQL

```bash
docker exec archops-postgres pg_dump -U archops archops > backup_$(date +%Y%m%d).sql
```

### Redis

Redis 使用 AOF（`appendonly yes`），数据在 Docker 卷 `redis_data` 中。

### 恢复

```bash
cat backup_20260101.sql | docker exec -i archops-postgres psql -U archops archops
```

## 密钥轮换（`JWT_SECRET` / `CREDENTIALS_MASTER_KEY`）

平台密钥解析优先级：环境变量 → 密钥文件（`archops.secrets.path`，Compose 下默认 `./data/secrets.properties`）→ 首次启动自动生成。

### 影响说明

| 密钥 | 保护内容 | 轮换影响 |
|--------|------------------|-----------------|
| `JWT_SECRET` | 签发 access/refresh JWT | **全部会话立即失效**，用户需重新登录。 |
| `CREDENTIALS_MASTER_KEY` | 加密 SSH 凭证与 AI Provider API Key | **旧密文无法解密**，需重新录入凭证与 Provider Key。库中已有 RAG embedding 不受影响。 |

自动生成的密钥会写入 secrets 卷一次；之后手动轮换效果相同。

### 推荐步骤

1. **安排维护窗口** — 需重启后端并重新登录。
2. **备份 PostgreSQL**（见上文）再轮换 `CREDENTIALS_MASTER_KEY`。
3. **生成新值**（至少 32 随机字节，可用 base64），例如 `openssl rand -base64 32`。
4. **更新配置：** 在 `deploy/compose/.env` 设置，或编辑 `archops_secrets` 卷中的 `jwt.secret` / `credentials.master-key`。
5. **重启后端**（`docker compose up -d`）。
6. **轮换 `CREDENTIALS_MASTER_KEY` 后：**
   - 在 **资产管理** 中重新录入 SSH 凭证
   - 在 **设置 → AI 设置** 中重新录入 Provider API Key
   - 若同时更换了 embedding 模型/维度，再执行 `POST /api/knowledge/reindex`
7. **通知用户** `JWT_SECRET` 变更后需重新登录。

### 无需重做的事项

- Flyway 迁移
- 资产主机/端口清单（仍在 PostgreSQL）
- 平台 AI 设置行（Provider ID、RAG 开关等）— 仅加密字段需重录

## 升级

```bash
git pull
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d --build
```

后端启动时会自动执行 Flyway 迁移。
