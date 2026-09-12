# Spec: 步骤断言（引擎判定 + 逐步详细结果落控制面）

**Status**: spec published；工单 01 **ready-for-agent**（frontier）  
**Basis**: ADR-0044 决议 4（步骤断言由执行引擎判定）与决议 2 在本刀收缩后的「逐步详细结果落控制面」；ADR-0045（既有 ExecuteStep 加料，不改 0045 正文）；ADR-0043；`CONTEXT.md`「操作计划」「步骤断言」「执行引擎」「控制面代发」「AI 编排层」（执行期只观察）  
**Source**: [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md) **B3 剩余**（无步骤断言、无逐步事件。单步代发已由执行引擎 01 闭合）  
**Predecessor**: 竖切 / 改策展 / 未绑定 01–09 / 冲突升级作废活跃计划（A1）/ 控制面执行引擎 01 均已闭合。失败即停作废、禁止改步重试已在控制面。引擎 `success` 现等于 SSH/fake 退出。本刀关闭「退出 0 即 COMPLETED」窗口，并把逐步详细结果写入已有计划 `executionLog`。  
**Testing seams (confirmed)**: **主接缝 = 控制面公开 HTTP API**（`POST /api/operation-plans/{id}/start-execution` 与 `GET /api/operation-plans/{id}`）。新测试夹具含运行中的执行引擎（引擎侧 fake + mTLS；`archops.ssh.mode=dispatch`）。gRPC 字段随 HTTP 故事验收，不单独当定义完成。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**。薄 UI / Playwright 不进本刀自动化主接缝。不要真 SSH 公网机。

**Confirmed scope pins**

1. 不改 `CONTEXT.md`，不重开 ADR-0039 / 0043 / **0044 正文** / **0045 正文**。**不立 ADR-0046**。ExecuteStep 增步骤断言字段写进本 Spec。
2. 本刀 = 审计 B3 剩余：步骤断言成真 + 执行引擎判定 + 逐步详细结果落控制面。编排层进程 / B-live / 工作台三档 **Out of Scope**。不要往 `.scratch/control-plane-executor/` 加票 02。
3. 票 01 = 本 Spec 全部 Must。不要先交空 `expected` 字段骨架。
4. 主接缝仍是现有 `start-execution` 同步 HTTP；内部代发仍是既有 ExecuteStep gRPC（加字段，不新开 RPC）。
5. 无步骤断言字段的旧计划保持上一刀：只看 SSH/fake 退出码。新生成的修实际计划必须带非空 `expected`。

---

## Problem Statement

已接受冲突处理人可以人审操作计划并由控制面按游标代发给执行引擎，但已审每步仍没有对工具结构化结果的预先约定。引擎把 SSH/fake 退出成功写成 `success`，`structured_output` 只是 stdout。退出码为 0 时计划可以 COMPLETED，即便结构化结果并不是「应该看到什么」。现引擎 fake 成功路径固定返回 `fake-ok {action}`，无法单独脚本化「退出成功但结构化结果不对」。ADR-0044 决议 4 要求步骤断言由执行引擎判定，禁止模型阅读输出判本步成败。决议 2 的逐步详细事件在编排层尚不存在时，本刀只把详细结果落在控制面，供以后观察。

---

## Solution

已审操作计划每步带结构化步骤断言（与 `params` 并列的 `expected` 键值）。控制面代发时把该约定放入既有 ExecuteStep；`plan_id` 仍只做关联。执行引擎在 SSH/fake 退出成功之后，把 `structured_output` 解析为 JSON 对象并对 `expected` 做键值包含匹配：两者都成立才 `success=true`。失败即停，计划 VOIDED，不得改步重试。控制面按 `success` 作废，不再自己解约定；`failure_reason` 区分 SSH 失败与步骤断言失败。逐步详细结果写入已有计划 `executionLog`（含 `structured_output`）。本刀不起 AI 编排层、不向编排层推送、不 stub 观察者进程。

---

## User Stories

