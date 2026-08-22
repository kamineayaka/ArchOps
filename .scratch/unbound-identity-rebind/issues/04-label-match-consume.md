# 04 — 标签命中收尾：清失联、消费候选、恢复升级链

**What to build:** 现场补标之后，下一次心跳用正确 `archops.object_id` 命中策展 Docker 容器：写入观测 `运行于`，清除身份失联，消费对应未绑定候选与绑定记忆，作废仍指向该候选/目标的未完成未绑定草案。此后位置偏差走既有冲突升级链。`runtimeId` 因删重建而变化则视为新的未绑定候选，禁止按显示名续上旧绑定。若在待补标绑定之后出现 `absentObjectIds`：走观测消失，解除指向该对象的绑定记忆，现场实体若仍缺标则回到待并入。

**Blocked by:** 03 — 逐条确认：新建写入对象；绑定只记对应关系

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

补标本身不在本票用 SSH / 操作计划完成：测试夹具直接发带正确标签的快照。先策展后补标（ADR-0012）的正向收尾在本票闭合。

- [x] 命中 X 的标签 → 观测 `运行于` 为快照上报主机；身份失联清除；绑定记忆与该候选不再待并入
- [x] 命中时若有开放未绑定草案指向该候选或「绑到 X / 用该候选新建」→ 草案作废，再接受被拒
- [x] 同一 `runtimeId` 仅刷新观察时间、草案仍开放时 → **不**作废未绑定草案
- [x] 命中后若观测宿主与策展 `运行于` 不等 → 既有比对发出/升级冲突（升级链恢复）；同名弱线索在命中前仍不得升级
- [x] 新建两条都已接受且随后观测与策展位置相等 → 不人造冲突、不进待确认关闭
- [x] `runtimeId` 变化后的未打标实体是新的未绑定候选，不得按名称接到 X
- [x] 待补标绑定之后 `absentObjectIds` 含 X → 观测消失（不是失联）；绑定记忆解除；仍缺标的现场实体回到待并入

**Out of this ticket:** 失联时禁止选支/诊断落点（见 05）；有序总 tracer（见 06）；UI；受控 SSH 打标签。

## Comments

03 TDD-done 后再开。不要做 05–07。不要把命中写成观测空洞恢复。

开工 prompt：[`docs/implement-unbound-identity-rebind-04-prompt.md`](../../../docs/implement-unbound-identity-rebind-04-prompt.md)（钉死消费键、作废范围、absent 与过期记忆释放、第一圈红灯，并把下面四条义务写进步骤）。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：capability 票须 witnessed red；第一圈必须是「命中后 `GET /api/observed/identity-lost/{X}` 期望 400」的诚实红灯，不要用未认证 401 或既有 PRESENT / ABSENT 绿灯冒充。

### 来自 01–03 合同审计的三条约束（`audit-01-03-opus.md`）

1. **第一圈就钉住问法翻转**（审计 C-3）：标签命中之前，`GET /api/observed/asks/actual-where` 会在冲突已按合并键开出「策展 A / 实际 B」的同时仍答 `IDENTITY_LOST`——两条读路径互相矛盾。命中收尾必须让 `availability=PRESENT`、`identityLost=false`、`GET /api/observed/identity-lost/{id}` 400，并且 `by-merge-key` 恢复升级链。
2. **改掉那条把「命中仍失联」当正确的旧断言**（审计 S-4）：`UnboundDraftItemReviewHttpAcceptanceTest.bindingToLabelMatchedPresentTargetIsRejected` 末尾断言命中后 `identity-lost` GET 仍 200。它是全仓唯一一条「命中之后」的失联断言；清标后应改为 400（该用例真正要证的是绑定被拒，错误码仍 `UNBOUND_BIND_TARGET_HEALTHY`）。
3. **绑定记忆分不清绑定与新建**（审计 ST-1）：`unbound_bind_memory` 没有来源列，而故事 50（`absentObjectIds` 解除记忆）只针对待补标的**绑定**。若需区分，用**新增** Flyway 加 `origin`，不要改 V18 / V19。

另：票 08 已把绑定门禁判据改成「失联之后是否又标签命中」（`labelMatchedAfterIdentityLoss`）。清标之后该判据自然退化为「有没有失联标」，不必回改。

4. **绑定记忆现在按策展对象唯一**（票 08 的 `V19`），而解绑只可能发生在命中收尾或 `absentObjectIds`。因此故事 37（`runtimeId` 变化算新候选）与故事 50（`absentObjectIds` 解除记忆）在 04 是**必做**：否则误绑之后没有任何 HTTP 回退路径。

### Cycle A — 标签命中清除身份失联，问法当场翻转
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.labelMatchClearsIdentityLostAndFlipsActualWhere`
```
UnboundLabelMatchConsumeHttpAcceptanceTest > labelMatchClearsIdentityLostAndFlipsActualWhere() FAILED
    java.lang.AssertionError at UnboundLabelMatchConsumeHttpAcceptanceTest.java:51
