---
status: "票 01 done。探测冲突上已有的改理想草案逐条确认。不重开 ADR-0048 票 01、薄 UI、Host Agent 执行探测命令。不要开票 02。"
---

# Spec: 探测冲突改理想草案逐条确认

**Status**: 工单 01 **done**。  
**Basis**: [`docs/specs/change-curated-draft.md`](change-curated-draft.md)（接受写入策展并立刻比对；拒绝不写；相等只进待确认关闭；确认关闭前再不等则离开待确认关闭）。探测草案的打开已在 [`docs/specs/probe-task-thin-ui.md`](probe-task-thin-ui.md) 闭合：开放草案、一条 `PENDING` 的 `PROBE_CURATED_VALUE`、不写策展。判决与确认关闭比较冲突上的 JSON 已在 [`docs/specs/probe-task-control-plane-judges.md`](probe-task-control-plane-judges.md)。本刀不重开那三张票，不改 ADR 正文。  
**Testing seams (confirmed)**: 控制面 HTTP API（含 Agent ingest）。Gradle/`MockMvc` 与 Compose `bootRun`+`curl` 是同一接缝。薄 UI 在本票 HTTP 循环变绿之后：`cd frontend && npm run build`，再加上 `/probe-tasks` 上按人的路径点一遍。Playwright 不是完成定义。

## Problem Statement

探测冲突上的改理想已经打开草案，条目仍是待确认，策展值不变。人还不能接受那一条把观测值写成策展，也不能因此让控制面立刻再判决并走到待确认关闭。拒绝同样没有在这条草案上被验收。

## Solution

1. 接受仍走现有草案条目接受。不新增解决动作。接受把该条目里的观测结构化值写成这条探测任务的策展值。
2. 写入后控制面立刻用**最新**探测结果与新策展值再判决，不等下一次心跳。相等则冲突进入待确认关闭，不自动关闭。任务读模型为正常，`conflictId` 为空。
3. 待确认关闭且两侧仍相等时，现有确认关闭可以关闭该冲突。
4. 确认关闭之前若交回更新的不等结果：离开待确认关闭，同一条冲突重开；已写入的策展值保持；确认关闭不能关单。
5. 拒绝该条目不改策展值，冲突保持开放。
6. 接受、拒绝、确认关闭不带用户身份，不记录操作者（ADR-0046）。

## User Stories

1. 人接受探测草案上唯一的待确认条目后，任务的策展值变为该条目提议的观测值，条目为已接受。诊断的解决动作仍只有修实际与改理想。
2. 接受后不必再交回心跳：最新结果与新策展值相等时，冲突为待确认关闭而不是已关闭；任务判决为正常。
3. 人确认关闭后，冲突为已关闭。
4. 待确认关闭之后、确认关闭之前，心跳交回与新策展值不等的结果：同一条冲突回到开放，策展值仍是已接受的值，确认关闭被拒绝。
5. 人拒绝该条目：策展值仍是原来的值，冲突仍开放，条目为已拒绝。

## Out of Scope

- 执行修实际的待审计划
- 持久化「恢复观测通道」
- Agent 停机时掏空它的全部快照观测
- ADR-0049、Kubernetes、编排层 gRPC、AI 建议正文、原因种类、「无法归类」
- 重开 ADR-0048 票 01、探测薄 UI、Host Agent 执行探测命令
- 认领、已知悉、自任、指派、接受、拒绝、转让、处理人

## Further Notes

- **Issue tracker**: [`.scratch/probe-curated-item-accept/issues/`](../../.scratch/probe-curated-item-accept/issues/)
- 打开草案仍不写策展。本刀从那份已经 OPEN 的草案开始。
- 策展值存在探测任务上。比较的是结构化 JSON，不是 `运行于` 宿主。
- 确认关闭比较该冲突上的 JSON，与探测判决票已有的关单相同。再不等时的离开待确认关闭沿用改策展：同一条冲突，不另开一条。
