# ArchOps 主线实施 — Agent 执行 Prompt

> 复制下方 **「一键 Prompt」** 到 Cloud Agent / 本地 Agent。  
> 任务清单：`docs/mainline-implementation-plan.md`（`ML-*`）  
> 北极星：`docs/product-vision.md`  
> 品牌：**ArchOps**（勿再写 CloudOps）

---

## 一键 Prompt（复制以下全部内容）

```text
你是 ArchOps AI Platform 的主线交付 Agent。目标是实现「活的 Architecture 知识库 + Work Log + RAG」闭环，而不是泛泛重构或堆工具。

## 必读（按顺序）

1. docs/product-vision.md — 尤其第 3–4 节（三大知识对象、难点立场）
2. docs/mainline-implementation-plan.md — 全部 ML-* 任务
3. docs/architecture.md — 了解现状工程结构
4. 可选并行：docs/architecture-refactor-todo.md（ARCH-*）、docs/agent-optimization-todo.md（OPT-*）
   - 冲突时：主线数据模型以 ML 为准；包依赖方向以 ARCH 为准；安全「先可信再自动」以愿景为准

## 仓库与分支

- 仓库：kamineayaka/ArchOps（工作区可能仍显示旧 remote 名 cloudops，以代码品牌 ArchOps 为准）
- 基线：main（若主线依赖未合并的 rename/vision PR，先 rebase 含 ArchOps 与 product-vision 的最新主线）
- 任务 ID：ML-0-xx … ML-8-xx
- 分支命名：cursor/ml-<task-id-小写>-71f3
  例：cursor/ml-3-01-architecture-proposal-71f3

## 你的目标

1. 打开 docs/mainline-implementation-plan.md，从第一个未完成 `[ ]` 的 ML 任务开始。
2. 用户指定任务时只做该任务及其硬依赖。
3. 每完成一项：
   - 勾选清单 `[x]`
   - commit：`feat(scope): ML-x-xx 简短说明`（契约/文档类可用 `docs:`）
   - 跑验证命令
   - push + Draft PR，body 列出 ML ID、验收点、与愿景条款的对应关系
4. 大阶段（如整个 ML-3）结束后，对照 docs/mainline-acceptance.md（若尚未创建则先做 ML-0-04）更新勾选。

## 产品硬约束（违反即停）

1. Architecture 是组织级 SSOT：默认 **Proposal + 人工确认**，禁止模型静默覆盖 SSOT。
2. 每条进入 SSOT 的事实必须有 **provenance**（命令/输出摘要/assetId/conversationId 等）。
3. Work Log ≠ Architecture ≠ Audit：禁止把日志隐式拷进 SSOT；晋升必须走 Proposal。
4. 变更分级 L0/L1/L2：L0 只写日志；L1/L2 才可产生架构提案；L2 通常还涉及执行审批。
5. 逻辑一份 Architecture，物理按 `global` / `group:{id}` / `asset:{id}` 分区。
6. RAG 必须可按对话目标范围过滤；禁止把整本 Architecture 无差别塞进 prompt 作为唯一策略。
7. 前端保持薄客户端：分类器、合并引擎、风险门在后端。
8. 不拆微服务、不多租户、不把主线做成「先完美 MCP 协议」。
9. 品牌与包名使用 ArchOps / com.archops / archops.* 配置前缀。
10. 不弱化 ApprovalGate；不删除审计链。

## 技术约定

- Backend：Java 21、Spring Boot 3、Flyway 新版本号 = 当前最大 V{n}+1，不改已发布迁移
- 新工具：com.archops.tools，实现 AgentTool
- 配置：archops.architecture.*、archops.rag.* 等写入 application.yml + .env.example
- Frontend：Vue 3 script setup、Naive UI、i18n 双语文案同步
- 测试：ML-1 及以后的数据/权限/合并路径必须有测试（单元或 Testcontainers 集成）

## Git 工作流

- git push -u origin <branch>
- 不同 ML ID 不混在一个 commit；同一 ML 可多 commit
- PR Draft；base=main
- 不 force push main

## 验证命令

```bash
cd backend && ./mvnw verify
# 若环境无 Docker，至少：
cd backend && ./mvnw -Dtest='!*IntegrationTest' verify

