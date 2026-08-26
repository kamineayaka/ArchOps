# 05 — 失联闸门修实际 / 改理想路径

**What to build:** 当冲突合并键的主体已是身份失联时，不得再按旧实际选「修实际」或「改理想」、不得审/执行该键操作计划。已有冲突保留（不新增状态枚举、不进入空洞挂起）。若已是待确认关闭则退回开放。活跃操作计划受阻即停作废。该键上未完成改理想草案作废。诊断不得再给出以旧实际为落点的分叉，只读说明身份失联，处理走未绑定草案 / 现场补标。心跳通道仍新鲜时，不得改走纯空洞的「恢复观测通道」分叉集。

**Blocked by:** 01 — 控制面推断身份失联；未绑定按现场实体 upsert；规范问法

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

本票复用竖切协作/计划/改策展草案，只加闸门。01 完成后即可与 02–04 并行准备，但两张都 unblocked 时按编号最小先做 02。冲突 GET 须能读到失联旗标。

- [ ] 失联主体上 `FIX_ACTUAL` / `CHANGE_CURATED` 选支失败；非处理人规则不变
- [ ] GET 诊断不含以旧实际为唯一落点的修实际 / 改理想分叉；文案用身份失联 / 未绑定观测候选，不用「以现场为准」
- [ ] 不得单因失联改走纯空洞恢复观测通道分叉集（除非心跳确实超时）
- [ ] 该合并键活跃操作计划作废（既有计划作废语义）；不得继续执行
- [ ] 该对象 `运行于` 上开放的改理想草案作废；再审条失败
- [ ] 冲突状态枚举不增加：`OPEN` 保持 `OPEN`；`PENDING_CLOSE` + 失联 → `OPEN`；不是 `SUSPENDED`
- [ ] 冲突 GET 可读身份失联；未绑定本身仍不新开冲突

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
Commit: 本圈绿灯提交。
