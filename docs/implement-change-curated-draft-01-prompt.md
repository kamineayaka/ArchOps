# 新对话：改策展票 01 开工（Prompt）

将下面整段复制到**新对话**作为第一条用户消息。若客户端支持手动附带 skill，请同时附上：

- `implement`（`.cursor/skills/implement/SKILL.md` 或 `.agents/skills/implement/SKILL.md`）
- `tdd`（`.agents/skills/tdd/SKILL.md`）

Frontier 按编号：`.scratch/change-curated-draft/issues/01-close-bootstrap-runs-on-overwrite.md`（无 blocker）。一次只做这一张。

若要改做并行 frontier **02**（诊断改理想分叉），把文中的票路径换成 `.scratch/change-curated-draft/issues/02-diagnosis-change-curated-fork.md`，并只验收 02 的清单。

---

```text
/implement

请加载并严格遵循 skill：implement（`.cursor/skills/implement/SKILL.md`）与 tdd（`.agents/skills/tdd/SKILL.md`）。
本对话任务：只实现 ArchOps 改策展 frontier 工单 01——关闭建底 POST 覆盖已有 `运行于`。不要做 02–06；不要重开 to-spec / to-tickets；不要改 CONTEXT 合同语义；不要重做竖切 01–13。

## 背景（已完成）

- 领域合同冻结：CONTEXT.md + ADR-0039
- 技术栈冻结：ADR-0043（Gradle / MyBatis-Plus / React+Ant / PG+Redis；禁止 Vue/JPA/Maven/Neo4j 必选/LangChain）
- 竖切 MVP 01–13 已实现并验收；本刀从该实现往上长
- Spec：docs/specs/change-curated-draft.md
- 工单已发布：.scratch/change-curated-draft/issues/ 01–06
- 主接缝已确认（不必再问）：唯一自动化验收接缝 = 控制面公开 HTTP API（统一 ApiResponse）。本票无 UI、无 SSH、无 Agent ingest 新行为

## 本票（唯一范围）

读并只验收：

`.scratch/change-curated-draft/issues/01-close-bootstrap-runs-on-overwrite.md`

用户可感知行为：

- 尚无 `运行于` 的容器：`POST /api/curated/facts/runs-on` 仍可插入第一条；随后「应该在哪」可读到该宿主（竖切建底保留）
- 已有 `运行于` 的容器：再 POST（同一或不同宿主）必须拒绝；事实与「应该在哪」均不变
- 拒绝经 HTTP 可断言（统一信封）；不测 Mapper / Redis 内部

从竖切往上长：今日 `confirmRunsOn` 对已有事实是覆盖写入。本票只关这条旁路，不引入草案/选支/诊断分叉/比对触发。

## 必读

1. AGENTS.md
2. 本票文件（验收清单）
3. docs/specs/change-curated-draft.md 中「Curated write bypass」与 Testing Decisions（HTTP only）
4. CONTEXT.md（只用术语：策展真相、运行于、草案——本票还不做草案）
5. docs/adr/0043-tech-stack.md
6. docs/adr/0006-curated-writes-via-itemized-proposals.md（为何必须关掉覆盖旁路）
7. 现行 HTTP 样板：CuratedTruthHttpAcceptanceTest 与既有 POST `/api/curated/facts/runs-on` 行为

## TDD / 实现约束

- 先红后绿：在 HTTP 接缝补一条（或一组）验收——先插入成功，再覆盖被拒且「应该在哪」不变。不要先写完实现再补测。
- 风格对齐既有 `*HttpAcceptanceTest`；不要新开测试金字塔。
- 既有只 POST 一次建底的测试应保持绿色。不要为了关旁路去改竖切 E2E 的故事语义。
- 业务错误走 BusinessException + 统一信封；DTO 继续用 record。
- Flyway：本票若无新表则不要空增脚本；禁止改已有 V*.sql。
- Redis 不是关系真相 SSOT。不要复活旧域包。
- 做完跑相关 HTTP 测试，并跑一遍 `cd backend && ./gradlew test`（Compose Redis/Postgres 按 AGENTS.md Cloud 说明）。
- 收尾用 code-review skill 做 Standards + Spec 两轴对照本票，再提交。

## 完成时必须做

1. 本票验收项全部勾上；`Status: done`
2. 更新 `docs/dev-handoff.md`：改策展 frontier 变为 02（01 已 done）；不要顺手实现 02
3. 不要改合同；不要把 CHANGE_CURATED / 草案表带进本票

开始：读票与现行 `POST /api/curated/facts/runs-on`，先写失败的 HTTP 覆盖拒绝测试，再改到绿。
```

---

完成后下一对话再实现 **02**（仍一次一张）。02 的开工把票路径换成 `.scratch/change-curated-draft/issues/02-diagnosis-change-curated-fork.md` 即可。
