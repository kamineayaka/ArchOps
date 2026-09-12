# 01 — 步骤断言成真：引擎判定 + executionLog 详细结果

**What to build:** 已接受冲突处理人审过的修实际操作计划，每步带对工具结构化结果的预先约定（`expected`）。控制面代发时把该约定放入既有 ExecuteStep；执行引擎按约定判定本步成败（JSON 对象键值包含 **且** SSH/fake 退出成功），不是退出码单独说了算，也不是模型读输出。退出成功但结构化结果不匹配 → 计划 VOIDED，不得改步重试。逐步详细结果写入已有计划 `executionLog`（含 `structuredOutput`）。本票 = Spec 全部 Must。不要先交空 `expected` 字段骨架。不改 CONTEXT / ADR-0044 / 0045 正文。不立 ADR-0046。

**Blocked by:** （无）

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条 HTTP 测试。Spec：[`docs/specs/plan-step-assertion.md`](../../../docs/specs/plan-step-assertion.md)。合同：`CONTEXT.md`「操作计划」「步骤断言」「执行引擎」「控制面代发」；ADR-0044 决议 4（及决议 2 在本刀收缩后的落控制面）。

来源：`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md` **B3 剩余**（无步骤断言、无逐步事件；单步代发已由执行引擎 01 闭合）。用户 grilling 已钉切面 C、slug `plan-step-assertion`、票 01 = 整段 Must。**不要写入** `control-plane-executor` / unbound / 改策展 / A1 目录。

现码缺口：`PlanStep` 无 `expected`；ExecuteStep 无断言字段；引擎 `success` = SSH/fake 退出；fake 成功 stdout 写死、无法脚本化「退出成功但结构化结果不对」；`executionLog` 无 `structuredOutput`。

规则模板冻结约定（HTTP 可钉）：

```
SSH_PRECHECK         expected { "precheck": "passed" }
MIGRATE_CONTAINER    expected { "migrated": "true" }
REFRESH_OBSERVATION  expected { "refresh": "ok" }
```

引擎判定：`structured_output` 为 JSON 对象；每个 `expected` 键必须存在且值为相等字符串；多余键允许；不能解析为对象 → 步骤断言失败。`success` = 退出 ∧ 步骤断言（无/空 `expected` 则仍只看退出码）。`failure_reason` 步骤断言失败以 `STEP_ASSERTION_FAILED` 开头；SSH 失败不得用该 token。

- [x] 新生成修实际计划：GET 计划 `steps[]` 每步非空 `expected`（上表）；人审后 `start-execution` 经引擎 fake（默认 JSON 满足约定）→ `COMPLETED`；`executionLog` 每步带 `structuredOutput`
- [x] 引擎 fake **退出成功** + JSON **不匹配**某步 `expected` → 计划 `VOIDED`；该步 `failureReason` 以 `STEP_ASSERTION_FAILED` 开头；`executionLog` 含该步 `structuredOutput`；再 `start-execution` → `PLAN_VOIDED`（不得改步重试）
- [x] 退出成功 + `structured_output` 不是 JSON 对象 → `VOIDED`，同为 `STEP_ASSERTION_FAILED`
- [x] fake 退出失败 → `VOIDED` 为 SSH 失败；`failureReason` **不以** `STEP_ASSERTION_FAILED` 开头
- [x] 无 `expected` 字段的旧计划 / 既有代发夹具：仍只看退出码；`ExecutorSingleStepDispatchHttpAcceptanceTest` 等仍绿
- [x] 不回归：空洞 / 升级 / 失联 VOIDED 后停发下一步；规则诊断 → 选支 → 人审；竖切控制面 fake 不经引擎；Host Agent 仍直连控制面心跳；非处理人不能 `start-execution`
- [x] ExecuteStep 带上 `expected`（同一 RPC，不加第二运输）；`plan_id` 只关联；引擎不读操作计划表、不写真相；控制面按 `success` 作废、不代判约定
- [x] 不改 `CONTEXT.md` / ADR-0039 / 0043 / **0044 正文** / **0045 正文**；不立 ADR-0046；Compose 保留 executor、不启编排层；无薄 UI

**Out of this ticket:** AI 编排层进程 / stub / 真推送；B-live；工作台三档；打断 MINA / cancel API；模型判步或把 WebClient/密钥加回控制面；未绑定 10；改策展 07；重开 A1；往 `control-plane-executor` 加票 02；新 RPC；引擎直读计划表；Playwright；真 SSH 公网机。

## Comments

