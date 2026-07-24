# ArchOps 主线实施 — Agent 执行 Prompt

> 复制下方 Prompt 到 Cloud Agent / 本地 Agent。  
> 任务清单：`docs/mainline-implementation-plan.md`（`ML-*`）  
> 北极星：`docs/product-vision.md`  
> OpsKat 学习：`docs/opskat-learning.md`  
> 品牌：**ArchOps**（勿再写 CloudOps）

**现状：** ML-0…ML-8 核心主线已在 main 落地。当前优先未完成项为 **OpsKat 第二波**：`ML-1-06/07`、`ML-3-07`、`ML-4-07`、`ML-8-06/07`。

---

## 一键 Prompt — OpsKat 第二波（推荐，复制以下全部）

```text
你是 ArchOps AI Platform 的交付 Agent。ML-0…ML-8 核心主线（Living Architecture / Proposal / RAG）已完成。
当前只做 docs/mainline-implementation-plan.md 中仍为 [ ] 的 **OpsKat 第二波** 任务。

## 必读（按顺序）

1. docs/product-vision.md — §3–4（SSOT / Proposal / L0L1L2）；写回规则不可放松
2. docs/opskat-learning.md — 全文，尤其「值得学的」与「不要照搬」
3. docs/mainline-implementation-plan.md — 搜索 ML-1-06、ML-1-07、ML-3-07、ML-4-07、ML-8-06、ML-8-07
4. docs/architecture.md — 当前工程结构（包名 com.archops）

## 仓库与分支

- 仓库：kamineayaka/ArchOps
- 基线：最新 origin/main
- 分支：cursor/ml-<task-id-小写>-71f3
  例：cursor/ml-1-06-asset-type-spi-71f3
- Commit：feat(scope): ML-1-06 简短说明
- 每项单独 Draft PR；勾选清单 [x]

## 推荐顺序（有依赖）

1. ML-1-06 资产类型 SPI（后端 Handler + 前端 register + docs/adding-an-asset-type.md）
2. 可并行：ML-1-07 Jump/proxy chain ；ML-8-07 Provider 向导
3. ML-4-07 Prompt 槽位组装（学 OpsKat PromptBuilder，内容必须是 Architecture/RAG/facts，禁止 Description 当 SSOT）
4. ML-3-07 执行 Grant + decision_source（只覆盖执行工具，不绕过知识 Proposal）
5. ML-8-06 AI 侧轨工作台壳（资产树 + 可钉住助手；可与 ML-4-07 uiContext 联动）

## 硬约束（违反即停）

1. 学 OpsKat 的注册表 / Grant / 槽位 / 侧轨 / 跳板；**不要**桌面 Unix socket、三套 SSH 路径、Description 当架构记忆。
2. 终端 WebSSH 与 ssh_exec **必须继续共用** SSH 池；跳板拨号走同一 Dialer。
3. Grant 不能自动合并 Architecture；不能让 HIGH 默认可 grant（除非产品配置显式允许且文档说明）。
4. 扩展资产类型靠 SPI 注册，禁止在共享调度里 switch(kind)。
5. 品牌 ArchOps / com.archops；前端薄客户端。
6. 不一次性实现完整 DB/Kafka/RDP GUI（stub 类型可以）。

## 验证

```bash
cd backend && ./mvnw verify
# 无 Docker 时：./mvnw -Dtest='!*IntegrationTest' verify
cd frontend && npm ci && npm run build
```

完成后更新 docs/mainline-implementation-plan.md 勾选与「ML-OpsKat 第二波」汇总表。

开始：从 ML-1-06 创建分支并实现（若用户指定了其他 ML-*-06/07，则从其依赖检查后执行）。
```

---

## 一键 Prompt — 完整主线（仅当从空仓库/分叉重做时用）

```text
你是 ArchOps 主线交付 Agent。实现「活的 Architecture + Work Log + RAG」闭环。
必读：docs/product-vision.md → docs/mainline-implementation-plan.md → docs/opskat-learning.md → docs/architecture.md。
从第一个 [ ] 的 ML 任务开始；已完成项勿重做。
硬约束同 docs/mainline-implementation-prompt.md「产品硬约束」+ OpsKat「不要照搬」。
分支 cursor/ml-<id>-71f3；验证 mvn verify + npm run build；Draft PR。
```

---

## 含 OpsKat 的分阶段 Prompt

### 阶段 O1 — 类型可扩展 + 跳板（ML-1-06 / ML-1-07）

```text
仓库 ArchOps，基线 origin/main。

必读：docs/opskat-learning.md §资产类型/SSH；docs/mainline-implementation-plan.md 中 ML-1-06、ML-1-07。

请完成：
1) ML-1-06 资产类型 SPI：后端 AssetTypeHandler 自注册 + 前端 registerAssetType；先覆盖现有 SERVER（及已有 kind）；可加 stub 证明 OCP；新增 docs/adding-an-asset-type.md。
2) ML-1-07 Jump/proxy chain：有序 SSH 跳板；Dialer 与 SSH 池统一服务终端与 ssh_exec。

不要做：完整 DB/RDP GUI；不要改 Architecture Proposal 语义。
分支建议：可一个 PR 含两项，或先 1-06 再 1-07。
验证：./mvnw verify && cd frontend && npm run build
勾选清单。约束见 docs/mainline-implementation-prompt.md。
```

