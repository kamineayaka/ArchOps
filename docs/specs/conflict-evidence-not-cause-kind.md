---
status: "实现中。合同是 ADR-0047。本刀只有票 01。不含 ADR-0048 / ADR-0049 的实现。"
---

# Spec: 案件带解决动作，不带原因种类

**Status**: spec published；工单 01 是本刀 frontier。本 PR 不含 ADR-0048 / ADR-0049 的实现。  
**Basis**: [`docs/adr/0047-conflict-evidence-not-cause-kind.md`](../adr/0047-conflict-evidence-not-cause-kind.md)。不改 ADR 正文。不改发现的定义。不改 ADR-0043 / 0044 / 0045 / 0046。  
**Testing seams (confirmed)**: **唯一验收主接缝 = 控制面公开 HTTP API**（含 Agent 心跳/快照 ingest）。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**。前端不进本刀自动化主接缝。

## Problem Statement

`DiagnosisRuleEngine` 仍是原因目录。诊断把分叉写成带 `kind` 的原因种类（`FIX_ACTUAL`、`CHANGE_CURATED`、`RESTORE_CHANNEL`、`EXPLAIN`）。身份失联被写成一条诊断分叉 `EXPLAIN_IDENTITY_LOST`。观测空洞的动作文案把「恢复观测通道 / 心跳 / 核验」捆成一类原因。调用方按分叉 id 选的是原因种类，不是解决动作。

ADR-0047：案件不携带原因种类。人请求的是封闭的三个解决动作之一。哪些动作可请求，只看轨道：两侧可读且不等，则可请求修实际与改理想；观测空洞只可请求恢复观测通道，不得用过期实际给出修现场。身份失联仍是标志，不是诊断种类，不得把过期实际当成唯一落点。不增加「无法归类」。不增加新的原因枚举。

## Solution

1. 两侧当前可读且不等：诊断列出两个可请求的解决动作，id 仍是已有的 `FIX_ACTUAL_TO_CURATED`（修实际）与 `CHANGE_CURATED_TO_OBSERVED`（改理想）。响应不带原因种类字段 `kind`，也不出现「无法归类」。请求改理想仍打开逐条草案，且在条目接受前不写策展。请求修实际仍打开待审操作计划。
2. 观测空洞：诊断只列出恢复观测通道（id 仍是 `RESTORE_HEARTBEAT_CHANNEL`，文案是「恢复观测通道」）。不列出修实际或改理想。
3. 身份失联：冲突读模型仍给出标志。诊断不再给出 `EXPLAIN_IDENTITY_LOST` 或任何原因种类。不把修实际 / 改理想当作可请求的唯一落点。

## User Stories

1. 两侧可读且不等时，人读到修实际与改理想两个解决动作，案件上没有原因种类。
2. 人请求改理想，得到开放的逐条草案，策展「应该在哪」不变。
3. 人请求修实际，得到待审操作计划。
4. 观测空洞时，人只读到恢复观测通道，读不到凭过期实际的修现场。
5. 身份失联仍是冲突上的标志；诊断不是一条「身份失联」种类，也不把旧实际当成唯一落点。

## Out of Scope

- 探测任务 CRUD、Agent 拉取任务、Kubernetes 清单、编排层进程、Spring Cloud、拆控制面
- 用户抽取、ABSENT 执行、占位 SSH、观测回写
- 新原因枚举、「无法归类」
- ADR-0048 / ADR-0049 的实现
- 改 ADR 正文；改发现定义；把空洞或身份失联折进冲突状态

## Further Notes

- **Issue tracker**: [`.scratch/conflict-evidence-not-cause-kind/issues/`](../../.scratch/conflict-evidence-not-cause-kind/issues/)
- **Contract**: ADR-0047。本刀只有票 01。
