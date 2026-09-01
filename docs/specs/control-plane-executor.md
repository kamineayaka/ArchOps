# Spec: 控制面执行引擎（单步代发 + MINA/凭证迁出）

**Status**: spec published；工单未拆（下一拍 `/to-tickets`）  
**Basis**: ADR-0044（运行时拓扑；本刀**实现** 0044，不重开拒绝项）；ADR-0045（控制面→执行引擎 = gRPC + Protobuf；`grpc.health.v1`；仅控制面可调 / mTLS）；ADR-0043；`CONTEXT.md`「操作计划」「执行引擎」「控制面代发」「步骤断言」（schema 本刀后置）「心跳」  
**Source**: [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md) **B1–B3 的第一刀**（B4 B-live / B5 编排层出站 / B6 工作台另刀）  
**Predecessor**: 竖切 / 改策展 / 未绑定 01–09 / 冲突升级作废活跃计划（A1）均已闭合。控制面进程内 LLM 出站已删；规则诊断兜底仍能警告→选支→人审→执行（0044 决议 7）。失败即停作废、禁止改步重试已在控制面。Host Agent 心跳仍直连控制面。现码偏离：Compose 仍三件套；生产 MINA 与主机凭证仍在控制面；`start-execution` 一次 HTTP + Redis 锁内跑完整份计划。  
**Testing seams (confirmed)**: **主接缝 = 控制面公开 HTTP API**（含 Agent 心跳/快照 ingest；已接受处理人 `start-execution` / 计划 `VOIDED`）。**新进程接缝 = 执行引擎 `grpc.health.v1`**（Compose 可 up、SERVING）。**窄负面接缝 = 无/错客户端证书调引擎 gRPC → 拒绝**。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**。前端薄 UI 不进本刀自动化主接缝。不要 Playwright。不要「真 SSH 一台公网机」当完成定义。

**Confirmed scope pins**

1. 不改 `CONTEXT.md`，不重开 ADR-0039 / 0043 / **0044 正文**。另立 **ADR-0045** 只冻内部运输与谁可以调引擎。
2. 本刀 = 审计 B 的执行引擎成真：独立进程 + MINA/凭证迁出 + 控制面一次下发一步 + 停发/作废在途一步。编排层 / B-live / 步骤断言 schema / 工作台三档 **Out of Scope**。
3. 票 01 = 本 Spec 全部 Must（不先交空骨架）。打断 MINA 会话是 Should / 后票，不进 01。
4. 主接缝仍是现有 `start-execution` 同步 HTTP；内部代发是 gRPC，不是第二条处理人 API。

---

## Problem Statement

运维已能在控制面人审操作计划并执行，但权力中心自己用 MINA 连物理主机，且一次请求把整份冻结计划跑完。ADR-0044 已拒绝「整份交给执行引擎内跑完」，并点名必须把 MINA/凭证迁出；空洞或冲突升级时，权力中心必须立刻停止下发下一步。现码在偏离上无法再加产品功能：没有独立执行引擎，没有单步代发，没有「在途成功丢弃、计划保持 VOIDED」。

## Solution

交付独立**执行引擎**进程（Java 21 + 已有 MINA）。已接受冲突处理人仍只打控制面 `start-execution`。控制面保持枢纽：门禁、游标、Redis 计划锁（下发前加）、作废。它一次用 gRPC 向引擎下发**一步**；引擎持主机凭证、连图内物理主机、返回结构化结果。空洞 / 身份失联 / 计划已 `VOIDED` 时不再下发下一步；若当前步已成功返回则丢弃该成功、计划保持 `VOIDED`。Compose 在现有三件套上只加执行引擎；健康口 `grpc.health.v1`；调用方仅控制面（mTLS，Compose/CI 自签）。旧竖切 HTTP 可继续控制面 fake；本刀新测试经代发打到引擎侧 fake。

---

## User Stories

