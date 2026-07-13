# CloudOps 后续工作清单

> 基于「自动密钥 + 动态 AI Provider」落地后的讨论整理。  
> 仓库：https://github.com/kamineayaka/CloudOps  
> 最近大版本：`a421e27` — 自动密钥引导、DB 驱动 AI Provider、RAG 嵌入绑定、AI 设置页。

**使用方式：** 按优先级从上到下推进；每项完成后勾选 `[x]`，单独 commit，PR 合并前跑通 CI。

---

## 已完成（参考）

- [x] 平台密钥自动引导（`PlatformSecretStore`，Compose volume）
- [x] Flyway V6：`ai_provider` + `platform_ai_settings`
- [x] AI Provider CRUD / 平台设置 API / 加密存储
- [x] `LlmRuntimeFactory`（OpenAI 兼容 + Anthropic）+ Agent / WebSocket `providerId`
- [x] RAG 嵌入从 DB Provider 解析（`DbEmbeddingProvider`）
- [x] 前端 AI 设置页（`/settings/ai`）+ 聊天下拉选 Provider
- [x] `OPENAI_API_KEY` 环境变量一次性种子迁移

---

## P0 — 质量与回归（优先）

| # | 任务 | 说明 | 完成标准 |
|---|------|------|----------|
| [x] P0-1 | **单元测试：`PlatformSecretStore`** | 覆盖 env > 文件 > 生成 优先级、首次持久化、重启复用 | `mvn test` 通过，临时目录测试 |
| [x] P0-2 | **单元测试：`LlmRuntimeFactory`** | OpenAI / Anthropic 请求体构造、API Key 加解密 | mock 或快照测试 |
| [x] P0-3 | **集成测试：AI Provider CRUD** | `@SpringBootTest` + Testcontainers Postgres | 创建/更新/删除/脱敏/默认 Provider 约束 |
| [x] P0-4 | **CI 全绿验证** | GitHub Actions 执行 `mvn verify`（含 Testcontainers 集成测试）+ `npm run build` | backend / frontend jobs 均通过 |

---

## P1 — 产品体验

| # | 任务 | 说明 | 完成标准 |
|---|------|------|----------|
| [x] P1-1 | **英文 UI（`en-US`）** | 新增 `frontend/src/locales/en-US.ts`，顶栏语言切换 | 主要页面双语可切换 |
| [x] P1-2 | **清理硬编码中文** | 如 `AssetsView`「已配置」等迁入 i18n | grep 无用户可见硬编码 |
| [x] P1-3 | **密钥轮换文档** | `docs/deployment.md`：轮换 `JWT_SECRET` / `CREDENTIALS_MASTER_KEY` 影响 | 文档可照做 |
| [x] P1-4 | **Embedding 切换指引** | 设置页或知识库 API 返回 reindex 提示文案 | 切换 Provider/dims 后有明确提示 |
| P1-5 | **审批后 Agent 续跑** | 工具 `PENDING_APPROVAL` 后用户批准可继续 ReAct 循环 | 独立 Issue，非本阶段必须 |

---

## P2 — 运维 MCP 工具（平台内置 `McpTool`）

当前仅 2 个内置工具：`list_assets`、`ssh_exec`（`backend/src/main/java/com/cloudops/mcp/`）。  
AI Agent 通过 `ToolRegistry` 注册，经 `ToolExecutorService` 走风险分级与审批门控。

### P2-A 建议新增的内置工具（Java 实现）

| 工具名 | 用途 | 风险建议 | 依赖模块 |
|--------|------|----------|----------|
| `get_asset` | 按 ID/名称查单资产详情（含凭证是否已配置） | LOW | `AssetService` |
| `batch_ssh_exec` | 对多资产并行只读诊断（uptime/df/docker ps） | MEDIUM | `SshExecTool` 复用 |
| `knowledge_search` | RAG 语义检索，返回 Top-K 片段 | LOW | `RagRetrievalService` |
| `get_architecture` | 返回最新架构快照摘要 | LOW | `ArchitectureSnapshotRepository` |
| `append_work_log` | 记录运维操作摘要到知识库 | LOW | `WorkLog` + 触发索引 |
| `list_pending_approvals` | 列出当前用户待审批项 | LOW | `ApprovalService` |
| `prometheus_query` | PromQL 即时查询（可选，需 observability 栈） | LOW | HTTP → Prometheus |
| `kubectl_get` | 经 SSH 在目标节点执行 `kubectl get`（包装） | MEDIUM | `ssh_exec` 模式 |

**实现约定：**

1. 每个工具实现 `McpTool` 接口，加 `@Component`，自动注册。
2. 在 `RiskClassifier` 中为每个工具配置默认风险等级。
3. 写操作 / 批量执行默认 MEDIUM 或 HIGH，走审批。
4. 工具描述面向 LLM，写清参数 schema 与使用场景。
5. 为每个工具补单元测试或集成测试（mock SSH / HTTP）。

