# 新对话：改策展票 03 开工（Prompt）

将下面整段复制到**新对话**作为第一条用户消息。若客户端支持手动附带 skill，请同时附上：

- `implement`（`.cursor/skills/implement/SKILL.md` 或 `.agents/skills/implement/SKILL.md`）
- `tdd`（`.agents/skills/tdd/SKILL.md`）

Frontier 按编号：`.scratch/change-curated-draft/issues/03-select-change-curated-draft.md`（blocker 02 已 done）。一次只做这一张。

---

```text
/implement /tdd

请加载并严格遵循 skill：implement（`.cursor/skills/implement/SKILL.md`）与 tdd（`.agents/skills/tdd/SKILL.md`）。
本对话任务：只实现 ArchOps 改策展 frontier 工单 03——已接受处理人选改理想后生成 ≥2 条开放草案：不写策展、不出操作计划。不要做 04–06；不要重开 to-spec / to-tickets；不要改 CONTEXT 合同语义；不要重做竖切 01–13；不要重做已闭合的改策展 01（建底 POST 覆盖拒绝）与 02（诊断改理想分叉）。

## 背景（已完成）

- 领域合同冻结：CONTEXT.md + ADR-0039
- 技术栈冻结：ADR-0043（Gradle / MyBatis-Plus / React+Ant / PG+Redis；禁止 Vue/JPA/Maven/Neo4j 必选/LangChain）
- 竖切 MVP 01–13 已实现并验收；本刀从该实现往上长
- Spec：docs/specs/change-curated-draft.md
- 工单已发布：.scratch/change-curated-draft/issues/ 01–06
- 改策展 01 已 done：POST /api/curated/facts/runs-on 对已有 运行于 拒绝（CURATED_RUNS_ON_EXISTS）
- 改策展 02 已 done：宿主 A vs 可用观测 B 时 GET 诊断同时含 FIX_ACTUAL_TO_CURATED 与 CHANGE_CURATED_TO_OBSERVED（kind=CHANGE_CURATED）；空洞/观测消失无改理想
- 主接缝已确认（不必再问）：唯一自动化验收接缝 = 控制面公开 HTTP API（统一 ApiResponse）。UI 只做薄页、不进自动化主接缝。本票无 SSH 执行、无按条接受/拒绝写入、无立刻比对

## 本票（唯一范围）

读并只验收：

`.scratch/change-curated-draft/issues/03-select-change-curated-draft.md`

用户可感知行为：

- 已接受处理人基于当前未过时诊断选择改理想：该冲突立刻出现恰好一份开放草案；规则夹具条目 ≥2（合并键容器 X：运行于 A→B；兄弟容器 Y：运行于 A→B，相互独立）
- 选支瞬间与任一条目被接受之前：GET「应该在哪」对 X 与 Y 仍为 A；策展真相未变
- 选支后无活跃操作计划；不启动 SSH；不出现策展对齐步骤
- 非处理人与待接受处理人选择改理想被拒绝；过时诊断上选支被拒绝（复用既有门禁，不重做协作）
- 开放草案时再次选择改理想被拒绝（每冲突至多一份开放草案）
- 开放草案时选择 FIX_ACTUAL 被拒绝；已有活跃操作计划时选择改理想被拒绝
- FIX_ACTUAL 仍跳过草案并仍创建操作计划（聚焦 HTTP 回归即可，不跑 SSH 执行）
- GET 开放草案可看出条目仍待确认；HTTP 可读「草案已创建」审计（可并入既有冲突事件列表）
- 草案与条目落 PostgreSQL（仅增量 Flyway）；Redis 不用作草案/关系真相 SSOT
- 冲突详情薄 UI：能看见并选择改理想分叉、列出草案条目；选择改理想不得再写成「生成操作计划」。UI 不进自动化主接缝

从竖切往上长：今日选支门禁在 OperationPlanService.selectBranch；POST /api/conflicts/{id}/branch-selection 对非 FIX_ACTUAL 直接 FORK_NOT_SUPPORTED；操作计划 branch_kind 历史约束仍只有修实际。本票复用同一门禁（已接受处理人、当前未过时诊断、每冲突一条活跃处理路径），为改理想走出草案而不是计划。不要把 CHANGE_CURATED 加进操作计划分支种类；FIX_ACTUAL 的 HTTP 响应须对既有 01–13 客户端/测试保持有效。

HTTP 形状按 Spec 默认：复用选支 POST（可判别 body）或与草案资源配对，只要能 GET 到开放草案。夹具：既有建底 API 准备主机 A/B、容器 X 与 Y（均带 archops.object_id）、策展 X/Y 皆运行于 A；Agent 快照仅须让 X 出现在 B（Y 不必冲突）。草案生成规则模板化，不依赖 LLM。

## 必读

1. AGENTS.md
2. 本票文件（验收清单）
3. docs/specs/change-curated-draft.md 中 Branch selection vs 操作计划 vs 草案、草案 items、Testing Decisions（HTTP only；本票只做到 tracer 第 5 步：选出草案且策展仍为 A、无活跃计划。第 6 步起的按条接受/拒绝与立刻比对是 04）
4. CONTEXT.md（术语：改理想、策展、观测、草案、逐条确认、已接受的冲突处理人——本票还不做条目写入）
5. docs/adr/0043-tech-stack.md
6. docs/adr/0006-curated-writes-via-itemized-proposals.md、docs/adr/0038（改策展须草案人审；纯修现场跳过草案）
7. 现行 HTTP 样板：OperationPlanReviewHttpAcceptanceTest、ConflictDiagnosisHttpAcceptanceTest、VerticalSliceHttpE2eAcceptanceTest；前端 ConflictDetailPage.tsx（按钮文案现为「选择分叉并生成操作计划」）
8. 前端 HTTP 只走 frontend/src/api/（可参考 .cursor/skills/add-frontend-page/SKILL.md 与 add-rest-api）

## TDD / 实现约束

- 先红后绿：在 HTTP 接缝补验收——已接受处理人选 CHANGE_CURATED_TO_OBSERVED 后，GET 开放草案条目 ≥2（X：A→B；Y：A→B），GET「应该在哪」X/Y 仍为 A，无活跃操作计划。不要先写完实现再补测。
- 风格对齐既有 *HttpAcceptanceTest；不要新开测试金字塔。不要为了本票去改竖切修实际故事语义。
- 业务错误走 BusinessException + 统一信封；DTO 继续用 record。
- Flyway：只增不改历史。本票需要草案+条目表时新增 V13（或下一号）；Postgres 为草案 SSOT。禁止改已有 V*.sql。不要把按条接受写入策展或立刻比对带进本票。
- 不要把 CHANGE_CURATED 写入 operation_plan.branch_kind；不要在选支瞬间写策展。
- Redis 不是关系真相/草案 SSOT。不要复活旧域包。规则引擎出草案即可，无 LLM 起草。
- 做完跑相关 HTTP 测试，并跑一遍 `cd backend && ./gradlew test`（Compose Redis/Postgres 按 AGENTS.md Cloud 说明）。UI 手工/冒烟即可。
- 收尾用 code-review skill 做 Standards + Spec 两轴对照本票，再提交。

## 完成时必须做

1. 本票验收项全部勾上；Status: done
2. 更新 docs/dev-handoff.md：改策展 frontier 变为 04（03 已 done）；不要顺手实现 04
3. 不要改合同；不要实现按条接受/拒绝写入策展

开始：读票与现行 POST /api/conflicts/{id}/branch-selection，先写失败的 HTTP 选改理想→开放草案验收（≥2 条、策展未写、无计划），再改到绿。
```

---

完成后下一对话再实现 **04**（逐条确认写入并立刻比对）。票路径 `.scratch/change-curated-draft/issues/04-itemized-accept-write-compare.md`。
