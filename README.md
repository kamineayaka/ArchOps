# ArchOps AI Platform

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**ArchOps AI Platform** 是一套面向 Linux 集群的云原生智能运维控制平面（B/S）。统一 Web 控制台集成：**图拓扑库存（Neo4j SSOT）**、AI 运维 Agent、Web SSH 操作台、架构事实 / Hybrid RAG、分级审批与防篡改审计。

适用于单台 VPS 到小规模生产主机；仓库内远程脚本可复用到**你自己的** Linux 主机（需 SSH 密钥），不绑定某一台固定机器。

## 功能模块

| 模块 | 说明 |
|---|---|
| **用户与 RBAC** | JWT 认证、角色（ADMIN / OPERATOR / VIEWER）、单会话挤下 |
| **拓扑图 / 图编辑** | Neo4j 为库存拓扑 SSOT；浏览全图、双击/右键连接进操作台；变更走草稿 → 提案 → 合并 |
| **凭证与连接** | SSH 等凭证 AES-256-GCM 加密；跳板语义由图边 `CONNECTS_VIA` 表达 |
| **SSH 连接池** | 按用户/资产复用会话；终端与 `ssh_exec` 共用 |
| **Web 操作台** | 浏览器终端（xterm.js + MINA SSHD）+ 会话坞 |
| **AI Agent** | ReAct 工具循环；可固定对话目标资产；只读图工具 `graph_neighborhood` / `graph_path` |
| **Hybrid RAG** | 以 Neo4j 邻域 + 架构事实为主，pgvector 文本记忆为辅 |
| **架构知识** | 分区事实 SSOT、提案审批合并（含图 ChangeSet） |
| **审批工作流** | 风险分级（LOW / MEDIUM / HIGH）与人工门控 |
| **审计中心** | 追加日志 + SHA-256 哈希链 |

## 快速开始（Docker Compose）

### 环境要求

- Docker 24+ 与 Docker Compose v2
- 最低 2 核、**4 GB 内存**（建议 8 GB；≤2 GB 用[预构建 / lowmem](docs/deployment.md)，勿直接并行 `--build`）
- 可选：启用图能力时需更大内存 + Compose `--profile graph`（≈2 GB 机不建议开 Neo4j）
- OpenAI 兼容 / Ollama 等（可在「AI 设置」配置；也可用 `OPENAI_API_KEY` 种子迁移）
- 前端开发：Node.js 22+

### 本机 / 单机部署

```bash
git clone https://github.com/kamineayaka/ArchOps.git
cd ArchOps

cp deploy/compose/.env.example deploy/compose/.env
# 编辑 CORS_ALLOWED_ORIGINS=http://你的主机IP,http://localhost
# 国内/慢网建议在 .env 中设置 NPM_REGISTRY / MAVEN_MIRROR，或构建时加 USE_CN_MIRRORS=1

USE_CN_MIRRORS=1 ./deploy/scripts/compose-build.sh
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d

# ≥4 GiB 也可：
# docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d --build
```

**可选：启用 Neo4j 图库存**

```bash
# .env: ARCHOPS_GRAPH_ENABLED=true
docker compose -f deploy/compose/compose.yaml --profile graph --env-file deploy/compose/.env up -d
```

≤2 GiB VPS：用 `LOWMEM=1` / `compose.lowmem.yaml`，优先 `PREBUILT=1`；细节见 [docs/deployment.md](docs/deployment.md)、[docs/test-deploy-server.md](docs/test-deploy-server.md)。

浏览器访问 **http://你的服务器IP**，默认账号：

- 用户名：`admin`
- 密码：`admin123`

**首次登录后请立即修改密码。**

建议管理员执行一次 `POST /api/knowledge/reindex` 初始化文本记忆索引。API：[docs/api.md](docs/api.md)。图设计：[docs/graph-ssot-design.md](docs/graph-ssot-design.md)。

### 远程 VPS 部署（给其他克隆者）

脚本是**通用的**，参数换成你自己的 `user@host` 即可（需已配置 SSH 公钥，不支持交互密码）：

```bash
# 一次性：swap≥4G、Docker、/opt/archops
./deploy/scripts/remote-provision.sh root@YOUR_HOST

# 推荐：本机构建后再同步（小内存机）
USE_CN_MIRRORS=1 PREBUILT=1 ./deploy/scripts/remote-deploy.sh root@YOUR_HOST
```

### 图与操作台快速路径

1. 在 **图编辑** 中添加 SERVER 节点并暂存凭证，提交提案并合并。  
2. 在 **拓扑图** 双击或右键「连接」跳转 **操作台**。  
3. 在 **Agent** 中选择目标资产后自然语言提问；拓扑类问题优先看图上下文 / `graph_*` 工具。

## 本地开发

```bash
# 依赖（无需 MinIO）
docker compose -f deploy/compose/compose.yaml up -d postgres redis
# 需要图时：再起 neo4j（--profile graph）并设置 ARCHOPS_GRAPH_ENABLED=true

cd backend && ./mvnw spring-boot:run   # :8080
cd frontend && npm install && npm run dev   # :5173
```

## 项目结构

```
ArchOps/
├── backend/           Spring Boot 3（Java 21）+ Flyway + 可选 Neo4j
├── frontend/          Vue 3 + Naive UI（拓扑图 / 图编辑 / 操作台 / Agent）
├── deploy/
│   ├── compose/       Docker Compose（含 lowmem / graph profile / prebuilt）
│   └── scripts/       本机构建与远程 provision/deploy
└── docs/              部署、API、图 SSOT 设计
```

## 部署方式

| 方式 | 适用场景 | 文档 |
|---|---|---|
| Docker Compose | 单机 / MVP / 小规模生产 | [docs/deployment.md](docs/deployment.md) |
| 远程脚本 | 任意可 SSH 的 Linux（含小内存 VPS） | [docs/test-deploy-server.md](docs/test-deploy-server.md) |
| API 速查 | 集成 / 排查 | [docs/api.md](docs/api.md) |

## 技术栈

| 层级 | 技术选型 |
|---|---|
| 后端 | Java 21、Spring Boot 3、Flyway、PostgreSQL + pgvector、Redis、可选 Neo4j |
| 前端 | Vue 3、Naive UI、Pinia、Cytoscape |
| AI | OpenAI 兼容 / Ollama；进程内 Agent 工具；Hybrid RAG（图 + 事实 + 向量） |
| 部署 | Docker Compose、Nginx |

## 安全

见 [SECURITY.md](SECURITY.md)。生产请务必：修改默认密钥与管理员密码；仅暴露 80/443 并上 TLS；限制 actuator 暴露面。

## 许可证

[MIT](LICENSE)
