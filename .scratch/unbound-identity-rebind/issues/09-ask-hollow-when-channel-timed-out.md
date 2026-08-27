# 09 — 失联叠加心跳超时：规范问法仍须说出观测空洞

**What to build:** 当某 Docker 容器既已被打上身份失联标、其观测通道又**确实心跳超时**时，规范问法「实际在哪」不得只答身份失联：`availability` 应为 `HOLLOW`（无当前可用观测值），同时 `identityLost` 旗标仍为 true。失联不是空洞（票 01 已立），但**空洞也不能被失联吞掉**——通道死了必须能读出来，否则运维与诊断会被引向「去现场补标」，而真相是 Agent 不在了。只改问法读模型；不改 `observed_fact.availability` 的取值域（仍 `PRESENT` / `ABSENT`），不新增冲突状态枚举，不动票 10 的超时扫描与挂起语义。

**Blocked by:** 01 — 控制面推断身份失联；未绑定按现场实体 upsert；规范问法

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)（「`availability` … Must not be set to `HOLLOW` or `ABSENT` **solely** because of 失联」——反过来说，通道确实超时时 `HOLLOW` 就是该答的那个值）。

来源：`.scratch/unbound-identity-rebind/audit-01-03-opus.md` 的 **C-1**（FIX-NOW / major）。审计探针命令与失败文本见该报告附录。注意票 10 的扫描会**删除**超时 Agent 写下的观测事实，所以读模型无法靠观测行判断「通道已死」还是「从未观测」；判据要看上报宿主的 `host_agent` 新鲜度。

- [x] 失联标存在 + 该对象策展 `运行于`（或失联标来源）宿主的 Agent 已超时 → 「实际在哪」`availability=HOLLOW`、`identityLost=true`、`observedValue.hostId` 为空、策展同屏（P2）
- [x] 失联标存在 + 心跳仍新鲜 → 仍是 `IDENTITY_LOST`（票 01 的 `neverObservedIdentityLostActualWhereIsNotHollow` / `identityLostActualWhereDoesNotReportStaleObservedHost` 不回归）
- [x] 无失联标 + 心跳超时 → 仍是 `HOLLOW`（票 10 `HeartbeatTimeoutHollowHttpAcceptanceTest` 不回归）
- [x] 观测消失仍是 `ABSENT`（票 01 `absentObjectIdsRemainUsableAbsentNotIdentityLost` 不回归）
- [x] `observed_fact.availability` 的 CHECK 不变；不新增 `ConflictStatus`；不改 `CONTEXT.md`

**Out of this ticket:** 诊断分叉集在失联 / 空洞下的取舍（票 05 的「除非心跳确实超时」）；标签命中收尾（票 04）；冲突挂起与计划作废（票 10 已实现，只做不回归）；UI。

## Comments

01–07 + 08 已闭合（PR #99 = 票 07 已合入），本票已 unblocked。开场 prompt：[`docs/implement-unbound-identity-rebind-09-prompt.md`](../../../docs/implement-unbound-identity-rebind-09-prompt.md)。本票是问法读模型能力票：witnessed red → green → refactor。不要做 A1 / ADR-0044 进程拆分 / 发明未绑定 10。不要重做 01–08 / 07 UI。代码 vs ADR-0044 审计 **A3 / 01–03 审计 C-1** 即本票。见 [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../audit-code-vs-adr-0044.md)。

审计推荐把这条与票 04 分开做，不要混进同一张票。若你更愿意把「失联与空洞并存时问法的优先级」写成合同，另立 ADR 议题；备选语义：(甲) 通道超时优先报空洞、失联降为旗标（审计推荐、本票采用）；(乙) 失联优先、空洞只在诊断分叉里体现。

### Cycle A — 失联叠加心跳超时扫描后「实际在哪」仍须说出观测空洞
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest.identityLostPlusHeartbeatTimeoutScanActualWhereIsHollow`
Red output:
```
JSON path "$.data.observedValue.availability"
Expected: is "HOLLOW"
     but: was "IDENTITY_LOST"
