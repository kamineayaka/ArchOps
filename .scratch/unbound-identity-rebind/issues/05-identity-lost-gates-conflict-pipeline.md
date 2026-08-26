# 05 — 失联闸门修实际 / 改理想路径

**What to build:** 当冲突合并键的主体已是身份失联时，不得再按旧实际选「修实际」或「改理想」、不得审/执行该键操作计划。已有冲突保留（不新增状态枚举、不进入空洞挂起）。若已是待确认关闭则退回开放。活跃操作计划受阻即停作废。该键上未完成改理想草案作废。诊断不得再给出以旧实际为落点的分叉，只读说明身份失联，处理走未绑定草案 / 现场补标。心跳通道仍新鲜时，不得改走纯空洞的「恢复观测通道」分叉集。

**Blocked by:** 01 — 控制面推断身份失联；未绑定按现场实体 upsert；规范问法

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

本票复用竖切协作/计划/改策展草案，只加闸门。01 完成后即可与 02–04 并行准备，但两张都 unblocked 时按编号最小先做 02。冲突 GET 须能读到失联旗标。

- [x] 失联主体上 `FIX_ACTUAL` / `CHANGE_CURATED` 选支失败；非处理人规则不变
- [x] GET 诊断不含以旧实际为唯一落点的修实际 / 改理想分叉；文案用身份失联 / 未绑定观测候选，不用「以现场为准」
- [x] 不得单因失联改走纯空洞恢复观测通道分叉集（除非心跳确实超时）
- [x] 该合并键活跃操作计划作废（既有计划作废语义）；不得继续执行
- [x] 该对象 `运行于` 上开放的改理想草案作废；再审条失败
- [x] 冲突状态枚举不增加：`OPEN` 保持 `OPEN`；`PENDING_CLOSE` + 失联 → `OPEN`；不是 `SUSPENDED`
- [x] 冲突 GET 可读身份失联；未绑定本身仍不新开冲突

**Out of this ticket:** 未绑定草案发起/接受（02–03）；标签命中收尾（04）；tracer（06）；UI；重做关单/协作产品化。

## Comments

Blocked by 01。01 与本票之间若 02 也已 unblocked，先做 02。不要把失联写成观测空洞。不要做 06–07。

01–04 + 08 已 TDD-done，本票已 unblocked。开场 prompt：[`docs/implement-unbound-identity-rebind-05-prompt.md`](../../../docs/implement-unbound-identity-rebind-05-prompt.md)。代码 vs ADR-0044 审计 **A2** 即本票范围；**A3** 是票 09；**A1**（升级不作废活跃计划）与 0044 进程债禁止写入本票。见 [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../audit-code-vs-adr-0044.md)。

### Cycle A — OPEN 冲突主体随后失联：GET 可读 identityLost，不把旧宿主当 PRESENT 实际
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.openConflictSubjectThenIdentityLostKeepsOpenWithoutPresentingStaleHost`
Red output:
AssertionError: No value at JSON path "$.data.identityLost" (PathNotFoundException). GET identity-lost 已 200（LABEL_CLUE_LOST）；冲突 GET 仍无旗标，observedValue 仍会把残留 observed_fact 展示为 PRESENT。
Green: ConflictCaseResponse 增加 identityLost 读模型；toResponse 查 identity_lost_mark；失联且非空洞时 observedValue=IDENTITY_LOST、hostId JSON null。不改 ConflictStatus，不走 onObservationBecameHollow。
Refactor: 抽出 observedTrackValue。
Commit: 22e9833

### Cycle B — PENDING_CLOSE 主体随后失联：退回 OPEN，不是 SUSPENDED
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.pendingCloseSubjectThenIdentityLostReturnsToOpenNotSuspended`
Red output:
JSON path "$.data.status" Expected: is "OPEN" but: was "PENDING_CLOSE". 失联标已写入，但 upsertIdentityLost 不触碰冲突状态。
Green: 新标落地时 ConflictDetectionService.onIdentityLost：PENDING_CLOSE → OPEN、清 pendingCloseAt、事件 UPGRADED reason=identity_lost。不 SUSPENDED、不走 onObservationBecameHollow。
Refactor: 抽出 IDENTITY_LOST_REASON。
Commit: 3821abc

### Cycle C — 失联后诊断不含修实际/改理想，也不含空洞恢复通道集
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.identityLostDiagnosisOmitsUniqueSiteForksAndHollowRestoreSet`
Red output:
Expected empty "$.data.forks[?(@.id=='FIX_ACTUAL_TO_CURATED')]" but found the pre-loss mismatch fork. 失联后仍返回旧 READY 诊断。
Green: onIdentityLost 重诊；DiagnosisRuleEngine.diagnoseIdentityLost 只读说明（EXPLAIN）；文案含身份失联/未绑定观测候选/补标；不含 RESTORE_HEARTBEAT_CHANNEL。SUSPENDED 仍走空洞规则。
Refactor: rulesFor；测试静态 assert。
Commit: 7d0b68b

### Cycle D — 已接受处理人选 FIX_ACTUAL → IDENTITY_LOST_BLOCKS_BRANCH
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.acceptedHandlerFixActualOnIdentityLostIsBlocked`
Red output:
JSON path "$.code" Expected: is "IDENTITY_LOST_BLOCKS_BRANCH" but: was "DIAGNOSIS_NOT_READY". 失联后重诊尚未 READY，选支先撞诊断门禁，未按失联闸门拒绝修实际。
Green: BranchSelectionService 在处理人检查之后、诊断 READY 之前，对 FIX_ACTUAL_TO_CURATED + identity_lost_mark 抛 IDENTITY_LOST_BLOCKS_BRANCH。
Refactor: 测试抽出 openMismatch / claimAsGeneral / identityLostOnObservedHost。
Commit: 93221d7

