# 06 — HTTP 主接缝有序 tracer（happy path + 负面最小集）

**What to build:** 为本刀建立一条以控制面公开 HTTP API（含 Agent 心跳/快照 ingest）为唯一自动化主接缝的有序验收：已接受处理人选改理想 → 草案逐条确认 → 接受即写策展 → 立刻比对进入待确认关闭 → 复用既有确认关闭。再加 Spec 规定的负面最小集。不以浏览器自动化或 SSH fake 作为本刀完成定义（本刀无新 SSH 接缝）。前端薄 UI 只作手工/冒烟，不进本票 CI 门槛。

**Blocked by:** 05 — 升级 / 空洞作废未完成草案；对齐后再漂则同一合并键升级

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md) 的 **Suite / tracer tickets**。01–05 已 TDD-done。本票钉有序 HTTP 套件，不实现新产品；禁止删除 01–05 生产来装红灯。开工 prompt：[`docs/implement-change-curated-draft-06-prompt.md`](../../../docs/implement-change-curated-draft-06-prompt.md)。

从竖切 MVP 往上长：竖切票 13 的修实际 + SSH fake 闭环保持独立、不要重写。本票新开本刀套件（风格对齐既有 `*HttpAcceptanceTest` / 竖切有序 HTTP E2E：统一信封、只断言后续 HTTP 可读状态）。`FIX_ACTUAL` 回归用聚焦选支/出计划断言，不要求再跑受控 SSH。

Happy path（须按序、可在 CI 稳定跑通）：

1. 建底：主机 A/B；容器 X、Y（带对象标签）；策展 X/Y 皆 `运行于` A
2. Agent 快照：X 在 B → 冲突警告存在（诊断可异步，等待方式与今日测试相同）
3. 一般角色认领 → 已接受处理人
4. GET 诊断：分叉含 `FIX_ACTUAL` **与** `CHANGE_CURATED`
5. 选择改理想 → 草案存在且 ≥2 条（X、Y `运行于` A→B）；GET 策展 X 仍为 A；无活跃操作计划
6. 非处理人接受条目 → 拒绝；策展仍为 A
7. 拒绝 Y 的条目 → Y「应该在哪」仍为 A
8. 接受 X 的条目 → X「应该在哪」为 B；Y 仍为 A
9. 不发新快照，GET 冲突 → 待确认关闭（策展 B = 观测 B）
10. 既有确认关闭 API → CLOSED（证明第 9 步未自动关单；复用竖切关单，不重做产品化）

- [x] 上列有序 happy path 可在 CI 经 HTTP 稳定跑通
- [x] 负面：选择改理想不改变策展（在任何条目接受之前断言）
- [x] 负面：非处理人 / 待接受不能选择改理想，也不能接受/拒绝条目
- [x] 负面：`FIX_ACTUAL` 仍跳过草案并仍创建操作计划（聚焦 HTTP，不必跑 SSH）
- [x] 负面：开放草案挡住 `FIX_ACTUAL`；活跃操作计划挡住改理想
- [x] 负面：建底 POST 覆盖已有 `运行于` → 拒绝，事实不变
- [x] 负面：草案待审（X 未接受）时快照 X 到 C → 同一合并键升级，草案作废，策展 X 仍为 A，待确认条目未写
- [x] 负面：心跳超时 / 空洞且草案开放 → 冲突挂起，草案作废，再接受被拒
- [x] 负面：X 已接受（待确认关闭）后再快照 X 到 C → 非并行新冲突；离开待确认关闭 / 同一合并键升级
- [x] 断言只落 HTTP 状态码、统一信封、以及后续 GET 可读状态；不测 MyBatis/Redis 内部；不把前端自动化当完成门槛

**Out of this ticket:** 实现新的业务能力（应已由 01–05 交付）；Playwright；SSH fake 作为第二接缝；重写竖切票 13。

## Comments

开工 prompt：[`docs/implement-change-curated-draft-06-prompt.md`](../../../docs/implement-change-curated-draft-06-prompt.md)。05 已合入 `main`（PR #72）。本票是本刀定义完成的有序 HTTP tracer，不是能力票。每圈先跑套件方法：首次即绿记 `reuse/regression` 并点名 01–05 聚焦测试；首次即红才是组合缺口，只补到既有语义。不要拆 01–05，不要改竖切 13，不要跑 SSH，不要在本对话 `/to-spec` 下一刀。

### Cycle 1 — happy path 1–10

`reuse/regression`（首次即绿；未改生产）。组合覆盖：

