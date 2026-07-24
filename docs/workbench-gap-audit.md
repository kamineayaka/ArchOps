# ArchOps 工作台缺口审计（对照 OpsKat + 内部 Bug）

> 触发：用户反馈资产表单过简、SSH 页不像 IDE、找不到 AI 配置、创建资产无反应、刷新 403；以及「SSH≈IDE / Agent≈侧栏」产品隐喻。  
> OpsKat 源码：`github.com/opskat/opskat`。  
> 相关：[`opskat-learning.md`](opskat-learning.md)、[`product-vision.md`](product-vision.md)、[`mainline-implementation-plan.md`](mainline-implementation-plan.md)。

---

## 0. 结论先说

1. **之前对 OpsKat 的态度过「防守」**：强调「不要照搬桌面模型」是对的，但低估了 **SSH 连接表单、终端工作台壳、AI Provider 表单** 这些可以 **几乎按字段照搬到 B/S** 的部分。  
2. ArchOps 主线（Architecture SSOT / Proposal / RAG）仍然正确，且 OpsKat **没有**；但 **没有合格的 SSH/AI 工作台，主线无法被用户触达**。  
3. 当前仓库里存在 **可复现的 P0 Bug**（角色字符串、nginx `/assets`、创建失败静默），解释了「创建没反应 / 刷新 403 / 找不到 AI 设置」。  
4. 产品隐喻应定为：**终端工作台 = IDE Window；Agent = Agent Window（Cursor 式）**。二者并列，不是「先进 AI 全页再想起 SSH」。

---

## 1. 重新审视 OpsKat 的参考价值

| 维度 | 旧判断 | 修正后 |
|------|--------|--------|
| 定位 | 单人桌面，少学 UI | **工作台 UX 与连接模型是一等参考**；桌面 IPC/socket 仍不照搬 |
| SSH 表单 | 「我们有凭证弹窗就行」 | **创建资产必须一次配齐可连接字段**（用户图 1）；凭证可仍加密存储 |
| 终端页 | 侧轨是 ML-8 增强 | **核心产品面**：左树 + 多 Tab 终端 + 可唤出 Agent（用户图 2） |
| AI 配置 | 有 Provider CRUD 即可 | **表单字段应对齐 OpsKat**（用户图 3）：拉模型、max tokens、context、reasoning |
| 资产种类 | 先 SERVER/K8S | 类型 SPI 已立项；**应尽快露出 DATABASE 等注册类型**，GUI 可渐进 |
| 知识/记忆 | Description 勿当 SSOT | **坚持**；ArchOps 用 Architecture Proposal，不跟 Description 路线 |

**一句话：** OpsKat = AI-powered SSH/运维工作台；ArchOps = 同级工作台 **+** 组织级 Architecture 记忆。工作台部分应大胆借鉴甚至字段级对齐；记忆与治理部分走我们自己的主线。

---

## 2. OpsKat 可借鉴 / 可照搬清单（按用户三图）

### 2.1 添加 SSH 资产（图 1）→ **应字段级对齐**

OpsKat 关键路径：

- `AssetForm.tsx` + `SSHConfigSection.tsx` + `SSHConfigSection.config.ts`
- 凭证：`PasswordSourceField` / managed credential / key
- 测试：`TestAssetConnection`（约 10s，可取消）

| 字段/能力 | OpsKat | ArchOps 现状 | 建议 |
|-----------|--------|--------------|------|
| 资产类型选择 | 有 | kind 下拉有，但创建表单弱 | 保留 SPI；创建弹窗按类型渲染 ConfigSection |
| 名称 | 有 | 有 | 保留 |
| 分组 | 有 | 有 Groups 页，创建表单未强绑定 | **创建表单内选分组** |
| 连接方式 直连/跳板 | 有 | jump 在凭证弹窗 | **并入创建/编辑主表单** |
| 主机 / 端口 | 有 | 有 | 端口默认 22 |
| 用户名 | 有 | 仅凭证弹窗默认 root | **创建时必填或默认 root** |
| 认证 密码/密钥 | 有 | 凭证弹窗有 | **创建流程一次完成** |
| 密码来源 手动/凭据库 | 有 | 无凭据库 UX | 先「手动输入」；凭据库可二期 |
| 描述 | 有 | 无 | 增加 description（**不作 SSOT**） |
| **测试连接** | 有 | **无** | **P0 必做** `POST /api/assets/test-connection` |
| 保存前校验提示 | 有 | 前端几乎无 | 名称/主机必填前端校验 |