1. As an 已接受冲突处理人, after I approve a newly generated 修实际 操作计划, I want each frozen step to carry a structured 步骤断言 (`expected`), so that “what the tool result must contain” is part of the reviewed plan, not a sentence in `description`.
2. As an 已接受冲突处理人, when I `start-execution` and the 执行引擎 SSH/fake **exits successfully** but `structured_output` **does not contain** that step’s `expected` keys/values, I want the plan **VOIDED**, so that exit 0 cannot complete a step that missed its 步骤断言.
3. As an 已接受冲突处理人, I want that voided plan to reject another `start-execution` with `PLAN_VOIDED`, so that 失败即停作废 and 禁止改步重试 still hold.
4. As an 已接受冲突处理人, I want GET 操作计划 (and the `start-execution` response) `executionLog` to show that step’s `structured_output` and a `failureReason` that marks **步骤断言失败** (not SSH failure), so that I can see why the step failed without a second API.
5. As an 已接受冲突处理人, when every frozen step’s SSH/fake exits successfully **and** each `structured_output` JSON object contains that step’s `expected`, I want the plan **COMPLETED**, so that the happy path still works after 步骤断言 becomes real.
6. As an 已接受冲突处理人, on that happy path I want GET 操作计划 `executionLog` to include each step’s `structured_output`, so that 逐步详细结果 are visible on the control plane with no 编排层 process.
7. As an 已接受冲突处理人, when SSH/fake **exits successfully** but `structured_output` is **not a JSON object**, I want the plan VOIDED as 步骤断言失败, so that unstructured logs cannot satisfy 步骤断言.
8. As an 已接受冲突处理人, when SSH/fake **exits unsuccessfully**, I want the plan VOIDED as **SSH failure** (not 步骤断言失败), so that the two failure kinds stay distinguishable.
9. As QA, I want a plan JSON **without** an `expected` field to keep the previous knife’s rule (success = SSH/fake exit only), so that 竖切 and 执行引擎 01 HTTP tests stay green.
10. As QA, I want newly generated 修实际 plans from the rules template to **always** include a non-empty `expected` on every step, so that Q8’s “new plans must carry 步骤断言” is true without waiting for an 编排层.
11. As the 控制面, I want to put that step’s `expected` on the existing ExecuteStep request, so that the 执行引擎 can judge without reading 操作计划 rows.
12. As the 执行引擎, I must not `SELECT` 操作计划 rows; `plan_id` remains correlation only, so that the cursor stays in the 控制面 (ADR-0045).
13. As the 执行引擎, I want `success=true` only when SSH/fake exit succeeded **and** (if `expected` is present and non-empty) every expected key exists in the parsed JSON object with an equal string value, so that 步骤断言 is containment of 工具结构化结果, not substring search of logs, and not exact whole-stdout equality.
14. As the 执行引擎, I want extra keys in `structured_output` to be allowed, so that tools may return more than the frozen 步骤断言 asks for.
15. As the 执行引擎, I want a JSON value that is not a string (or does not equal the expected string) to fail 步骤断言, so that type-coercion does not silently pass.
16. As the 控制面, I want to keep using ExecuteStep `success` as “exit ∧ 步骤断言” and **not** add `assertion_ok`, so that I void on `!success` without re-interpreting `expected`.
17. As the 控制面, I want `failure_reason` to carry a stable token `STEP_ASSERTION_FAILED` when 步骤断言 fails, and not to use that token for SSH failures, so that HTTP tests can distinguish the two.
18. As a 冲突处理人, I want plan `voidReason` to use that same step failure text when 步骤断言 fails, matching today’s SSH-void pattern, so that GET 计划 is enough.
19. As the rules engine (决议 7), I want `buildFixActualSteps` to write the frozen `expected` maps for `SSH_PRECHECK` / `MIGRATE_CONTAINER` / `REFRESH_OBSERVATION`, so that 警告→选支→人审→执行 still produces an executable plan with 步骤断言 while no 编排层 exists.
20. As the 执行引擎, I must not treat “观测已对齐” or “策展已改” as 步骤断言; 步骤断言 only constrains 工具结构化结果, so that the engine never becomes a 真相 author.
21. As QA, I want the 执行引擎 fake to script **exit success + chosen `structured_output`** independently of `failActions`, so that the main story is testable without a public host.
22. As QA, I want the default fake success stdout for an action that has `expected` to be a JSON object that **satisfies** that step’s `expected`, so that the happy path does not need a one-off override.
23. As QA, I want existing `ExecutorSingleStepDispatchHttpAcceptanceTest` (and sibling dispatch/legacy tests) to stay green, so that adding 步骤断言 does not break 单步代发.
24. As QA, I want 空洞 / 升级 / 失联 VOIDED-then-stop-next-step tests to stay green, so that this knife does not reopen A1 or the previous knife’s cursor.
25. As QA, I want a VOIDED-for-步骤断言 plan to still refuse in-place retry (`PLAN_VOIDED`), so that frozen-plan discipline does not split by failure kind.
26. As any viewer, I want GET 操作计划 `steps[]` to expose each step’s `expected` after generation/review, so that the frozen 步骤断言 is auditable on the public HTTP envelope (still JSON, not protobuf).
27. As a non-handler, I still want `start-execution` denied, so that only the 已接受冲突处理人 runs plans.
28. As the 控制面, I do not want a new handler-facing API for 步骤断言 or 逐步详细结果, so that the public REST envelope stays `start-execution` / GET 计划.
29. As Compose, I want the 执行引擎 service to remain and **no** AI 编排层 service in this knife, so that we do not ship an empty capability process.
30. As the future AI 编排层, I want this knife’s persisted `executionLog` to be the observation surface I will read later; I do not need a stub inbox or a push now, so that 「推送」this knife equals 控制面已持久化.
31. As product, I do not want 逐步详细结果 written as 冲突事件 (`WARNED` / `PLAN_VOIDED` / …), so that 协作生命周期 stays separate from 工具逐步结果.
32. As product, I do not want a new table or 编排层 inbox for this knife, so that we do not invent a second SSOT beside plan `executionLog`.
33. As security review, I want ExecuteStep to still forbid plaintext host secrets and 业务库/客户/订单/财务 payloads, so that 0041 禁载荷 still holds on the additive fields.
34. As the 执行引擎, I must not write 策展 / 观测 / 冲突 tables, so that 步骤断言 never becomes a side-channel to 真相.
35. As Host Agent, I still want to POST `/api/agent/heartbeat` straight to the 控制面, so that 心跳 does not detour through the 执行引擎 or an 编排层.
36. As product, I want 连接工作台, B-live, and interrupting an in-flight MINA session to remain out of this knife, so that this slice stays 步骤断言.
37. As product, I want no model reading of stdout to judge the step, and no LLM keys on the 控制面, so that ADR-0044 rejections stay closed.
38. As an implementer, I want to add fields on the existing ExecuteStep messages rather than a second RPC, so that ADR-0045 transport is not forked.
39. As an implementer, I want missing/null/`expected` empty to mean “exit-code-only” (previous knife), so that old frozen JSON and legacy fake paths do not VOIDED for lack of 步骤断言.
40. As CI for **legacy** 竖切 HTTP tests, I want control-plane `archops.ssh.mode=fake` suites to stay green without requiring the 执行引擎, so that 决议 7 does not break.
41. As CI for **this knife’s new** tests, I want `start-execution` to go through gRPC dispatch to an 执行引擎 whose fake can return mismatched JSON at exit 0, so that we prove 引擎判定 without a public host.
42. As production, I want `mina` as production SSH to remain only on the 执行引擎, so that the 控制面 does not grow in-process SSH while adding 步骤断言.
43. As QA, I want protobuf field numbers / proto unit tests **not** to be the definition of done, so that the HTTP story remains the seam.
44. As QA, I want no Playwright and no thin UI Must, so that this knife is not a frontend slice.

