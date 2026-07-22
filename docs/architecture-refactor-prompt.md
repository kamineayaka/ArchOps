# CloudOps 架构重构 — Agent 执行 Prompt

> 将本文 **「一键 Prompt」** 整段复制到 Cursor Cloud Agent / 本地 Agent。  
> 任务详情：`docs/architecture-refactor-todo.md`  
> 注意：本清单是 **架构重构（ARCH-\*)**，与 `docs/agent-optimization-todo.md`（OPT-\* 安全质量）分开。若冲突，**架构决策（A0 诚实化）优先**。

---

## 一键 Prompt（复制以下全部内容）

```text
你是 CloudOps AI Platform 的架构重构 Agent。按 docs/architecture-refactor-todo.md 执行，不要擅自改成微服务拆分或多租户。

## 仓库与分支

- 仓库：kamineayaka/CloudOps（工作区 /workspace）
- 基线分支：main
- 任务清单：docs/architecture-refactor-todo.md（任务 ID：ARCH-A*）
- 必读结论表：清单顶部「架构结论」——保留模块化单体；改边界/命名/依赖/状态叙事；现在不拆服务
- 参考：docs/architecture.md、docs/ssh-connection-pool-design.md、docs/deployment.md
- 并行清单：docs/agent-optimization-todo.md（OPT-*）。与 ARCH 重叠时（如 user_assets、权限），以 ARCH-A0 决策为准，并在 OPT 项中改为引用 ARCH ID

## 你的目标

1. 打开 docs/architecture-refactor-todo.md，从第一个未完成的 ARCH-A* 开始。
2. 默认顺序：A0 → A1 → A2 → A3 → A4。用户指定任务 ID 时只做该项及其直接依赖。
3. 每完成一项：
   - 将清单中对应 `[ ]` 改为 `[x]`
   - 单独 commit：`refactor(scope): ARCH-A0-XX 简短说明`
   - 跑通验证命令
   - push 并更新 Draft PR（body 列出已完成 ARCH ID）
4. A0 类「实现或删除」任务：必须在 PR 描述写明选择了哪条路，禁止保持悬空 schema。

## 架构硬约束（违反即停）

1. 保持单个 Spring Boot 可部署单元（允许同仓新增 ssh-gateway 模块/进程，但默认仍 Compose 一体交付）。
2. 禁止把 RiskClassifier / ApprovalGate / ReAct 状态机搬到前端。
3. 禁止为了“好听”保留 MCP 协议伪装：进程内工具必须叫 tools（或等价诚实命名）。
4. 重构后依赖方向必须趋向清单中的目标图；不得新增 `approval → ai`、`tools → ai`、`knowledge → ai` 引用。
5. 在 A3 HA 方案落地前，不得把 Helm/文档改成鼓励多 replicas 而不加警告。
6. 不修改 .cursor/plans/；不弱化审批门控。
7. 对外 REST/WS 路径尽量兼容；若 breaking，必须更新前端与 docs/api-contracts.md（A4-03）。

## Git 工作流

- 分支名：`cursor/arch-<task-id-小写>-71f3`（例：`cursor/arch-a1-01-rename-tools-71f3`）
- `git push -u origin <branch>`
- 大搬迁可同一任务多 commit，但不同 ARCH ID 不要混在一个 commit
- PR 默认 Draft；base=main

## 验证命令（每项完成后）

```bash
cd backend && ./mvnw verify
cd frontend && npm ci && npm run build
```

涉及 Compose/Helm：
```bash
docker compose -f deploy/compose/compose.yaml config
# 若已有 Helm templates：cd deploy/helm && helm lint .
```

涉及包搬迁：额外 grep 旧包名应无残留（除变更记录/历史文档注明处）：
```bash
rg "com\\.cloudops\\.mcp" backend/src || true
```

## 分阶段完成定义

### A0
- 每个虚构概念要么有完整代码路径，要么从 schema/拓扑/文档删除
- 「无状态」表述已删除或改为「单活有状态」

### A1
- 包名与职责匹配；ssh 可被 terminal/tools/ai 向下依赖
- tools 不再依赖 ai DTO

### A2
- approval/tools/knowledge 对 ai 无反向/环依赖
- 审批续跑经事件或 SPI，功能回归通过

### A3
- 有状态清单与单副本契约
- SSH/WS HA 有 ADR + 实现或明确 sticky-only

### A4
- ArchUnit（或等价）守护依赖；architecture.md 与代码一致

## 多 Agent 并行（可选）

| Agent | 任务 |
|-------|------|
| A | A0-01, A0-02（权限/ACL，避免两人同改 user） |
| B | A0-03 MinIO；A0-04/A0-05 文档诚实化 |
| C | A1-01 → A1-04（tools 链） |
| D | A1-02（ssh 抽取） |
| E | A1-03 → A1-05 → A2-*（需等 C/D 关键接口） |

不要两人同时大规模搬迁同一 package。

## 输出格式（每项结束后）

```markdown
## ARCH-Ax-xx — 标题

