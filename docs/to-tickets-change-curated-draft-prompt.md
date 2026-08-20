# 新对话：改策展草案 Spec → to-tickets（Prompt）

将下面整段复制到**新对话**作为第一条用户消息。若客户端支持手动附带 skill，请同时附上 `to-tickets`（仓库路径：`.cursor/skills/to-tickets/SKILL.md` 或 `.agents/skills/to-tickets/SKILL.md`）。

前置：[`docs/specs/change-curated-draft.md`](specs/change-curated-draft.md) 已发布（`ready-for-agent`）。竖切 MVP 01–13 **不要重拆**。

---

```text
/to-tickets

请加载并严格遵循 skill：to-tickets（`.cursor/skills/to-tickets/SKILL.md`）。
本对话任务：把已发布的 ArchOps「改策展 / 改理想（草案逐条确认）」Spec 拆成一组 tracer-bullet 工单（含阻塞边）。不要写业务实现代码；不要重开领域 grilling / 技术选型；不要改 CONTEXT 合同语义；不要 to-spec 重写。

## 背景（已完成）

- 领域合同冻结：CONTEXT.md + ADR-0039
- 技术栈冻结：ADR-0043
- 竖切 MVP 已实现并经 VM 人工验收：docs/specs/vertical-slice-mvp.md 与 .scratch/vertical-slice-mvp/issues/ 01–13
- 本刀 Spec 已发布：docs/specs/change-curated-draft.md（ready-for-agent；本地发布）
- 测试接缝已确认：唯一验收主接缝 = 控制面公开 HTTP API（含 Agent ingest）；前端最小 UI 手工/冒烟；本刀无 SSH 接缝

## 必读后再拆票

1. docs/specs/change-curated-draft.md          ← 拆票唯一主输入
2. CONTEXT.md（只用术语）
3. docs/adr/0043-tech-stack.md
4. docs/adr/0006-curated-writes-via-itemized-proposals.md
5. docs/dev-handoff.md
6. 快速扫一眼现行实现（诊断仅 FIX_ACTUAL、选支即出计划、POST runs-on 可覆盖已有事实），票面须承认「从竖切 MVP 往上长」

## 故事范围（票不得扩出 Spec）

已接受处理人选「改策展/改理想」→ 草案逐条确认（≥2 条）→ 接受即写入策展 → 立即比对与冲突演进（相等则待确认关闭，仍不等则同一合并键升级，空洞则挂起并作废草案）。

钉死：不含计划内策展对齐步骤、不含改策展后的新 SSH 计划；FIX_ACTUAL 路径须回归仍跳过草案。

Out of Scope（票不得偷带）：自我迭代、指标大盘、网络可达全矩阵、Y2 策展对齐步骤、Neo4j、多租户、完整 xterm、Vue/JPA/Maven/LangChain、K8s/DB 全对象、未绑定候选绑定。

## 执行 to-tickets 流程时注意

1. 按 skill：先基于 Spec 起草垂直切片票（每票窄而完整：可演示/可验证；单票适合一个全新上下文窗口；优先垂直而非「先全表再全 API」的横向层）。
2. 每票写清：Title / Blocked by / What it delivers（用户可感知的端到端行为，不要写成纯分层任务清单）。
3. 用 CONTEXT 术语；遵守 ADR-0043。
4. 验收对齐 Spec：自动化以 HTTP API 主接缝为准；UI 可在相关票里做最小可演示，但不另开「只做 UI 层」横切片当主路径。
5. Prefactor 仅在为本刀真正降阻时单列；禁止借 prefactor 复活旧语义或重做 01–13。
6. 先把票清单 + 阻塞图画给我确认（粒度 / 边是否对 / 是否要合并拆分）；我批准后再发布。
7. 发布：按 `docs/agents/issue-tracker.md` 本地发布到
   `.scratch/change-curated-draft/issues/NN-<slug>.md`
   （从 01 起按依赖序；每票一个文件；模板见 to-tickets skill；Status: ready-for-agent）。
   并更新 docs/dev-handoff.md 指向该目录。不要用 `gh issue create`（Cloud `gh` 只读）。
8. 不要实现任何票的业务代码；发布后提示我下一对话从无 blocker 的 frontier 票开工。

开始：先输出建议的票清单（编号、标题、Blocked by、交付的可验证行为）与简图，等我确认粒度后再写入 `.scratch/...` 并收束。
```

---

工单已发布至 `.scratch/change-curated-draft/issues/`（01–06）。现行 frontier：**01 TDD 重做**。开工提示词：[`docs/implement-change-curated-draft-01-prompt.md`](implement-change-curated-draft-01-prompt.md)。`/implement` 走 [`docs/agents/tdd.md`](agents/tdd.md)。