1. As an 已接受冲突处理人, I want to `start-execution` on an APPROVED 操作计划 via the same control-plane HTTP as today, so that I do not learn a new API to run frozen steps.
2. As an 已接受冲突处理人, I want each frozen step to be dispatched **one at a time** to the 执行引擎, so that the engine never receives the whole 操作计划 to run internally.
3. As the 控制面, I want to hold the Redis 计划锁 before each dispatch, so that two replicas cannot execute the same active plan concurrently.
4. As the 执行引擎, I want to open MINA SSH to the graph-resident 物理主机 named by `targetHostId`, so that the control plane is no longer the production SSH path.
5. As the 执行引擎, I want to load and decrypt host credentials myself from the same PostgreSQL ciphertext the control plane wrote, so that the dispatch payload never carries plaintext secrets.
6. As the 控制面 credential write API, I want to keep storing encrypted host secrets, so that operators can still register SSH material without the dispatch client decrypting it.
7. As the 控制面, I must not decrypt host secrets on the production SSH / dispatch path, so that 「凭证迁出」is a real power move, not a rename.
8. As a 冲突处理人, when 观测空洞 (heartbeat timeout) voids my EXECUTING plan, I want the control plane to **stop dispatching the next step**, so that work does not continue against expired 实际.
9. As a 冲突处理人, if the in-flight step returns success after that void, I want that success discarded and the plan to remain `VOIDED`, so that a late SSH ok cannot complete a voided plan.
10. As a 冲突处理人, I want 身份失联 voiding to use the same stop-dispatch / discard-success hook, so that 失联落地 does not leave a running cursor.
11. As QA, I want conflict-upgrade voiding (already closed as A1) to ride the same `VOIDED` flag without this knife re-implementing upgrade, so that one cursor check covers 空洞 / 失联 / 升级.
12. As any viewer, I want failure-at-a-step to still 失败即停作废 and forbid in-place retry, so that frozen-plan discipline does not regress.
13. As an operator running Compose, I want an 执行引擎 service beside postgres / redis / archops, so that delivery matches ADR-0044’s executor process (without an AI 编排层 process in this knife).
14. As CI, I want `grpc.health.v1` on the 执行引擎 to report SERVING (probe using the self-signed client certificate), so that “process exists and is ready” is assertable.
15. As the 控制面, I want to call ExecuteStep only over gRPC with a client certificate the 执行引擎 trusts, so that anonymous callers cannot run tools.
16. As anyone who is not the 控制面, I want my gRPC call without a valid client certificate to be rejected, so that the 执行引擎 is not a second public entry.
17. As the future AI 编排层, I must not be given that client certificate in this knife or later by accident of Compose env sprawl, so that 0044’s ban on orchestrator-held executor credentials stays closed.
18. As CI for **legacy** 竖切 HTTP tests, I want `archops.ssh.mode=fake` on the control plane to keep those tests green without booting the 执行引擎, so that 决议 7 (warn → select → review → execute without 编排层) does not break.
19. As CI for **this knife’s new** tests, I want the HTTP `start-execution` path to go through gRPC dispatch to an 执行引擎 whose SSH adapter is fake, so that we prove the hub protocol without a public host.
20. As production, I want `mode=mina` to exist only on the 执行引擎, so that the control plane cannot enlarge in-process production SSH.
21. As the 执行引擎, I want `success` on ExecuteStep to mean this knife’s SSH/fake outcome, so that we do not pretend 步骤断言 schema has shipped.
22. As the 执行引擎, I must not `SELECT` 操作计划 rows, so that the cursor stays in the 控制面.
23. As the 执行引擎, I must not write 策展 / 观测 / 冲突 tables, so that 执行引擎 never becomes a truth author.
24. As Host Agent, I still want to POST `/api/agent/heartbeat` straight to the 控制面, so that 心跳 does not detour through the 执行引擎 or an 编排层.
25. As a non-handler, I still want `start-execution` denied, so that only the 已接受冲突处理人 runs plans.
26. As a 冲突处理人, I want a VOIDED plan to reject `start-execution` with `PLAN_VOIDED`, so that voided plans are not retried in place.
27. As QA, I want existing rule-diagnosis HTTP tests to stay green, so that removing in-process MINA does not block 警告→选支→人审.
28. As QA, I want a recording fake on the 执行引擎 to show which `targetHostId` / action / seq was asked, so that dispatch content is visible without real SSH.
29. As the 控制面, I want to re-read plan status and conflict state **between** steps inside the same `start-execution` HTTP request, so that a concurrent 空洞 void is honored before the next gRPC call.
30. As the 控制面, I do not want the Redis lock to make the whole plan an uninterruptible critical section, so that 「立刻停止下发」is possible before the next step.
31. As Compose, I do not want an AI 编排层 service in this knife, so that we do not ship an empty capability process while SSH still moves.
32. As Compose, I do not want Host Agent in the default control-plane Compose, so that ADR-0043/0044 delivery of Agent stays systemd.
33. As an implementer, I want a frozen ExecuteStep request/response (below), so that ticket 01 does not invent a second transport.
34. As an implementer, I want mTLS material for Compose/CI to be self-signed fixtures generated by this knife, so that we do not build a human-operated PKI product.
35. As an operator, I want the 执行引擎 image to include a gRPC health probe client, so that Compose healthchecks can speak `grpc.health.v1` under mTLS.
36. As QA, I want a negative HTTP case where the 执行引擎 is down / health not SERVING to fail `start-execution` without executing MINA on the control plane, so that we do not silently fall back to in-process production SSH.
37. As a 冲突处理人, I want step logs returned on the existing `start-execution` HTTP response to still list seq / action / host / success, so that the public REST envelope does not change to protobuf.
38. As the 控制面, I want `planId` on ExecuteStep to be correlation only, so that the 执行引擎 cannot load the frozen step list from the database.
39. As security review, I want no business-library / customer / order / finance payloads on ExecuteStep, so that 0041 禁载荷 still holds on the new hop.
40. As product, I want 连接工作台 to remain out of this knife, so that we do not open a second SSH path while moving the first.
41. As product, I want 现场状态读取 (B-live) out of this knife, so that diagnosis does not grow a field channel before dispatch exists.
42. As product, I want AI 编排层 model egress out of this knife, so that we do not put keys on a new process before the power path (SSH) has left the 控制面.
43. As QA, I want interrupting an in-flight MINA session to be explicitly **not** required for ticket 01, so that 01 does not become session-kill engineering.
44. As a future knife, I want this ExecuteStep endpoint to be the same 执行引擎 连接工作台 will share, so that we do not grow a bypass later (B6 still out of **this** spec).

