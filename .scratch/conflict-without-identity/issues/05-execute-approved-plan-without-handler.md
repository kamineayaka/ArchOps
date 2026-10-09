# 05 — 执行已批准计划不再询问处理人、不记操作者

**What to build:** 已批准并冻结的操作计划可以开始执行，不再询问是不是已接受处理人，也不需要把执行者记成领域事实。失败仍即停作废；不得改步重试。未批准仍不能执行。

**Blocked by:** 02 — 批准并冻结操作计划不经身份、不记操作者

**Status:** blocked

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046 后果（执行不再询问处理人）。本票未开始。

- [ ] 票 02 批准后的计划：不带用户身份头的 `POST /api/operation-plans/{id}/start-execution` 可以开工；完成时冲突事件 `PLAN_COMPLETED` 的 `actorUserId` 为 null
- [ ] 带了用户身份头，`PLAN_COMPLETED` 的 `actorUserId` 仍为 null
- [ ] `VOIDED` 仍是 `PLAN_VOIDED`；`DRAFT_REVIEW` 仍是 `PLAN_NOT_APPROVED`

**Out of this ticket:** 确认关闭、协作身份路由、薄 UI、解决断点（不把占位命令换成真命令，不让完成的计划写观测）。

## Comments
