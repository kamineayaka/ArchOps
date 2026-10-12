---
status: "票 01 TDD-done。本刀闭合。不重开 ADR-0048 票 01，不重开薄 UI 票 01。"
---

# Spec: Host Agent 拉取探测任务、执行命令、交回结构化结果

**Status**: 工单 01 **TDD-done**。本刀闭合。  
**Basis**: [`docs/adr/0048-probe-task-control-plane-judges.md`](../adr/0048-probe-task-control-plane-judges.md)。控制面保存任务并判决交回已在 [`docs/specs/probe-task-control-plane-judges.md`](probe-task-control-plane-judges.md) 票 01 闭合。薄 UI 已在 [`docs/specs/probe-task-thin-ui.md`](probe-task-thin-ui.md) 票 01 闭合。本刀不重开那两张票，不改 ADR 正文。  
**Testing seams (confirmed)**: **主接缝 = 控制面公开 HTTP API**（现有 `POST /api/agent/heartbeat` ingest，以及随后的 `GET /api/probe-tasks/{id}` / 冲突诊断）。Host Agent 的命令执行用可注入的 runner 测，不要求真实主机、真实 SSH 或 MINA。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**。

## Problem Statement

控制面已经在心跳上交出探测任务，并判决交回的 `probeResults`。仓库里的 Host Agent 仍只发心跳和快照，不读取 `probeTasks`，也不把命令的结构化结果交回去。

## Solution

1. 已部署形态仍是主机上的 systemd Python Agent。它在现有心跳上拉取 `probeTasks`。不另开任务推送通道。不把「重新打包」写成唯一交付；忽略 `probeTasks` 的旧进程仍可心跳。本仓库的 `agent/heartbeat.py` 开始执行拉到的命令。
2. 每条任务经可注入的 runner 执行。Agent 不判决，不把结果送给 AI。交回仍是同一条 ingest 上的 `probeResults`（`taskId`、`readable`、`value`、`evidence`）。原始输出放在 `evidence`。
3. 控制面沿用已有判决，本刀不重写：
   - 可读且结构化值等于策展值：正常。不打开冲突。不进 AI。
   - 可读且不等于策展值：冲突，附上交回的原始证据。解决动作仍是 `FIX_ACTUAL_TO_CURATED` 与 `CHANGE_CURATED_TO_OBSERVED`。没有原因种类，没有「无法归类」。
   - 读不出，或命令超时因而没有交回：该任务观测空洞，不是冲突。
4. 同一主机上的任务彼此独立。一台主机至多一个探测 Agent、没有 Agent 的主机仍然有效，这两条保持 ADR-0048 票 01 已落地的行为。

## User Stories

1. Agent 心跳拉到任务，runner 给出与策展值相等的可读输出，控制面判为正常。
2. runner 给出与策展值不等的可读输出，控制面打开带原始证据的冲突，解决动作仍是修实际与改理想。
3. runner 给出读不出的输出，Agent 交回 `readable=false`，该任务变为观测空洞。
4. runner 报告命令超时，Agent 不交回该任务；到期扫描后该任务是观测空洞。
5. 同一主机两条任务，一条超时或读不出只空洞自己，另一条相等的仍是正常。
6. 心跳响应没有任务时，Agent 不提交 `probeResults`。

## HTTP tracer（循环顺序）

1. 保存任务后，Agent 进程对控制面心跳、用注入的 runner 得到 stdout `{"up": true}`，再把 `probeResults` 交回同一 ingest：`judgment=NORMAL`，无冲突。
2. stdout `{"up": false}`：冲突 `evidence` 为该原文；诊断 forks 为修实际与改理想，无 `kind`。
3. 退出码非 0 且输出为 `command error`：`judgment=HOLLOW`，无冲突。
4. runner `timedOut=true`，且 `intervalSeconds=0`：Agent 不交回该任务；`scan-overdue` 把它列入 `hollowedTaskIds`，`judgment=HOLLOW`，`evidence` 为空。
5. 两条任务一次拉取：一条超时不交回，一条 stdout 等于策展。前者经扫描为 HOLLOW，后者 NORMAL。
6. 拉取列表为空：Agent 只心跳一次，请求体没有 `probeResults`。

字面量（测试不得从生产代码反推）：

| 项 | 值 |
|---|---|
| 策展结构化值 | `{"up": true}` |
| 相等的命令输出 | `{"up": true}` |
| 不等的命令输出 | `{"up": false}` |
| 读不出输出 | `command error` |
| 命令 | `printf '{"up":true}'` |
| 冲突解决动作 id | `FIX_ACTUAL_TO_CURATED`、`CHANGE_CURATED_TO_OBSERVED` |

## Out of Scope

- 把「恢复观测通道」写成一条新的持久化。空洞不是冲突，本刀不发明这个写入。
- Agent 进程停掉就掏空它的全部快照观测。
- ADR-0049、Kubernetes、编排层 gRPC、AI 建议正文、原因种类、「无法归类」。
- 协作、处理人、操作者身份。
- 任务推送通道、控制面 SSH、MINA、真实主机。
- 重开 ADR-0048 票 01 或薄 UI 票 01。

## Further Notes

- **Issue tracker**: [`.scratch/probe-task-agent-runs/issues/`](../../.scratch/probe-task-agent-runs/issues/)
- 一台主机至多一个探测 Agent、零 Agent 的主机仍有效：保持票 01 的 `PROBE_AGENT_ALREADY_BOUND` 与无 Agent 时 `judgment=HOLLOW`。本刀不改那两条的控制面规则。
- 交回结果的第二次心跳不重复附带状态快照。
