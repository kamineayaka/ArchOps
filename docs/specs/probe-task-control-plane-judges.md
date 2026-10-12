---
status: "票 01 ready-for-agent。本刀只做 ADR-0048 的 HTTP 第一刀。"
---

# Spec: 探测任务由控制面判决

**Status**: 工单 01 实施中。本刀不含 ADR-0049、编排层 gRPC、Kubernetes、原因种类。  
**Basis**: [`docs/adr/0048-probe-task-control-plane-judges.md`](../adr/0048-probe-task-control-plane-judges.md)。不改 ADR 正文。不改 ADR-0047 的解决动作集合。不改 ADR-0043 / 0044 / 0045 / 0046。  
**Testing seams (confirmed)**: **唯一验收主接缝 = 控制面公开 HTTP API**（含 Agent 心跳 ingest）。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**。前端不进本刀。不执行真实 SSH。不要求重新打包 Host Agent。

## Problem Statement

控制面还没有探测任务。一条命令的结构化结果无法写成观测，也无法由控制面判决成「等于策展 / 不等于策展 / 读不出或逾期」。已在跑的 Host Agent 只能心跳和快照，心跳响应不带该主机的任务列表。

ADR-0048：探测任务是控制面上的数据。唯一配置的正常是策展值。Agent 在心跳上拉取任务，不判决。控制面判决每一次交回。不是每个结果都进 AI。不存放异常、错误或未定义种类的目录。

## Solution

1. 控制面保存探测任务：绑定一台物理主机，带命令、间隔秒数，以及该任务的策展结构化值。该策展值是唯一的正常。同一主机上的多条任务彼此独立。没有 Agent 的主机仍可保存任务；此时没有可用观测，是观测空洞，不是冲突。
2. 已部署的 Host Agent 在现有 `POST /api/agent/heartbeat` 上拉取本主机任务（命令与间隔）。任务改动由下一次心跳带上。不因此改 Agent 二进制。控制面不执行该命令，也不 SSH 到主机。
3. 同一心跳可以交回探测结果（测试以伪造的 Agent ingest 提交）。控制面判决：
   - 可读且结构化值等于策展值：正常。不打开冲突。不进 AI。
   - 可读且不等于策展值：打开冲突，附上交回的原始证据。诊断的解决动作仍是 ADR-0047 的修实际与改理想（`FIX_ACTUAL_TO_CURATED`、`CHANGE_CURATED_TO_OBSERVED`）。响应没有原因种类 `kind`，也没有「无法归类」。
   - 读不出（`readable=false`，或没有结构化值），或已拉取且到期仍未交回：该任务观测空洞，不打开冲突。
4. 一条任务读不出或逾期，只空洞那一条。另一条已判为正常的任务保持正常。
5. 一台主机至多一个执行探测任务的 Agent。第一台在该主机心跳并拉取任务的 Agent 成为该主机的探测 Agent。另一台 Agent 的心跳仍可刷新存活，但任务列表为空；它交回的探测结果被拒绝。没有探测 Agent 的主机仍然有效。

## User Stories

1. 人在一台还没有 Agent 的主机上保存探测任务，读回命令和策展值，看到观测空洞，看不到冲突，也看不到异常种类。
2. 该主机上的 Agent 心跳，响应里带上自己的任务列表。
3. Agent 交回与策展值相等的可读结果，控制面判为正常，不产生冲突。
4. Agent 交回与策展值不等的可读结果，控制面打开带原始证据的冲突，解决动作仍是修实际与改理想。
5. Agent 交回读不出的结果，该任务变为观测空洞，不是冲突。
6. Agent 已拉取任务，但在到期前没有交回，扫描后该任务是观测空洞，不是冲突。
7. 同一主机两条任务：一条读不出只空洞自己，另一条相等的任务仍是正常。
8. 第二台 Agent 心跳拿不到任务列表，交回结果被拒绝；第一台 Agent 仍可交回。

## HTTP tracer（循环顺序）

1. `POST /api/probe-tasks` 然后 `GET /api/probe-tasks/{id}`：无 Agent，`judgment=HOLLOW`，`conflictId` 为空。
2. `POST /api/agent/heartbeat`：`probeTasks` 含该任务的 `command`。
3. 心跳带可读且等于 `{"up":true}` 的 `probeResults`：`judgment=NORMAL`，无冲突。
4. 心跳带可读且等于 `{"up":false}`、`evidence=stdout=down`：冲突 `evidence` 为该原文；诊断 forks 为修实际与改理想，无 `kind`。
5. `readable=false`：`judgment=HOLLOW`，无冲突。
6. `intervalSeconds=0`，心跳拉取后 `POST /api/probe-tasks/scan-overdue`：该任务在 `hollowedTaskIds` 中，`judgment=HOLLOW`，无冲突。
7. 两条任务一次心跳：一条 `readable=false`，一条值等于策展。前者 HOLLOW，后者 NORMAL。
8. 第二台 Agent 心跳 `probeTasks` 为空；它提交结果得到 `PROBE_AGENT_ALREADY_BOUND`。

字面量（测试不得从生产代码反推）：

| 项 | 值 |
|---|---|
| 策展结构化值 | `{"up": true}` |
| 不等的观测值 | `{"up": false}` |
| 原始证据 | `stdout=down` |
| 读不出证据 | `command error` |
| 命令 | `printf '{"up":true}'` |
| 冲突解决动作 id | `FIX_ACTUAL_TO_CURATED`、`CHANGE_CURATED_TO_OBSERVED` |
| 第二台 Agent 错误码 | `PROBE_AGENT_ALREADY_BOUND` |

## Out of Scope

- 远程多任务运维超出本刀：Agent 停机时掏空它的全部快照观测、把 AI 建议写成正文、任务推送、控制面 SSH 下发、重新打包 Agent
- Kubernetes、三个 Deployment、编排层进程、编排层↔控制面 gRPC、模型 HTTPS（ADR-0049）
- 原因种类目录、「无法归类」、把每个结果送进 AI
- 前端页面
- 改 ADR 正文；复活认领 / 处理人 / 身份门禁

## Further Notes

- **Issue tracker**: [`.scratch/probe-task-control-plane-judges/issues/`](../../.scratch/probe-task-control-plane-judges/issues/)
- **Contract**: ADR-0048。本刀只有票 01。
- 心跳契约的可选字段见 `docs/contracts/agent-heartbeat-snapshot.md`。现有 `agent/heartbeat.py` 不解析新字段，因此不必替换已部署的 Agent。
