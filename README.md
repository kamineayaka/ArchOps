# ArchOps AI Platform

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**ArchOps AI Platform** 是一套面向 Linux 集群的云原生智能运维控制平面（B/S）。统一 Web 控制台集成：**图拓扑库存（Neo4j SSOT）**、AI 运维 Agent、Web SSH 操作台、架构事实 / Hybrid RAG、分级审批与防篡改审计。

## 功能模块

| 模块 | 说明 |
|---|---|
| **用户与 RBAC** | JWT 认证、角色（ADMIN / OPERATOR / VIEWER）、单会话挤下 |
| **拓扑图 / 图编辑** | Neo4j 为库存拓扑 SSOT；浏览全图、双击/右键连接进操作台；变更走草稿 → 提案 → 合并 |
| **凭证与连接** | SSH 等凭证 AES-256-GCM 加密；跳板语义由图边 `CONNECTS_VIA` 表达 |
| **SSH 连接池** | 按用户/资产复用会话；终端与 `ssh_exec` 共用 |
| **Web 操作台** | 浏览器终端（xterm.js + MINA SSHD）+ 会话坞 |
| **AI Agent** | ReAct 工具循环；目标资产上下文；只读图工具 + `propose_graph_change` / `propose_architecture_update`（提案审批） |
| **Hybrid RAG** | 以 Neo4j 邻域 + 架构事实为主，pgvector 文本记忆为辅 |
| **架构知识** | 分区事实 SSOT、提案审批合并（含图 ChangeSet） |
| **审批工作流** | 风险分级（LOW / MEDIUM / HIGH）与人工门控 |
| **审计中心** | 追加日志 + SHA-256 哈希链 |

## 快速开始（Docker Compose）

### 环境要求

- Docker 24+ 与 Docker Compose v2
- 建议 2 核、**≥4 GB 内存**（推荐 8 GB；Neo4j 为必选依赖）
- 可选：OpenAI 兼容 / Ollama（可在「AI 设置」配置）
- 前端本地开发：Node.js 22+

### 部署

```bash
git clone https://github.com/kamineayaka/ArchOps.git
cd ArchOps/deploy/compose

cp .env.example .env
# 编辑 CORS_ALLOWED_ORIGINS=http://你的主机IP,http://localhost
# 确认 NEO4J_PASSWORD（≥8 字符）
# 慢网可选：取消注释 NPM_REGISTRY / MAVEN_MIRROR

docker compose up -d --build
```

浏览器访问 **http://你的服务器IP**，默认账号 `admin` / `admin123`（**首次登录后请立即修改密码**）。

默认管理员审批策略为 `MANUAL_A`（连 LOW 风险工具也需审批）。运维账号建议改为 `RISK_BASED_B`（LOW 自动、MEDIUM/HIGH 人工）。

建议管理员执行一次 `POST /api/knowledge/reindex` 初始化文本记忆索引。  
更多说明：[docs/deployment.md](docs/deployment.md)、[docs/api.md](docs/api.md)、[docs/graph-ssot-design.md](docs/graph-ssot-design.md)。

### 图与操作台快速路径

1. 在 **图编辑** 中添加 SERVER 节点并暂存凭证，提交提案并合并。  
2. 在 **拓扑图** 双击或右键「连接」跳转 **操作台**。  
3. 在 **Agent** 中选择目标资产后自然语言提问。

## 本地开发

```bash
# 依赖
docker compose -f deploy/compose/compose.yaml up -d postgres redis neo4j

cd backend && ./mvnw spring-boot:run   # :8080
cd frontend && npm install && npm run dev   # :5173
```

## 项目结构

```
ArchOps/
├── backend/           Spring Boot 3（Java 21）+ Flyway + Neo4j
├── frontend/          Vue 3 + Naive UI
├── deploy/compose/    Docker Compose
└── docs/              部署、API、图 SSOT 设计
```

## 技术栈

| 层级 | 技术选型 |
|---|---|
| 后端 | Java 21、Spring Boot 3、Flyway、PostgreSQL + pgvector、Redis、Neo4j |
| 前端 | Vue 3、Naive UI、Pinia、Cytoscape |
| AI | OpenAI 兼容 / Ollama；进程内 Agent 工具；Hybrid RAG（图 + 事实 + 向量） |
| 部署 | Docker Compose、Nginx |

## 安全

见 [SECURITY.md](SECURITY.md)。生产请务必：修改默认密钥与管理员密码；仅暴露 80/443 并上 TLS；限制 actuator 暴露面。

## 许可证

[MIT](LICENSE)