### P2-B 内置 MCP 文档与示例

| # | 任务 | 说明 |
|---|------|------|
| P2-B1 | 新增 `docs/mcp-tools.md` | 列出所有内置工具、参数、风险等级、审批策略 |
| P2-B2 | 更新 `README.zh-CN.md` 工具表 | 与文档同步 |
| P2-B3 | Agent System Prompt 优化 | `AiAgentService` 中提示优先 `list_assets` → 只读诊断 → 写操作需审批 |

---

## P3 — 外部 MCP Server（Cursor / 开发机侧）

CloudOps **平台内** Agent 使用 Java `McpTool`；**开发/运维同学在 Cursor IDE** 中可额外接入标准 MCP Server，加速日常排障与批量操作。  
建议在仓库新增 `docs/mcp-cursor-setup.md`（配置示例，密钥不进 Git）。

### 推荐引入或自研的 MCP

| MCP | 场景 | 来源建议 | 优先级 |
|-----|------|----------|--------|
| **SSH** | 远程命令、文件读写、批量巡检 | 已有 `user-ssh` 或自研 `cloudops-ssh-mcp` 对接平台资产 API | 高 |
| **GitHub** | Issue/PR/CI 状态、发布 | 官方或社区 GitHub MCP | 高 |
| **Prometheus** | 指标查询、告警上下文 | 社区 Prometheus MCP 或薄封装 HTTP | 中 |
| **Kubernetes** | 集群资源 describe/logs（若用 K8s） | 社区 k8s MCP | 中 |
| **PostgreSQL / Redis** | 只读 SQL、缓存诊断 | 社区 DB MCP（限内网、只读账号） | 中 |
| **JobLens / Spark** | 大数据作业提交、Yarn 日志（若集群有 Spark） | 参考现有 JobLens MCP 模式自研 | 低（按环境） |
| **CloudOps API** | 自研 MCP：调平台 REST（资产、审批、知识库、AI） | **建议自研** `mcp-cloudops`，统一认证 | 高 |

### P3 自研 `mcp-cloudops` 建议能力

```
tools:
  - cloudops_list_assets
  - cloudops_ssh_exec          # 代理到平台 API，继承 RBAC + 审批
  - cloudops_search_knowledge
  - cloudops_create_approval
  - cloudops_chat              # 可选：把自然语言转平台 Agent
```

**技术选型：** TypeScript `@modelcontextprotocol/sdk` 或 Python `mcp`，读 `CLOUDOPS_URL` + `CLOUDOPS_TOKEN`。

| # | 任务 | 说明 |
|---|------|------|
| P3-1 | 脚手架 `tools/mcp-cloudops/` | package.json / README / 最小 `list_assets` |
| P3-2 | `docs/mcp-cursor-setup.md` | `.cursor/mcp.json` 示例（SSH + cloudops） |
| P3-3 | CI 可选 job | 仅 lint/build MCP 包，不阻塞主流程 |

---

## P4 — 架构与部署（按需）

| # | 任务 | 说明 |
|---|------|------|
| P4-1 | Ollama Provider 类型 | `ProviderType.OLLAMA`，UI 可配（计划外，可后续） |
| P4-2 | Helm Secret 与密钥引导对齐 | K8s 仍可用 Secret 覆盖 env |
| P4-3 | WebSocket 流式对话 | `AiStreamWebSocketHandler` 真流式 token（当前 Agent 偏同步） |
| P4-4 | 可观测性栈默认 Dashboard | Grafana 预置 CloudOps 面板 |

---

## 不在近期范围

- Intlayer 等 i18n 第三方（当前仅 zh-CN，手写维护即可）
- 审批通过后全自动续跑（见 P1-5，单独立项）
- 多租户 / 多组织隔离

---

## Cloud Agent 启动模板（出门无人值守时用）

将下面内容贴到 [Cursor Cloud Agents](https://cursor.com/dashboard?tab=cloud-agents)，并附上本文件链接：

```text
仓库：kamineayaka/CloudOps，分支 main。
按 docs/TODO.md 从 P0 开始顺序执行；每完成一项：
1. mvn verify && cd frontend && npm run build
2. 单独 commit（说明 why）
3. 全部完成后开 PR，不要直接 force push main

当前迭代重点：P0 测试 + P2 内置 MCP 工具（先做 get_asset、knowledge_search、batch_ssh_exec）。

约束：
- 不修改 .cursor/plans/ 下文件
- 最小 diff，匹配现有代码风格
- 破坏性命令必须走 ApprovalGate，不得绕过
```

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-12 | 初版：P0–P4 + MCP 内置/外部分工 + Cloud Agent 模板 |