- `ChangeCuratedDraftHttpAcceptanceTest.acceptedHandlerSelectsChangeCuratedOpensDraftWithTwoPendingRunsOnItems`
- `ChangeCuratedDraftHttpAcceptanceTest.selectChangeCuratedDoesNotWriteCuratedShouldWhere`
- `ChangeCuratedDraftHttpAcceptanceTest.selectChangeCuratedDoesNotCreateActiveOperationPlan`
- `ChangeCuratedDraftItemHttpAcceptanceTest.nonHandlerCannotAcceptDraftItem`
- `ChangeCuratedDraftItemHttpAcceptanceTest.acceptedHandlerRejectsSiblingDoesNotWriteCurated`
- `ChangeCuratedDraftItemHttpAcceptanceTest.acceptedHandlerAcceptsMergeKeyWritesCuratedShouldWhereToObservedHost`
- `ChangeCuratedDraftItemHttpAcceptanceTest.acceptMergeKeyComparesImmediatelyToPendingCloseWithoutNewSnapshot`
- `ChangeCuratedDraftItemHttpAcceptanceTest.acceptedHandlerConfirmCloseAfterDraftAcceptClosesConflict`
- `ConflictDiagnosisHttpAcceptanceTest` 同时给出 `FIX_ACTUAL_TO_CURATED` 与 `CHANGE_CURATED_TO_OBSERVED`

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.happyPath_hostsAB_curatedRunsOnA_snapshotXOnB_claim_changeCurated_rejectY_acceptX_pendingClose_confirmClose
```

首次输出（witnessed green）：

```text
> Task :compileJava
> Task :processResources
> Task :classes
> Task :compileTestJava
> Task :processTestResources NO-SOURCE
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 16s
4 actionable tasks: 4 executed
```

Refactor 后同方法再跑仍绿：`BUILD SUCCESSFUL in 6s`。

### Cycle 2 — 负面 1 选择改理想不改变策展

`reuse/regression`（首次即绿；未改生产）。对应 `ChangeCuratedDraftHttpAcceptanceTest.selectChangeCuratedDoesNotWriteCuratedShouldWhere`。06 套件钉：选支之后、任何 accept 之前，X/Y「应该在哪」仍为 A，开放草案两条均为 PENDING。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.selectChangeCuratedDoesNotWriteCuratedBeforeAnyItemAccept
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

### Cycle 3 — 负面 2 非处理人 / 待接受不能选、也不能审条

拆成两方法。首次均绿；未改生产；错误码仍为 `PLAN_REQUIRES_ACCEPTED_HANDLER`（无新码）。

**3a 不能选** `reuse/regression`：`ChangeCuratedDraftHttpAcceptanceTest.nonHandlerCannotSelectChangeCurated`、`pendingHandlerCannotSelectChangeCurated`。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.nonHandlerAndPendingAcceptCannotSelectChangeCurated
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

Refactor 后同方法仍绿：`BUILD SUCCESSFUL in 5s`。

**3b 不能审条** `reuse/regression`：`ChangeCuratedDraftItemHttpAcceptanceTest.nonHandlerCannotAcceptDraftItem`、`nonHandlerCannotRejectDraftItem`；待接受审条钉 `CuratedDraftService.requireAcceptedHandler`（`HandlerAcceptance.ACCEPTED` 且 actor 即处理人）。转让夹具：`POST /api/conflicts/{id}/transfer-handler` → `PENDING_ACCEPT`，`user-general-2-demo` accept/reject 皆 400，策展仍为 A。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.nonHandlerAndPendingAcceptCannotReviewDraftItems
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

Refactor（抽出 `transferHandlerPending`）后同方法仍绿：`BUILD SUCCESSFUL in 5s`。

### Cycle 4 — 负面 3 FIX_ACTUAL 仍跳过草案并仍创建操作计划

`reuse/regression`（首次即绿；未改生产；未跑 SSH / start-execution）。对应 `ChangeCuratedDraftHttpAcceptanceTest.fixActualStillSkipsDraftAndCreatesOperationPlan`。选 `FIX_ACTUAL_TO_CURATED`：`branchKind=FIX_ACTUAL`、`skipsDraft=true`、`status=DRAFT_REVIEW`；GET open → `DRAFT_NOT_FOUND`；GET active 200 且同一计划。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.fixActualStillSkipsDraftAndCreatesOperationPlan
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

### Cycle 5a — 负面 4 开放草案挡住 FIX_ACTUAL

`reuse/regression`（首次即绿；未改生产；未执行计划）。对应 `ChangeCuratedDraftHttpAcceptanceTest.openDraftBlocksFixActualSelection`。先选 CHANGE_CURATED，再选 FIX_ACTUAL → `OPEN_DRAFT_BLOCKS_FIX_ACTUAL`；GET open 仍 OPEN；GET active 仍 `PLAN_NOT_FOUND`。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.openDraftBlocksFixActualSelection
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

### Cycle 5b — 负面 4 活跃操作计划挡住改理想

`reuse/regression`（首次即绿；未改生产；未执行计划）。对应 `ChangeCuratedDraftHttpAcceptanceTest.activePlanBlocksChangeCuratedSelection`。先选 FIX_ACTUAL 出计划，再选 CHANGE_CURATED → `PLAN_ALREADY_ACTIVE`；GET open 仍 `DRAFT_NOT_FOUND`。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.activePlanBlocksChangeCuratedSelection
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

### Cycle 7 — 负面 5 建底 POST 覆盖已有运行于 → 拒绝

`reuse/regression`（首次即绿；未改生产）。对应 `CuratedTruthHttpAcceptanceTest.bootstrapPostRejectsOverwriteToDifferentHost` / `bootstrapPostRejectsOverwriteToSameHost`。容器 Z 策展运行于 A 后，再 POST 指向 B 或再指向 A 皆 `CURATED_RUNS_ON_EXISTS`，`data=null`，「应该在哪」仍为 A。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.bootstrapPostRejectsOverwriteOfExistingRunsOn
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

### Cycle 8 — 负面 6 草案待审时快照 X 到 C

`reuse/regression`（夹具编译笔误修完即绿；未改生产）。对应 `ChangeCuratedDraftVoidHttpAcceptanceTest.snapshotBtoCWhileDraftPendingUpgradesSameConflictAndVoidsOpenDraftWithoutWritingCurated` 与 `acceptAndRejectAfterUpgradeAreDraftVoidedAndCuratedStaysA` / `getDraftByIdAfterUpgradeShowsVoidedWithPendingItems`。agentId 用 `agent-{objectX}-c`，不与 B 撞车。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.snapshotBtoCWhileDraftPendingUpgradesSameConflictAndVoidsDraft
```