---

## Implementation Decisions

### Topology

- Four-process target remains ADR-0044. **This knife adds only the 执行引擎** to default Compose: postgres, redis, `archops:latest` (控制面 + 前端静态), 执行引擎. No AI 编排层 service. Host Agent stays out of that Compose.
- 执行引擎 is an independent Java 21 + Spring Boot 3 process (same language as MINA already in tree). Do not switch the engine to Python without a new ADR.
- Control plane stays multi-replica in design. This knife may run a single 执行引擎 replica.

### Transport (ADR-0045)

- Control plane → 执行引擎: **gRPC + Protobuf**, one RPC per frozen step. Not JSON HTTP, not Redis queue, not engine pull, not a second sequencer.
- Readiness: **`grpc.health.v1`**. Compose/CI probes must present the client certificate.
- Public handler API remains REST `/api/...` + `ApiResponse`. Protobuf never becomes the 处理人-facing contract.

Frozen ExecuteStep shape (hub protocol for ticket 01; 步骤断言 fields omitted on purpose):

```
ExecuteStepRequest {
  plan_id          // correlation only; engine must not load 操作计划 rows
  step_seq
  action
  params           // string map; includes whatever the frozen step already had
  target_host_id   // graph-resident 物理主机; engine loads ciphertext creds by this id
}

ExecuteStepResponse {
  step_seq
  success          // this knife: SSH/fake outcome, not 步骤断言
  structured_output
  failure_reason
}
```

