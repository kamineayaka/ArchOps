# 05 — 执行已批准计划不再询问处理人、不记操作者

**What to build:** 已批准并冻结的操作计划可以开始执行，不再询问是不是已接受处理人，也不需要把执行者记成领域事实。失败仍即停作废；不得改步重试。未批准仍不能执行。

**Blocked by:** 02 — 批准并冻结操作计划不经身份、不记操作者

**Status:** done

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046 后果（执行不再询问处理人）。

- [x] 票 02 批准后的计划：不带用户身份头的 `POST /api/operation-plans/{id}/start-execution` 可以开工；完成时冲突事件 `PLAN_COMPLETED` 的 `actorUserId` 为 null
- [x] 带了用户身份头，`PLAN_COMPLETED` 的 `actorUserId` 仍为 null
- [x] `VOIDED` 仍是 `PLAN_VOIDED`；`DRAFT_REVIEW` 仍是 `PLAN_NOT_APPROVED`

**Out of this ticket:** 确认关闭、协作身份路由、薄 UI、解决断点（不把占位命令换成真命令，不让完成的计划写观测）。

## Comments

### Cycle A — 无身份开工并完成
Red command: `cd backend && ./gradlew test --tests com.archops.plan.AnonymousStartExecutionHttpAcceptanceTest.startExecutionWithoutIdentityCompletesAndRecordsNoActor --offline`

```text
AnonymousStartExecutionHttpAcceptanceTest > startExecutionWithoutIdentityCompletesAndRecordsNoActor() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<401>
        at AnonymousStartExecutionHttpAcceptanceTest.java:42
BUILD FAILED
```

Green command: same test, BUILD SUCCESSFUL (exit 0).
`POST /api/operation-plans/{id}/start-execution` is permitAll. `startExecution` no longer takes an actor and no longer calls `AcceptedHandlerPolicy`. An approved plan reaches `COMPLETED`. `PLAN_COMPLETED.actorUserId` is null.

### Cycle B — 带头仍不记操作者
First-run green (reuse). The null actor was already written in cycle A.
`startExecutionWithUserHeaderStillOmitsActor` BUILD SUCCESSFUL. `PLAN_COMPLETED.actorUserId` stays null.

### Cycle C — 已作废
First-run green (reuse). `VOIDED` was already rejected before the handler gate.
`voidedPlanStartExecutionStaysPlanVoided` BUILD SUCCESSFUL. Anonymous start-execution returns `PLAN_VOIDED`.

### Cycle D — 未批准
First-run green (reuse). `DRAFT_REVIEW` already fell through to `PLAN_NOT_APPROVED` once the handler check was gone.
`unapprovedStartExecutionStaysPlanNotApproved` BUILD SUCCESSFUL.

### Suite and review
`cd backend && ./gradlew test --offline` → 246 tests, 0 failures, 0 errors.
Standards: no hunk breach. Spec: the acceptance lines are covered. Confirm-close, collaboration routes, UI, placeholder SSH, and observation writeback are absent. ADR text is unchanged.
Refactor: the review-plan setup is one helper inside this test class.
