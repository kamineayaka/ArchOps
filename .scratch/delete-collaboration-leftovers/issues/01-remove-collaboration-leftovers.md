# 01 — 删掉冲突链上的协作残留

**What to build:** 认领、已知悉、自任、指派、接受、拒绝、转让从冲突 HTTP 与服务上消失（404 或路由不存在），不再以 200 空写留着。冲突 GET 不再返回协作/处理人/归属。冲突列表与详情，以及诊断选支、批准并冻结、改策展逐条确认、确认关闭、启动执行，不带头也能完成，且不记录操作者。冲突页去掉演示身份，刷新不依赖所选用户；解决动作留在页上。

**Blocked by:** None. Baseline is origin/main at the merge of PR #125 (`97bc9fe`) with ADR-0046.

**Status:** done

**TDD:** capability。主接缝为控制面 HTTP API。合同：ADR-0046（不重开）。发现与解决语义不改。

- [x] `POST` 认领、已知悉、已知悉并自任、指派、接受、拒绝、转让对已存在的冲突返回 404，不写领域事实
- [x] 冲突 GET 列表与详情不返回 collaboration / owner / handler
- [x] 冲突列表、详情，以及诊断选支、批准并冻结、改策展逐条确认、确认关闭、启动执行，不发送用户头即成功，且不记录操作者
- [x] 冲突页没有演示身份，列表与详情刷新不依赖所选用户；解决动作仍在
- [x] 发现不变：两侧可用且不等仍是冲突；空洞不是冲突；未绑定不挂在冲突上；身份失联仍是标志

**Out of this ticket:** 编排层、B-live、工作台、未绑定 10、执行引擎票 02、ABSENT 分叉、占位 SSH、观测回写、用户微服务、Spring Cloud。领域外的登录若未绑定或策展写入仍要用，留着。

## Comments

### Cycle A — claim route is gone
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.claimOnOpenConflictIsNotFoundWithoutUser --offline`
`Status expected:<404> but was:<401>` at `CollaborationLeftoverRemovedHttpAcceptanceTest.java:38`.
Green command: same test, exit 0. Removed `POST /api/conflicts/{id}/claim` and its service method. Unmapped API paths return 404 (`NOT_FOUND`) instead of an internal error, and the old path is not an auth gate.
Refactor: dropped empty claim helpers left in older HTTP tests.
Commit: `df85dcd` Remove the claim route so a conflict cannot be claimed.

### Cycle B — acknowledge route is gone
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.acknowledgeOnOpenConflictIsNotFoundWithoutUser --offline`
`Status expected:<404> but was:<401>` at line 54.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `38edaca` Remove the acknowledge route so 已知悉 is not a conflict action.

### Cycle C — self-appoint route is gone
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.selfAppointOnOpenConflictIsNotFoundWithoutUser --offline`
`Status expected:<404> but was:<401>` at line 70.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `9dfbec2` Remove self-appoint so 自任 is not a conflict action.

### Cycle D — assign route is gone
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.assignOnOpenConflictIsNotFoundWithoutUser --offline`
`Status expected:<404> but was:<401>` at line 81.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `6951006` Remove assign so 指派 is not a conflict action.

### Cycle E — accept-handler route is gone
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.acceptOnOpenConflictIsNotFoundWithoutUser --offline`
`Status expected:<404> but was:<401>` at line 90.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `dd935d0` Remove accept-handler so 接受处理人 is not a conflict action.

### Cycle F — reject-handler route is gone
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.rejectOnOpenConflictIsNotFoundWithoutUser --offline`
`Status expected:<404> but was:<401>` at line 101.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `b2f91f6` Remove reject-handler so 拒绝处理人 is not a conflict action.

### Cycle G — transfer route is gone
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.transferOnOpenConflictIsNotFoundWithoutUser --offline`
`Status expected:<404> but was:<401>` at line 112.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `ae4fb23` Remove transfer so 转让处理人 is not a conflict action.

### Cycle H — conflict list without a user
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.listActiveConflictsWithoutUserOmitsCollaboration --offline`
`Status expected:<200> but was:<401>` at line 121.
Green command: same test, exit 0. List omits collaboration / owner / handler.
Refactor: no structural change.
Commit: `f16c1fe` Let the conflict list be read without a user.

### Cycle I — conflict detail without a user
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.getConflictWithoutUserOmitsCollaboration --offline`
`Status expected:<200> but was:<401>` at line 134.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `0e4b7f1` Let a conflict detail be read without a user.

### Cycle J — diagnosis read without a user
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.diagnosisReadWithoutUser --offline`
`Status expected:<200> but was:<401>` at line 149.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `3782618` Let diagnosis be read without a user.

