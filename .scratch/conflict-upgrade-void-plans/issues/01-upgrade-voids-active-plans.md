# 01 — 冲突升级作废活跃操作计划

**What to build:** 健康对象（未身份失联）上，同一合并键观测实际值变化导致**冲突升级**时，必须作废该冲突上全部活跃操作计划（与空洞挂起 / 身份失联落地同形），使指向过时实际的计划不可再审/执行。覆盖 `upgradeOpen`（OPEN 上 B→C）与 `reopenFromPendingClose`（待确认关闭后再漂）。复用既有 `voidActivePlans` / `voidActivePlansForConflict`；`voidReason` 与草案作废对齐为 `conflict_upgrade`；追加 `PLAN_VOIDED` 事件。不改合同，不立新 ADR。

**Blocked by:** （无）

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条 HTTP 测试。Spec：[`docs/specs/conflict-upgrade-void-plans.md`](../../../docs/specs/conflict-upgrade-void-plans.md)。合同：`CONTEXT.md`「AI 诊断」升级后作废活跃计划；ADR-0027。

来源：`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md` **A1**（合同冲突）。用户 2026-08-27 明示人排期开本票。**不要写入** `.scratch/unbound-identity-rebind/`。

现码缺口：`ConflictDetectionService.upgradeOpen` / `reopenFromPendingClose` 只 `voidOpenForConflict` + `scheduleAsyncDiagnosis`，不调用 `voidActivePlans`。空洞路径与 `onIdentityLost` 会作废计划。`startExecution` 只查计划仍为 APPROVED 且冲突仍 OPEN，不查诊断是否 STALE。

- [ ] OPEN 冲突 + 已接受处理人 + APPROVED（或 IN_REVIEW）操作计划指向观测宿主 B；新鲜心跳把可用观测 `运行于` 改到 C（策展仍 A、主体无失联标）→ 同一 conflict id 升级（`UPGRADED`、lineage 含 B→C）；该计划 `VOIDED` 且 `voidReason=conflict_upgrade`；`POST .../start-execution`（及对 VOIDED 的 approve）→ `PLAN_VOIDED`；冲突事件含 `PLAN_VOIDED`（planId + reason）
- [ ] 升级后旧诊断为 STALE / 新诊断被调度；不得用旧诊断 id 选支（`DIAGNOSIS_NOT_READY` 或不接受 STALE）——不回归竖切选支门禁
- [ ] PENDING_CLOSE 期间观测再漂离开相等 → 退回 OPEN 的同键升级路径同样作废活跃计划（`voidReason=conflict_upgrade`）
- [ ] 同一观测快照重复 ingest / 比对（未真正改变 observed target）→ **不作废**既有活跃计划
- [ ] 不回归：心跳超时空洞作废（`observation_hollow_heartbeat_timeout` / `HeartbeatTimeoutHollowHttpAcceptanceTest`）；身份失联作废（`identity_lost` / `IdentityLostPipelineGateHttpAcceptanceTest`）；升级作废 OPEN 改理想草案（`ChangeCuratedDraftVoidHttpAcceptanceTest`）
- [ ] 不新增 `ConflictStatus`；不改 `CONTEXT.md` / 已有 ADR 正文；无新产品路由；Flyway 仅在确有新列时才 V21+（本票预期不需要）

**Out of this ticket:** ADR-0044 进程拆分；未绑定 10；改策展 07；UI；问法读模型；扩大生产直连 SSH；把 WebClient/LLM 加回控制面。

## Comments

用户明示排期。开场 prompt：[`docs/implement-conflict-upgrade-void-plans-01-prompt.md`](../../../docs/implement-conflict-upgrade-void-plans-01-prompt.md)。一次只做本票。样板：`HeartbeatTimeoutHollowHttpAcceptanceTest`（空洞作废）与 `IdentityLostPipelineGateHttpAcceptanceTest`（失联作废）的计划夹具；生产改动应落在 `upgradeOpen` / `reopenFromPendingClose` 调用既有 `voidActivePlans(...)`，reason 字面量 `conflict_upgrade`（与草案 void 理由一致）。

### Cycle A witnessed red (2026-08-27)

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictUpgradeVoidsActivePlanHttpAcceptanceTest.upgradeOpenBtoCVoidsApprovedPlanAndRejectsStartExecution
```

```text
ConflictUpgradeVoidsActivePlanHttpAcceptanceTest > upgradeOpenBtoCVoidsApprovedPlanAndRejectsStartExecution() FAILED
    java.lang.AssertionError: JSON path "$.data.status"
    Expected: is "VOIDED"
         but: was "APPROVED"
        at ConflictUpgradeVoidsActivePlanHttpAcceptanceTest.java:65
BUILD FAILED
```

OPEN 观测 B→C 升级后计划仍 APPROVED（A1 缺口）。生产：`upgradeOpen` 在 `voidOpenForConflict` 之后调用 `voidActivePlans(..., "conflict_upgrade")`。

### Cycle A green + refactor (2026-08-27)

### Cycle B reuse (2026-08-27)

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictUpgradeVoidsActivePlanHttpAcceptanceTest.upgradeOpenBtoCWritesPlanVoidedEventWithConflictUpgradeReason
```

First-run BUILD SUCCESSFUL (`reuse` of `voidActivePlans`：`voidReason=conflict_upgrade` + `PLAN_VOIDED` 事件 planId/reason)。显式断言保留。Refactor：抽出 `heartbeatObservedOnNewHost`。

### Cycle C witnessed red (2026-08-27)

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictUpgradeVoidsActivePlanHttpAcceptanceTest.pendingCloseDriftVoidsApprovedPlanWithConflictUpgradeReason
```

```text
JSON path "$.data.status"
Expected: is "VOIDED"
     but: was "APPROVED"
```

待确认关闭后再漂同键升级不作废计划。生产：`reopenFromPendingClose` 同样调用 `voidActivePlans(..., CONFLICT_UPGRADE_REASON)`。

### Cycle C green + refactor (2026-08-27)

Same test command: BUILD SUCCESSFUL. Refactor: `voidOpenDraftsAndPlansThenRediagnose` 供 `upgradeOpen` 与 `reopenFromPendingClose` 共用。

### Cycle D reuse (2026-08-27)

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictUpgradeVoidsActivePlanHttpAcceptanceTest.sameObservedSnapshotRepeatDoesNotVoidApprovedPlan
```

First-run BUILD SUCCESSFUL（`reuse` of `sameObservedSnapshot`：重复 ingest 不升级、不作废计划、无 `UPGRADED`/`PLAN_VOIDED`）。