---

## Implementation Decisions

### Contract

- Implement ADR-0044 决议 4. Do not edit `CONTEXT.md` or ADR-0039 / 0043 / 0044 / 0045 bodies. Do not open ADR-0046. Additive ExecuteStep fields live in this spec.
- 步骤断言 is the frozen `expected` map on each 已审 `PlanStep`. Do not put the convention in `description`. Do not smuggle it as reserved keys inside `params`. Do not store it in a side table the engine would have to load.

### `PlanStep` / HTTP envelope

- Public `PlanStep` gains `expected`: `map<string,string>` (JSON object of string values), alongside `seq` / `action` / `description` / `params`.
- Missing, null, or empty `expected` → previous knife: `success` follows SSH/fake exit only.
- Newly generated 修实际 steps **must** each have a non-empty `expected`.
- Frozen rule-template maps (决议 7; exact pairs for HTTP to pin against):

```
SSH_PRECHECK         expected { "precheck": "passed" }
MIGRATE_CONTAINER    expected { "migrated": "true" }
REFRESH_OBSERVATION  expected { "refresh": "ok" }
```

- GET 操作计划 returns `steps[].expected`. `start-execution` / GET `executionLog` remain the step-result surface.

### ExecuteStep (ADR-0045 additive; same RPC)

```
ExecuteStepRequest {
  plan_id          // correlation only
  step_seq
  action
  params
  target_host_id
  expected         // map<string,string>; empty/absent = exit-code-only
}

ExecuteStepResponse {
  step_seq
  success          // exit ∧ 步骤断言 (when expected non-empty); else exit only
  structured_output
  failure_reason   // SSH failure text, or STEP_ASSERTION_FAILED[…]
}
```

