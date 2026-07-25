# 部署指南

## Docker Compose（推荐，单机）

低内存 VPS 的远程初始化与同步脚本用法，见 [test-deploy-server.md](./test-deploy-server.md)。

### 1. 服务器要求

| 资源 | 最低 | 推荐 |
|---|---|---|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 40 GB | 100 GB SSD |
| 系统 | Ubuntu 22.04+ / Debian 12+ / CentOS Stream 9+ | |

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
```

### 4. 启动平台

```bash
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d --build
```

健康检查：

```bash
curl http://localhost/actuator/health
```

### 5. 安装后检查清单

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
