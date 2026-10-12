# 01 — 控制面保存探测任务并判决交回

**What to build:** 控制面保存绑定主机的探测任务（命令、间隔、策展结构化值）。Host Agent 在现有心跳上拉取任务。控制面判决交回：相等则正常且无冲突；不等则带原始证据的冲突，解决动作仍是 ADR-0047 的修实际与改理想；读不出或到期未交回则该任务观测空洞，不是冲突。不分类异常种类，不把每个结果送进 AI。一台主机至多一个探测 Agent；没有 Agent 的主机仍可保存任务。

**Blocked by:** None. Baseline is origin/main at `b883bc4` (PR #131, ADR-0047).

**Status:** done

**TDD:** capability。主接缝为控制面 HTTP API。合同：ADR-0048。Spec：[`docs/specs/probe-task-control-plane-judges.md`](../../../docs/specs/probe-task-control-plane-judges.md)。

- [x] 无 Agent 的主机可保存任务：读回命令 `printf '{"up":true}'` 与策展值 `{"up":true}`，`judgment=HOLLOW`，`conflictId` 为空，响应没有原因种类也没有「无法归类」
- [x] 该主机 Agent 的心跳响应 `probeTasks` 带上这条任务的命令
- [x] 可读且等于策展值：`judgment=NORMAL`，不打开冲突，不进 AI
- [x] 可读且不等于策展值：冲突附原始证据 `stdout=down`；诊断只有 `FIX_ACTUAL_TO_CURATED` 与 `CHANGE_CURATED_TO_OBSERVED`，没有 `kind`，没有「无法归类」
- [x] 读不出：`judgment=HOLLOW`，不是冲突
- [x] 已拉取且 `intervalSeconds=0` 仍未交回：`scan-overdue` 把该任务列入 `hollowedTaskIds`，`judgment=HOLLOW`，不是冲突
- [x] 同一主机两条任务独立：一条读不出只使自己 HOLLOW，另一条相等的仍是 NORMAL
- [x] 第二台 Agent 心跳拿不到任务；它交回结果为 `PROBE_AGENT_ALREADY_BOUND`
- [x] 已打开冲突后读不出：任务 `HOLLOW`，冲突 `SUSPENDED`
- [x] 已打开冲突后相等：任务 `NORMAL`，冲突 `PENDING_CLOSE`；确认关闭后 `CLOSED`

**Out of this ticket:** Agent 停机掏空全部快照观测、AI 建议正文、任务推送、SSH 执行命令、重新打包 Agent、Kubernetes、编排层 gRPC、前端、原因种类、「无法归类」。

## Comments

### Cycle A — store a probe task on a host with no agent; hollow, not a conflict
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.storesProbeTaskOnHostWithNoAgentAsHollowNotConflict --offline`
`AssertionError: Status expected:<200> but was:<404>` body `{"success":false,"code":"NOT_FOUND","message":"Not found","data":null}`
Green command: same test, exit 0.
Refactor: service comment names the no-result read as 观测空洞.
Commit: `ff9969a` Store a probe task whose only normal is the curated value.

### Cycle B — heartbeat pulls the host's probe tasks
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.heartbeatPullsProbeTasksForThatHost --offline`
`AssertionError` at line 91 caused by `PathNotFoundException` (`probeTasks` missing).
Green command: same test, exit 0.
Refactor: heartbeat helper takes `JsonNode`; contract names `probeTasks`.
Commit: `5d0af4c` Return probe tasks on the host agent heartbeat.

### Cycle C — readable value equal to curated is normal, no conflict, no AI
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.readableEqualCuratedValueIsNormalWithoutConflict --offline`
`AssertionError: JSON path "$.data.judgment" Expected: is "NORMAL" but: was "HOLLOW"`
Green command: same test, exit 0.
Refactor: one `toResponse` path for hollow and judged rows.
Commit: `14206f9` Judge a readable probe result that matches the curated value as normal.

### Cycle D — readable unequal opens a conflict with raw evidence and ADR-0047 actions
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.readableUnequalOpensConflictWithEvidenceAndResolutionActions --offline`
`AssertionError` at line 143 (`judgment` was not `CONFLICT`).
Green command: same test, exit 0. Diagnosis source `RULES`, forks `FIX_ACTUAL_TO_CURATED` / `CHANGE_CURATED_TO_OBSERVED`, no `kind`.
Refactor: `judgeOne` holds the three-way readable compare.
Commit: `fe5a88a` Open a conflict when a readable probe value differs from curated.

### Cycle E — unreadable result is hollow, not a conflict
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.unreadableResultIsHollowNotConflict --offline`
`AssertionError` at line 202 (evidence was not `command error`; the miss was not recorded).
Green command: same test, exit 0. `judgment=HOLLOW`, `conflictId` null.
Refactor: no structural change.
Commit: `9cd226b` Record an unreadable probe result as observation hollow.

### Cycle F — pulled task with no result by due time is hollow, not a conflict
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.overdueReportIsHollowNotConflict --offline`
`AssertionError` at line 226 (`POST /api/probe-tasks/scan-overdue` was 404).
Green command: same test, exit 0. `hollowedTaskIds` contains the task; `judgment=HOLLOW`; `conflictId` null.
Refactor: pull records `dueAt`; judgment clears the awaiting window.
Commit: `67bfc57` Hollow a pulled probe task that was not returned by its due time.

### Cycle G — one hollow task does not hollow another task on the same host
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.oneTaskHollowDoesNotHollowAnotherTaskOnTheSameHost --offline`
reuse/regression: first run exit 0. Covered by `judgeOne` per task (`readableEqualCuratedValueIsNormalWithoutConflict`, `unreadableResultIsHollowNotConflict`).
Refactor: `joinResults` builds one heartbeat body.
Commit: `6d24b39` Keep probe tasks on one host independent when one result is unreadable.

### Cycle H — a second agent does not become a second probe agent
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.secondProbeAgentDoesNotReceiveTasksOrSubmitResults --offline`
`AssertionError` at line 287 (`probeTasks` was not empty for the second agent).
Green command: same test, exit 0. Second heartbeat `probeTasks` length 0; its result is `PROBE_AGENT_ALREADY_BOUND`; the first agent can still report `NORMAL`.
Refactor: `claimOrMatchProbeAgent` names the first-pull binding.
Commit: `9e64181` Allow only one probe agent to pull and return tasks for a host.

### Cycle I — a later unreadable result suspends the open probe conflict
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.unreadableAfterConflictSuspendsTheCase --offline`
`AssertionError` at line 344 (`status` stayed `OPEN`).
Green command: same test, exit 0. Task `judgment=HOLLOW`, `conflictId` null; conflict `SUSPENDED`, `observationHollow=true`.
Refactor: no structural change in this cycle.
Commit: `223cfc9` Move an open probe conflict when a later result changes.

### Cycle J — a later equal result moves the conflict to pending close
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.equalAfterConflictMovesToPendingClose --offline`
`AssertionError` at line 381 (`status` was not `PENDING_CLOSE`).
Green command: same test, exit 0. Task `NORMAL`; conflict `PENDING_CLOSE`; `POST /api/conflicts/{id}/confirm-close` returns `CLOSED`.
Refactor: `probeValuesEqual` reads both JSON columns through one `TypeReference<JsonNode>`. Same test re-run, exit 0.
Commit: `223cfc9` (same commit as Cycle I; the two follow-ups share `judgeOne`).

### Code review (Standards + Spec), fixed point `b883bc4`
Standards: no hard violations. Flyway only adds V26–V30. Judgement: `confirmCloseProbe` repeats the close update, and a failed probe confirm-close appends `CONFIRM_FAILED` in the outer transaction so that event rolls back; `onProbeBecameHollow` repeats the heartbeat hollow suspend; judgment outcome is a string; `PROBE` is a new branch in diagnosis and confirm-close.
Spec: tracer items 1–8 are in the HTTP tests. Accepted limits are in the spec Further Notes: probe conflicts share merge key `(host, PROBE)` and are distinguished by `probeTaskId`; no general `observed_fact` row; pull includes `curatedExpected`; a second agent's result request is 400 and does not refresh liveness; a later equal leaves the case `PENDING_CLOSE` while the task read model is `NORMAL`; suspended probe diagnosis reuses 「恢复观测通道」 and its heartbeat-timeout hypothesis. Full suite after `223cfc9`: `./gradlew test --offline` BUILD SUCCESSFUL, classes=49 tests=289 failures=0 errors=0 skipped=0.
