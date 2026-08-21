# 01 — 控制面推断身份失联；未绑定按现场实体 upsert；规范问法

**What to build:** Host Agent 心跳快照仍按标签匹配 Docker 容器。缺标 / 未知标签写成未绑定观测候选，但同一现场实体（`sourceHostId` + `runtimeId`）只保留一行并刷新观察时间。当上报主机是该容器的策展 `运行于` 宿主或当前可用观测宿主（从未写过观测则只看策展宿主），本快照未标签命中且未进入 `absentObjectIds` 时，控制面给该对象打身份失联——不必等 Agent 填写 `identityLostObjectIds`。他机快照不得给 X 打失联。规范问法「实际在哪」在失联时不得把旧宿主当实际，也不得把失联写成观测空洞或观测消失。未打标同名仍不承诺冲突升级链。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条 HTTP 测试。先 witnessed red，再写本票生产代码。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

从竖切往上长：今日未绑定每次快照都插入新行；身份失联只信 Agent 声明；GET 未绑定不含 labels；标签稍后命中也不清失联。本票只把观测侧认得出、列得出、问得清；不发起草案、不绑定、不闸门选支。

- [ ] 缺标快照 → 未绑定 `MISSING_LABEL`，`upgradeChainPromised=false`；未知 `archops.object_id` → `UNKNOWN_OBJECT_ID`
- [ ] 同一 `sourceHostId` + `runtimeId` 再心跳 → upsert（刷新 `observedAt` / 名称 / 标签 / 原因），列表不因每拍多一行
- [ ] GET 未绑定含 labels（至少现场 `archops.object_id`）、`runtimeId`、`name`、`reason`、`sourceHostId`；`upgradeChainPromised` 恒为 false
- [ ] 策展/可用观测宿主上的快照未标签命中且未声明观测消失 → 该 Docker 容器身份失联；Agent `identityLostObjectIds` 在同一主机范围内仍有效
- [ ] 既非策展 `运行于` 宿主、也非当前可用观测宿主的快照，不得给该容器打身份失联
- [ ] `absentObjectIds` 仍写入观测消失（可用值不存在），不是身份失联，也不是观测空洞
- [ ] 「应该在哪」仍答策展；「实际在哪」在失联时同屏策展，且不得把失联前宿主当实际（`availability` 不得为 `PRESENT`；不得单因失联报 `HOLLOW` / `ABSENT`）
- [ ] 未打标同名路径：`by-merge-key` 仍不承诺升级链（竖切票 13 负面不回归）
- [ ] 心跳契约文档写明：控制面推断失联、未绑定 upsert、主机范围；不改 `CONTEXT.md`

**Out of this ticket:** 未绑定草案、绑定记忆、新建策展对象、失联时选支/诊断闸门、标签命中收尾、薄 UI、SSH、Y2、LLM。

## Comments

Frontier。一次只做本票。HTTP 主接缝；Flyway 只增不改历史。不要做 02–07。

开工 prompt：[`docs/implement-unbound-identity-rebind-01-prompt.md`](../../../docs/implement-unbound-identity-rebind-01-prompt.md)。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：capability 票须 witnessed red；不要为装红灯删除竖切未打标 / 观测消失 / 命中 `运行于` 的生产。

### Cycle A — GET 未绑定含现场 labels JSON 对象
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.unknownObjectIdUnboundCandidateListsFieldLabels
```
UnboundIdentityLostIngestHttpAcceptanceTest > unknownObjectIdUnboundCandidateListsFieldLabels() FAILED
    java.lang.AssertionError at UnboundIdentityLostIngestHttpAcceptanceTest.java:75
java.lang.AssertionError:
Expected: is <true>
     but: was <false>
	at com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.unknownObjectIdUnboundCandidateListsFieldLabels(UnboundIdentityLostIngestHttpAcceptanceTest.java:75)
```
（`labels` 缺失，`path("labels").isObject()` 为 false）
Green command: 同上，BUILD SUCCESSFUL / exit 0。`unlabeledAndIdentityLostDoNotPromiseUpgradeChain` 仍绿。
Refactor: `parseLabels` 抽出 `LABEL_MAP` TypeReference，空/坏 JSON 回空对象。
Commit: a28cd49 Expose field labels on GET unbound candidates.

### Cycle B — 同一 sourceHostId+runtimeId 未绑定 upsert
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.sameHostAndRuntimeIdUnboundCandidateIsUpserted
```
UnboundIdentityLostIngestHttpAcceptanceTest > sameHostAndRuntimeIdUnboundCandidateIsUpserted() FAILED
    java.lang.AssertionError at UnboundIdentityLostIngestHttpAcceptanceTest.java:146
java.lang.AssertionError:
Expected: is <1>
     but: was <2>
```
Green command: 同上，BUILD SUCCESSFUL / exit 0。`unlabeledAndIdentityLostDoNotPromiseUpgradeChain` 仍绿（一次快照两个不同 runtimeId）。
Refactor: `persistUnbound` 改名为 `upsertUnbound`，抽出 `applyUnboundSnapshot` / `toUnboundSummary`。
Commit: 14a2e6a Upsert unbound candidates by host and runtime id.