首次红灯是测试写错（插入 helper 时丢掉 `postBranch` 方法头 → compile-fail），修测试后：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

### Cycle 9 — 负面 7 心跳超时 / 空洞且草案开放

`reuse/regression`（首次即绿；未改生产；未 Thread.sleep；未换 agent）。对应 `ChangeCuratedDraftVoidHttpAcceptanceTest.heartbeatTimeoutWhileDraftOpenSuspendsConflictAndVoidsDraft`。回拨 `agent-{objectX}` 的 lastHeartbeatAt，POST `/api/observed/scan-heartbeat-timeouts`。冲突 SUSPENDED + observationHollow；「实际在哪」HOLLOW；「应该在哪」仍为 A（不是不存在）；草案 VOIDED / DRAFT_VOIDED。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.heartbeatTimeoutWhileDraftOpenSuspendsConflictAndVoidsDraft
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

### Cycle 10 — 负面 8 接受 X 待确认关闭后再快照到 C

`reuse/regression`（首次即绿；未改生产；未 confirm-close）。对应 `ChangeCuratedDraftVoidHttpAcceptanceTest.acceptMergeKeyThenSnapshotCLeavesPendingCloseKeepsCuratedBAndVoidsDraft`。同一 conflict id 离开 PENDING_CLOSE 回到 OPEN；策展仍 B、观测 C；活跃冲突仍 1；草案 VOIDED；Y 仍 PENDING 时 accept → DRAFT_VOIDED，Y「应该在哪」仍为 A。

命令：

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.acceptMergeKeyThenSnapshotCLeavesPendingCloseKeepsCuratedB
```

首次输出（witnessed green）：

```text
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 5s
4 actionable tasks: 2 executed, 2 up-to-date
```

### Cycle K — 票级回归与 code-review

命令：

```text
cd backend && ./gradlew cleanTest test
```

```text
BUILD SUCCESSFUL in 14s
5 actionable tasks: 2 executed, 3 up-to-date
```

JUnit：93 tests, 0 failures, 0 errors, 0 skipped。本套件 11 方法全绿。01–05 与竖切 13 保持绿（`ChangeCuratedDraftHttpAcceptanceTest` 11、`ChangeCuratedDraftItemHttpAcceptanceTest` 8、`ChangeCuratedDraftVoidHttpAcceptanceTest` 7、`CuratedTruthHttpAcceptanceTest` 5、`VerticalSliceHttpE2eAcceptanceTest` 4）。未删 01–05；未改竖切 13；未跑 SSH / start-execution；未改已有 V*.sql。

`/code-review`（固定点 = `main` merge-base `40a97cc`）：

- Standards：无硬违规。判断：负面 2 审条方法内 400 信封重复（未抽以保持断言可读）。
- Spec：无缺失、无范围漂移、无实现错误。待接受审条钉 `PLAN_REQUIRES_ACCEPTED_HANDLER`。

本刀闭合。下一对话不要默认 `/implement`，不要加 07。下一刀见 [`docs/grill-next-knife-prompt.md`](../../../docs/grill-next-knife-prompt.md)（`/grill-with-docs`）。

