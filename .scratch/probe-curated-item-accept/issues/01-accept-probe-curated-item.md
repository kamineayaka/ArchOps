# 01 — 接受探测改理想条目：写入策展并立刻再判决

**What to build:** 探测冲突上已经有一份 OPEN 的改理想草案，里面一条 `PENDING` 的 `PROBE_CURATED_VALUE`，打开时没有写策展。接受这一条把条目里的观测结构化值写成该任务的策展值，并立刻用最新探测结果再判决。相等则进入待确认关闭，确认关闭可以关单。确认关闭前若交回更新的不等结果，离开待确认关闭并重开同一条冲突，已写入的策展值保持，确认关闭不能关单。拒绝不改策展，冲突保持开放。不新增解决动作。人审不带身份、不记操作者。HTTP 变绿后，`/probe-tasks` 可以接受条目并确认关闭。

**Blocked by:** None. Baseline is origin/main at `6434b8157960e40b6a68ad3a748f392cfc76563c` (PR #134). 改理想打开草案且不写策展已在 PR #133（Flyway V31）。不重开 ADR-0048 票 01、薄 UI、Host Agent 执行探测命令。

**Status:** done

**TDD:** capability。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)。主接缝是控制面 HTTP API。Spec：[`docs/specs/probe-curated-item-accept.md`](../../../docs/specs/probe-curated-item-accept.md)。

- [x] 接受待确认条目后，任务策展值变为该条目的观测结构化值；条目为已接受；解决动作仍只有修实际与改理想
- [x] 接受后不交回新心跳：最新结果与新策展值相等时，冲突为待确认关闭（不是已关闭）；任务判决为正常，`conflictId` 为空
- [x] 待确认关闭且两侧仍相等时，确认关闭后冲突为已关闭
- [x] 确认关闭前交回更新的不等结果：同一条冲突离开待确认关闭并重开；策展值保持已接受的值；确认关闭不能关单
- [x] 拒绝该条目不改策展值，冲突保持开放，条目为已拒绝
- [x] 接受、拒绝、确认关闭不带用户身份，不记录操作者
- [x] `/probe-tasks` 可以接受条目并确认关闭；`npm run build` 通过

**Out of this ticket:** 执行修实际计划、持久化「恢复观测通道」、Agent 停机掏空全部快照观测、ADR-0049 / Kubernetes / 编排层 gRPC、AI 建议正文、原因种类、「无法归类」、重开已闭合的探测票、协作身份。

## Comments

### Cycle A — 接受条目把观测值写成策展，不新增解决动作
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeCuratedItemAcceptHttpAcceptanceTest.acceptProbeDraftItemWritesObservedValueAsCuratedExpected --offline`
```
ProbeCuratedItemAcceptHttpAcceptanceTest > acceptProbeDraftItemWritesObservedValueAsCuratedExpected() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<400>
Body = {"success":false,"code":"DRAFT_ITEM_KIND_UNSUPPORTED","message":"本刀只接受「运行于」目标变更条目","data":null}
BUILD FAILED
```
Green command: same test, exit 0. Item `ACCEPTED` / `PROBE_CURATED_VALUE`, `createdBy` null, task `curatedExpected.up` false. Diagnosis forks stay `FIX_ACTUAL_TO_CURATED` and `CHANGE_CURATED_TO_OBSERVED`.
Refactor: one probe-vs-runs-on branch in `acceptItem`. Same test re-run, exit 0.
Commit: `9ec20af` Accept a probe 改理想 item by writing its observed value as the curated expected.

### Cycle B — 接受后立刻再判决，相等进入待确认关闭
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeCuratedItemAcceptHttpAcceptanceTest.acceptRejudgesLatestResultToPendingCloseWithoutClosing --offline`
```
ProbeCuratedItemAcceptHttpAcceptanceTest > acceptRejudgesLatestResultToPendingCloseWithoutClosing() FAILED
    java.lang.AssertionError: JSON path "$.data.status"
Expected: is "PENDING_CLOSE"
     but: was "OPEN"
BUILD FAILED
```
Green command: same test, exit 0. No new heartbeat. Conflict `PENDING_CLOSE`, both structured values `up=false`. Task judgment `NORMAL`, `conflictId` null. Not `CLOSED`.
Refactor: `markProbePendingClose` shared with the heartbeat equal path. Same test and `equalAfterConflictMovesToPendingClose` exit 0.
Commit: `d67aa55` Re-judge the latest probe result when a 改理想 item is accepted.

