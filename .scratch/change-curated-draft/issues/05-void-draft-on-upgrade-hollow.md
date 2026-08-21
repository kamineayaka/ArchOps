# 05 — 升级 / 空洞作废未完成草案；对齐后再漂则同一合并键升级

**What to build:** 开放草案期间若冲突升级或观测进入空洞：改理想选支作废、草案作废、尚未接受的条目永远不写策展，且作废后不能再接受/拒绝。已接受的条目已是策展真相，后续比对用新策展值。若合并键条目已接受、冲突已在待确认关闭，观测再漂到另一宿主：退出待确认关闭，按同一合并键升级/重开，不得并行第二条开放冲突。空洞路径仍挂起冲突（不关闭），并拒绝再审条。

**Blocked by:** 04 — 逐条确认：接受即写策展并立刻比对（相等 → 待确认关闭）

**Status:** ready-for-agent

从竖切 MVP 往上长：升级、空洞挂起、作废活跃操作计划已在竖切交付。本票把同一触发接到开放草案（以及已选改理想），不重做挂起/升级/关单产品化，也不引入新的 SSH 计划。

- [ ] 合并键条目仍待确认时，快照将 X 的可用观测从 B 改为 C：同一合并键升级（一条、留脉络），不新开并行开放冲突；开放草案作废；策展 X 仍为 A；待确认条目未写入
- [ ] 心跳超时使合并键观测空洞、草案仍开放：冲突挂起（不关闭）；草案作废；再接受/拒绝被拒绝
- [ ] GET 开放/该份草案可看出已作废；作废草案不可再修改
- [ ] 合并键条目已接受且冲突已待确认关闭后，快照再报 X 运行于 C：离开待确认关闭，同一合并键升级/重开，不是第二条并行开放冲突
- [ ] 已接受条目保持其已写入的策展值；后续比对以该新策展值为准（例如已改为 B 后再观测到 C）
- [ ] HTTP 可读「草案已作废」审计；作废后的选支不能再当当前处理路径继续审条
- [ ] 不把空洞或观测消失收成「策展改为不存在」；不重做修实际计划作废语义（已有计划作废保持）

**Out of this ticket:** 本刀总 E2E 套件（06）、Y2 对齐步、改策展后再出 SSH 计划、自我迭代。

## Comments

开工 prompt：[`docs/implement-change-curated-draft-05-prompt.md`](../../../docs/implement-change-curated-draft-05-prompt.md)。01–04 TDD-done（04 已合入 `main`）。本票要把竖切已有的升级/空洞接到开放草案；第一圈诚实红灯是 B→C 后草案仍 OPEN（或仍能 accept），不要拆 04 的接受写入与比对，不要重做挂起/计划作废。不要做 06。

### Step A — seams

04 `openChangeCuratedDraft` takes `itemXId`/`itemYId` from GET `/api/conflicts/{id}/curated-drafts/open` (`data.items[].id` by subject). `draftId` is `data.id` on that same GET; 04's `OpenDraft` did not keep it. B→C is `POST /api/curated/hosts` then heartbeat+snapshot with a different `agentId` (`agent-{objectX}-c`). Heartbeat timeout is backdate `HostAgent.lastHeartbeatAt` + `POST /api/observed/scan-heartbeat-timeouts`. GET open only queries `status=OPEN`; after VOIDED, GET open is `DRAFT_NOT_FOUND` unless GET by id exists.

### Step B — cycle 1: 待审草案时快照 B→C 升级并作废开放草案 (red)

New test `ChangeCuratedDraftVoidHttpAcceptanceTest.snapshotBtoCWhileDraftPendingUpgradesSameConflictAndVoidsOpenDraftWithoutWritingCurated`. Conflict upgrade (same id, lineage B then C, 策展 still A) reused 竖切 `upgradeOpen`; this cycle's missing behavior is the open 草案.

Red:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftVoidHttpAcceptanceTest.snapshotBtoCWhileDraftPendingUpgradesSameConflictAndVoidsOpenDraftWithoutWritingCurated
```

```text
ChangeCuratedDraftVoidHttpAcceptanceTest > snapshotBtoCWhileDraftPendingUpgradesSameConflictAndVoidsOpenDraftWithoutWritingCurated() FAILED
    java.lang.AssertionError: Status expected:<400> but was:<200>
	at ...ChangeCuratedDraftVoidHttpAcceptanceTest.java:72
BUILD FAILED in 16s
```

GET open after B→C still 200 OPEN. Conflict GET / lineage / 「应该在哪」A already green — 竖切 upgrade, not this cycle's failure.

Green: `upgradeOpen` calls `CuratedDraftService.voidOpenForConflict` (`@Lazy` to avoid the detection↔draft cycle). PENDING items are not written.

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftVoidHttpAcceptanceTest.snapshotBtoCWhileDraftPendingUpgradesSameConflictAndVoidsOpenDraftWithoutWritingCurated
BUILD SUCCESSFUL in 5s
```

Refactor: javadoc only; same test + 04 accept still green.

### Step C — cycle 2: 作废后审条失败码是 DRAFT_VOIDED (red)

Red:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftVoidHttpAcceptanceTest.acceptAndRejectAfterUpgradeAreDraftVoidedAndCuratedStaysA
```

```text
ChangeCuratedDraftVoidHttpAcceptanceTest > acceptAndRejectAfterUpgradeAreDraftVoidedAndCuratedStaysA() FAILED
    java.lang.AssertionError: JSON path "$.code"
Expected: is "DRAFT_VOIDED"
     but: was "DRAFT_NOT_FOUND"
BUILD FAILED in 5s
```

`requireOpen` after void looks like the draft never existed. Do not change the expectation to `DRAFT_NOT_FOUND`.

Green: item review uses `requireReviewableDraft` (latest row including VOIDED) and throws `DRAFT_VOIDED`. GET open still uses `requireOpen` → `DRAFT_NOT_FOUND`.

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftVoidHttpAcceptanceTest.acceptAndRejectAfterUpgradeAreDraftVoidedAndCuratedStaysA
BUILD SUCCESSFUL in 5s
```

Refactor: `findLatest`; test helper `snapshotXOnHostC`. Cycle 1 + 04 non-handler still green.


