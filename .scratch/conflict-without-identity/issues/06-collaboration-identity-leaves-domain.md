# 06 — 协作身份动作退出领域

**What to build:** 冲突上不再有已知悉、归属、自任、指派、接受、拒绝、转让或处理人。这些写入路由不再产生领域事实。冲突 GET 不再把它们呈现为协作身份。旧的「开计划」身份门禁不再返回处理人。

**Blocked by:** 04 — 两侧相等后确认关闭不经身份、不记操作者；05 — 执行已批准计划不再询问处理人、不记操作者

**Status:** done

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。本票已完成。基线是 04 与 05 的 git 合并，diff 只含本票。

- [x] `POST` 认领、已知悉、已知悉并自任、指派、接受、拒绝、转让不再写入已知悉、归属或处理人；冲突 GET 不再把这些字段当领域事实返回
- [x] `POST /api/conflicts/{id}/operation-plans` 不再以处理人门禁决定能否开计划，响应也不再带处理人 id
- [x] 发现不变：两侧可用且不等仍是冲突；空洞不是冲突；未绑定不挂在冲突上；身份失联仍是标志
- [x] 票 01–05 的无身份选支、批准、逐条确认、确认关闭、执行仍可用

**Out of this ticket:** 薄 UI、解决断点、编排层、B-live、工作台。

## Comments

Suite: `cd backend && ./gradlew test --offline` → 260 tests, 0 failures.

### Claim

`./gradlew test --tests com.archops.conflict.CollaborationIdentityLeavesDomainHttpAcceptanceTest.claimDoesNotRecordCollaborationIdentity --offline`

Red: `Expected no value at JSON path "$.data.collaboration" but found {acknowledged=true, ... handlerAcceptance=ACCEPTED}` at `CollaborationIdentityLeavesDomainHttpAcceptanceTest.java:42`.

Green: claim is read-only; assembler omits collaboration. BUILD SUCCESSFUL.

### Acknowledge

`./gradlew test --tests com.archops.conflict.CollaborationIdentityLeavesDomainHttpAcceptanceTest.acknowledgeDoesNotRecordCollaborationIdentity --offline`

Red: `$.data[*].eventType` contained `ACKNOWLEDGED` at line 73.

Green: acknowledge is read-only. BUILD SUCCESSFUL.

### Self-appoint

`./gradlew test --tests com.archops.conflict.CollaborationIdentityLeavesDomainHttpAcceptanceTest.selfAppointDoesNotRecordCollaborationIdentity --offline`

Red: events contained `HANDLER_ACCEPTED` at line 90.

Green: self-appoint is read-only. BUILD SUCCESSFUL.

### Assign

`./gradlew test --tests com.archops.conflict.CollaborationIdentityLeavesDomainHttpAcceptanceTest.assignDoesNotRecordCollaborationIdentity --offline`

Red: `Status expected:<200> but was:<400>` at line 103 (`CONFLICT_NOT_ACKNOWLEDGED`).

Green: assign is read-only. BUILD SUCCESSFUL.

### Accept

`./gradlew test --tests com.archops.conflict.CollaborationIdentityLeavesDomainHttpAcceptanceTest.acceptDoesNotRecordCollaborationIdentity --offline`

Red: `Status expected:<200> but was:<400>` at line 120.

Green: accept is read-only. BUILD SUCCESSFUL.

### Reject

`./gradlew test --tests com.archops.conflict.CollaborationIdentityLeavesDomainHttpAcceptanceTest.rejectDoesNotRecordCollaborationIdentity --offline`

Red: `Status expected:<200> but was:<400>` at line 139.

Green: reject is read-only. BUILD SUCCESSFUL.

### Transfer

`./gradlew test --tests com.archops.conflict.CollaborationIdentityLeavesDomainHttpAcceptanceTest.transferDoesNotRecordCollaborationIdentity --offline`

Red: `Status expected:<200> but was:<400>` at line 158.

Green: transfer is read-only. BUILD SUCCESSFUL.

### Open plan

`./gradlew test --tests com.archops.conflict.CollaborationIdentityLeavesDomainHttpAcceptanceTest.openPlanDoesNotUseHandlerGateOrReturnHandlerId --offline`

Red: `Status expected:<200> but was:<400>` at line 175 (handler gate).

Green: open-plan no longer calls a handler policy, and `OpenOperationPlanResponse` has no handler id. BUILD SUCCESSFUL.

### Identity body no longer required

Assign without a body: red `Status expected:<200> but was:<500>` at line 175, then green.

Reject without a body: red `Status expected:<200> but was:<500>` at line 186, then green.

Transfer without a body: red `Status expected:<200> but was:<500>` at line 197, then green.

### Reuse

Discovery (unequal tracks, hollow, unbound off the conflict, identity-lost flag) stayed green on the existing HTTP tests. No detection change.

Tickets 01–05 stayed green: `AnonymousBranchSelectionHttpAcceptanceTest`, `AnonymousPlanApproveHttpAcceptanceTest`, `AnonymousDraftItemConfirmHttpAcceptanceTest`, `AnonymousConfirmCloseHttpAcceptanceTest`, `AnonymousStartExecutionHttpAcceptanceTest`.

### Review

Standards: no hard violation. Judgement: duplicated read-only collaboration methods; collaboration record still exists but is omitted. Spec partial (required identity bodies) closed by the three follow-up cycles above.