java.lang.AssertionError: Status expected:<400> but was:<200>
```
（命中写观测 PRESENT 后 `identity_lost_mark` 仍在；`GET /api/observed/identity-lost/{X}` 仍 200。这是审计 C-3 的诚实红灯，不是未认证、不是既有 PRESENT / ABSENT 绿灯。）
Green command: 同上，exit 0。命中分支删除该对象的 `identity_lost_mark`（在 `upsertObservedPresent` / reconcile 之前）；同一快照的 `identityLostObjectIds` 不再把刚命中的对象重新打标。问法 `identityLost=false`、`availability=PRESENT`、上报宿主；「应该在哪」仍是策展宿主。
Regression: `UnboundDraftItemReviewHttpAcceptanceTest` 全类绿（审计 S-4：`bindingToLabelMatchedPresentTargetIsRejected` 末尾改为 `400 IDENTITY_LOST_NOT_FOUND`；主断言仍 `UNBOUND_BIND_TARGET_HEALTHY`）。`UnboundIdentityLostIngestHttpAcceptanceTest.currentlyUsableObservedHostSnapshotInfersIdentityLost` 与 `identityLostActualWhereDoesNotReportStaleObservedHost` 仍绿。`UnboundBindGateHttpAcceptanceTest` 全类绿。
Refactor: `clearIdentityLostMark` 抽出并注明状态表语义；显式失联声明跳过本快照已命中对象，避免清标被同请求写回。
Commit: `dcbf5c5` feat(unbound): clear 身份失联 on label match

### Cycle B — 命中消费绑定记忆，误绑后可再绑
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.labelMatchConsumesBindMemorySoALaterEntityCanBindAgain`
```
UnboundLabelMatchConsumeHttpAcceptanceTest > labelMatchConsumesBindMemorySoALaterEntityCanBindAgain() FAILED
    java.lang.AssertionError at UnboundLabelMatchConsumeHttpAcceptanceTest.java:232
java.lang.AssertionError: Status expected:<200> but was:<400>
```
（命中后记忆仍在：夹具跳过已被绑的 X，对 u04b-rt-2 发起草案得到 400 `UNBOUND_DRAFT_FIXTURE_UNAVAILABLE`。随后补实现时又撞上 V17 `curated_draft_candidate_fk`，删除候选行会 500，故新增 V20 去掉该 FK，草案仍保留 `candidate_id` 审计指针。）
Green command: 同上，exit 0。命中时删除 `curated_object_id = X` 的绑定记忆，并删除这些记忆键与本次命中 `(hostId, runtimeId)` 的未绑定候选行。补标 → 重新失联 → 重绑在 HTTP 上走通。
Regression: `UnboundBindGateHttpAcceptanceTest` 全类绿（V19 目标唯一未被削弱）。Cycle A 同套件仍绿。
Refactor: `consumeAfterLabelMatch` / `deleteUnboundCandidate` 抽出；V20 只增不改历史脚本。
Commit: `d9f1d90` feat(unbound): consume bind memory and 未绑定候选 on label match

### Cycle C — 命中作废未绑定草案；再审条 DRAFT_VOIDED；事件可读
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.labelMatchVoidsOpenUnboundDraftAndRejectsFurtherReview`
```
UnboundLabelMatchConsumeHttpAcceptanceTest > labelMatchVoidsOpenUnboundDraftAndRejectsFurtherReview() FAILED
    java.lang.AssertionError at UnboundLabelMatchConsumeHttpAcceptanceTest.java:152
JSON path "$.data.status" Expected: is "VOIDED" but: was "OPEN"
```
Green command: 同上，exit 0。命中时按候选 id / (host, runtime) / BIND 目标 / 已接受 CREATE 主语作废 OPEN 未绑定草案；`CuratedDraftEventType.DRAFT_VOIDED` 写入 `curated_draft_event`（不写 conflict_case_event）；`beginUnboundItemReview` 对 VOIDED 抛 `DRAFT_VOIDED`。条目仍 PENDING，策展「运行于」不变。
Regression: `UnboundDraftItemReviewHttpAcceptanceTest` 全类绿。`bindingToLabelMatchedPresentTargetIsRejected` 在命中后草案已被作废，再接受变为 `DRAFT_VOIDED`（不再到达 `UNBOUND_BIND_TARGET_HEALTHY`）；S-4 的 identity-lost 400 仍在。`labelMatchedAfterIdentityLoss` 门禁保留。
Refactor: 作废范围集中在 `voidOpenUnboundAfterLabelMatch`；ingest 先作废再删候选行。
Commit: `ca4f4c8` feat(unbound): void OPEN 未绑定草案 on label match

### Cycle D — 仅刷新同一 runtimeId 不作废未绑定草案
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.unlabeledReheartbeatDoesNotVoidOpenUnboundDraft`
reuse/regression：首跑绿。点名 `UnboundDraftItemReviewHttpAcceptanceTest.unlabeledReheartbeatAfterBindStaysConsumedAndIdentityLost`（03 Cycle E：刷新观察时间不作废）与 01 的 upsert。不另写生产。
Green command: 同上，exit 0。
Refactor: 无结构改动
Commit: （与 E–H 同批回填）