### Cycle C — 策展宿主快照无声明也推断身份失联
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.curatedHostSnapshotInfersIdentityLostWithoutAgentDeclaration
```
UnboundIdentityLostIngestHttpAcceptanceTest > curatedHostSnapshotInfersIdentityLostWithoutAgentDeclaration() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<400>
             Body = {"success":false,"code":"IDENTITY_LOST_NOT_FOUND",...}
```
Green command: 同上，BUILD SUCCESSFUL / exit 0。夹具未带 `identityLostObjectIds`。`unlabeledAndIdentityLostDoNotPromiseUpgradeChain` 仍绿。
Refactor: 推断独立为 `inferIdentityLost` + `reportingHostInIdentityLostScope`；`identityLostObjectIds` 循环未加主机范围。
Commit: 6621092 Infer identity lost from in-scope unlabeled snapshots.

### Cycle D — 他机快照不得给 X 打失联
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.otherHostSnapshotDoesNotMarkIdentityLost
reuse/regression：首跑绿。与 Cycle C 同一主机范围规则（`reportingHostInIdentityLostScope`：上报 host 须为策展「运行于」或当前可用观测宿主；他机 u01d-hc 不在范围）。不另写生产。
Green command: 同上，BUILD SUCCESSFUL / exit 0。C 上未打标实体仍可按 runtimeId 列为未绑定。
Refactor: 无结构改动。
Commit: ece05d4 Reject identity-lost inference from an out-of-scope host.

### Cycle E — 当前可用观测宿主也可推断身份失联
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.currentlyUsableObservedHostSnapshotInfersIdentityLost
reuse/regression：首跑绿。与 Cycle C 同一主机范围（`reportingHostInIdentityLostScope` 已认 `observed_fact` PRESENT 且未超时的观测宿主）。不另写生产。命中那一次仍写 PRESENT（本圈不改 actualWhere）。
Green command: 同上，BUILD SUCCESSFUL / exit 0。
Refactor: 无结构改动。
Commit: bdd05ae Infer identity lost from the currently usable observed host.

### Cycle F — 从未观测仅推断失联时「实际在哪」不是空洞
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.neverObservedIdentityLostActualWhereIsNotHollow
```
UnboundIdentityLostIngestHttpAcceptanceTest > neverObservedIdentityLostActualWhereIsNotHollow() FAILED
    java.lang.AssertionError: No value at JSON path "$.data.identityLost"
Caused by: com.jayway.jsonpath.PathNotFoundException: No results for path: $['data']['identityLost']
```
Green command: 同上，BUILD SUCCESSFUL / exit 0。`actualWhereWithoutObservationIsHollowWithCuratedOnScreen` 仍绿。未改 `observed_fact.availability` CHECK。
Refactor: 抽出 `observedAskValue`；IDENTITY_LOST 仅读模型。
Commit: 46a5fa4 Project identity lost on 实际在哪 without calling it hollow.

### Cycle G — 失联后「实际在哪」不得报旧宿主
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.identityLostActualWhereDoesNotReportStaleObservedHost
reuse/regression：首跑绿。与 Cycle F 同一读模型（`observedAskValue` 在有 identity_lost_mark 时忽略库内旧 PRESENT）。不另写生产、不清失联标。
Green command: 同上，BUILD SUCCESSFUL / exit 0。
Refactor: 无结构改动。
Commit: 7e1a3ba Keep stale observed hosts off 实际在哪 after identity loss.

### Cycle H — 超范围 identityLostObjectIds 不得给 X 打失联
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.outOfScopeIdentityLostObjectIdsDoNotMarkContainer
```
UnboundIdentityLostIngestHttpAcceptanceTest > outOfScopeIdentityLostObjectIdsDoNotMarkContainer() FAILED
    java.lang.AssertionError: Status expected:<400> but was:<200>
```
（独立 witnessed red：声明循环原先不看主机范围。）
Green command: 同上，BUILD SUCCESSFUL / exit 0。`unlabeledAndIdentityLostDoNotPromiseUpgradeChain` 仍绿（范围内声明）。
Refactor: 抽出 `findCuratedRunsOn`；声明循环复用 `reportingHostInIdentityLostScope`。
Commit: 10ce6e5 Ignore out-of-scope Agent identityLostObjectIds.

### Cycle I — absentObjectIds 仍是观测消失不是失联
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.absentObjectIdsRemainUsableAbsentNotIdentityLost
reuse/regression：首跑绿。推断已跳过 `absentCuratedIds`；`observedAskValue` 在无 mark 时走 ABSENT。点名 `ObservedHeartbeatHttpAcceptanceTest.absentObjectIdIsUsableAbsentNotHollow`。不另写生产。
Green command: 同上，且 `absentObjectIdIsUsableAbsentNotHollow` 仍绿。
Refactor: 无结构改动。
Commit: a2eeb15 Keep absentObjectIds as 观测消失, not identity lost.

### Cycle J — 未打标同名不承诺升级链
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.unlabeledSameNameDoesNotPromiseUpgradeChain
reuse/regression：首跑绿。点名 `VerticalSliceHttpE2eAcceptanceTest.negative_unlabeledSnapshotDoesNotPromiseUpgradeChain`。GET `/api/conflicts/by-merge-key` 仍 400 `CONFLICT_NOT_FOUND`。不另写生产、不按 name 匹配。
Green command: 同上，BUILD SUCCESSFUL / exit 0。
Refactor: 无结构改动。
Commit: 839723b Keep unlabeled same-name snapshots off the upgrade chain.
