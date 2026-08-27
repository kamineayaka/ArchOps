# 09 — 失联叠加心跳超时：规范问法仍须说出观测空洞

**What to build:** 当某 Docker 容器既已被打上身份失联标、其观测通道又**确实心跳超时**时，规范问法「实际在哪」不得只答身份失联：`availability` 应为 `HOLLOW`（无当前可用观测值），同时 `identityLost` 旗标仍为 true。失联不是空洞（票 01 已立），但**空洞也不能被失联吞掉**——通道死了必须能读出来，否则运维与诊断会被引向「去现场补标」，而真相是 Agent 不在了。只改问法读模型；不改 `observed_fact.availability` 的取值域（仍 `PRESENT` / `ABSENT`），不新增冲突状态枚举，不动票 10 的超时扫描与挂起语义。

**Blocked by:** 01 — 控制面推断身份失联；未绑定按现场实体 upsert；规范问法

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)（「`availability` … Must not be set to `HOLLOW` or `ABSENT` **solely** because of 失联」——反过来说，通道确实超时时 `HOLLOW` 就是该答的那个值）。

来源：`.scratch/unbound-identity-rebind/audit-01-03-opus.md` 的 **C-1**（FIX-NOW / major）。审计探针命令与失败文本见该报告附录。注意票 10 的扫描会**删除**超时 Agent 写下的观测事实，所以读模型无法靠观测行判断「通道已死」还是「从未观测」；判据要看上报宿主的 `host_agent` 新鲜度。

- [ ] 失联标存在 + 该对象策展 `运行于`（或失联标来源）宿主的 Agent 已超时 → 「实际在哪」`availability=HOLLOW`、`identityLost=true`、`observedValue.hostId` 为空、策展同屏（P2）
- [ ] 失联标存在 + 心跳仍新鲜 → 仍是 `IDENTITY_LOST`（票 01 的 `neverObservedIdentityLostActualWhereIsNotHollow` / `identityLostActualWhereDoesNotReportStaleObservedHost` 不回归）
- [ ] 无失联标 + 心跳超时 → 仍是 `HOLLOW`（票 10 `HeartbeatTimeoutHollowHttpAcceptanceTest` 不回归）
- [ ] 观测消失仍是 `ABSENT`（票 01 `absentObjectIdsRemainUsableAbsentNotIdentityLost` 不回归）
- [ ] `observed_fact.availability` 的 CHECK 不变；不新增 `ConflictStatus`；不改 `CONTEXT.md`

**Out of this ticket:** 诊断分叉集在失联 / 空洞下的取舍（票 05 的「除非心跳确实超时」）；标签命中收尾（票 04）；冲突挂起与计划作废（票 10 已实现，只做不回归）；UI。

## Comments

01–07 + 08 已闭合（PR #99 = 票 07 已合入），本票已 unblocked。开场 prompt：[`docs/implement-unbound-identity-rebind-09-prompt.md`](../../../docs/implement-unbound-identity-rebind-09-prompt.md)。本票是问法读模型能力票：witnessed red → green → refactor。不要做 A1 / ADR-0044 进程拆分 / 发明未绑定 10。不要重做 01–08 / 07 UI。代码 vs ADR-0044 审计 **A3 / 01–03 审计 C-1** 即本票。见 [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../audit-code-vs-adr-0044.md)。

审计推荐把这条与票 04 分开做，不要混进同一张票。若你更愿意把「失联与空洞并存时问法的优先级」写成合同，另立 ADR 议题；备选语义：(甲) 通道超时优先报空洞、失联降为旗标（审计推荐、本票采用）；(乙) 失联优先、空洞只在诊断分叉里体现。