### 2.2 SSH 工作台（图 2）→ **应布局级对齐（B/S 实现）**

OpsKat：`AssetTree` + `TopTabBar` 多终端 Tab + `SideAssistantPanel`（AI）。

| 能力 | OpsKat | ArchOps 现状 | 建议 |
|------|--------|--------------|------|
| 左栏资产树 | 分组树 + 搜索 | AppLayout 有 `AssetNavTree`，体验弱 | 终端页默认展开；点击即开 Tab |
| 多会话 Tab | 有 | **单下拉 + 单 xterm** | **P0：多 Tab 终端**（每资产一会话） |
| 连接状态条 | user@host \| 时长 | 弱 | 状态条：user@host、延迟/重连 |
| AI 入口 | 顶栏/侧栏唤出 | 侧轨默认关且难发现 | **终端内常驻「打开 Agent」**；隐喻= Agent Window |
| SFTP | 有 | 无 | P2 |
| 分屏终端 | 有 | 无 | P2 |

### 2.3 添加 AI 提供商（图 3）→ **应字段级对齐 / 可照搬结构**

OpsKat：`AIProviderForm.tsx` + `FetchAIModels` + reasoning effort。

| 字段 | OpsKat | ArchOps 现状 | 建议 |
|------|--------|--------------|------|
| 类型 OpenAI 兼容 / Anthropic | 有 | 有 | 对齐文案与默认 Base URL |
| 名称 | 有 | 有 | — |
| 模型 + **获取模型** | 有 | 部分有 fetch | **保证按钮可用 + 错误提示** |
| API 地址 | 有 | 有 | 默认 openai/anthropic |
| API Key | 有 | 有 | 脱敏 |
| 最大输出 Token | 有 | 需核对是否暴露 | **表单露出，0=默认** |
| 上下文窗口 | 有 | 需核对 | **表单露出，0=默认** |
| 思考深度 reasoning | 有（off/low/…/max） | **很可能缺失** | **按 OpsKat 枚举照搬到 API+UI** |
| 首次向导 | `AISetupWizard` | ML-8-07 已立项未完成感 | 无 Provider 时强制引导 |
| 入口可见性 | Settings 清晰 | **ROLE bug 导致找不到** | 先修 Bug，再做向导 |

### 2.4 资产种类（数据库 / K8s / Kafka…）

OpsKat 注册：ssh, local, serial, k8s, database, redis, mongodb, kafka, etcd, oss, vnc, rdp。

ArchOps 已有 kind：`SERVER, CLUSTER, SERVICE, NETWORK, DATABASE`（SPI 方向），但：

- 创建体验仍像「只有服务器」；
- **缺少按类型的连接动作**（terminal / query / page）；
- DATABASE 等无专用面板。

**建议分期：**

| 期 | 内容 |
|----|------|
| P0 | SERVER SSH 表单+测试连接+终端 IDE 壳+AI 配置对齐 |
| P1 | K8s 作为「集群页/只读概览」或经 SSH kubectl（不必一次做满 OpsKat K8s GUI） |
| P1 | DATABASE：类型配置（host/port/user/db）+ 测试连接；Query 面板可后置 |
| P2 | Redis / Kafka / Mongo 等按同一 SPI 加类型；面板复用 query 壳 |

---

## 3. ArchOps 内部 Bug 清单（已核对代码）

### P0 — 阻塞使用