### 阶段 O2 — Prompt 槽位 + Provider 向导（ML-4-07 / ML-8-07）

```text
仓库 ArchOps。ML-1-06 建议已合并（非硬依赖 8-07）。

必读：docs/opskat-learning.md「上下文组装」「AI Provider」；计划中 ML-4-07、ML-8-07。

请完成：
1) ML-4-07 AgentContextAssembler：固定槽位 = 目标 + 范围 RAG + facts + work logs + 可选 uiContext；禁止整本 Architecture dump；禁止 Description 当 SSOT。
2) ML-8-07 AI Provider 首次向导：无 Provider 时引导；Test connection；拉模型；与现有 Settings 共用存储。

验证与勾选同上。不要实现 Grant 或侧轨（留给 O3/O4）。
```

### 阶段 O3 — 执行 Grant（ML-3-07）

```text
仓库 ArchOps。

必读：docs/opskat-learning.md 工具治理；docs/product-vision.md（知识提案门不可被 grant 绕过）；计划 ML-3-07。

请实现 execution_grant（用户+资产范围+pattern+TTL+conversation）+ decision_source 审计。
HIGH 默认不可 grant。仅覆盖执行类工具（ssh_exec 等），不影响 Architecture Proposal 审批。

集成测试：同会话二次命中 GRANT；跨用户/资产不命中。
分支 cursor/ml-3-07-execution-grant-71f3。
```

### 阶段 O4 — AI 侧轨工作台壳（ML-8-06）

```text
仓库 ArchOps。建议 ML-4-07 已合并以便上报 uiContext。

必读：docs/opskat-learning.md 前端信息架构；计划 ML-8-06。

请实现：资产树导航 + 可钉住 AI 侧轨（Terminal/Assets/Architecture 旁可用同一 Agent 会话）。
复用现有 AI API/WS；移动端降级抽屉/全页。
不要移植 OpsKat 的 Wails Tab 或 RDP/VNC。

分支 cursor/ml-8-06-ai-side-rail-71f3。
验证 npm run build + 关键页面手动/组件测试。
勾选 ML-8-06。
```

### 阶段 O-ALL — 一次做完 OpsKat 第二波（强 Agent / 可拆 PR）

```text
按 docs/mainline-implementation-plan.md「ML-OpsKat 第二波」六项全部完成。
顺序：ML-1-06 → (ML-1-07 ∥ ML-8-07) → ML-4-07 → ML-3-07 → ML-8-06。
每项至少一 commit；优先多项 Draft PR 而非巨型单 PR。
必读 docs/opskat-learning.md：只改编，不照搬桌面模型。
硬约束与验证见 docs/mainline-implementation-prompt.md 一键 Prompt — OpsKat 第二波。
```

### 阶段 W0–W2 — 工作台急救（用户三图 + Bug）

```text
必读 docs/workbench-gap-audit.md 全文。

产品隐喻：SSH 终端页 = IDE Window；Agent = Agent Window（类似 Cursor）。

请按波次交付（可多 PR）：
W0：确认 BUG-01/02/03 已在 main（角色 ADMIN、nginx try_files、资产创建错误提示）；补回归。
W1：对齐 OpsKat「添加 SSH」表单（主机/端口/用户/认证/密码或密钥/分组/描述/测试连接）；创建一次可连。
W2：终端 IDE 壳 — 左资产树、多 Tab 会话、状态条、一键打开 Agent 侧轨。

字段级可对照 / 照搬 OpsKat：AssetForm+SSHConfigSection、AIProviderForm（含 reasoning）。
不要用 Description 当 Architecture SSOT；不要桌面 socket。
验证：npm run build；手动：创建 SERVER→测试连接→多 Tab 终端→侧轨对话。
```

### 阶段 W3 — AI Provider 对齐图 3（W3-01…W3-07）

```text
仓库 ArchOps，基线 origin/main。
必读：docs/workbench-gap-audit.md「Wave W3」表格；docs/opskat-learning.md AI 配置节。
对照 OpsKat：AIProviderForm / AISetupWizard（reasoningEffort、fetch models、max tokens、context window）。

请按 W3-01→W3-07 交付（可拆 PR）：
1) 表单字段与图 3 对齐（含思考深度 none/low/medium/high/xhigh/max）。
2) 后端实体/DTO/Flyway + 运行时生效。
3) 获取模型 + 测试连通（失败有 toast）。
4) 无 Provider 时首次向导（合并 ML-8-07）；与 /settings/ai 共用 API。
5) Chat/侧轨实际使用这些参数；更新契约/勾选审计文档。

分支：cursor/w3-ai-provider-parity-71f3（或按子任务拆分）。
验证：./mvnw verify && npm run build；手工走完向导并成功发一条 AI 消息。
不要：明文打日志 Key；不要改 Architecture SSOT 语义。
```

