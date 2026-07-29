# 部署指南

API 一览见 [api.md](./api.md)。

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

## 配置与启动

```bash
cd deploy/compose
cp .env.example .env
```

编辑 `.env`：

- `CORS_ALLOWED_ORIGINS` — 浏览器访问来源（含本机与服务器地址）
- `NEO4J_PASSWORD` — ≥8 字符
- `JWT_SECRET` / `CREDENTIALS_MASTER_KEY` — 可留空，首次启动自动生成并写入卷
- 可选：`NPM_REGISTRY` / `MAVEN_MIRROR`（慢网构建加速）
- 可选：`OPENAI_API_KEY`（种子 AI Provider；也可在控制台配置）

```bash
docker compose up -d --build
```

健康检查：

```bash
curl -fsS http://localhost/actuator/health/liveness
curl -fsS http://localhost/actuator/health/readiness
```

默认登录：`admin` / `admin123`（立即修改）。

## 上线检查清单

- [ ] 修改默认管理员密码
- [ ] 配置 `CORS_ALLOWED_ORIGINS`
- [ ] 确认 `NEO4J_PASSWORD` ≥8 字符
- [ ] 在图编辑中录入资产与凭证，或导入现有数据
- [ ] 配置 AI Provider（控制台或 `OPENAI_API_KEY`）
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
git pull   # 或同步新版本源码
docker compose up -d --build
```

Flyway 会在后端启动时自动迁移；**不要改已有** `V{N}__*.sql`，只新增下一个版本。
