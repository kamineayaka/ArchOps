# 01 — 诊断选支不经身份、不记操作者

**What to build:** 开放冲突上，当前诊断已就绪时，任何人可以显式诊断选支，不需要用户身份，也不需要已接受处理人。选「修实际」仍生成待审操作计划；选「改理想」仍生成开放草案且不生成操作计划。选支结果与冲突事件不记录操作者。身份失联仍挡住修实际与改理想。诊断未就绪或不是当前诊断时仍拒绝。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

**TDD:** capability。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条 HTTP 测试。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。

- [ ] 两侧不等的 OPEN 冲突、诊断 READY、无人认领：不带 `X-ArchOps-User-Id` 的 `POST /api/conflicts/{id}/branch-selection`（`FIX_ACTUAL_TO_CURATED`）→ 200，操作计划 `DRAFT_REVIEW`、`skipsDraft=true`、`createdBy` 为 null；随后 GET 该计划 `createdBy` 仍为 null
- [ ] 同一选支若带了用户身份头，计划 `createdBy` 仍为 null（身份不写成领域事实）
- [ ] 不带身份头、无处理人，选 `CHANGE_CURATED_TO_OBSERVED` → 开放草案、`createdBy` 为 null、没有活跃操作计划；冲突事件 `DRAFT_CREATED` 的 `actorUserId` 为 null
- [ ] 身份失联时，无处理人选择修实际或改理想仍是 `IDENTITY_LOST_BLOCKS_BRANCH`，不是处理人门禁
- [ ] 过时诊断 id 仍是 `DIAGNOSIS_NOT_READY`；已有活跃计划时再选仍是 `PLAN_ALREADY_ACTIVE`
- [ ] 本票不放开批准：非处理人对上述待审计划 `POST .../approve` 仍是 `PLAN_REQUIRES_ACCEPTED_HANDLER`
- [ ] 不改发现：空洞 ≠ 冲突，未绑定不进冲突，身份失联仍是标志

**Out of this ticket:** 批准并冻结、逐条确认、确认关闭、执行、删掉已知悉/指派/认领路由、薄 UI、解决断点、编排层、B-live、工作台、未绑定 10、执行引擎票 02。

## Comments