### Cycle C — 确认关闭关单
Ran:
```
cd backend && ./gradlew test --tests com.archops.observed.ProbeCuratedItemAcceptHttpAcceptanceTest.confirmCloseAfterEqualRejudgeClosesTheProbeConflict --offline
BUILD SUCCESSFUL
```
reuse/regression: first run exit 0. Covered by `ProbeTaskJudgmentHttpAcceptanceTest.equalAfterConflictMovesToPendingClose` (`ConflictCollaborationService.confirmCloseProbe`). Cycle B left the conflict `PENDING_CLOSE` with equal JSON, so confirm-close closes it and does not auto-close before the request. No user header.
Refactor: no structural change. The new method only pins that close on the accept path.
Commit: `e5c1b3e` Record that confirm-close already closes an equal probe conflict.

### Cycle D — 确认关闭前交回更新的不等结果
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeCuratedItemAcceptHttpAcceptanceTest.newerUnequalResultBeforeConfirmCloseReopensTheSameConflict --offline`
```
ProbeCuratedItemAcceptHttpAcceptanceTest > newerUnequalResultBeforeConfirmCloseReopensTheSameConflict() FAILED
    java.lang.AssertionError: JSON path "$.data.status"
Expected: is "OPEN"
     but: was "PENDING_CLOSE"
BUILD FAILED
```
Green command: same test, exit 0. Same conflict id, curated `up=false`, observed `up=true`, judgment `CONFLICT`. Confirm-close `CONFLICT_NOT_PENDING_CLOSE` and the conflict stays `OPEN`. Open draft `DRAFT_NOT_FOUND`.
Refactor: no structural change. `reopenProbeFromPendingClose` already matches the runs-on drift path (status, `UPGRADED`, void open draft).
Commit: `d16bc87` Leave 待确认关闭 when a newer probe result is unequal.

### Cycle E — 拒绝不写策展，冲突保持开放
Ran:
```
cd backend && ./gradlew test --tests com.archops.observed.ProbeCuratedItemAcceptHttpAcceptanceTest.rejectProbeDraftItemLeavesCuratedValueAndConflictOpen --offline
BUILD SUCCESSFUL
```
reuse/regression: first run exit 0. Covered by `CuratedDraftService.rejectItem` (marks `REJECTED`, does not write 策展, does not re-judge). No user header. `createdBy` stays null. Curated `up` stays true. Conflict stays `OPEN`.
Refactor: no structural change.
Commit: `c1f1117` Record that rejecting a probe draft item leaves the curated value unchanged.

### Cycle F — `/probe-tasks` 接受并确认关闭
`cd frontend && npm run build` exit 0 (`tsc --noEmit && vite build`).
Browser on Vite `:5173` → control plane `:8080`. Task `probe-8c0be653-f57d-4098-87bf-2b5d46bb04ab`: 改理想 leaves 策展 `{"up": true}` and an OPEN/PENDING item; 接受 writes `{"up": false}`, judgment 正常, 确认关闭 appears; 确认关闭 leaves the conflict CLOSED. No identity selector, no cause picker. Reload of a later 待确认关闭 task (`probe-ba8236da-ec96-4805-98d2-90977367a720`) still shows 确认关闭 because the conflict id is kept in sessionStorage after the task read model drops `conflictId`. That click also CLOSED.
Commit: `02e734b` plus the reload follow-up.

### Code review (Standards + Spec), fixed point `6434b81`
Standards: no layering, identity, or Flyway hard violation. Accept, reject, and confirm-close use the existing API client and omit the user header. Judgement: `rejudgeLatestAgainstCurated` repeats the equal/unequal shape of `judgeOne`; `reopenProbeFromPendingClose` repeats the runs-on drift path; the page repeats the busy/error wrapper. Cycles C and E were first-run green and are recorded as reuse, same as change-curated 04 confirm-close. Not deleted to manufacture a red.
Spec: accept writes the item's observed value and re-judges without a new heartbeat; equality is 待确认关闭 and not CLOSED; a newer unequal result reopens the same conflict and confirm-close cannot close it; reject does not write. The page's confirm-close survives reload. No new resolution action. Out of scope stays out.
