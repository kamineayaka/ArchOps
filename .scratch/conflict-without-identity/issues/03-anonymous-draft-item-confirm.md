# 03 — 改策展草案逐条确认不经身份、不记操作者

**What to build:** 冲突上的改理想草案，以及未绑定草案，都可以逐条接受或拒绝，不需要用户身份，也不需要已接受处理人。接受仍立即写入策展并按合并键比对；未相等不得因此自动关闭。确认事件不记录操作者。

**Blocked by:** 01 — 诊断选支不经身份、不记操作者

**Status:** done

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。

- [x] 不带用户身份头，接受改理想草案中「运行于」条目 → 策展目标改为该条目的目标；冲突事件 `DRAFT_ITEM_ACCEPTED` 的 `actorUserId` 为 null；两侧因此相等时进入待确认关闭，不自动 `CLOSED`
- [x] 不带身份头拒绝另一条目 → `DRAFT_ITEM_REJECTED`，`actorUserId` 为 null，策展不被该条目改写
- [x] 带了用户身份头，上述事件的 `actorUserId` 仍为 null
- [x] 未绑定草案的逐条接受/拒绝同样不需要身份头，且不把用户 id 写入草案事件

**Out of this ticket:** 确认关闭、执行、协作身份路由、薄 UI、解决断点。

## Comments

### Cycle A — 无身份接受改理想「运行于」
Red command: `cd backend && ./gradlew test --tests com.archops.curated.AnonymousDraftItemConfirmHttpAcceptanceTest.acceptRunsOnWithoutIdentityWritesCuratedAndRecordsNoActor`

```text
AnonymousDraftItemConfirmHttpAcceptanceTest > acceptRunsOnWithoutIdentityWritesCuratedAndRecordsNoActor() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<401>
        at AnonymousDraftItemConfirmHttpAcceptanceTest.java:48
BUILD FAILED
```

Green command: same test, BUILD SUCCESSFUL (exit 0).
`POST .../curated-drafts/open/items/{id}/accept` is permitAll. Accept no longer takes an actor and no longer calls `AcceptedHandlerPolicy`. `DRAFT_ITEM_ACCEPTED.actorUserId` is null. Equal sides become `PENDING_CLOSE` and stay off `CLOSED`.

### Cycle B — 无身份拒绝另一条目
Red command: `cd backend && ./gradlew test --tests com.archops.curated.AnonymousDraftItemConfirmHttpAcceptanceTest.rejectRunsOnWithoutIdentityLeavesCuratedAndRecordsNoActor`

```text
Status expected:<200> but was:<401>
        at AnonymousDraftItemConfirmHttpAcceptanceTest.java:85
BUILD FAILED
```

Green command: same test, BUILD SUCCESSFUL. Reject is permitAll, `DRAFT_ITEM_REJECTED.actorUserId` is null, and the curated target stays put.

### Cycle C — 带了用户身份头也不记操作者
Red: not manufactured. First run green.
Command: `cd backend && ./gradlew test --tests com.archops.curated.AnonymousDraftItemConfirmHttpAcceptanceTest.itemReviewWithUserHeaderStillOmitsActor`
reuse of Cycles A and B (BUILD SUCCESSFUL). The methods never read the user header.

### Cycle D — 未绑定草案逐条接受
Red command: `cd backend && ./gradlew test --tests com.archops.curated.AnonymousDraftItemConfirmHttpAcceptanceTest.unboundAcceptWithoutIdentityRecordsNoActor`

```text
Status expected:<200> but was:<401>
```

After permitAll, the same test was red again: `Status expected:<200> but was:<500>` because `curated_object.created_by` is NOT NULL. `V24` drops NOT NULL and keeps the user foreign key. Green command: same test, BUILD SUCCESSFUL. The draft event actor is null.

### Cycle E — 未绑定草案逐条拒绝
Red command: `cd backend && ./gradlew test --tests com.archops.curated.AnonymousDraftItemConfirmHttpAcceptanceTest.unboundRejectWithoutIdentityRecordsNoActor`

```text
Status expected:<200> but was:<401>
        at AnonymousDraftItemConfirmHttpAcceptanceTest.java:170
BUILD FAILED
```

Green command: same test, BUILD SUCCESSFUL. Opening an unbound draft still requires a user. Confirm-close and start-execution still require an accepted handler.

### Ticket-end suite

```text
cd backend && ./gradlew test
```

BUILD SUCCESSFUL：243 tests, 0 failures, 0 errors.

### Code review (Standards + Spec, vs ticket 01 `bc87892`)

**Standards:** no hard violations. A null-only `actorUserId` parameter on unbound accept was removed in the refactor. `V24` keeps the user foreign keys.

**Spec:** no findings. Anonymous accept and reject cover both change-curated and unbound drafts. A user header still leaves the events without an actor. Confirm close, execution, and breakpoints are unchanged.