### 决策（仅 A0）
- 选择：实现 / 删除 / 方案A|B

### 变更摘要
- ...

### 验证
- [ ] ./mvnw verify
- [ ] npm run build
- [ ] 依赖方向检查（如适用）

### 清单
- docs/architecture-refactor-todo.md 已勾选
```

## 禁止事项

- 不要 force push main
- 不要提交真实密钥
- 不要用 -DskipTests 掩盖失败
- 不要一次 PR 塞入无依赖关系的多个阶段（A0+A3 混搭禁止）
- 不要引入“暂时保留空表/空容器以后再说”

开始：读取 docs/architecture-refactor-todo.md，找到第一个 [ ]，创建分支并实现。
```

---

## 单任务 Prompt 模板

替换 `{TASK_ID}` / `{TITLE}`：

```text
仓库 kamineayaka/CloudOps，分支 main。

请仅执行 docs/architecture-refactor-todo.md 中的 {TASK_ID}（{TITLE}）。

要求：
1. 阅读该任务的决策要求、实现要点、完成标准与依赖
2. 若依赖未完成，先完成依赖或在 PR 说明阻塞
3. 分支：cursor/arch-{task-id-小写}-71f3
4. commit：refactor(scope): {TASK_ID} 简短说明
5. 验证：cd backend && ./mvnw verify && cd frontend && npm run build
6. 勾选清单；开 Draft PR
7. 遵守 docs/architecture-refactor-prompt.md 硬约束

A0 任务必须在 PR 写明「实现或删除」的选择。
```

### 示例：重命名 mcp → tools

```text
仓库 kamineayaka/CloudOps，分支 main。

请仅执行 ARCH-A1-01（将 mcp 重命名为 tools）。
分支 cursor/arch-a1-01-rename-tools-71f3。
保持 HTTP/WS 路径兼容；更新 README/architecture 中 MCP 协议误导表述。
验证 ./mvnw verify 与 npm run build。约束见 docs/architecture-refactor-prompt.md。
```

### 示例：审批解耦

```text
仓库 kamineayaka/CloudOps，分支 main。

请仅执行 ARCH-A2-01（approval 不再直接调用 AiAgentService）。
用 Spring 事件或 SPI 让 ai.agent 订阅审批通过事件并续跑。
完成后 approval 包零 import com.cloudops.ai。
约束见 docs/architecture-refactor-prompt.md。
```

---

## 分阶段 Prompt

### 阶段 A0 — 诚实化

```text
按 docs/architecture-refactor-todo.md 完成全部 A0（ARCH-A0-01 … A0-05）。
每项单独 commit/PR 或同阶段多个 PR；A0-01/02/03 必须写明实现或删除。
不要开始 A1 大搬迁。约束见 docs/architecture-refactor-prompt.md。
```

### 阶段 A1+A2 — 边界与解环

```text
A0 已合并。按顺序执行 A1 然后 A2（ARCH-A1-* → ARCH-A2-*）。
目标：tools/ssh 边界清晰；approval/tools/knowledge 不再环依赖 ai。
可拆多个 Draft PR，但依赖方向不得回退。约束见 docs/architecture-refactor-prompt.md。
```

### 阶段 A3 — 有状态与 HA

```text
包边界已稳定。执行 A3：状态清单、单副本契约、SSH/WS HA（含 ADR）、Agent 队列隔离。
允许较大工程量；先 ADR 再编码。约束见 docs/architecture-refactor-prompt.md。
```

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-22 | 初版：架构重构一键 Prompt + 单任务/分阶段模板 |
