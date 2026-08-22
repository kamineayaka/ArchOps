# 04 — 标签命中收尾：清失联、消费候选、恢复升级链

**What to build:** 现场补标之后，下一次心跳用正确 `archops.object_id` 命中策展 Docker 容器：写入观测 `运行于`，清除身份失联，消费对应未绑定候选与绑定记忆，作废仍指向该候选/目标的未完成未绑定草案。此后位置偏差走既有冲突升级链。`runtimeId` 因删重建而变化则视为新的未绑定候选，禁止按显示名续上旧绑定。若在待补标绑定之后出现 `absentObjectIds`：走观测消失，解除指向该对象的绑定记忆，现场实体若仍缺标则回到待并入。

**Blocked by:** 03 — 逐条确认：新建写入对象；绑定只记对应关系

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

补标本身不在本票用 SSH / 操作计划完成：测试夹具直接发带正确标签的快照。先策展后补标（ADR-0012）的正向收尾在本票闭合。

- [ ] 命中 X 的标签 → 观测 `运行于` 为快照上报主机；身份失联清除；绑定记忆与该候选不再待并入
- [ ] 命中时若有开放未绑定草案指向该候选或「绑到 X / 用该候选新建」→ 草案作废，再接受被拒
- [ ] 同一 `runtimeId` 仅刷新观察时间、草案仍开放时 → **不**作废未绑定草案
- [ ] 命中后若观测宿主与策展 `运行于` 不等 → 既有比对发出/升级冲突（升级链恢复）；同名弱线索在命中前仍不得升级
- [ ] 新建两条都已接受且随后观测与策展位置相等 → 不人造冲突、不进待确认关闭
- [ ] `runtimeId` 变化后的未打标实体是新的未绑定候选，不得按名称接到 X
- [ ] 待补标绑定之后 `absentObjectIds` 含 X → 观测消失（不是失联）；绑定记忆解除；仍缺标的现场实体回到待并入

**Out of this ticket:** 失联时禁止选支/诊断落点（见 05）；有序总 tracer（见 06）；UI；受控 SSH 打标签。

## Comments

03 TDD-done 后再开。不要做 05–07。不要把命中写成观测空洞恢复。

### 来自 01–03 合同审计的三条约束（`audit-01-03-opus.md`）

1. **第一圈就钉住问法翻转**（审计 C-3）：标签命中之前，`GET /api/observed/asks/actual-where` 会在冲突已按合并键开出「策展 A / 实际 B」的同时仍答 `IDENTITY_LOST`——两条读路径互相矛盾。命中收尾必须让 `availability=PRESENT`、`identityLost=false`、`GET /api/observed/identity-lost/{id}` 400，并且 `by-merge-key` 恢复升级链。
2. **改掉那条把「命中仍失联」当正确的旧断言**（审计 S-4）：`UnboundDraftItemReviewHttpAcceptanceTest.bindingToLabelMatchedPresentTargetIsRejected` 末尾断言命中后 `identity-lost` GET 仍 200。它是全仓唯一一条「命中之后」的失联断言；清标后应改为 400（该用例真正要证的是绑定被拒，错误码仍 `UNBOUND_BIND_TARGET_HEALTHY`）。
3. **绑定记忆分不清绑定与新建**（审计 ST-1）：`unbound_bind_memory` 没有来源列，而故事 50（`absentObjectIds` 解除记忆）只针对待补标的**绑定**。若需区分，用**新增** Flyway 加 `origin`，不要改 V18 / V19。

另：票 08 已把绑定门禁判据改成「失联之后是否又标签命中」（`labelMatchedAfterIdentityLoss`）。清标之后该判据自然退化为「有没有失联标」，不必回改。