Forbidden on the wire: plaintext host secrets; full step list; 诊断作业包; 业务库/客户/订单/财务.

### Auth

- mTLS between 控制面 and 执行引擎. Engine **requires** a client certificate.
- Compose/CI: self-signed fixtures generated/mounted by this knife. External CA, rotation, human PKI = Later.
- AI 编排层 must not receive that client certificate (now or when that process is added).

### Credentials and MINA

- Host SSH ciphertext remains in PostgreSQL. Control plane write APIs still encrypt-at-rest.
- 执行引擎 reads the same ciphertext and decrypts with the same encryption key this knife (no dedicated read-only DB role). Engine process must not write 策展/观测/冲突/计划.
- Control plane production path: **delete or disable** in-process MINA. Keep only the dispatch client. Transitional **test** fake may remain on the control plane for **legacy** HTTP tests (决议 7). New tests in this spec must not use control-plane production MINA.
- `mina` as production SSH exists only on the 执行引擎.

### `start-execution` loop

- One handler HTTP request still runs the approved plan to completion or void (Q8). Internally: lock → dispatch **one** ExecuteStep → apply result → **re-read** plan + conflict → if `VOIDED` / 空洞 / not OPEN, stop.
- Do not hand the whole frozen plan to the engine.
- Redis lock remains on the control plane and must not make the whole plan uninterruptible: after a step returns, a concurrent 空洞 void must be visible before the next RPC.
- In-flight step: wait for RPC return; on `VOIDED`, discard success; do not abort MINA session in ticket 01.

### Modules (logical)

- `plan`: `start-execution` becomes cursor + gRPC dispatch; still 失败即停作废; still handler gate.
- `common`: dispatch client (gRPC, mTLS); control-plane fake SSH adapter may remain for legacy tests only.
- 执行引擎 process: MINA adapter, fake adapter, credential decrypt, ExecuteStep service, `grpc.health.v1`.
- `curated`: credential **write** stays; production decrypt for SSH moves to the engine.
- `observed` / `agent`: heartbeat ingest unchanged (直连控制面).
- `conflict`: existing void-on-空洞 / 失联 / 升级 (A1) stay; this knife consumes `VOIDED` at the cursor.
- Frontend: **not** a Must.

### Persistence

- No new relationship-truth tables expected. Additive Flyway only if a column is truly required (default: none).
- Redis remains locks/queues/cache, not 关系真相 SSOT.

### Delivery

- New Compose service for the 执行引擎; image includes gRPC health probe client.
- Do not put model keys on the control plane or the 执行引擎.

---

## Testing Decisions

### What makes a good test

- Assert **external behavior** at the confirmed seams: HTTP status/`ApiResponse`/plan `VOIDED`/execution logs; 执行引擎 `grpc.health.v1` SERVING; gRPC without valid client cert rejected.
- Do **not** assert mapper internals, Redis key shapes, private call graphs, or protobuf field numbers as the definition of done.
- Drive `/implement` **one cycle at a time** (red → green → refactor) on the HTTP seam first; health + mTLS negative are part of ticket 01 Must, not a substitute for the handler story.

### Primary seam (confirmed)

- Control-plane public HTTP API, including Agent heartbeat/snapshot ingest.
- New tests’ fixture **must** include a running 执行引擎 (fake SSH on the engine, mTLS fixtures). `start-execution` must not fall back to control-plane production MINA.
- Prior art: `ControlledSshExecHttpAcceptanceTest`, `HeartbeatTimeoutHollowHttpAcceptanceTest`, `VerticalSliceHttpE2eAcceptanceTest`.

### Process seam (confirmed)

- Compose (or the same test fixture) : 执行引擎 `grpc.health.v1` SERVING with client cert.

### Narrow negative seam (confirmed)

- Call ExecuteStep (or equivalent gRPC) with missing/wrong client certificate → rejected.
- Not a general protobuf unit-test pyramid.

### HTTP tracer (happy path for this knife)

