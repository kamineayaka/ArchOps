# 部署指南

API 一览见 [api.md](./api.md)。

ArchOps **唯一交付模式**为镜像即交付物：使用方只需 Compose 与 `.env`，通过 `image:` 拉取（或离线 `load`）预构建镜像，**不支持**在目标机用 Compose 从源码 `build`。

## 要求

| 项 | 建议 |
|---|---|
| CPU | ≥2 核 |
| 内存 | **≥4 GiB**（推荐 8 GiB） |
| 软件 | Docker 24+、Compose v2 |

Neo4j 与 Postgres、Redis 同级，为必选依赖。

## 安装 Docker（若尚未安装）

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
```

## 使用方：在线拉取

发布方已将 `backend` / `frontend` 推到镜像仓库后：

```bash
# 只需 deploy/compose（无需整个 Git 仓库也可）
cd deploy/compose
cp .env.example .env
```

编辑 `.env`：

- `ARCHOPS_IMAGE_PREFIX` / `ARCHOPS_VERSION` — 与发布方推送的镜像一致
- `CORS_ALLOWED_ORIGINS` — 浏览器访问来源（含本机与服务器地址）
- `NEO4J_PASSWORD` — ≥8 字符
- `JWT_SECRET` / `CREDENTIALS_MASTER_KEY` — 可留空，首次启动自动生成并写入卷
- 可选：`OPENAI_API_KEY`（种子 AI Provider；也可在控制台配置）

```bash
docker compose up -d
```

Compose 会拉取：

| 服务 | 镜像 |
|---|---|
| backend | `${ARCHOPS_IMAGE_PREFIX}/backend:${ARCHOPS_VERSION}` |
| frontend | `${ARCHOPS_IMAGE_PREFIX}/frontend:${ARCHOPS_VERSION}` |
| postgres | `pgvector/pgvector:pg16`（官方） |
| redis | `redis:7-alpine`（官方） |
| neo4j | `neo4j:5.26-community`（官方） |

## 使用方：离线 tar 包

目标机不能访问镜像仓库时：

**发布方：**

```bash
bash deploy/scripts/build-images.sh
bash deploy/scripts/package-offline.sh
# 得到 archops-images-<version>.tar，连同 deploy/compose/ 一并拷贝
```

**使用方：**

```bash
bash deploy/scripts/load-offline.sh archops-images-latest.tar
cd deploy/compose
cp .env.example .env   # 确认 ARCHOPS_IMAGE_PREFIX / ARCHOPS_VERSION 与打包时一致
docker compose up -d
```

离线包内已含 Postgres / Redis / Neo4j 官方镜像；`compose.yaml` 中的 `image:` 名称与 tag 必须与 `docker load` 后的镜像完全一致。

## 发布方：构建与推送镜像

仅在完整源码树中打镜像（交付产物），不是目标机部署路径：

```bash
# 可选：写入 deploy/compose/.env 中的 ARCHOPS_VERSION / ARCHOPS_IMAGE_PREFIX
# 慢网可设 NPM_REGISTRY / MAVEN_MIRROR

bash deploy/scripts/build-images.sh
docker login ghcr.io   # 或你的私有仓库
bash deploy/scripts/push-images.sh
```

## 健康检查与登录

```bash
curl -fsS http://localhost/actuator/health/liveness
curl -fsS http://localhost/actuator/health/readiness
```

默认登录：`admin` / `admin123`（立即修改）。

Agent：默认管理员审批策略 `MANUAL_A`（连 LOW 工具也需审批）；运维账号建议 `RISK_BASED_B`。会话授权不含 `propose_*` 提案工具。

## 上线检查清单

- [ ] 修改默认管理员密码
- [ ] 配置 `CORS_ALLOWED_ORIGINS`
- [ ] 确认 `NEO4J_PASSWORD` ≥8 字符
- [ ] 确认 `ARCHOPS_IMAGE_PREFIX` / `ARCHOPS_VERSION` 可拉取（或已离线 load）
- [ ] 在图编辑中录入资产与凭证，或导入现有数据
- [ ] 配置 AI Provider（控制台或 `OPENAI_API_KEY`）
- [ ] 确认运维账号审批策略（建议 `RISK_BASED_B`）
- [ ] 管理员调用 `POST /api/knowledge/reindex` 初始化 RAG（如需）
- [ ] 生产环境为 80/443 配置 TLS；限制 actuator 暴露面

## 备份

### PostgreSQL

```bash
docker exec archops-postgres pg_dump -U archops archops > backup_$(date +%Y%m%d).sql
```

### Redis / Neo4j

数据在 Docker 卷 `redis_data`、`neo4j_data` 中；密钥在 `archops_secrets`。

### 恢复

```bash
cat backup_YYYYMMDD.sql | docker exec -i archops-postgres psql -U archops archops
```

## 密钥轮换（`JWT_SECRET` / `CREDENTIALS_MASTER_KEY`）

解析优先级：环境变量 → 密钥文件（`archops.secrets.path`）→ 首次启动自动生成。

| 密钥 | 保护内容 | 轮换影响 |
|---|---|---|
| `JWT_SECRET` | access/refresh JWT | 全部会话失效，需重新登录 |
| `CREDENTIALS_MASTER_KEY` | SSH / Provider 密文 | 旧密文无法解密，需重新录入 |

推荐：维护窗口内备份 PostgreSQL → 生成新密钥（如 `openssl rand -base64 32`）→ 写入 `.env` 或 secrets 卷 → `docker compose up -d` → 重新录入凭证并通知用户重新登录。

## 升级

```bash
cd deploy/compose
# 修改 .env 中 ARCHOPS_VERSION=新版本（或保持 latest 后 pull）
docker compose pull
docker compose up -d
```

Flyway 会在后端启动时自动迁移；**不要改已有** `V{N}__*.sql`，只新增下一个版本。
