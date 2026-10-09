# 04 — 两侧相等后确认关闭不经身份、不记操作者

**What to build:** 冲突处于待确认关闭且两侧仍相等时，显式确认关闭可以完成，不需要用户身份，也不需要已接受处理人。关闭事件不记录操作者。未相等不得关闭。

**Blocked by:** 03 — 改策展草案逐条确认不经身份、不记操作者

**Status:** done

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。票 03 能在无身份下把两侧写成相等并进入待确认关闭，本票接着做确认关闭。

- [x] 待确认关闭且两侧相等：不带用户身份头的 `POST /api/conflicts/{id}/confirm-close` → 200，状态 `CLOSED`；`CLOSED` 事件 `actorUserId` 为 null
- [x] 带了用户身份头，`CLOSED` 事件 `actorUserId` 仍为 null
- [x] 确认瞬间两侧已不等 → `CONFLICT_NOT_ALIGNED`，冲突不关闭
- [x] 非待确认关闭仍不能确认关闭

**Out of this ticket:** 执行、协作身份路由、薄 UI、解决断点。

## Comments

### Cycle A — 无身份确认关闭
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.AnonymousConfirmCloseHttpAcceptanceTest.confirmCloseWithoutIdentityWhenTracksEqual --offline`

```text
AnonymousConfirmCloseHttpAcceptanceTest > confirmCloseWithoutIdentityWhenTracksEqual() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<401>
        at AnonymousConfirmCloseHttpAcceptanceTest.java:41
BUILD FAILED
```

Green command: same test, BUILD SUCCESSFUL (exit 0).
`POST /api/conflicts/{id}/confirm-close` is permitAll. `confirmClose` no longer takes an actor and no longer calls `AcceptedHandlerPolicy`. Equal tracks become `CLOSED`. `CLOSED.actorUserId` is null.

### Cycle B — 带头仍不记操作者
First-run green (reuse). The null actor was already written in cycle A, so a present `X-ArchOps-User-Id` cannot go red.
`confirmCloseWithUserHeaderStillOmitsActor` BUILD SUCCESSFUL. `CLOSED.actorUserId` stays null.

### Cycle C — 两侧已不等
First-run green (reuse). The unequal check already reopened before the handler gate was removed.
`confirmCloseWhenTracksDriftStaysUnclosed` BUILD SUCCESSFUL. Anonymous confirm-close returns `CONFLICT_NOT_ALIGNED` and the case is not `CLOSED`.

### Cycle D — 非待确认关闭
First-run green (reuse). `CONFLICT_NOT_PENDING_CLOSE` already rejected non-pending cases.
`confirmCloseWhenNotPendingCloseIsRejected` BUILD SUCCESSFUL. Status stays `OPEN`.

### Suite and review
`cd backend && ./gradlew test --offline` → 247 tests, 0 failures, 0 errors.
Standards: no hard violations. Spec: the four acceptance lines are covered; no approve, execution, collaboration routes, UI, or ADR text in the diff.
Refactor: renamed the pending-close pin that still said the handler confirms, and the vertical-slice comment that still said 处理人确认.
