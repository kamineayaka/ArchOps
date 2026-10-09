# 01 — 诊断选支不经身份、不记操作者

**What to build:** 开放冲突上，当前诊断已就绪时，任何人可以显式诊断选支，不需要用户身份，也不需要已接受处理人。选「修实际」仍生成待审操作计划；选「改理想」仍生成开放草案且不生成操作计划。选支结果与冲突事件不记录操作者。身份失联仍挡住修实际与改理想。诊断未就绪或不是当前诊断时仍拒绝。

**Blocked by:** None — can start immediately

**Status:** done

**TDD:** capability。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条 HTTP 测试。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。

- [x] 两侧不等的 OPEN 冲突、诊断 READY、无人认领：不带 `X-ArchOps-User-Id` 的 `POST /api/conflicts/{id}/branch-selection`（`FIX_ACTUAL_TO_CURATED`）→ 200，操作计划 `DRAFT_REVIEW`、`skipsDraft=true`、`createdBy` 为 null；随后 GET 该计划 `createdBy` 仍为 null
- [x] 同一选支若带了用户身份头，计划 `createdBy` 仍为 null（身份不写成领域事实）
- [x] 不带身份头、无处理人，选 `CHANGE_CURATED_TO_OBSERVED` → 开放草案、`createdBy` 为 null、没有活跃操作计划；冲突事件 `DRAFT_CREATED` 的 `actorUserId` 为 null
- [x] 身份失联时，无处理人选择修实际或改理想仍是 `IDENTITY_LOST_BLOCKS_BRANCH`，不是处理人门禁
- [x] 过时诊断 id 仍是 `DIAGNOSIS_NOT_READY`；已有活跃计划时再选仍是 `PLAN_ALREADY_ACTIVE`
- [x] 本票不放开批准：非处理人对上述待审计划 `POST .../approve` 仍是 `PLAN_REQUIRES_ACCEPTED_HANDLER`
- [x] 不改发现：空洞 ≠ 冲突，未绑定不进冲突，身份失联仍是标志

**Out of this ticket:** 批准并冻结、逐条确认、确认关闭、执行、删掉已知悉/指派/认领路由、薄 UI、解决断点、编排层、B-live、工作台、未绑定 10、执行引擎票 02。

## Comments

### Cycle A — 无身份、无处理人选修实际，计划不记操作者
Red command: `cd backend && ./gradlew test --tests com.archops.plan.AnonymousBranchSelectionHttpAcceptanceTest.fixActualWithoutIdentityOrHandlerOpensReviewPlan`

```text
AnonymousBranchSelectionHttpAcceptanceTest > fixActualWithoutIdentityOrHandlerOpensReviewPlan() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<401>
        at AnonymousBranchSelectionHttpAcceptanceTest.java:43
BUILD FAILED
```

Green command: same test, BUILD SUCCESSFUL (exit 0).
Refactor: `selectBranch` no longer takes an actor; static imports in the test.
Prior pins that required `PLAN_REQUIRES_ACCEPTED_HANDLER` on 诊断选支 now expect the selection to proceed, or `IDENTITY_LOST_BLOCKS_BRANCH` when the subject is 身份失联. Approve / item confirm / `operation-plans` gate pins stay.

### Cycle B — 带了用户身份头也不记操作者
Red: not manufactured. First run green.
Command: `cd backend && ./gradlew test --tests com.archops.plan.AnonymousBranchSelectionHttpAcceptanceTest.fixActualWithUserHeaderStillOmitsCreatedBy`
reuse/regression of Cycle A `createdBy=null` (BUILD SUCCESSFUL).

### Cycle C — 无身份选改理想，草案与 DRAFT_CREATED 不记操作者
Red command: `cd backend && ./gradlew test --tests com.archops.plan.AnonymousBranchSelectionHttpAcceptanceTest.changeCuratedWithoutIdentityOpensDraftAndRecordsNoActor`

```text
Status expected:<200> but was:<500>
Resolved Exception Type = java.lang.NullPointerException
```

Fixture then corrected to two `运行于` rows (single-container select is `DRAFT_ITEMS_INCOMPLETE`, which is the existing draft rule, not this ticket).
Green command: same test, BUILD SUCCESSFUL (exit 0).
Refactor: `createForChangeCurated` and `BranchSelectionService.select` no longer take an actor.

### Cycle D — 已有计划 / 过时诊断 / 批准门禁仍在
First run green (reuse). Commands:

```text
cd backend && ./gradlew test --tests com.archops.plan.AnonymousBranchSelectionHttpAcceptanceTest.secondFixActualSelectIsRejectedWhilePlanActive --tests com.archops.plan.AnonymousBranchSelectionHttpAcceptanceTest.staleDiagnosisIdIsRejectedWithoutHandler --tests com.archops.plan.AnonymousBranchSelectionHttpAcceptanceTest.approveStillRequiresAcceptedHandler
```

BUILD SUCCESSFUL. `PLAN_ALREADY_ACTIVE` / `DIAGNOSIS_NOT_READY` / `PLAN_REQUIRES_ACCEPTED_HANDLER` were already enforced outside the removed select gate.
身份失联：`IdentityLostPipelineGateHttpAcceptanceTest.nonHandlerBranchSelectionOnIdentityLostIsBlocked` now expects `IDENTITY_LOST_BLOCKS_BRANCH` (suite green).
发现路径未改；本票夹具仍能从两侧不等的心跳开出 OPEN 冲突。

### Ticket-end suite

```text
cd backend && ./gradlew test
```

BUILD SUCCESSFUL：237 tests, 0 failures. After the identity-lost pin and V23: 238 tests, 0 failures.

### Code review (Standards + Spec, vs origin/main)

**Standards:** Cycles A and C have witnessed red (401, then 500 NPE). Cycles B and D are first-run green reuse of the null `createdBy` write and of gates that already existed; production from other tickets was not deleted to manufacture red. Ticket status is `done` after this review. `AGENTS.md` §3 now points the old handler sentence at ADR-0046.

**Spec:** Selection behavior matches the ticket. `V21`/`V22` had also dropped the user foreign keys; `V23` puts them back so null is allowed and later paths can still store a real user id. 改理想 under 身份失联 with no user header is pinned by `identityLostBlocksChangeCuratedSelectionWithoutUserHeader` (first-run green: the fork check already rejected both ids).

### Identity-lost pin (review follow-up)

```text
cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.identityLostBlocksChangeCuratedSelectionWithoutUserHeader --tests com.archops.plan.AnonymousBranchSelectionHttpAcceptanceTest
```

BUILD SUCCESSFUL (reuse of `IDENTITY_LOST_BLOCKS_BRANCH`; null `createdBy` still inserts under the restored foreign key).