一次只做本票。票内 TDD 按 Spec HTTP tracer 圈：happy path（带 `expected` COMPLETED + log）→ 退出成功但不匹配 VOIDED → 非 JSON VOIDED → SSH 失败可区分 → 无 `expected` 回归。不要先交只加空字段的骨架。样板：`ExecutorSingleStepDispatchHttpAcceptanceTest`、`OperationPlanReviewHttpAcceptanceTest`、`ControlledSshExecHttpAcceptanceTest`。新测试夹具：引擎在测、`archops.ssh.mode=dispatch`、fake 可脚本化成功 stdout。

### Cycle 1 witnessed red (2026-09-12)

```text
cd backend && ./gradlew test --tests com.archops.plan.PlanStepAssertionHttpAcceptanceTest.approvedFixActualPlanCarriesExpectedAndCompletesWhenEngineJsonContainsIt
```

```text
PlanStepAssertionHttpAcceptanceTest > approvedFixActualPlanCarriesExpectedAndCompletesWhenEngineJsonContainsIt() FAILED
    java.lang.AssertionError: No value at JSON path "$.data.steps[0].expected.precheck"
        Caused by:
        com.jayway.jsonpath.PathNotFoundException: Missing property in path $['data']['steps'][0]['expected']
BUILD FAILED
```

GET 已审修实际计划没有 `steps[].expected`。无步骤断言字段、默认 fake stdout 仍是 `fake-ok {action}`。

### Cycle 1 green + refactor (2026-09-12)

Same test command: BUILD SUCCESSFUL. 规则模板写入冻结 `expected`；同一 ExecuteStep 加 `expected`；引擎退出成功后 JSON 对象包含匹配；默认 fake JSON 满足约定（允许多余键）；`executionLog.structuredOutput` 落控制面。Refactor：默认 stdout 方法改为 private。

### Cycle 2 witnessed red (2026-09-12)

```text
cd backend && ./gradlew test --tests com.archops.plan.PlanStepAssertionHttpAcceptanceTest.exitSuccessWithMismatchedJsonVoidsPlanAsStepAssertionFailedAndBlocksRetry
```

```text
> Task :compileTestJava FAILED
PlanStepAssertionHttpAcceptanceTest.java:107: error: cannot find symbol
        engine.fakeSsh().succeedWithStdout("SSH_PRECHECK", "{\"precheck\":\"failed\",\"source\":\"fake\"}");
                        ^
  symbol:   method succeedWithStdout(String,String)
BUILD FAILED
```

引擎 fake 无法脚本化「退出成功 + 任意 structured_output」。

### Cycle 2 green + refactor (2026-09-12)

Same test command: BUILD SUCCESSFUL. `succeedWithStdout` 独立于 `failActions`；退出成功但 JSON 不匹配 → `VOIDED`，`failureReason` 以 `STEP_ASSERTION_FAILED` 开头，再 start → `PLAN_VOIDED`。Refactor：hamcrest `startsWith` 静态导入。

### Cycle 3 reuse (2026-09-12)

```text
cd backend && ./gradlew test --tests com.archops.plan.PlanStepAssertionHttpAcceptanceTest.exitSuccessWithNonJsonStructuredOutputVoidsPlanAsStepAssertionFailed
```

First-run BUILD SUCCESSFUL（cycle 1 引擎判定已把非 JSON 对象写成 `STEP_ASSERTION_FAILED`）。显式 HTTP 断言保留。

### Cycle 4 reuse (2026-09-12)

```text
cd backend && ./gradlew test --tests com.archops.plan.PlanStepAssertionHttpAcceptanceTest.fakeExitFailureVoidsPlanAsSshFailureNotStepAssertion
```

First-run BUILD SUCCESSFUL（退出失败仍走 SSH `failure_reason`，不用 `STEP_ASSERTION_FAILED`）。显式区分两种失败。

### Cycle 5 reuse (2026-09-12)

```text
cd backend && ./gradlew test --tests com.archops.plan.PlanStepAssertionHttpAcceptanceTest.planJsonWithoutExpectedStillCompletesOnExitCodeOnly
```

First-run BUILD SUCCESSFUL（缺/空 `expected` 仍只看出码；脚本化非 JSON / 不匹配 stdout 仍 `COMPLETED`）。旧计划回归钉在 HTTP 接缝。

### Ticket-end suite + review (2026-09-12)

```text
cd backend && ./gradlew test
```

BUILD SUCCESSFUL；`tests=189 failures=0 errors=0 skipped=0`（含 `PlanStepAssertionHttpAcceptanceTest` 与 `ExecutorSingleStepDispatchHttpAcceptanceTest`）。

`/code-review` vs `origin/main`: Standards 无硬违规；Spec 无缺失/越界。本票闭合。
