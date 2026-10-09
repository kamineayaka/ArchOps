# 02 — 批准并冻结操作计划不经身份、不记操作者

**What to build:** 待审操作计划可以被显式批准并冻结，不需要用户身份，也不需要已接受处理人。批准不记录批准者。未批准的计划仍不能执行。已作废的计划仍不能批准。

**Blocked by:** 01 — 诊断选支不经身份、不记操作者

**Status:** done

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。

- [x] 票 01 选出的 `DRAFT_REVIEW` 修实际计划：不带用户身份头的 `POST /api/operation-plans/{id}/approve` → 200，状态 `APPROVED`，`executionIntent=true`，`reviewedBy` 为 null
- [x] 带了用户身份头再批准，`reviewedBy` 仍为 null
- [x] 未批准时 `start-execution` 仍是 `PLAN_NOT_APPROVED`（执行门禁留到票 05）
- [x] `VOIDED` 计划批准仍是 `PLAN_VOIDED`

**Out of this ticket:** 逐条确认、确认关闭、去掉执行的处理人门禁、协作身份路由、薄 UI、解决断点。

## Comments

### Cycle A — 无身份、无处理人批准并冻结，不记批准者
Red command: `cd backend && ./gradlew test --tests com.archops.plan.AnonymousPlanApproveHttpAcceptanceTest.approveWithoutIdentityOrHandlerFreezesPlan`

```text
AnonymousPlanApproveHttpAcceptanceTest > approveWithoutIdentityOrHandlerFreezesPlan() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<401>
        at AnonymousPlanApproveHttpAcceptanceTest.java:41
BUILD FAILED
```

Green command: same test, BUILD SUCCESSFUL (exit 0).
`POST /api/operation-plans/{id}/approve` is permitAll. `approve` no longer takes an actor and no longer calls `requireAcceptedHandler`. `reviewedBy` is written null. `executionIntent` is true for `APPROVED`.

### Cycle B — 带了用户身份头也不记批准者
Red: not manufactured. First run green.
Command: `cd backend && ./gradlew test --tests com.archops.plan.AnonymousPlanApproveHttpAcceptanceTest.approveWithUserHeaderStillOmitsReviewedBy`
reuse of Cycle A `reviewedBy=null` (BUILD SUCCESSFUL). The approve method never reads the user header.

### Cycle C — 未批准仍不能执行
First run green (reuse). The execution handler gate stays until ticket 05, so the fixture claims a handler first and then `start-execution` returns `PLAN_NOT_APPROVED`.
Command: `cd backend && ./gradlew test --tests com.archops.plan.AnonymousPlanApproveHttpAcceptanceTest.unapprovedStartExecutionStaysPlanNotApproved`
BUILD SUCCESSFUL.

### Cycle D — 已作废仍不能批准
First run green (reuse). Upgrade voids the `DRAFT_REVIEW` plan; anonymous approve returns `PLAN_VOIDED`. The void check stays before any other approve rule.
Command: `cd backend && ./gradlew test --tests com.archops.plan.AnonymousPlanApproveHttpAcceptanceTest.voidedPlanApproveStaysPlanVoided`
BUILD SUCCESSFUL.

Prior pins: `OperationPlanReviewHttpAcceptanceTest` expects `reviewedBy` null. Ticket 01's `approveStillRequiresAcceptedHandler` is now `approveWithoutAcceptedHandlerFreezesPlan`. Item confirm and `POST /api/conflicts/{id}/operation-plans` still return `PLAN_REQUIRES_ACCEPTED_HANDLER`.

### Ticket-end suite

```text
cd backend && ./gradlew test
```

BUILD SUCCESSFUL：242 tests, 0 failures, 0 errors.

### Code review (Standards + Spec, vs ticket 01 `bc87892`)

**Standards:** no hard violations. Filter `permitAll` plus method `@PreAuthorize("permitAll()")` matches 诊断选支. Duplicated HTTP fixture helpers inside the new test are a judgement call and were left in place; no shared fixture was extracted across tickets.

**Spec:** no findings. Anonymous approve freezes a review plan, a user header still leaves `reviewedBy` null, unapproved start stays `PLAN_NOT_APPROVED`, and a voided plan stays `PLAN_VOIDED`. Execution handler gate, item confirm, confirm close, and breakpoints are unchanged.