| ID | 现象 | 根因 | 位置 | 修复方向 |
|----|------|------|------|----------|
| **BUG-01** | 创建资产点了没反应 | `handleCreate` 无 `try/catch`、失败无 `message.error`；后端 400（缺 host/name）被静默 | `frontend/src/views/AssetsView.vue` ~123–137 | ~~待修~~ **已在 workbench-gap 分支修复**：校验+toast |
| **BUG-02** | 刷新资产页 nginx 403 | SPA 路由 `/assets` 与 Vite 静态目录 `/assets/` 冲突 | `frontend/nginx.conf` | ~~待修~~ **已修复**：`try_files $uri /index.html;` |
| **BUG-03** | 找不到 / 进不去 AI 设置 | API 角色 `ADMIN`，前端判断 `ROLE_ADMIN` | AppLayout / router / Dashboard | ~~待修~~ **已修复**：`utils/roles.ts` 归一化 |
| **BUG-04** | 创建资产不能一次配 SSH | 用户名/密码/密钥只在「凭证」二步弹窗；无测试连接 | AssetsView + API | 合并为 OpsKat 式表单；新增 test-connection |

### P1 — 体验严重受损

| ID | 现象 | 根因 | 修复方向 |
|----|------|------|----------|
| **BUG-05** | 终端不像 IDE | 单会话、无多 Tab；树切换不 `watch` route | 多 Tab 会话管理；`watch(() => route.params.assetId)` |
| **BUG-06** | Agent 与终端割裂 | AI 侧轨默认关；无「IDE+Agent」隐喻引导 | 终端页默认提供打开 Agent；空 Provider 引导去设置 |
| **BUG-07** | 凭证保存同样静默失败 | `handleSaveCredential` 同 BUG-01 | 同错误处理 |
| **BUG-08** | VIEWER 操作 403 非 JSON | 无 `AccessDeniedException` 处理器 | GlobalExceptionHandler |
| **BUG-09** | AI Provider 高级字段不全 | 缺 reasoning / context 等与 OpsKat 对齐 | 扩展 DTO + 表单（照搬枚举） |

### P2 — 债务

| ID | 说明 |
|----|------|
| BUG-10 | `deploy/compose/nginx.conf` 可能缺 `/ws/`（若误用则终端挂） |
| BUG-11 | WebSocket 握手硬编码弱角色（历史问题） |
| BUG-12 | 资产 kind 校验：CLUSTER 可不填 host，易建「空壳」资产 |

---

## 4. 产品结构：IDE Window ↔ Agent Window

```
┌─────────────┬──────────────────────────────┬─────────────────┐
│  资产树      │   SSH / 终端 Tabs（IDE）        │  Agent（可选）   │
│  分组/搜索   │   xterm · 状态条 · 重连         │  对话 · 工具     │
│  + 添加资产  │   （用户亲手操作资产）            │  · 提案卡片      │
└─────────────┴──────────────────────────────┴─────────────────┘
         │                              │
         │         共用 SSH 池           │
         └──────────────┬───────────────┘
                        ▼
              Architecture SSOT + RAG（主线记忆）
```

- **IDE（SSH 页）**：人直接操作；多 Tab；连接配置必须完备。  
- **Agent**：自然语言驱动同一批资产；上下文来自目标+RAG+facts（不是 Description）。  
- **二者并列**，类似 Cursor 的编辑区与 Agent 区。

---

## 5. 建议实施波次（可直接派 Agent）

### Wave W0 — 修 Bug（立刻）

1. BUG-01/07 错误提示 — **已在 main（#18）**；本波补 `roles`/`apiError` 与 nginx SPA 回归脚本  
2. BUG-02 nginx `try_files` — **已在 main**；线上需 redeploy（勿用 `$uri/`）  
3. BUG-03 角色字符串 — **已在 main**（`utils/roles.ts`）  
4. 回归：`npm test` + `npm run check:nginx`；admin 可见 AI 设置；创建失败有 toast；刷新 `/assets` → 200

### Wave W1 — SSH 可连（对齐图 1） — **本分支交付**

1. 创建/编辑 SERVER：主机、端口、用户、认证、密钥/密码、分组、描述、跳板（`SshAssetForm`）  
2. `POST /api/assets/test-connection` + `POST /api/assets/{id}/test-connection`（共用 `AssetSshDialer`）  
3. 保存时一次写入凭证；列表展示「可连接」

### Wave W2 — 终端 IDE + Agent（对齐图 2） — **本分支交付**

1. 多 Tab 终端会话（`useTerminalSessions`）  
2. 左树点击打开/聚焦 Tab（路由 `terminal/:assetId` watch）  
3. 终端内「打开 Agent」侧轨；进入终端默认展开资产树  
4. 状态条：连接状态 · user@host · 时长 · 重连