```
范围内未打标快照已打失联标；回拨该上报宿主 `host_agent` 并 `POST /api/observed/scan-heartbeat-timeouts` 之后，问法仍因 `lostMark != null` 短路为 `IDENTITY_LOST`，观测空洞被吞掉。
Green: `observedAskValue` 在失联标存在时先看策展「运行于」宿主与失联标 `sourceHostId` 的 `host_agent` 新鲜度；超时则 `availability=HOLLOW`，`identityLost` 仍为 true。不删标、不改 `observed_fact.availability`、不问法路径调用 `onObservationBecameHollow`。
Refactor: `ObservationFreshnessService` 抽出 `heartbeatCutoff`；同源宿主不重复查通道。
Commit: `2121fe7`

### Cycle B — 失联标存在且心跳仍新鲜时「实际在哪」仍是 IDENTITY_LOST
Command:
`cd backend && ./gradlew test --tests com.archops.observed.IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest.identityLostWithFreshHeartbeatActualWhereStaysIdentityLost`
reuse/regression：首跑绿。票 01 `neverObservedIdentityLostActualWhereIsNotHollow` / `identityLostActualWhereDoesNotReportStaleObservedHost` 已钉「不得单因失联报 HOLLOW」；本圈把同一语义放进 09 类，确认 Cycle A 的 `host_agent` 新鲜度判据在心跳未回拨时不把失联改写成空洞。不另写生产。
Green command: 同上，exit 0。
Refactor: 抽出 `heartbeatUnlabeled` / `backdateAgent` / `assertActualWhere` / `assertShouldWhere` / `assertIdentityLost`。
Commit: `7aedd41`

### Cycle C — 失联叠加回拨宿主但不扫，问法仍须说出观测空洞
Command:
`cd backend && ./gradlew test --tests com.archops.observed.IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest.identityLostPlusStaleHeartbeatWithoutScanActualWhereIsHollow`
reuse/regression：首跑绿。Cycle A 已按 `host_agent` 新鲜度（策展「运行于」+ 失联标 `sourceHostId`）决定 `HOLLOW`，不依赖票 10 扫描删行。本圈夹具先标签命中留下 `observed_fact`，再范围内未打标打失联，只回拨不上扫描；问法仍 `HOLLOW` + `identityLost=true`，标仍在。不另写生产。
Green command: 同上，exit 0。
Refactor: 抽出 `heartbeatLabeled`。
Commit: `34cd8e1`

### Cycle D — 无失联标的心跳超时仍是 HOLLOW
Command:
`cd backend && ./gradlew test --tests com.archops.observed.IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest.heartbeatTimeoutWithoutIdentityLostStillAnswersHollow`
reuse/regression：首跑绿。票 10 `HeartbeatTimeoutHollowHttpAcceptanceTest` 已覆盖无标超时 → `HOLLOW`；本圈确认 09 问法改动不把无标超时改写成 `IDENTITY_LOST`。不另写生产。
Green command: 同上，exit 0。
Refactor: 抽出 `scanHeartbeatTimeouts`。
Commit: `63bcc71`

### Cycle E — 观测消失仍是 ABSENT，不是 HOLLOW / IDENTITY_LOST
Command:
`cd backend && ./gradlew test --tests com.archops.observed.IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest.absentObjectIdsRemainAbsentNotHollowOrIdentityLost`
reuse/regression：首跑绿。票 01 `absentObjectIdsRemainUsableAbsentNotIdentityLost` 已钉观测消失；本圈确认 09 的通道超时优先不把 `ABSENT` 改写成 `HOLLOW` 或 `IDENTITY_LOST`。不另写生产。
Green command: 同上，exit 0。
Refactor: 抽出 `assertNoIdentityLost`。
Commit: `e24e585`

### Cycle F — 上报通道仍新鲜时，仅策展宿主超时不得把失联答成空洞
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest.identityLostKeepsIdentityLostWhenOnlyCuratedHostTimedOutAndReportingHostIsFresh`
Red output:
```
JSON path "$.data.observedValue.availability"
Expected: is "IDENTITY_LOST"
     but: was "HOLLOW"
```
code-review Spec 轴：`identityLostChannelTimedOut` 对策展「运行于」与 `sourceHostId` 做 OR，策展宿主超时会把仍新鲜的上报通道答成空洞，违反 Story 12 / 审计 A3「sourced agent」。
Green: 问法只看失联标来源宿主（无则回落策展「运行于」）的 `host_agent` 新鲜度。
Refactor: 无。上报通道优先于策展宿主，OR 短路删掉。
Commit: `41305f9`

票结束：`cd backend && ./gradlew cleanTest test` → BUILD SUCCESSFUL，170 tests, 0 failures。`IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest` 6/6。问法读模型闭合。不要发明未绑定 10。A1 仍另开。不要自动做 A1 / ADR-0044 进程拆分。不改 `CONTEXT.md` / 已有 ADR 正文。

### Code review（Standards + Spec）
- Standards: 无硬违规。判断项：`"HOLLOW"` / `"IDENTITY_LOST"` 字面量沿用既有 ask DTO；B–E 首跑绿记 reuse/regression（不删 01/10 生产装红灯）；Cycle F 为 Spec 轴补的上报通道新鲜度。
- Spec: Cycle A 诚实红灯（失联+扫描后曾答 `IDENTITY_LOST`）。Story 12 / 审计 A3：问法以失联标 `sourceHostId` 为上报通道（无则回落策展「运行于」），仅策展宿主超时不得吞掉仍新鲜的失联。未改 `observed_fact.availability`、未新增 `ConflictStatus`、GET 不清标、不问法路径调用 `onObservationBecameHollow`、未改诊断 forks / A1 / 07 UI / Flyway / CONTEXT / ADR。
- 未做未绑定 10 / A1 / ADR-0044 进程拆分。
