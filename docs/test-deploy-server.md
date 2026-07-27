# 测试 / 部署服务器

将一台 Linux VPS 变为 ArchOps 的 **测试 / 部署** 主机（Docker Compose；内存 ≤ 2 GiB 时使用 lowmem overlay）。

## 目标主机（请按实际填写）

| 项 | 值 |
|---|---|
| Host | `YOUR_HOST` |
| OS | Ubuntu 24.04 LTS（或其他兼容发行版） |
| SSH | `root@YOUR_HOST`（**必须 SSH 密钥**；脚本使用 `BatchMode=yes`，不支持交互密码） |
| 应用路径 | `/opt/archops` |
| 版本戳 | `/opt/archops-releases/VERSION`（`/opt/archops/VERSION` 为其软链） |
| 公网 URL | `http://YOUR_HOST` |

> 不要把密码或私钥提交进 Git。脚本**只支持密钥登录**——首次务必 `ssh-copy-id`，就绪后可关闭密码登录。

本机私有备注可写在 `docs/test-deploy-server.local.md`（已 gitignore，不会进仓库）。

## 一次性初始化

在已具备 SSH 密钥访问的工作站上：

```bash
# 首次安装公钥（密码登录仅用于这一步）
ssh-copy-id root@YOUR_HOST

# 验证免密
ssh -o BatchMode=yes root@YOUR_HOST 'echo ok'

# 扩大 swap、确保 Docker、创建 /opt/archops 与 /opt/archops-releases
./deploy/scripts/remote-provision.sh root@YOUR_HOST
```

## 配置环境

```bash
cp deploy/compose/.env.example deploy/compose/.env
# 编辑 CORS（以及可选的 OPENAI_API_KEY）：
# CORS_ALLOWED_ORIGINS=http://YOUR_HOST
# ≤2 GiB 主机建议：
# JAVA_OPTS=-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m

# 国内 / 慢网构建镜像源（会传给 Dockerfile）：
# NPM_REGISTRY=https://registry.npmmirror.com
# MAVEN_MIRROR=https://maven.aliyun.com/repository/public
```

`deploy/compose/.env` 已被 gitignore — 只保留在本机 / 服务器。

## 远端同步范围（只传部署必要文件）

`remote-deploy.sh` **不会**把整仓镜像到 `/opt/archops`。规则见 `deploy/rsync-deploy.filter`。

| 上传 | 不上传（ignore） |
|---|---|
| `deploy/`（compose、脚本、filter；不含 `.env`，本机有则单独 `scp`） | `docs/`、`README.md`、`LICENSE`、`SECURITY.md` |
| `backend/`、`frontend/`（见下） | `.git/`、`.cursor/`、IDE 配置、`node_modules/` |
| | 密钥 / `.env*`（除 `.env.example`） |

**`PREBUILT=1`（推荐）** 远端只要能打 runtime 镜像：

- `backend/Dockerfile.prebuilt` + `backend/target/*.jar`
- `frontend/Dockerfile.prebuilt` + `frontend/nginx.conf` + `frontend/dist/`
- 源码树（`src/`、`pom.xml`、`package.json` 等）会被 exclude，并清理旧同步残留

**未设 PREBUILT** 才同步前后端源码（仍排除 `node_modules/`、本地 `target/`/`dist/`），供远端 `docker compose build`。

他人部署最小步骤：拿齐上述 payload + 本机 `deploy/compose/.env`（或服务器上从 `.env.example` 生成），再跑本脚本。

## 部署 / 升级

```bash
# 国内 ECS 强烈推荐带上 USE_CN_MIRRORS=1（否则 npm/Maven 可能卡 30–60+ 分钟）
USE_CN_MIRRORS=1 ./deploy/scripts/remote-deploy.sh root@YOUR_HOST

# 默认：lowmem + 远端源码构建（脚本会：docker pull → 串行 build → up）
./deploy/scripts/remote-deploy.sh root@YOUR_HOST

# ≤2 GiB 主机更推荐：先在较强机器上构建 JAR/dist，再 PREBUILT
# （小内存机上同时跑 Maven + npm 极易 OOM）
cd backend && ./mvnw -DskipTests package && cd ..
cd frontend && npm ci && npm run build && cd ..
USE_CN_MIRRORS=1 PREBUILT=1 ./deploy/scripts/remote-deploy.sh root@YOUR_HOST

# 主机内存 ≥4 GiB 时可用满配
USE_CN_MIRRORS=1 LOWMEM=0 ./deploy/scripts/remote-deploy.sh root@YOUR_HOST
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
cat /opt/archops-releases/VERSION   # 或 ssh 上查看本次部署戳
```

## 1.6–2 GiB VPS 注意

- **不要**在低内存机上直接 `docker compose up -d --build`（Maven + npm 并行极易 OOM）。
- 始终使用 `compose.lowmem.yaml`（脚本默认 `LOWMEM=1`）。
- 保持至少 4 GiB swap。
- 优先 `PREBUILT=1` 或 `LOAD_IMAGES=1`。
- 若 Docker Hub / 镜像源失败（如 TLS handshake timeout），可从已缓存层重建：

```bash
./deploy/scripts/rebuild-images-from-cache.sh
docker compose -p archops -f compose.yaml -f compose.images.yaml -f compose.lowmem.yaml --env-file .env up -d
```