### Cycle K — active plan read without a user
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.activePlanReadWithoutUser --offline`
`Status expected:<200> but was:<401>` at line 165. Follow-up asserts `createdBy` is null.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `d162764` Let the active operation plan be read without a user.

### Cycle L — plan by id without a user
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.planReadWithoutUser --offline`
`Status expected:<200> but was:<401>` at line 185. Follow-up asserts `reviewedBy` is null.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `b88a606` Let an operation plan be read by id without a user.

### Cycle M — open draft without a user
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.openDraftReadWithoutUserOmitsActor --offline`
`Status expected:<200> but was:<401>` at line 216. Follow-up asserts `createdBy` is null.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `e0b787d` Let the open curated draft be read without a user.

### Cycle N — draft by id without a user
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.draftByIdReadWithoutUserOmitsActor --offline`
`Status expected:<200> but was:<401>` at line 252. Follow-up asserts `createdBy` is null.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `4ddfee1` Let a conflict draft be read by id without a user.

### Cycle O — 应该在哪 without a user
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.shouldWhereReadWithoutUser --offline`
`Status expected:<200> but was:<401>` at line 266.
Green command: same test, exit 0.
Refactor: no structural change.
Commit: `89d03a1` Let 应该在哪 be read without a user.

### Reuse — human-review writes already anonymous
Not new reds. Existing methods already succeed with no user and a null actor: `AnonymousBranchSelectionHttpAcceptanceTest`, `AnonymousPlanApproveHttpAcceptanceTest`, `AnonymousDraftItemConfirmHttpAcceptanceTest`, `AnonymousConfirmCloseHttpAcceptanceTest`, `AnonymousStartExecutionHttpAcceptanceTest`.

### Refactor — drop stored collaboration columns
List and detail already omitted the payload. `V25__drop_conflict_collaboration_columns.sql` drops `acknowledged`, `acknowledged_at`, `owner_user_id`, `handler_user_id`, `handler_acceptance`. V6 is untouched. Re-ran list, detail, and `ConflictCaseAssemblerTest`: exit 0.
Commit: `052c9c6` Drop stored collaboration columns from conflict cases.

### UI — hide 演示身份 on conflict pages
After the HTTP slice was green. Conflict list/detail reload does not read the selected user. Resolution actions stay. `cd frontend && npm run build` exit 0.
Commit: `2858dd3` Hide demo identity on the conflict pages.

### Repair — missing whole-draft accept is 404
Full suite first went red: `UnboundDraftItemReviewHttpAcceptanceTest.itemReviewEventsAreReadableAndWholeDraftAcceptDoesNotExist` expected 500 and got 404, because an unmapped route is `NOT_FOUND` instead of an internal error. Assertion updated to 404 + `NOT_FOUND`. `cd backend && ./gradlew test --offline` then BUILD SUCCESSFUL (275 tests; later re-run UP-TO-DATE).
Commit: `13d80f6` Expect a missing whole-draft accept route to be 404.

### Cycle P — by-merge-key stays an auth challenge
The anonymous list matcher also covered `GET /api/conflicts/by-merge-key`, so a missing user became `403 AUTH_FORBIDDEN`.
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.byMergeKeyWithoutUserStaysUnauthorized --offline`
`Status expected:<401> but was:<403>` at line 262. Body `AUTH_FORBIDDEN`.
Green command: same test, exit 0. A more specific authenticated matcher sits ahead of `GET /api/conflicts/*`.
Refactor: no structural change.
Commit: `984993e` Keep merge-key reads behind the user header.

### Code review
Standards: no hard violations. Judgement: `ConflictCollaborationService` and a few older test names still say collaboration/claim after those writes left. Duplicated 404 assertions across the new test and `CollaborationIdentityLeavesDomainHttpAcceptanceTest`. Left as names of the old suite, not new product.
Spec: the seven routes, anonymous reads, and null actors match the ticket. The 404 mapping also changes other missing routes from 500 to `NOT_FOUND` (the unbound whole-draft accept repair above). by-merge-key is cycle P, not an anonymous read.

### UI check
Vite `:5173` against bootRun `:8080`. Conflict list and detail have no 演示身份; 刷新 keeps the row; detail shows 诊断 / 选支 and 确认关闭. `/unbound` still shows 演示身份. `POST /api/conflicts/{id}/claim` without a user is `404 NOT_FOUND`. `GET /api/conflicts` without a user is `200` and omits collaboration / owner / handler. Full suite after cycle P: `./gradlew test --offline` BUILD SUCCESSFUL, 276 tests, 0 failed.
