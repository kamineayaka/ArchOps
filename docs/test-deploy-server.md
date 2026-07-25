# 测试 / 部署服务器

将一台 Linux VPS 变为 ArchOps 的 **测试 / 部署** 主机（Docker Compose；内存 ≤ 2 GiB 时使用 lowmem overlay）。

## 目标主机（请按实际填写）

| 项 | 值 |
|---|---|
| Host | `YOUR_HOST` |
| OS | Ubuntu 24.04 LTS（或其他兼容发行版） |
| SSH | `root@YOUR_HOST`（推荐 **SSH 密钥**，勿用密码） |
| 应用路径 | `/opt/archops` |
| 公网 URL | `http://YOUR_HOST` |

> 不要把密码或私钥提交进 Git。首次用 `ssh-copy-id`，就绪后可关闭密码登录。

本机私有备注可写在 `docs/test-deploy-server.local.md`（已 gitignore，不会进仓库）。

## 一次性初始化

在已具备 SSH 密钥访问的工作站上：

```bash
# 首次安装公钥
ssh-copy-id root@YOUR_HOST

# 扩大 swap、确保 Docker、创建 /opt/archops
./deploy/scripts/remote-provision.sh root@YOUR_HOST
```

## 配置环境

```bash
cp deploy/compose/.env.example deploy/compose/.env
# 编辑 CORS（以及可选的 OPENAI_API_KEY）：
# CORS_ALLOWED_ORIGINS=http://YOUR_HOST
# ≤2 GiB 主机建议：
# JAVA_OPTS=-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m
```

`deploy/compose/.env` 已被 gitignore — 只保留在本机 / 服务器。

## 部署 / 升级

```bash
# 默认：lowmem overlay + 远端源码构建
./deploy/scripts/remote-deploy.sh root@YOUR_HOST

# ≤2 GiB 主机推荐：先在较强机器上构建 JAR/dist
cd backend && ./mvnw -DskipTests package && cd ..
cd frontend && npm ci && npm run build && cd ..
PREBUILT=1 ./deploy/scripts/remote-deploy.sh root@YOUR_HOST

# 主机内存 ≥4 GiB 时可用满配
LOWMEM=0 ./deploy/scripts/remote-deploy.sh root@YOUR_HOST
```

### 可选：别处构建镜像，再加载到 VPS

```bash
docker compose -f deploy/compose/compose.yaml build
docker save archops-backend archops-frontend | gzip > /tmp/archops-images.tar.gz
LOAD_IMAGES=1 SKIP_BUILD=1 ./deploy/scripts/remote-deploy.sh root@YOUR_HOST
```

镜像名遵循 Compose 项目命名（目录 / `-p` 为 `archops` 时一般为 `archops-*`）。

## 验证

```bash
curl -fsS http://YOUR_HOST/actuator/health
# 登录：admin / admin123  — 立即修改
```

## 1.6–2 GiB VPS 注意

- 始终使用 `compose.lowmem.yaml`（脚本默认 `LOWMEM=1`）。
- 保持至少 4 GiB swap。
- 若 VPS 上 Maven/`npm` 容易 OOM，优先预构建或加载镜像。
- 若 Docker Hub / 镜像源失败（如 TLS handshake timeout），可从已缓存层重建：

```bash
./deploy/scripts/rebuild-images-from-cache.sh
docker compose -p archops -f compose.yaml -f compose.images.yaml -f compose.lowmem.yaml --env-file .env up -d
```