### Wave W3 — AI 配置对齐图 3（Provider 工作台）

**目标：** Admin 能在 5 分钟内配好可用 Provider；表单字段与 OpsKat「添加提供商」对齐，可字段级照搬结构。  
**产品位置：** Settings → AI（`/settings/ai`）；无 Provider 时强制向导。  
**依赖：** W0 BUG-03（角色可见性）已修复；现有 `AiProvider` CRUD。  
**对照：** OpsKat `AIProviderForm.tsx` / `AISetupWizard.tsx` / `ai_provider_entity`。

| ID | 任务 | 实现要点 | 完成标准 | 状态 |
|----|------|----------|----------|------|
| **W3-01** | Provider 表单字段对齐 | 类型（OpenAI 兼容 / Anthropic）、名称、模型、API 地址（默认 URL + 帮助文案）、API Key（脱敏）、**最大输出 Token**（0=默认）、**上下文窗口**（0=默认）、**思考深度 reasoning**（`none/low/medium/high/xhigh/max`，max 仅 Anthropic） | 与图 3 字段一一对应；i18n zh/en；保存进 DB | [x] |
| **W3-02** | 后端 DTO / 实体补齐 | `AiProvider` + Request/Response 含 `maxOutputTokens`、`contextWindow`、`reasoningEnabled`、`reasoningEffort`；Flyway 如缺列则新增；运行时传给 `LlmRuntime` | 单测：序列化/校验；Anthropic+max 归一化规则（可参考 OpsKat） | [x] |
| **W3-03** | 获取模型 | 「获取模型」调用 Provider `/models`（或已有 fetch API）；下拉可选 + 可手输；失败可读错误 | 填 Key+Base URL 后一点击即可列出模型 | [x] |
| **W3-04** | 测试连通 | 「测试连接」或向导内 Test：最小 chat/models 探活；超时与错误文案 | 错误 Key / 错误 URL 有明确 toast，不静默 | [x] |
| **W3-05** | 首次向导（合并 ML-8-07） | 无 active Provider 时：Dashboard / AI 页 / 侧轨弹出向导：类型→地址/Key→Test→拉模型→设默认→完成 | 新部署可走完；与 Settings 页共用同一 API，不双写 | [x] |
| **W3-06** | Agent 使用配置 | Chat / 侧轨可选 Provider；`maxOutputTokens` / `contextWindow` / reasoning 实际作用于请求 | 改 Provider 后新消息生效；有集成或手工验收清单 | [x] |
| **W3-07** | 文档与验收 | 更新 API 契约（若有 Provider 段）；本文件勾选 W3 | 对照图 3 字段清单全部打勾 | [x] |

**W3 不做：** API Key 明文进前端日志；多租户密钥隔离；改 Architecture SSOT 语义。

**推荐 PR 拆分：** W3-01+02 → W3-03+04 → W3-05+06 → W3-07。

---

### Wave W4 — 资产种类扩展（Database / K8s / Kafka…）

**目标：** 不止「服务器 / 集群」；类型靠 SPI 扩展；每种类型有**可保存配置 + 测试连接 + connectAction**，查询/管理面板可后置。  
**依赖：** ML-1-06 类型 SPI；W1 SSH 表单模式（按类型 ConfigSection）。  
**对照：** OpsKat `assetTypes/*` + `openAsset.ts` connect 矩阵。

