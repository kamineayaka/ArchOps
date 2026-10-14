# 01 — 接受探测改理想条目：写入策展并立刻再判决

**What to build:** 探测冲突上已经有一份 OPEN 的改理想草案，里面一条 `PENDING` 的 `PROBE_CURATED_VALUE`，打开时没有写策展。接受这一条把条目里的观测结构化值写成该任务的策展值，并立刻用最新探测结果再判决。相等则进入待确认关闭，确认关闭可以关单。确认关闭前若交回更新的不等结果，离开待确认关闭并重开同一条冲突，已写入的策展值保持，确认关闭不能关单。拒绝不改策展，冲突保持开放。不新增解决动作。人审不带身份、不记操作者。HTTP 变绿后，`/probe-tasks` 可以接受条目并确认关闭。

**Blocked by:** None. Baseline is origin/main at `6434b8157960e40b6a68ad3a748f392cfc76563c` (PR #134). 改理想打开草案且不写策展已在 PR #133（Flyway V31）。不重开 ADR-0048 票 01、薄 UI、Host Agent 执行探测命令。

**Status:** ready-for-agent

**TDD:** capability。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)。主接缝是控制面 HTTP API。Spec：[`docs/specs/probe-curated-item-accept.md`](../../../docs/specs/probe-curated-item-accept.md)。

- [x] 接受待确认条目后，任务策展值变为该条目的观测结构化值；条目为已接受；解决动作仍只有修实际与改理想
- [x] 接受后不交回新心跳：最新结果与新策展值相等时，冲突为待确认关闭（不是已关闭）；任务判决为正常，`conflictId` 为空
- [ ] 待确认关闭且两侧仍相等时，确认关闭后冲突为已关闭
- [ ] 确认关闭前交回更新的不等结果：同一条冲突离开待确认关闭并重开；策展值保持已接受的值；确认关闭不能关单
- [ ] 拒绝该条目不改策展值，冲突保持开放，条目为已拒绝
- [ ] 接受、拒绝、确认关闭不带用户身份，不记录操作者
- [ ] `/probe-tasks` 可以接受条目并确认关闭；`npm run build` 通过

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
Commit: re-judges the latest probe result after the curated write.