- Do not add `assertion_ok`. Do not add a second RPC. Do not put plaintext secrets or a full step list on the wire.

### Engine judgment

After `sshPort.exec`:

1. If exit failed → `success=false`; `failure_reason` is the SSH/fake reason (must **not** be `STEP_ASSERTION_FAILED`); `structured_output` may still carry stdout.
2. If exit succeeded and `expected` is missing/empty → `success=true` (previous knife).
3. If exit succeeded and `expected` is non-empty → parse `structured_output` as a JSON **object**.
   - Not an object / not JSON → `success=false`, `failure_reason` starts with `STEP_ASSERTION_FAILED`.
   - For every expected key: the object must contain that key; the JSON value must be a string equal to the expected value. Extra keys allowed. Any miss → `success=false`, `failure_reason` starts with `STEP_ASSERTION_FAILED`.
4. Both exit success and containment success → `success=true`.

Forbidden: LLM/model reading stdout; 控制面 re-evaluating `expected` after a successful engine `success`; engine `SELECT` 操作计划; treating 观测/策展 table state as the 步骤断言.

### `executionLog` (C: 逐步详细结果落控制面)

- Extend the existing plan `executionLog` JSON (no new table, not 冲突事件, not an 编排层 inbox).
- Each log line keeps `seq` / `action` / `hostId` / `command` / `success` / `failureReason` and **adds** `structuredOutput`.
- Persistence of that log on the 控制面 **is** this knife’s 「推送」. Do not stub an outbound consumer.

### `start-execution` loop

- Unchanged shape: lock → one ExecuteStep → apply `success` → re-read VOIDED / 空洞 / 失联 / 升级 → next step or stop.
- `!success` still VOIDED immediately (now includes 步骤断言失败). Do not rewrite frozen steps. Do not add a cancel API. Do not interrupt in-flight MINA.

### Who writes `expected`

- This knife: rules template that already builds 修实际 steps. Not the 编排层. Not a human-edited extra review field beyond approving the plan that already contains `expected`.

### Fake

- Engine fake must script exit success with an arbitrary `structured_output` string (JSON or not), independent of `failActions`.
- Default success stdout for the three template actions must be JSON objects that satisfy the frozen `expected` maps above (extra keys allowed).
- Legacy control-plane fake remains for legacy HTTP tests only.

### Persistence / delivery

- Prefer additive fields inside existing `stepsJson` / `executionLogJson`. Flyway only if a real new column is required (default: none). Redis is not 关系真相 SSOT.
- Compose: keep `postgres` / `redis` / `archops` / `executor`. Do not add an AI 编排层 service. Host Agent stays out of default Compose.

### Modules (logical)

- `plan`: `PlanStep.expected`; `start-execution` copies `expected` onto ExecuteStep; writes `structuredOutput` into `executionLog`; rules template writes frozen maps; still 失败即停作废.
- 执行引擎: parse JSON; containment match; `STEP_ASSERTION_FAILED`; scriptable fake stdout.
- 控制面 dispatch client: pass `expected` on the existing RPC.
- `conflict` / `observed` / `agent`: consume existing VOIDED flags only; heartbeat still hits the 控制面.
- Frontend: **not** a Must.

---

## Testing Decisions

### What makes a good test

- Assert **external behavior** at the confirmed HTTP seam: status / `ApiResponse` / plan `COMPLETED` or `VOIDED` / `PLAN_VOIDED` / `executionLog` (`success`, `failureReason`, `structuredOutput`) / `steps[].expected`.
- Do **not** treat protobuf field numbers, mapper internals, Redis key shapes, or private call graphs as the definition of done.
- Drive `/implement` **one cycle at a time** (red → green → refactor) on the HTTP seam. This conversation does not write tests.

### Primary seam (confirmed)

- Control-plane public HTTP: `POST /api/operation-plans/{id}/start-execution` and `GET /api/operation-plans/{id}`.
- New tests’ fixture **must** include a running 执行引擎 (engine fake, mTLS). `start-execution` must not fall back to control-plane production MINA.
- Prior art: `ExecutorSingleStepDispatchHttpAcceptanceTest`, `ControlledSshExecHttpAcceptanceTest`, `OperationPlanReviewHttpAcceptanceTest`.