### Cycle E — 已接受处理人选 CHANGE_CURATED → IDENTITY_LOST_BLOCKS_BRANCH
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.acceptedHandlerChangeCuratedOnIdentityLostIsBlocked`
Red output:
JSON path "$.code" Expected: is "IDENTITY_LOST_BLOCKS_BRANCH" but: was "FORK_NOT_FOUND". D 圈只拦 FIX_ACTUAL_TO_CURATED。
Green: 同一闸门加上 CHANGE_CURATED_TO_OBSERVED。
Refactor: rejectUniqueSiteForkWhenIdentityLost。
Commit: 57c1b6a

### Cycle F — 非处理人选支仍 PLAN_REQUIRES_ACCEPTED_HANDLER
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.nonHandlerBranchSelectionOnIdentityLostStillRequiresAcceptedHandler`
Red output:
（先把失联闸门放到处理人检查之前）Expected: is "PLAN_REQUIRES_ACCEPTED_HANDLER" but: was "IDENTITY_LOST_BLOCKS_BRANCH"。
Green: 恢复「已接受处理人」先于失联闸门。
Refactor: 注释钉死优先级。
Commit: b72f952

### Cycle G — 失联前活跃计划作废；再 approve → PLAN_VOIDED
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.identityLostVoidsActivePlanAndApproveIsPlanVoided`
Red output:
JSON path "$.data.status" Expected: is "VOIDED" but: was "DRAFT_REVIEW". onIdentityLost 不作废计划。
Green: onIdentityLost 调 voidActivePlansForConflict(reason=identity_lost)；approve 对 VOIDED 返回 PLAN_VOIDED（不再误报 PLAN_NOT_IN_REVIEW）。
Refactor: 抽出 voidActivePlans，空洞路径复用事件追加。
Commit: eb85115

### Cycle H — 失联前开放改理想草案作废；再审条 DRAFT_VOIDED
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.identityLostVoidsOpenChangeCuratedDraftAndAcceptIsDraftVoided`
Red output:
JSON path "$.data.status" Expected: is "VOIDED" but: was "OPEN". onIdentityLost 不作废 CHANGE_CURATED 草案。
Green: voidOpenForConflict(reason=identity_lost)；仅 origin=CHANGE_CURATED。
Refactor: 注释钉死不作废未绑定草案。
Commit: e9a204b

### Cycle I — 未打标同名仍不新开冲突（回归）
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.unlabeledSameNameStillDoesNotOpenConflict`
Red output:
reuse/regression：first-run green。竖切 13 / 票 01 已保证 by-merge-key 400 CONFLICT_NOT_FOUND。本圈不改生产。
Green: 无。
Refactor: 无。
Commit: cf4da48

### Cycle J — 标签命中清标后诊断再次含修实际/改理想
Red command:
`cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.labelMatchAfterIdentityLostRestoresUniqueSiteDiagnosisForks`
Red output:
Expected forks containing FIX_ACTUAL_TO_CURATED and CHANGE_CURATED_TO_OBSERVED but was EXPLAIN_IDENTITY_LOST. 04 已清标且冲突 GET identityLost=false，但同快照 reconcile 不重诊。
Green: clearIdentityLostMark 若删到行则 onIdentityLostCleared → scheduleAsyncDiagnosis。不重做 04 消费。
Refactor: 无行为变化。
Commit: d62dd5f

Keep-green: `UnboundBindGateHttpAcceptanceTest` 不再把失联后残留 observed_fact 的旧宿主读成 PRESENT。改断言 identityLost=true、availability 非 PRESENT、hostId JSON null；绑定未写观测仍由 status=OPEN + 问法 IDENTITY_LOST 证明。
Commit: a3708c7

Ticket-end suite: `cd backend && ./gradlew cleanTest test` — BUILD SUCCESSFUL; 151 tests, 0 failures (IdentityLostPipelineGateHttpAcceptanceTest 10/10). Keep-green 01–04/08, hollow, diagnosis, plan review, change-curated, vertical-slice, SSH fake 仍绿。

Code-review (Standards + Spec vs origin/main): Standards — no hard violations; judgement-call smells only (duplicated mark probe / unique-site fork id pair). Spec — no findings vs stories 14–16 / 45–49 / Negative 8. A1 / 09 / 0044 未写入。