### Cycle E — 命中后观测宿主 ≠ 策展「运行于」→ 升级链恢复
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.labelMatchOnADifferentHostRestoresTheUpgradeChain`
reuse/regression：首跑绿。点名 `ConflictWarnUpgradeHttpAcceptanceTest` 的 B→C 升级 + Cycle A 清标。命中之前未打标同名仍 `CONFLICT_NOT_FOUND`；B 命中开出 OPEN；C 再命中升级且 `observedLineage` 含 B→C。不另写比对引擎。
Green command: 同上，exit 0。
Refactor: 无结构改动
Commit: （与 D/F–H 同批回填）

### Cycle F — 新建两条都接受后相等 → 不人造冲突
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.acceptedCreateAndRunsOnThenEqualHitDoesNotInventAConflict`
reuse/regression：首跑绿。点名 `reconcileMergeKey` 相等且无活跃直接 return，以及 `UnboundDraftItemReviewHttpAcceptanceTest.acceptingRunsOnAfterCreateWritesFirstCuratedRunsOn`。
Green command: 同上，exit 0。
Refactor: 无结构改动
Commit: （与 D/E/G–H 同批回填）

### Cycle G — runtimeId 变化 → 新候选；过期记忆释放；不按名接回
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.runtimeIdChangeIsANewCandidateAndReleasesStaleBindMemory`
```
UnboundLabelMatchConsumeHttpAcceptanceTest > runtimeIdChangeIsANewCandidateAndReleasesStaleBindMemory() FAILED
    java.lang.AssertionError at UnboundLabelMatchConsumeHttpAcceptanceTest.java:496
java.lang.AssertionError: Status expected:<200> but was:<400>
```
（删重建后 u04g-rt-2 出现在待并入，但 (A, u04g-rt-1)→X 的记忆仍在，夹具跳过 X，发起草案 400。）
Green command: 同上，exit 0。带快照的心跳把未再报告的 runtime 上的绑定记忆与候选行过期释放；不清 X 的失联标；同名不点亮升级链。`unlabeledReheartbeatAfterBindStaysConsumedAndIdentityLost` 仍绿（同一 runtime 仍报告则不释放）。
Refactor: `releaseStaleBindMemory` 抽出，只在 `processSnapshot` 末尾对报告清单求补。
Commit: `d8b0f97` feat(unbound): release stale bind memory and treat absent as 观测消失

### Cycle H — 待补标绑定之后 absentObjectIds 含 X → 观测消失并回到待并入
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.absentObjectIdsAfterBindIsObservedAbsenceAndReturnsTheFieldEntityToPending`
```
UnboundLabelMatchConsumeHttpAcceptanceTest > absentObjectIdsAfterBindIsObservedAbsenceAndReturnsTheFieldEntityToPending() FAILED
    java.lang.AssertionError at UnboundLabelMatchConsumeHttpAcceptanceTest.java:404
JSON path "$.data.identityLost" Expected: is <false> but: was <true>
```
Green command: 同上，exit 0。absent 分支清失联标、释放该对象绑定记忆、不删仍缺标的候选行；问法 `ABSENT` / hostId JSON null / `identityLost=false`。01 `absentObjectIdsRemainUsableAbsentNotIdentityLost` 与 `absentObjectIdsWinOverIdentityLostObjectIdsOnSameSnapshot` 仍绿。
Refactor: `releaseBindMemoryForObject` 抽出，与命中消费共用删除记忆、但不删候选。
Commit: `d8b0f97` feat(unbound): release stale bind memory and treat absent as 观测消失

### Cycle I — 心跳契约文档跟上命中收尾与绑定记忆（审计 S-2）
`docs/contracts/agent-heartbeat-snapshot.md`：绑定记忆 = 逐条确认后的匹配状态；命中收尾顺序；absent 释放记忆并回到待并入；快照不再报告的 runtime 过期释放；心跳-only 不释放、不推断。不改 `CONTEXT.md`，不新开 ADR。
Commit: `2db2886` docs(unbound): describe bind memory and label-match consume on heartbeat

### Cycle J — 票级回归与收尾
`cd backend && ./gradlew cleanTest test` → **141 tests, 0 failures**（23 个测试类）。含 01 / 02 / 03 / 08、竖切负面、`ChangeCuratedDraft*`、`HeartbeatTimeoutHollow`。

`/code-review`（merge-base `origin/main` / `39fc65f`）：**Standards = no hard violations**（Flyway 只增 V20；分层与构造器注入未破；Redis 未参与；气味均为 judgement：`bindKey` 与 `unboundHostRuntimeKey` 重复、VOIDED 级联与改策展相似）。**Spec = spec-faithful**。审计处置可追溯：C-3 = Cycle A 清标；S-4 = identity-lost 400，命中后再接受因草案已作废而为 `DRAFT_VOIDED`（故事 42；`labelMatchedAfterIdentityLoss` 仍保留）；S-2 = Cycle I 契约文档；ST-1 不新加 origin 列（消费/释放按对象键，票内已钉死）。Story 50 以待并入重新出现为 HTTP 证据；absent 后 X 是观测消失不是失联，夹具按票允许不再开第二份草案。未实现 05–07 / 09。

下一 frontier = **05**。票 09（审计 C-1）待人排期。
