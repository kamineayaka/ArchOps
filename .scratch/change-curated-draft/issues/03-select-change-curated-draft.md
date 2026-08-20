# 03 — 选改理想生成 ≥2 条草案：不写策展、不出操作计划

**What to build:** 已接受的冲突处理人基于**当前未过时**诊断选择改理想后，系统立即生成该冲突上唯一一份开放草案（确认前不是策展真相），且**不**创建操作计划、不启动 SSH、不入队策展对齐步骤。规则夹具必须给出至少两条可独立确认的 `运行于` 条目（合并键容器 X：A→B；兄弟容器 Y：A→B），以便验收「逐条」而非整单全有或全无。选支瞬间与任一条目被接受之前，「应该在哪」仍返回原策展宿主。修实际路径保持跳过草案、仍出操作计划。冲突详情薄 UI 能选改理想并列出草案条目（本票不做按条接受/拒绝写入）。

**Blocked by:** 02 — 诊断同时给出「修实际」与「改理想」分叉

**Status:** ready-for-agent

**TDD redo:** yes — 验收标准不变。先前实现与测试同提交，不算 TDD 完成。按 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md) 从 witnessed red 重做。待 02 TDD-done。

从竖切 MVP 往上长：选支门禁曾在「出操作计划」服务里，且非 `FIX_ACTUAL` 直接拒绝；操作计划 `branch_kind` 历史约束仍只有修实际。本票复用同一门禁（已接受处理人、当前诊断、每冲突一条活跃处理路径），为改理想走出草案而不是计划。不要把 `CHANGE_CURATED` 加进操作计划分支种类；`FIX_ACTUAL` 的 HTTP 响应须对既有客户端保持有效。HTTP 形状按 Spec 默认：复用选支 POST（可判别 body）或与草案资源配对，只要能 GET 到开放草案。TDD 重做从红灯开始（见 Comments）；不要改已有 V13。

夹具：用既有建底 API 准备主机 A/B、容器 X 与 Y（均带对象标签）、策展 X/Y 皆 `运行于` A；Agent 快照仅须让 X 出现在 B（Y 不必冲突）。草案生成规则模板化，不依赖 LLM；模型不可用也不得挡住出草案。

- [ ] 已接受处理人选择改理想：该冲突出现恰好一份开放草案；条目 ≥2（X：A→B 合并键；Y：A→B 兄弟，相互独立）
- [ ] 选支后、条目接受前：GET「应该在哪」对 X 与 Y 仍为 A；策展真相未变
- [ ] 选支后无活跃操作计划；不启动 SSH；不出现策展对齐步骤
- [ ] 非处理人与待接受处理人选择改理想被拒绝；过时诊断上选支被拒绝（复用既有门禁，不重做协作）
- [ ] 开放草案时再次选择改理想被拒绝（每冲突至多一份开放草案）
- [ ] 开放草案时选择 `FIX_ACTUAL` 被拒绝；已有活跃操作计划时选择改理想被拒绝
- [ ] `FIX_ACTUAL` 仍跳过草案并仍创建操作计划（聚焦 HTTP 回归即可，不跑 SSH 执行）
- [ ] GET 开放草案可看出条目仍待确认；HTTP 可读「草案已创建」审计（可并入既有冲突事件列表）
- [ ] 草案与条目落 PostgreSQL（仅增量 Flyway）；Redis 不用作草案/关系真相 SSOT
- [ ] 冲突详情薄 UI：能看见并选择改理想分叉、列出草案条目；选择改理想不得再写成「生成操作计划」。UI 不进自动化主接缝

**Out of this ticket:** 按条接受/拒绝写入（04）、关建底覆盖（01，本票不写策展故不挡开工）、升级/空洞作废（05）、有序 E2E 套件（06）、Y2 对齐步、改策展后再出 SSH 计划、LLM 起草、整单一键全接受。

## Comments

HTTP 接缝（先前同提交落地，**不是** TDD 完成证据）：`ChangeCuratedDraftHttpAcceptanceTest`。已接受处理人 `POST /api/conflicts/{id}/branch-selection`（`CHANGE_CURATED_TO_OBSERVED`）生成开放草案；`GET /api/conflicts/{id}/curated-drafts/open` 条目 ≥2（X/Y `运行于` A→B，PENDING）。选支后「应该在哪」仍为 A，活跃计划 `PLAN_NOT_FOUND`，事件 `DRAFT_CREATED`。Flyway V13 草案表；`FIX_ACTUAL` 仍跳过草案出计划。无按条写入策展。