### Not a definition-of-done seam

- ExecuteStep proto unit tests / field-number assertions.
- Playwright / thin UI.
- New `grpc.health.v1` or mTLS-negative cases (already closed on 执行引擎 01; must not regress).
- An 编排层 consumer.

### HTTP tracer (happy path + main story)

1. Setup via existing APIs: hosts A/B, container X, curated `运行于` A; snapshot X on B; 认领 → 已接受处理人; select 修实际; approve 操作计划 (rules template, three steps with frozen `expected`).
2. `POST start-execution` with 执行引擎 up (default fake JSON satisfies `expected`): plan `COMPLETED`; GET plan `steps[].expected` present and non-empty; `executionLog` has `structuredOutput` JSON for each step.
3. Repeat setup; script engine fake **exit success** + JSON that **omits or mismatches** the first (or a chosen) step’s `expected` → `VOIDED`; `executionLog` shows `structuredOutput` and `failureReason` starting with `STEP_ASSERTION_FAILED`; second `start-execution` → `PLAN_VOIDED`.
4. Repeat setup; script exit success + **non-JSON** `structured_output` → `VOIDED` as 步骤断言失败 (`STEP_ASSERTION_FAILED`).
5. Repeat setup; `failActions` (exit failure) → `VOIDED` as SSH failure; `failureReason` does **not** start with `STEP_ASSERTION_FAILED`.

### Negative / non-regression (minimum)

1. Existing `ExecutorSingleStepDispatchHttpAcceptanceTest` and dispatch/engine-down siblings still green.
2. Legacy 竖切 / 规则诊断 HTTP suite still green with control-plane fake **without** requiring the 执行引擎.
3. 空洞 / 升级 / 失联 stop-dispatch tests still green (regression, not a new story).
4. Non-handler / 待接受 still cannot `start-execution`.
5. Engine down is not a reason to skip 警告 or 选支 (决议 7).

### Supporting doubles (not extra product APIs)

- 执行引擎 SSH fake with scriptable success stdout (this knife).
- Control-plane SSH fake **only** for legacy tests.

### Modules under acceptance focus

- `plan` HTTP + rules template `expected`; 执行引擎 ExecuteStep judgment + fake; dispatch client field pass-through.
- Frontend: not in automated definition of done.

---

## Out of Scope

- AI 编排层进程 / stub 编排层 / 向编排层真推送 / 编排层直连执行引擎
- B-live（决议 5）
- 连接工作台三档（审计 B6）/ 工作台 SSH
- 打断在途 MINA 会话；新开 cancel API
- 把 WebClient / 模型密钥加回控制面；控制面进程内 LLM；模型阅读输出判本步成败
- 未绑定 10；改策展 07；重开 A1 实现；往 `.scratch/control-plane-executor/` 加票 02
- 新 RPC 替代 ExecuteStep；引擎直读操作计划表；整份计划交给引擎内跑完
- 用 B-live 阅读代替步骤断言
- G2 时钟运营化；自我迭代；N² 可达；完整 xterm；多租户；JWT；Neo4j
- 薄 UI / Playwright / 真 SSH 公网机
- 改 `CONTEXT.md` / ADR-0039 / 0043 / **0044 正文** / **0045 正文**；新开 ADR-0046
- Vue / JPA 当地基 / Maven / LangChain / Redis 当关系真相 SSOT

---

## Further Notes

- **Issue tracker**: [`.scratch/plan-step-assertion/issues/`](../../.scratch/plan-step-assertion/issues/)（票 01 `ready-for-agent`；禁止写入 `control-plane-executor` / `unbound-identity-rebind` / `change-curated-draft` / `conflict-upgrade-void-plans`）。
- **Predecessor spec**: [`docs/specs/control-plane-executor.md`](control-plane-executor.md) listed 步骤断言 schema as Out of Scope; that was the previous knife’s boundary, not a contract rejection.
- **Why no new ADR**: 0044 already froze 引擎判定 and rejected 模型判步; 0045 already froze ExecuteStep. This knife fills omitted fields in a Spec.
- **Next Matt step**: frontier = 票 01。新对话 `/implement` `/tdd`。不要自动做编排层 / B-live / 工作台。不要往 `control-plane-executor` 加票 02。