| ID | 任务 | 实现要点 | 完成标准 | 状态 |
|----|------|----------|----------|------|
| **W4-01** | SPI 与文档固化 | `docs/adding-an-asset-type.md`：后端 Handler + 前端 `registerAssetType` + ConfigSection + Test + connectAction；禁止共享 `switch(kind)` | 按文档能加 stub 类型而不改调度核心 | [x] W4a |
| **W4-02** | connectAction 模型 | 统一：`terminal` \| `query` \| `page` \| `none`；资产树/列表「连接」按注册表分发 | SERVER→terminal；未实现面板的类型友好提示而非抛错 | [x] W4a |
| **W4-03** | DATABASE 最小可配 | host、port、username、password、database 可选、跳板可选；`test-connection`（至少 TCP，可选 JDBC 探活） | 可创建/编辑/测试；列表有类型标签 | [x] W4a（创建+测试；跳板 ID 可存，探活直连） |
| **W4-04** | K8s 最小可配 | API server URL **或**「跳板 SSH + kubectl」二选一；kubeconfig/凭证加密；测试只读 `version`/`get ns` | 可保存可测；**不做**完整 K8s 控制台 | [x] W4b |
| **W4-05** | Kafka 最小可配（可与 Redis 二选一先做） | bootstrap、可选 SASL、跳板；测试连通或 list topics | 类型可选可测；面板可后置 | |
| **W4-06** | Query 壳（可选同期） | 通用查询页：选资产 → 简单 SQL/命令 → 结果表；先接 DATABASE 只读或 Redis PING | 至少一种非 SSH 类型能在 UI 执行一条只读操作 | |
| **W4-07** | Agent 工具扩展 | `list_assets` 按 type 过滤；只读 `db_ping` / `k8s_get`（LOW）；写操作走审批 | 越权资产拒绝 | |
| **W4-08** | 验收剧本 | `docs/workbench-w4-acceptance.md` | 创建→测试→（可选）query→Agent 只读 可打勾 | |

**W4 子波：**

| 子波 | 内容 |
|------|------|
| **W4a** | W4-01 + W4-02 + W4-03（DATABASE） |
| **W4b** | W4-04（K8s） |
| **W4c** | W4-05 + W4-06（Kafka/Redis + Query 壳） |
| **W4d** | W4-07 + W4-08（Agent 工具 + 验收） |

**W4 不做：** 一次做齐 RDP/VNC/SFTP/全量 Kafka 管理台；Description 当 Architecture；绕过审批的写工具。

---

### 波次总览（派工用）

| 波次 | 主题 | 状态指引 |
|------|------|----------|
| W0 | P0 Bug | 已合入 main（#18）；回归 |
| W1 | SSH 表单 + 测试连接 | 对齐图 1（本分支/后续 PR） |
| W2 | 多 Tab 终端 + Agent 侧栏 | 对齐图 2 |
| **W3** | **AI Provider 表单 / 向导 / reasoning** | **已交付（本波次）；对齐图 3；W3-01…07** |
| **W4** | **多资产类型 SPI 落地** | **W4a 进行中（DATABASE）；W4b–d 后续** |

**建议顺序：** W0 → W1 → W2 → **W3**（可与 **W4a** 并行）→ W4b→W4d。

---

## 6. 与现有 ML / OpsKat 文档关系

| 原任务 | 调整 |
|--------|------|
| ML-1-06 类型 SPI | 保留；**加速**并绑定创建表单 ConfigSection |
| ML-1-07 跳板 | 并入 W1 主表单，不单挂凭证 |
| ML-8-06 AI 侧轨 | **升为工作台 P0**，不是锦上添花 |
| ML-8-07 Provider 向导 | 与 W3 合并；字段以 OpsKat 为准可照搬 |
| ML-4-07 Prompt 槽位 | 继续；侧轨打开时注入 uiContext=terminal |
| 「不要照搬 OpsKat」 | **收窄范围**：不照搬桌面进程/socket/Description-SSOT；**表单与工作台壳鼓励对齐** |

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-23 | 初版：用户三图反馈 + OpsKat 重读 + ArchOps Bug 审计 + W0–W4 波次 |
| 2026-07-23 | 正式展开 W3（W3-01…07）与 W4（W4-01…08 / W4a–d）可派工计划 |
| 2026-07-23 | W0 确认已合入 main（#18）并补回归；W1 SSH 表单+测试连接；W2 终端 IDE 多 Tab |
| 2026-07-24 | W3 交付：Provider 字段/Flyway/runtime、拉模型+测连通 toast、首次向导（Dashboard/AI/侧轨）、Agent 生效、契约勾选 |
| 2026-07-24 | W4a：SPI/文档固化、connectAction 分发、DATABASE 表单+TCP/JDBC 探活 |
| 2026-07-24 | W4b：K8S 类型（API Server+/version 或跳板 kubectl get ns）；connectAction=page |