TDD 重做：把多行为测试拆成一圈一条；若已绿则先去掉该票生产行为（**不要改 V13**；新 schema 用下一号）。HTTP 循环全绿后再接线薄 UI。不要做 04–06。

### Step A — restore FIX_ACTUAL-only select so TDD starts red

Removed `CHANGE_CURATED` draft write from the shared branch-selection gate (back to `FORK_NOT_SUPPORTED`). Split the co-committed multi-behavior test; cycle 1 HTTP method is `acceptedHandlerSelectsChangeCuratedOpensDraftWithTwoPendingRunsOnItems`. Did not edit V13.

Red:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftHttpAcceptanceTest.acceptedHandlerSelectsChangeCuratedOpensDraftWithTwoPendingRunsOnItems
```

```text
ChangeCuratedDraftHttpAcceptanceTest > acceptedHandlerSelectsChangeCuratedOpensDraftWithTwoPendingRunsOnItems() FAILED
    java.lang.AssertionError at ChangeCuratedDraftHttpAcceptanceTest.java:48

java.lang.AssertionError: Status expected:<200> but was:<400>
Body = {"success":false,"code":"FORK_NOT_SUPPORTED","message":"Ticket 07 only supports FIX_ACTUAL / 修实际回策展宿主","data":null}
BUILD FAILED in 16s
```

POST 改理想 is 400 `FORK_NOT_SUPPORTED` (not HTTP 500). No open 草案.

### Step C — cycle 1: 选改理想 opens ≥2 pending 运行于 items

Red (same command as Step A; still `FORK_NOT_SUPPORTED` before this slice’s production change).

Green: shared gate routes `CHANGE_CURATED` to rule-templated 草案 create (V13 tables reused). POST + GET open draft: `OPEN`, items ≥2, X/Y `运行于` A→B `PENDING`. No `hasOpen` / `hasActive` pipeline mutex yet; no `DRAFT_CREATED` event yet.

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftHttpAcceptanceTest.acceptedHandlerSelectsChangeCuratedOpensDraftWithTwoPendingRunsOnItems
BUILD SUCCESSFUL in 4s
```

Refactor (no behavior change): HTTP helpers `postBranch` / `getOpenDraft`. Same test still green.

### Step D — cycle 2: 选支后「应该在哪」仍为 A

Ran:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftHttpAcceptanceTest.selectChangeCuratedDoesNotWriteCuratedShouldWhere
BUILD SUCCESSFUL in 4s
```

Already green from cycle 1 (create 草案 does not write 策展). Regression only; no extra production. GET 「应该在哪」 for X and Y stays host A (`question` / `track` literals).

Refactor: HTTP helper `getShouldWhere`.

### Step E — cycle 3: 选支后无活跃操作计划

Ran:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftHttpAcceptanceTest.selectChangeCuratedDoesNotCreateActiveOperationPlan
BUILD SUCCESSFUL in 4s
```

Already green from cycle 1 (CHANGE_CURATED does not call 操作计划 create). GET active plan is 400 `PLAN_NOT_FOUND`. No SSH / 策展对齐步骤 because no plan exists.

### Step F — cycle 4: 非处理人 cannot select 改理想

Ran:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftHttpAcceptanceTest.nonHandlerCannotSelectChangeCurated
BUILD SUCCESSFUL in 5s
```

Already green: reused `requireAcceptedHandler` gate (`PLAN_REQUIRES_ACCEPTED_HANDLER`). Senior viewer is not the 已接受处理人. No extra production; collaboration not reimplemented.

### Step G — cycle 5: 待接受处理人 cannot select 改理想

Ran:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftHttpAcceptanceTest.pendingHandlerCannotSelectChangeCurated
BUILD SUCCESSFUL in 4s
```

Already green from the same gate: `PENDING_ACCEPT` is not 已接受. Assignee POST is `PLAN_REQUIRES_ACCEPTED_HANDLER`. No extra production.

### Step H — cycle 6: 过时诊断 cannot select 改理想

Ran:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftHttpAcceptanceTest.staleDiagnosisCannotSelectChangeCurated
BUILD SUCCESSFUL in 4s
```

Already green: reused current-diagnosis check (`DIAGNOSIS_NOT_READY` when body `diagnosisId` is stale after B→C upgrade). No extra production.