1. Setup via existing APIs: hosts A/B, container X, curated `运行于` A; snapshot X on B; 认领 → 已接受处理人; select 修实际; approve 操作计划 (≥2 SSH-bearing steps).
2. `POST start-execution` with 执行引擎 up (engine fake): all frozen steps succeed; GET plan `COMPLETED`; fake on **engine** recorded seq/action/`targetHostId`; control plane did not use production MINA.
3. Repeat setup; during EXECUTING (slow/scripted fake so there is a next step), expire heartbeat → 空洞 voids the plan; assert no further dispatch; GET plan `VOIDED` with existing hollow `voidReason`; `start-execution` on that id → `PLAN_VOIDED`.
4. Optional: 身份失联 void during EXECUTING uses the same stop/discard hook.
5. 空洞 after a scripted in-flight **success** still leaves plan `VOIDED` (success discarded).

### Negative / non-regression (minimum)

1. 执行引擎 not SERVING → `start-execution` fails; no control-plane production SSH.
2. No/wrong client cert to engine gRPC → rejected.
3. Non-handler / 待接受 cannot `start-execution`.
4. Legacy 竖切 / 规则诊断 HTTP suite still green with control-plane fake **without** requiring the 执行引擎.
5. Engine down is not a reason to skip 警告 or 选支 (决议 7).
6. Dispatch body has no plaintext secret (observable via engine fake recording / response; do not log secrets).

### Supporting doubles (not extra product APIs)

- 执行引擎 SSH fake for new tests.
- Control-plane SSH fake **only** for legacy tests.
- Self-signed mTLS fixtures.

### Modules under acceptance focus

- `plan` execution HTTP, 执行引擎 ExecuteStep + health, credential decrypt on engine, conflict void flags consumed at cursor.
- Frontend: not in automated definition of done.

---

## Out of Scope

- B6 连接工作台三档 / 工作台 SSH / 图结构工作台执行
- 把 WebClient / 模型密钥加回控制面；控制面进程内 LLM
- AI 编排层进程、模型出站白名单落点、逐步事件推给编排层
- B-live 现场状态读取 / 扩图诊断轮次
- 步骤断言 schema（CONTEXT 词保留；本刀 ExecuteStep 不含断言字段）
- 打断引擎当前 MINA 会话（Should / 后票）
- 整份冻结计划交给执行引擎内跑完（0044 已拒绝）
- 引擎直读操作计划表；编排层短时令牌/证书直连执行引擎
- 未绑定 10；改策展 07；重开 A1 实现（升级作废计划已闭合；本刀只消费 `VOIDED`）
- G2 时钟运营化；自我迭代；N² 可达；完整 xterm；多租户；JWT；Neo4j
- 薄 UI / Playwright
- 外接 CA、证书轮转产品、引擎专用只读数据库角色
- Vue / JPA 当地基 / Maven / LangChain / Redis 当关系真相 SSOT
- 改 `CONTEXT.md` / ADR-0039 / 0043 / **0044 正文**

---

## Further Notes

- **Issue tracker**: `.scratch/control-plane-executor/`（Spec 指针已放；工单待 `/to-tickets` 批准后写入 `issues/`）。禁止写入 `.scratch/unbound-identity-rebind/`、`.scratch/change-curated-draft/`、`.scratch/conflict-upgrade-void-plans/`。
- **ADR-0045**: [`docs/adr/0045-control-plane-executor-grpc.md`](../adr/0045-control-plane-executor-grpc.md) — 运输与谁可以调；字段表以本 Spec 为准。
- **Ticket 01 Must** = this Spec’s Must (grilling Q14-A). Do not ship an empty process skeleton as 01.
- **Next Matt step**: `/to-tickets`（先票清单 + 阻塞图，批准后再写 issues）。不要本对话 `/implement`。
- **Why a new ADR**: internal gRPC is hard to reverse, surprising next to public REST, and a real trade-off vs JSON HTTP / Redis queue. It does **not** reopen 0044 rejections.
