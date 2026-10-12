# 01 — Host Agent 拉取探测任务并交回结构化结果

**What to build:** 仓库里的 Host Agent 在现有心跳上拉取本主机的探测任务，用可注入的 runner 执行每条命令，并把结构化结果交回现有 ingest。可读且等于策展值则控制面维持正常；可读且不等则打开带原始证据的冲突，解决动作仍是 ADR-0047 的修实际与改理想；读不出或超时未交回则只空洞该任务。不分类异常种类，不把结果送给 AI。

**Blocked by:** None. Baseline is origin/main at `ff8cdc0` (PR #133, probe thin UI). ADR-0048 ticket 01 and the thin-UI ticket stay closed.

**Status:** ready-for-agent

**TDD:** capability。主接缝为控制面 HTTP API（现有 Agent ingest）。命令执行用可注入 runner，不要求真实主机、SSH 或 MINA。合同：ADR-0048。Spec：[`docs/specs/probe-task-agent-runs.md`](../../../docs/specs/probe-task-agent-runs.md)。

- [x] Agent 拉取任务并用注入 runner 执行；stdout `{"up": true}` 交回后 `judgment=NORMAL`，无冲突，不进 AI
- [x] stdout `{"up": false}` 交回后冲突附该原文；诊断只有 `FIX_ACTUAL_TO_CURATED` 与 `CHANGE_CURATED_TO_OBSERVED`，没有 `kind`，没有「无法归类」
- [x] 读不出（`command error`）交回 `readable=false`，该任务 `judgment=HOLLOW`，不是冲突
- [x] 命令超时则不交回；`intervalSeconds=0` 时 `scan-overdue` 将该任务列入 `hollowedTaskIds`，`judgment=HOLLOW`，`evidence` 为空
- [ ] 同一主机两条任务独立：一条超时只使自己 HOLLOW，另一条相等的仍是 NORMAL
- [x] 心跳没有任务时不提交 `probeResults`（第二次心跳也不附带快照）
- [x] 零 Agent 的主机仍可保存任务且为 HOLLOW；一台主机至多一个探测 Agent。这两条沿用 ADR-0048 票 01，不重写

**Out of this ticket:** 持久化「恢复观测通道」、Agent 停机掏空全部快照观测、ADR-0049、Kubernetes、编排层 gRPC、AI 建议正文、原因种类、「无法归类」、协作、处理人、操作者、任务推送、控制面 SSH、MINA、真实主机。不重开 ADR-0048 票 01。不重开薄 UI 票 01。

## Comments

### Cycle A — readable stdout equal to the curated value stays normal
Red command: `cd backend && ./gradlew test --tests com.archops.agent.ProbeAgentRunHttpAcceptanceTest.readableEqualCommandOutputIsNormalWithoutConflict --offline`
`AssertionFailedError: agent exit 2 stdout= stderr=python3: can't open file '/workspace/agent/probe_cycle.py': [Errno 2] No such file or directory`
Green command: same test, exit 0. `judgment=NORMAL`, `conflictId` null, evidence `{"up": true}`, no AI fields.
Refactor: `CommandOutcome` names the runner result; heartbeat errors raise `ProbeCycleError` instead of `SystemExit`. Same test re-run, exit 0.
Commit: `e021107` Have the host agent post a probe result that matches the curated value.

### Cycle B — readable stdout that differs opens a conflict with that raw output
Red command: `cd backend && ./gradlew test --tests com.archops.agent.ProbeAgentRunHttpAcceptanceTest.readableUnequalCommandOutputOpensConflictWithRawEvidence --offline`
reuse/regression: first run exit 0. The agent posts parsed stdout without comparing it to the curated value (`results_for_tasks` from `readableEqualCommandOutputIsNormalWithoutConflict`). The control plane already opens the conflict in `ProbeTaskJudgmentHttpAcceptanceTest.readableUnequalOpensConflictWithEvidenceAndResolutionActions`.
Refactor: no structural change.
Commit: `494e0ca` Lock the unequal probe stdout to the existing conflict judgment.

### Cycle C — unreadable command output is hollow for that task
Red command: `cd backend && ./gradlew test --tests com.archops.agent.ProbeAgentRunHttpAcceptanceTest.unreadableCommandOutputIsHollowForThatTask --offline`
`AssertionFailedError: agent exit 1` with `json.decoder.JSONDecodeError: Expecting value: line 1 column 1 (char 0)` from `json.loads(outcome.stdout)`.
Green command: same test, exit 0. `readable=false`, evidence `command error`, `judgment=HOLLOW`, `conflictId` null.
Refactor: `structured_value` and `raw_evidence` split a readable JSON post from a failed command. Class re-run, exit 0.
Commit: `4f3c31e` Post an unreadable probe command as observation hollow.

### Cycle D — a timed-out command omits the report and stays hollow
Red command: `cd backend && ./gradlew test --tests com.archops.agent.ProbeAgentRunHttpAcceptanceTest.timedOutCommandOmitsTheReportAndStaysHollow --offline`
`AssertionFailedError: expected: <0> but was: <1>` (`probeResults` still contained the timed-out task).
Green command: same test, exit 0. `probeResults` empty; `hollowedTaskIds` contains the task; `judgment=HOLLOW`; `evidence` null; `conflictId` null.
Refactor: `result_for_task` returns nothing on timeout. Same test re-run, exit 0.
Commit: `981795a` Omit a timed-out probe command so only that task can go hollow.

### Cycle E — one timed-out task does not hollow the equal task
Red command: `cd backend && ./gradlew test --tests com.archops.agent.ProbeAgentRunHttpAcceptanceTest.oneTimedOutTaskDoesNotHollowTheEqualTask --offline`
reuse/regression: first run exit 0. `result_for_task` already decides per task (`timedOutCommandOmitsTheReportAndStaysHollow`, `readableEqualCommandOutputIsNormalWithoutConflict`). The control plane already keeps tasks independent in `ProbeTaskJudgmentHttpAcceptanceTest.oneTaskHollowDoesNotHollowAnotherTaskOnTheSameHost`.
Refactor: no structural change.
Commit: `75ff3d5` Keep one host's probe tasks independent when a command times out.

### Cycle F — an empty pull does not submit probeResults
Red command: `cd agent && python3 -m unittest test_probe_cycle.NoTasksTests.test_no_tasks_posts_one_heartbeat_without_probe_results`
`TypeError: pull_and_report() got an unexpected keyword argument 'poster'`
Green command: same unittest, exit 0. One heartbeat, no `probeResults`. Gradle hook `emptyPullDoesNotSubmitProbeResults` exit 0. Equal HTTP path still exit 0.
Refactor: `poster` is optional so the HTTP client stays the default. No further structural change.
Commit: `451061b` Skip a result post when the heartbeat returns no probe tasks.

### Cycle G — the result heartbeat does not repeat the snapshot
Red command: `cd agent && python3 -m unittest test_probe_cycle.SnapshotReportTests.test_result_post_omits_snapshot`
`TypeError: pull_and_report() got an unexpected keyword argument 'snapshot'`
Green command: same unittest, exit 0. First post carries `snapshot` and no `probeResults`; second post carries `probeResults` and no `snapshot`.
Refactor: Gradle hooks share `runPythonUnittest`. Both hooks re-run, exit 0.
Commit: `db48d25` Keep the probe result heartbeat from repeating the snapshot.

### Cycle H — the heartbeat process runs the pulled command and posts it
Red command: `cd backend && ./gradlew test --tests com.archops.agent.ProbeAgentRunHttpAcceptanceTest.heartbeatProcessRunsThePulledCommandAndPostsIt --offline`
`AssertionError: JSON path "$.data.judgment" Expected: is "NORMAL" but: was "HOLLOW"`
Green command: same test, exit 0. `evidence` is `{"up":true}`, `conflictId` null.
Refactor: `heartbeat.py` delegates to `pull_and_report` with `subprocess_runner`. Contract and README say an older process that ignores `probeTasks` can still heartbeat.
Commit: `0e96a3b` Run pulled probe commands from the host agent heartbeat.

### Cycle I — a second heartbeat process does not submit results; a host with no agent stays hollow
Red command: `cd backend && ./gradlew test --tests com.archops.agent.ProbeAgentRunHttpAcceptanceTest.secondHeartbeatProcessDoesNotSubmitResults --offline`
reuse/regression: first run exit 0. Empty `probeTasks` already skips `probeResults` (`emptyPullDoesNotSubmitProbeResults`). The control plane already refuses a second probe agent in `ProbeTaskJudgmentHttpAcceptanceTest.secondProbeAgentDoesNotReceiveTasksOrSubmitResults`.
Zero-agent host: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.storesProbeTaskOnHostWithNoAgentAsHollowNotConflict --tests com.archops.observed.ProbeTaskJudgmentHttpAcceptanceTest.secondProbeAgentDoesNotReceiveTasksOrSubmitResults --offline` exit 0. reuse/regression of ADR-0048 ticket 01. No new production.
Refactor: no structural change.
Commit: `b729bb3` Keep a second heartbeat process from posting probe results.

### Cycle J — an unreachable control plane stays a failed heartbeat
Red command: `cd agent && python3 -m unittest test_probe_cycle.UnreachableControlPlaneTests.test_unreachable_control_plane_is_a_probe_cycle_error`
`urllib.error.URLError: <urlopen error [Errno 111] Connection refused>` (not `ProbeCycleError`). The previous heartbeat client caught `URLError` and returned 1.
Green command: same unittest, exit 0. `post_heartbeat` raises `ProbeCycleError`, which `heartbeat.py` turns into exit 1.
Refactor: no structural change.
Commit: `00a1d00` Report an unreachable control plane as a failed heartbeat.

### Cycle K — the task interval is not a command kill timer
Red command: `cd agent && python3 -m unittest test_probe_cycle.SubprocessRunnerTests.test_task_interval_does_not_kill_the_command`
`AssertionError: True is not false` (`timed_out` after `intervalSeconds=1` killed `sleep 2`).
Green command: same unittest, exit 0. The command finishes, `timed_out` is false, stdout is `{"up":true}`.
Refactor: no structural change. `intervalSeconds` stays the control plane overdue clock.
Commit: `8eea3c5` Let a probe command finish instead of killing it at the task interval.

### Cycle L — a silent command failure does not invent evidence text
Red command: `cd agent && python3 -m unittest test_probe_cycle.SilentFailureTests.test_silent_nonzero_exit_keeps_empty_evidence`
`AssertionError: '' != 'command error'`
Green command: same unittest, exit 0. `readable=false`, `evidence` is empty. HTTP `unreadableCommandOutputIsHollowForThatTask` still exit 0 when stderr is `command error`.
Refactor: no structural change.
Commit: (this slice) Keep empty command output as empty probe evidence.

### Code review (Standards + Spec), fixed point `ff8cdc0`
Standards: hard findings were first-run-green capability cycles B/E/I (recorded as reuse/regression; production from an earlier cycle of this ticket plus ADR-0048 ticket 01 was not deleted to manufacture red), Python post-count and snapshot-shape tests that do not themselves call the HTTP API (judgment cycles A–D and H do), and the unreachable-host path dropping `URLError`. Cycle J restores that failure as `ProbeCycleError`. Judgement: duplicated process-wait helpers, repeated agentId/hostId/sentAt, and `probe_cycle.py` holding both the runner and the stdin fixture.
Spec: stories 1–6 are present. `subprocess_runner` was killing the command at `intervalSeconds`; cycle K removes that kill timer. `raw_evidence` invented `command error` when both streams were empty; cycle L keeps the raw output, including empty. No ADR-0049, no cause kinds, no AI text, no collaboration, no task push, no snapshot-wide hollow, no persisted 「恢复观测通道」.
