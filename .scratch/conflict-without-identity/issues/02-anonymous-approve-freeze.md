# 02 — 批准并冻结操作计划不经身份、不记操作者

**What to build:** 待审操作计划可以被显式批准并冻结，不需要用户身份，也不需要已接受处理人。批准不记录批准者。未批准的计划仍不能执行。已作废的计划仍不能批准。

**Blocked by:** 01 — 诊断选支不经身份、不记操作者

**Status:** blocked

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。本票未开始。

- [ ] 票 01 选出的 `DRAFT_REVIEW` 修实际计划：不带用户身份头的 `POST /api/operation-plans/{id}/approve` → 200，状态 `APPROVED`，`executionIntent=true`，`reviewedBy` 为 null
- [ ] 带了用户身份头再批准，`reviewedBy` 仍为 null
- [ ] 未批准时 `start-execution` 仍是 `PLAN_NOT_APPROVED`（执行门禁留到票 05）
- [ ] `VOIDED` 计划批准仍是 `PLAN_VOIDED`

**Out of this ticket:** 逐条确认、确认关闭、去掉执行的处理人门禁、协作身份路由、薄 UI、解决断点。

## Comments
