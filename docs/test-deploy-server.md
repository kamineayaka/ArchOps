# 测试 / 部署服务器

将一台 Linux 主机变为 ArchOps 的 **测试 / 部署** 机（Docker Compose）。

**默认验证目标：kamiserver**（见 `.cursor/rules/remote-kamiserver.mdc`）。Neo4j 为图库存 SSOT 的必选依赖，主机内存需 **≥4 GiB**（建议 8 GiB）；`<4 GiB` 时 `remote-deploy.sh` / `compose-build.sh` 会直接拒绝。

## 目标主机（请按实际填写）

| 项 | 值 |
|---|---|
| Host | `YOUR_HOST`（默认验证可用 `kamiserver`） |
| OS | Ubuntu 24.04 LTS（或其他兼容发行版） |
| SSH | `user@YOUR_HOST`（**必须 SSH 密钥**；脚本使用 `BatchMode=yes`，不支持交互密码） |
| 内存 | **≥4 GiB**（推荐 8 GiB） |
| 应用路径 | `/opt/archops` |
| 版本戳 | `/opt/archops-releases/VERSION`（`/opt/archops/VERSION` 为其软链） |
| URL | `http://YOUR_HOST` |

> 不要把密码或私钥提交进 Git。脚本**只支持密钥登录**——首次务必 `ssh-copy-id`，就绪后可关闭密码登录。

本机私有备注可写在 `docs/test-deploy-server.local.md`（已 gitignore，不会进仓库）。

## 一次性初始化

在已具备 SSH 密钥访问的工作站上：

```bash
# 首次安装公钥（密码登录仅用于这一步）
ssh-copy-id user@YOUR_HOST

# 验证免密
ssh -o BatchMode=yes user@YOUR_HOST 'echo ok'

# 确保 Docker、创建 /opt/archops 与 /opt/archops-releases
./deploy/scripts/remote-provision.sh user@YOUR_HOST
```

## 配置环境

```bash
cp deploy/compose/.env.example deploy/compose/.env
# 编辑 CORS 与 Neo4j 密码（≥8 字符）：
# CORS_ALLOWED_ORIGINS=http://YOUR_HOST,http://localhost
# NEO4J_PASSWORD=archopsneo4j

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
# 推荐：本机构建 + 同步（默认验证机 kamiserver）
cd backend && ./mvnw -DskipTests package && cd ..
cd frontend && npm ci && npm run build && cd ..
USE_CN_MIRRORS=1 PREBUILT=1 bash deploy/scripts/remote-deploy.sh kamiserver

# 通用主机
USE_CN_MIRRORS=1 PREBUILT=1 bash deploy/scripts/remote-deploy.sh user@YOUR_HOST

# 可选：收紧容器限额（主机仍须 ≥4 GiB；不会关闭 Neo4j）
USE_CN_MIRRORS=1 PREBUILT=1 LOWMEM=1 bash deploy/scripts/remote-deploy.sh user@YOUR_HOST
```

### 可选：别处构建镜像，再加载到 VPS

```bash
docker compose -f deploy/compose/compose.yaml build
docker save archops-backend archops-frontend | gzip > /tmp/archops-images.tar.gz
LOAD_IMAGES=1 SKIP_BUILD=1 ./deploy/scripts/remote-deploy.sh user@YOUR_HOST
```

镜像名遵循 Compose 项目命名（目录 / `-p` 为 `archops` 时一般为 `archops-*`）。

## 验证

```bash
# 与 Compose healthcheck 一致（推荐）
curl -fsS http://YOUR_HOST/actuator/health/liveness
curl -fsS http://YOUR_HOST/actuator/health/readiness
# 登录：admin / admin123  — 立即修改
cat /opt/archops-releases/VERSION   # 或 ssh 上查看本次部署戳
```

readiness 会包含 Neo4j；Neo4j DOWN 时整体 readiness 应失败。

## 内存门槛

| 模式 | 内存 | 做法 |
|---|---|---|
| **全栈运行（必选 Neo4j）** | ≥4 GiB（推荐 8） | `PREBUILT=1` 或本机构建后部署 |
| **本机构建** | ≥4 GiB 推荐 8 | `USE_CN_MIRRORS=1 bash deploy/scripts/compose-build.sh` |
| **&lt;4 GiB** | 不支持 | 脚本拒绝；勿再「关 Neo4j 将就跑」 |

半成功续跑：

```bash
RESUME=frontend USE_CN_MIRRORS=1 bash deploy/scripts/compose-build.sh
```