cd frontend && npm ci && npm run build
```

涉及 Compose：
```bash
docker compose -f deploy/compose/compose.yaml config
```

## 分阶段 Definition of Done

### ML-0
- 愿景、领域模型、API 契约、验收剧本文档齐全且互链

### ML-1
- 可创建 Hadoop 组并绑到对话；工具默认作用组内资产

### ML-2
- 分区 Architecture + 结构化事实 + 版本冲突 + 回滚可用

### ML-3
- 提案状态机跑通；合并写审计；默认非自动覆盖；可选窄自动合并配置默认安全

### ML-4
- Classifier + propose_architecture_update + 剧本中能产出 NN/DN 类提案

### ML-5
- 工作日志按会话查询；晋升仅经 Proposal

### ML-6
- 范围化 RAG；context 不再只靠整本 summary；分区增量索引

### ML-7
- 知识读写与提案批准按角色/资产范围强制执行

### ML-8
- 架构浏览器 + 提案台 + 聊天引用；验收剧本可演示；核心指标暴露

## 多 Agent 并行建议

| Agent | 任务带 |
|-------|--------|
| Doc | ML-0-* |
| Asset | ML-1-* |
| SSOT | ML-2-* （等 ML-0-02、ML-1-05） |
| Proposal | ML-3-* （依赖 ML-2） |
| Agent 写回 | ML-4-* （依赖 ML-3 API） |
| RAG | ML-6 可与 ML-3 后期并行 |
| UI | ML-8 在对应 API 就绪后跟进 |

禁止两人同时改同一 Flyway 版本号或同一领域聚合根。

## 每项输出格式

```markdown
## ML-x-xx — 标题

### 对应愿景条款
- 例：§4.1 写回不可靠 / §4.3 L0L1L2

### 变更摘要
- ...

### 验证
- [ ] mvn verify（或说明跳过集成测试原因）
- [ ] npm run build
- [ ] 新增测试：...

### 清单
- docs/mainline-implementation-plan.md 已勾选
```

## 禁止事项

- 不要为赶进度开启「全面自动写 Architecture」
- 不要引入无消费者的空表/空容器（与诚实化原则一致）
- 不要用 -DskipTests 作为默认 CI 策略回退
- 不要一次 PR 塞入无依赖关系的多个阶段（如 ML-1+ML-6 无共享交付物时禁止混搭）
- 不要把产品名写回 CloudOps

开始：读取 docs/mainline-implementation-plan.md 与 docs/product-vision.md，找到第一个 [ ]，创建分支并实现。
```

---

## 单任务 Prompt 模板

```text
仓库 ArchOps（kamineayaka），工作区 /workspace。

请仅执行 docs/mainline-implementation-plan.md 中的 {TASK_ID}（{TITLE}）。
先读 docs/product-vision.md 相关章节与该任务依赖；依赖未完成则先完成或在 PR 声明阻塞。

分支：cursor/ml-{id-小写}-71f3
Commit：feat(scope): {TASK_ID} 简短说明
验证：./mvnw verify（或 -Dtest='!*IntegrationTest'）且 frontend npm run build
勾选清单；Draft PR。

硬约束见 docs/mainline-implementation-prompt.md。
```

### 示例：Proposal 状态机

```text
请仅执行 ML-3-01（Proposal 状态机与存储）。
实现 architecture_proposal 表与状态机 DRAFT/PENDING_REVIEW/APPROVED/REJECTED/AUTO_MERGED/MERGED。
默认不自动合并进 SSOT。依赖 ML-2-02 若未完成需先补齐 fact 模型。
约束见 docs/mainline-implementation-prompt.md 与 docs/product-vision.md §4.1。
```

### 示例：L0/L1/L2 分类 + 提案工具

```text
请执行 ML-4-02 与 ML-4-03：ChangeClassifier + propose_architecture_update 工具。
df 类只读为 L0；角色发现为 L1 并创建 Proposal；禁止无 provenance 的事实。
System prompt 更新可含在同 PR（ML-4-05）若改动内聚。
```

---

## 分阶段 Prompt

### 阶段 ML-0+ML-1（故事地基）

```text
按 docs/mainline-implementation-plan.md 完成 ML-0 与 ML-1。
交付：愿景/领域/API/验收文档 + AssetGroup + 对话目标含组 + 工具范围限制。
不要开始 Proposal 引擎。约束见 docs/mainline-implementation-prompt.md。
```

### 阶段 ML-2+ML-3（SSOT + 提案）

```text
ML-0/1 已合并。完成 ML-2 与 ML-3：分区 Architecture、结构化事实、Proposal 流水线、合并/回滚/审计。
自动合并默认关闭或极严。完成后更新 mainline-acceptance 相关勾选。
```

### 阶段 ML-4+ML-5+ML-6（写回闭环）

```text
完成 Agent 写回（L0/L1/L2）、Work Log 会话化、范围化 RAG。
验收：Hadoop 剧本能走通「发现→提案→（人工合并）→新会话检索命中」。
```

### 阶段 ML-7+ML-8（治理与体验）

```text
完成知识 ACL/角色矩阵与前端架构浏览器、提案台、指标。
对照 docs/mainline-acceptance.md 做一次完整演示级验收。
```

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-22 | 初版：主线一键 Prompt + 单任务/分阶段模板 |
