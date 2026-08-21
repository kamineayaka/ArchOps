# 04 — 逐条确认：接受即写策展并立刻比对（相等 → 待确认关闭）

**What to build:** 已接受处理人对开放草案内的条目逐条接受或拒绝。接受的那一条立即写入策展真相（不等待其余条目、不等待心跳）；拒绝的那一条不改策展。写入后必须立刻跑与快照 ingest **同一套**策展/观测比对：合并键上两轨可用且相等 → 冲突进入待确认关闭（绝不自动关闭），再用既有处理人确认关闭 API 关单；未接受的兄弟事实保持原策展。非处理人不能审条。冲突详情薄 UI 提供按条接受/拒绝。

**Blocked by:** 01 — 关闭建底 POST 覆盖已有 `运行于`；03 — 选改理想生成 ≥2 条草案：不写策展、不出操作计划

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)。01 与 03 已 TDD-done；本票是现行 frontier。

从竖切 MVP 往上长：今日比对只在观测写入后触发；策展突变不会自己走进待确认关闭。关单、待确认关闭提醒可见、确认时若已漂移则失败——这些已由竖切票交付，本票只复用，不重做关单产品化。合法策展改写在本刀只剩「接受的草案条目」；建底 POST 覆盖已由 01 关闭。

示踪混确：拒绝兄弟 Y，接受合并键 X。接受 X 后（观测仍为 B）不必再等快照，「应该在哪」为 B，冲突为待确认关闭；Y「应该在哪」仍为 A。

- [ ] 已接受处理人接受 X 的条目：X「应该在哪」立即为当前可用观测宿主 B；条目状态为已接受
- [ ] 已接受处理人拒绝 Y 的条目：Y「应该在哪」仍为 A；条目状态为已拒绝；X 不受影响
- [ ] 非处理人接受或拒绝任一条目被拒绝；策展不变
- [ ] 接受合并键条目后，**不**再发新快照：GET 冲突为待确认关闭（非 CLOSED）；提醒对查看者保持可见（非已知悉静音）
- [ ] 仅已接受处理人可走既有确认关闭 API；确认成功后为 CLOSED（证明第 9 步没有自动关单）
- [ ] 若只拒绝合并键、只接受兄弟：合并键冲突不得仅因兄弟被接受而进入待确认关闭
- [ ] 规范问法：接受 X 后「应该在哪」答 B；冲突/实际读取仍展示观测轨，不得把单轨说成唯一真相
- [ ] HTTP 可读「条目已接受（含写入）」与「条目已拒绝」审计
- [ ] 按条接受/拒绝与随后比对在同一持久化事务边界内完成（多副本下草案真相不放副本内存；Redis 可作锁但不是 SSOT）
- [ ] 薄 UI：处理人可按条接受/拒绝并看到条目状态与「应该在哪」变化。UI 不进自动化主接缝
- [ ] 无「整单全接受」、无 AI 独自定稿、无策展对齐步骤推迟写入

**Out of this ticket:** 选支瞬间写策展（03 已禁止，回归由 06 收束）、升级/空洞作废未完成草案（05）、有序总 tracer（06）、修实际 SSH 执行、Y2 对齐步。

## Comments

开工 prompt：[`docs/implement-change-curated-draft-04-prompt.md`](../../../docs/implement-change-curated-draft-04-prompt.md)。01–03 TDD-done。本票尚无 accept/reject HTTP；第一圈红灯应为 404 或编译失败。不要用建底 POST 覆盖已有 `运行于` 来写策展。不要做 05–06。

### Step A — seams

GET `/api/conflicts/{id}/curated-drafts/open` returns `items[].id` / `mergeKey` / `fromHostId` / `toHostId`. Confirm-close is `POST /api/conflicts/{id}/confirm-close`; events are `GET /api/conflicts/{id}/events`. No accept/reject routes yet.

### Step B — cycle 1: 非处理人不能审条 (red)

New test `ChangeCuratedDraftItemHttpAcceptanceTest.nonHandlerCannotAcceptDraftItem`. Fixture reuses 03 shape (X on B conflict, Y curated on A, general user accepted handler, then open 草案).

Red:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftItemHttpAcceptanceTest.nonHandlerCannotAcceptDraftItem
```

```text
ChangeCuratedDraftItemHttpAcceptanceTest > nonHandlerCannotAcceptDraftItem() FAILED
    java.lang.AssertionError: Status expected:<400> but was:<500>
Body = {"success":false,"code":"INTERNAL_ERROR","message":"No static resource api/conflicts/.../curated-drafts/open/items/.../accept.","data":null}
BUILD FAILED in 15s
```

Missing POST maps to `ResourceHttpRequestHandler` (`NoResourceFoundException` → 500 envelope). Not `PLAN_REQUIRES_ACCEPTED_HANDLER`. 策展 untouched.

Green: POST accept/reject routes + 已接受处理人 gate only. No 策展 write; items stay PENDING.

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftItemHttpAcceptanceTest.nonHandlerCannotAcceptDraftItem
BUILD SUCCESSFUL in 5s
```

Refactor: `getOpen` reuses `requireOpen`. Same test + 03 `acceptedHandlerSelectsChangeCuratedOpensDraftWithTwoPendingRunsOnItems` still green.

### Step C — cycle 2: 已接受处理人拒绝兄弟 Y (red)

Red:

```text
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftItemHttpAcceptanceTest.acceptedHandlerRejectsSiblingDoesNotWriteCurated
```

```text
ChangeCuratedDraftItemHttpAcceptanceTest > acceptedHandlerRejectsSiblingDoesNotWriteCurated() FAILED
    java.lang.AssertionError: JSON path "$.data.items[?(@.id=='ditem-...')].status"
Expected: a collection containing "REJECTED"
     but: mismatches were: [was "PENDING"]
BUILD FAILED in 5s
```

Reject route exists (cycle 1 gate) but does not change item status. 策展 still A.