### 阶段 W4 — 多资产类型（W4-01…W4-08，建议 W4a→W4d）

```text
仓库 ArchOps，基线 origin/main。
必读：docs/workbench-gap-audit.md「Wave W4」；docs/opskat-learning.md 资产类型/connect 矩阵。

产品隐喻不变：SSH 仍是 IDE；其他类型用 query/page，Agent 可只读探活。

推荐子波（多 PR）：
W4a：W4-01 文档+SPI 固化，W4-02 connectAction，W4-03 DATABASE 最小可配+测试连接
W4b：W4-04 K8s 最小可配（不做完整控制台）
W4c：W4-05 Kafka 或 Redis 最小可配 + W4-06 Query 壳（至少一种只读操作）
W4d：W4-07 Agent 只读工具 + W4-08 验收文档

硬约束：扩展靠 register，禁止共享 switch(kind)；写操作走审批；面板可后置但表单+测试必须先可用。
验证：mvn verify + npm run build；按 workbench-w4-acceptance 剧本打勾。
分支例：cursor/w4a-database-asset-type-71f3
```

### 阶段 W3+W4 — 并行提示（两 Agent）

```text
Agent A：只做 docs/workbench-gap-audit.md Wave W3（AI Provider）。
Agent B：在 W1/W2 与 SPI 就绪后做 W4a（DATABASE），勿与 A 改同一 Provider 文件。
二者都必读 workbench-gap-audit.md；完成后在文档变更记录注明完成的 W3-xx / W4-xx。
```

---

## 单任务 Prompt 模板

```text
仓库 ArchOps，基线 origin/main。

请仅执行 docs/mainline-implementation-plan.md 中的 {TASK_ID}（{TITLE}）。
先读该任务「来源/实现/完成标准/不做什么」与 docs/opskat-learning.md 对应章节。
分支：cursor/ml-{id-小写}-71f3
Commit：feat(scope): {TASK_ID} 简短说明
验证：cd backend && ./mvnw verify ；cd frontend && npm run build
勾选清单；Draft PR。
硬约束：docs/mainline-implementation-prompt.md + 愿景「先可信再自动」。
```

### 示例：类型 SPI

```text
请仅执行 ML-1-06（资产类型 SPI）。
对标 OpsKat AssetTypeHandler，落地 com.archops 的注册表 + 前端 registerAssetType + docs/adding-an-asset-type.md。
禁止共享调度 switch(kind)。不要做完整第三方协议 GUI。
约束见 docs/mainline-implementation-prompt.md 与 docs/opskat-learning.md。
```

### 示例：AI 侧轨

```text
请仅执行 ML-8-06（资产树 + AI 侧轨）。
参考 OpsKat 侧栏助手，但保持 Vue/B/S；复用现有 Agent WS。
可与 uiContext（ML-4-07）联动；若 4-07 未合并，侧轨先独立可用。
```

---

## 产品硬约束（所有阶段通用）

1. Architecture SSOT：默认 Proposal + 人工确认；禁止静默覆盖。  
2. 事实必须有 provenance。  
3. Work Log ≠ Architecture ≠ Audit。  
4. L0/L1/L2 分级写回。  
5. 范围化 RAG；禁止只靠整本 summary。  
6. 前端薄客户端。  
7. 不拆微服务 / 不多租户。  
8. ArchOps / `com.archops` 品牌与包名。  
9. 不弱化 ApprovalGate / 审计链。  
10. OpsKat：学模式不学单用户桌面信任边界。

---

## 多 Agent 并行（OpsKat 波）

| Agent | 任务 |
|-------|------|
| A | ML-1-06（类型 SPI）→ 再 ML-1-07 |
| B | ML-8-07（Provider 向导） |
| C | ML-4-07（Prompt 槽位，可与 A 后半并行） |
| D | ML-3-07（Grant，勿与审批重构冲突） |
| E | ML-8-06（侧轨，最好等 C） |

禁止两人同改同一 Flyway 版本或同一聚合根。

---

## 每项输出格式

```markdown
## ML-x-xx — 标题

### 对应
- 愿景条款 / opskat-learning 章节

### 变更摘要
- ...

### 验证
- [ ] mvn verify
- [ ] npm run build
- [ ] 测试：...

### 清单
- mainline-implementation-plan.md 已勾选
```

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-22 | 初版：主线一键 Prompt + 单任务/分阶段模板 |
| 2026-07-23 | 增加 OpsKat 第二波一键 Prompt 与 O1–O4 / O-ALL 阶段模板 |
| 2026-07-23 | 增加工作台 W3 / W4 分阶段 Prompt（Provider 对齐图 3、多资产类型） |
