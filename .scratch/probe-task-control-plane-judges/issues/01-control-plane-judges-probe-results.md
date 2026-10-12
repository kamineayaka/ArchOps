# 01 — 控制面保存探测任务并判决交回

**What to build:** 控制面保存绑定主机的探测任务（命令、间隔、策展结构化值）。Host Agent 在现有心跳上拉取任务。控制面判决交回：相等则正常且无冲突；不等则带原始证据的冲突，解决动作仍是 ADR-0047 的修实际与改理想；读不出或到期未交回则该任务观测空洞，不是冲突。不分类异常种类，不把每个结果送进 AI。一台主机至多一个探测 Agent；没有 Agent 的主机仍可保存任务。

**Blocked by:** None. Baseline is origin/main at `b883bc4` (PR #131, ADR-0047).

**Status:** ready-for-agent

**TDD:** capability。主接缝为控制面 HTTP API。合同：ADR-0048。Spec：[`docs/specs/probe-task-control-plane-judges.md`](../../../docs/specs/probe-task-control-plane-judges.md)。

- [x] 无 Agent 的主机可保存任务：读回命令 `printf '{"up":true}'` 与策展值 `{"up":true}`，`judgment=HOLLOW`，`conflictId` 为空，响应没有原因种类也没有「无法归类」
- [x] 该主机 Agent 的心跳响应 `probeTasks` 带上这条任务的命令
- [ ] 可读且等于策展值：`judgment=NORMAL`，不打开冲突，不进 AI
- [ ] 可读且不等于策展值：冲突附原始证据 `stdout=down`；诊断只有 `FIX_ACTUAL_TO_CURATED` 与 `CHANGE_CURATED_TO_OBSERVED`，没有 `kind`，没有「无法归类」
- [ ] 读不出：`judgment=HOLLOW`，不是冲突
- [ ] 已拉取且 `intervalSeconds=0` 仍未交回：`scan-overdue` 把该任务列入 `hollowedTaskIds`，`judgment=HOLLOW`，不是冲突
- [ ] 同一主机两条任务独立：一条读不出只使自己 HOLLOW，另一条相等的仍是 NORMAL
- [ ] 第二台 Agent 心跳拿不到任务；它交回结果为 `PROBE_AGENT_ALREADY_BOUND`

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
Commit: (cycle C)
