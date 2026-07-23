# ArchOps AI Platform

[English](README.md) | 中文文档

[![CI](https://github.com/kamineayaka/ArchOps/actions/workflows/ci.yml/badge.svg)](https://github.com/kamineayaka/ArchOps/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**ArchOps AI Platform** 是一套面向 Linux 服务器集群的云原生智能运维控制平面，采用 B/S 架构。它提供统一的 Web 管理界面，集成 AI 辅助运维、Web SSH 终端、进程内工具注册表、知识库 RAG、分级审批工作流，以及可防篡改的审计日志。

适用于从单台 VPS 到生产级集群的部署场景，方便其他开发者自行搭建与二次开发。

## 功能模块

| 模块 | 说明 |
|---|---|
| **用户与 RBAC** | JWT 认证、角色权限（ADMIN / OPERATOR / VIEWER）、单会话挤下 |
| **资产管理** | 服务器 / 集群 / 服务清单，SSH 凭证 AES-256-GCM 加密存储 |
| **SSH 连接池** | 服务端按用户/资产复用 SSH 会话，支持预热 API，终端与 `ssh_exec` 共用 |
| **Web SSH 终端** | 浏览器内终端（xterm.js + MINA SSHD），基于连接池，支持 PTY 缩放 |
| **AI Agent** | ReAct 工具调用循环；对话可固定目标资产，`ssh_exec` 无需每次传 `assetId` |
| **内置工具注册表** | 可扩展的进程内 Agent 工具（`ssh_exec`、`list_assets` 等） |
| **审批工作流** | 按 RBAC 分级风险识别（LOW / MEDIUM / HIGH）与人工门控 |
| **知识库** | 架构快照 + 工作日志 + pgvector RAG 语义检索 |
| **审计中心** | 追加写入日志 + SHA-256 哈希链防篡改校验 |
| **可观测性** | Prometheus 指标、Grafana 仪表盘、Loki 日志聚合 |

## 快速开始（Docker Compose）

### 环境要求

- Docker 24+ 与 Docker Compose v2
- 最低 2 核 CPU、4 GB 内存（启用可观测性栈建议 8 GB）
- OpenAI 兼容 API Key 或 Anthropic API Key（可在控制台「AI 设置」中配置；也可通过 `OPENAI_API_KEY` 环境变量种子迁移）
- 前端开发需 Node.js 22+

### 部署步骤

```bash
git clone https://github.com/kamineayaka/ArchOps.git
cd ArchOps

# 配置环境（JWT/凭证密钥可留空，首次启动自动生成并持久化）
cp deploy/compose/.env.example deploy/compose/.env
# 可选：在 deploy/compose/.env 中设置 OPENAI_API_KEY，首次启动会自动迁移为默认 AI Provider
# 也可在部署后于 Web 控制台「AI 设置」中配置多个 Provider

# 启动平台
docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env up -d --build

# （可选）启动可观测性栈
docker compose -f deploy/compose/compose.observability.yaml up -d
```

浏览器访问 **http://你的服务器IP**，使用默认账号登录：

- 用户名：`admin`
- 密码：`admin123`

**首次登录后请立即修改默认密码。**

首次部署后，建议以管理员身份调用 `POST /api/knowledge/reindex` 初始化 RAG 向量索引。

### SSH 连接池与 AI 目标资产

1. 在 **资产管理** 中录入资产并保存 SSH 凭证。
2. 在 **AI 运维** 页为当前对话选择 **目标资产**（保存后会自动预热连接池）。
3. 直接用自然语言提问（如「检查磁盘使用情况」），Agent 会对已选资产执行 `ssh_exec`，无需每次指定 `assetId`。
4. **Web 终端** 复用同一连接池；也可调用 `POST /api/ssh/pool/{assetId}/warm` 手动预热。

详见 [docs/ssh-connection-pool-design.md](docs/ssh-connection-pool-design.md)。

## 本地开发

```bash
# 仅启动依赖服务
docker compose -f deploy/compose/compose.yaml up -d postgres redis minio

# 后端（端口 8080）
cd backend && ./mvnw spring-boot:run

# 前端（端口 5173，需 Node.js 22+）
cd frontend && npm install && npm run dev
```

## 项目结构

```
ArchOps/
├── backend/           Spring Boot 3（Java 21），模块化包结构
├── frontend/          Vue 3 + Naive UI + TypeScript
├── deploy/
│   ├── compose/       Docker Compose（单节点 + 可观测性）
│   └── helm/          Kubernetes Helm Chart
├── docker/            共享 Dockerfile 与基础设施配置
├── docs/              架构与部署文档
└── .github/workflows/ CI 流水线
```

## 部署方式

| 方式 | 适用场景 | 文档 |
|---|---|---|
| Docker Compose | 单机 / MVP / 小规模生产 | [docs/deployment.md](docs/deployment.md) |
| Kubernetes Helm | 生产高可用（Chart 脚手架） | [deploy/helm/](deploy/helm/) |

## 技术栈

| 层级 | 技术选型 |
|---|---|
| 后端 | Java 21、Spring Boot 3、Flyway、PostgreSQL + pgvector、Redis |
| 前端 | Vue 3、Naive UI、Pinia、vue-i18n |
| AI | OpenAI 兼容 API / Ollama，进程内 Agent 工具，pgvector RAG |
| 部署 | Docker Compose、Nginx、Prometheus / Grafana / Loki |

产品愿景与整体思想见 [docs/product-vision.md](docs/product-vision.md)。  
主线实施计划（Living Architecture）见 [docs/mainline-implementation-plan.md](docs/mainline-implementation-plan.md)，Agent Prompt 见 [docs/mainline-implementation-prompt.md](docs/mainline-implementation-prompt.md)。  
更多工程架构说明见 [docs/architecture.md](docs/architecture.md)。

## 安全

漏洞报告流程与生产加固清单见 [SECURITY.md](SECURITY.md)。

生产环境请务必：

- 修改 `JWT_SECRET`、`CREDENTIALS_MASTER_KEY` 等默认密钥
- 修改默认管理员密码
- 仅对外暴露 80/443，并配置 TLS
- 按需限制 `/actuator/prometheus` 等监控端点访问

## 参与贡献

欢迎提交 Issue 与 Pull Request，请参阅 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

[MIT](LICENSE)
