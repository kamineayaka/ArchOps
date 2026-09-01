# 01 — 执行引擎成真：单步 gRPC 代发 + MINA/凭证迁出

**What to build:** 已接受冲突处理人仍打控制面 `start-execution`。控制面按游标 **一次向执行引擎 gRPC 下发一步**（ADR-0045）；执行引擎（独立进程，Java 21 + MINA）持主机凭证、连图内物理主机。空洞（及既有身份失联 / 升级 `VOIDED` 旗标）后 **不再下发下一步**；在途步若已成功返回则 **丢弃成功、计划保持 `VOIDED`**。Compose 只加执行引擎；`grpc.health.v1` SERVING；mTLS 自签，非控制面证书拒绝。不把整份操作计划交给引擎内跑完。不改 CONTEXT / ADR-0044 正文。

**Blocked by:** （无）

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条测试（主接缝 HTTP；进程/mTLS 接缝见 Spec）。Spec：[`docs/specs/control-plane-executor.md`](../../../docs/specs/control-plane-executor.md)。合同：`CONTEXT.md`「操作计划」「执行引擎」「控制面代发」；ADR-0044 决议 1–3、7；ADR-0045。

来源：`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md` **B1–B3**（第一刀）。用户 grilling 已钉切面 B、slug `control-plane-executor`、票 01 = 整段 B Must。**不要写入** unbound / 改策展 / A1 目录。

现码缺口：Compose 仅 postgres/redis/archops；`MinaSshPort` 在控制面；`startExecution` 一次 Redis 锁内跑完整份计划，步间不重读 `VOIDED`。无执行引擎进程、无 gRPC ExecuteStep、无步骤断言（本票也不做断言 schema）。

- [x] Compose（或同形测试夹具）起执行引擎；`grpc.health.v1` 在自签客户端证书下为 SERVING
- [x] 已接受处理人 `POST .../start-execution`：逐步 gRPC 代发；引擎侧 fake 记录 seq/action/`targetHostId`；计划可 `COMPLETED`；**控制面生产 MINA 未执行**
- [x] 主机凭证由引擎解密；代发包无明文秘密；控制面代发路径不解密
- [x] 多步执行中心跳超时 → 观测空洞作废计划：不再下发下一步；GET 计划 `VOIDED`（既有 hollow `voidReason`）；对该 id `start-execution` → `PLAN_VOIDED`
- [x] 在途步脚本化成功返回时若计划已 `VOIDED`：丢弃成功，计划保持 `VOIDED`（不 `COMPLETED`）
- [x] 无/错客户端证书调引擎 gRPC → 拒绝；引擎 down / 非 SERVING → `start-execution` 失败且不回退控制面生产 SSH
- [x] 不回归：既有规则诊断 → 选支 → 人审 HTTP；竖切可用控制面 `archops.ssh.mode=fake` **不经引擎**；失败即停作废、禁止改步重试；Host Agent 仍 POST `/api/agent/heartbeat` 直连控制面
- [x] 不改 `CONTEXT.md` / ADR-0039 / 0043 / **0044 正文**；不把整份计划交给引擎；引擎不读操作计划表、不写策展/观测/冲突；无编排层进程；无薄 UI

**Out of this ticket:** 打断 MINA 会话；步骤断言 schema；逐步事件给编排层；AI 编排层 / 模型出站；B-live；工作台三档；未绑定 10；改策展 07；重开 A1；把 WebClient/密钥加回控制面；外接 CA；Playwright；真 SSH 公网机。

## Comments

开场 prompt：[`docs/implement-control-plane-executor-01-prompt.md`](../../../docs/implement-control-plane-executor-01-prompt.md)。一次只做本票。票内 TDD 按故事圈（夹具起引擎 → 一步代发 → 迁 MINA/凭证 → 空洞停发/丢弃 → health/mTLS 负面），不要先交只探活的空骨架。样板：`ControlledSshExecHttpAcceptanceTest`、`HeartbeatTimeoutHollowHttpAcceptanceTest`、`VerticalSliceHttpE2eAcceptanceTest`。

### Cycle 1 witnessed red (2026-09-01)

```text
cd backend && ./gradlew test --tests com.archops.plan.ExecutorSingleStepDispatchHttpAcceptanceTest.startExecutionDispatchesFrozenStepsToEngineFakeWithoutControlPlaneMina
```

```text
> Task :compileTestJava FAILED
ExecutorEngineHandle.java:22: error: cannot find symbol
        ConfigurableApplicationContext context = new SpringApplication(ExecutorApplication.class).run(
                                                                       ^
  symbol:   class ExecutorApplication
ExecutorEngineHandle.java:36: error: cannot find symbol
        return context.getBean(ExecutorGrpcServer.class).port();
                               ^
  symbol:   class ExecutorGrpcServer
BUILD FAILED
```

无执行引擎进程 / 无 gRPC ExecuteStep / `start-execution` 仍走控制面 in-process SSH。

### Cycle 1 green + refactor (2026-09-01)

Same test command: BUILD SUCCESSFUL. 控制面 `archops.ssh.mode=dispatch` 经 gRPC ExecuteStep 打到独立引擎上下文的 fake；`MinaSshPort` 不在控制面。Refactor：`PlanStepCommands`、`ExecuteStepResult.from`、ExecuteStep 响应映射。

### Cycle 2 witnessed red (2026-09-01)

```text
cd backend && ./gradlew test --tests com.archops.plan.ExecutorSingleStepDispatchHttpAcceptanceTest.hollowDuringExecutionStopsNextDispatchAndLeavesPlanVoided
```

```text
ExecutorSingleStepDispatchHttpAcceptanceTest > hollowDuringExecutionStopsNextDispatchAndLeavesPlanVoided() FAILED
    java.lang.AssertionError at ExecutorSingleStepDispatchHttpAcceptanceTest.java:152
    assertThat(engine.recordedCalls()).hasSize(1);
BUILD FAILED
```

空洞作废发生在第 1 步 in-flight 时仍下发后续步（引擎 fake 记录 >1）。

### Cycle 2 green + refactor (2026-09-01)

Same test command: BUILD SUCCESSFUL. 步间 / RPC 返回后重读 `VOIDED`，停发下一步；COMPLETED 只 CAS `EXECUTING`。Refactor：`isVoided` / `stopBecauseAlreadyVoided`。

### Cycle 3 reuse (2026-09-01)

```text
cd backend && ./gradlew test --tests com.archops.plan.ExecutorSingleStepDispatchHttpAcceptanceTest.inFlightEngineSuccessAfterHollowIsDiscardedAndPlanStaysVoided
```

First-run BUILD SUCCESSFUL（cycle 2 的 VOIDED 重读已丢弃在途成功：引擎 fake 记 success，HTTP/GET 仍 `VOIDED` 且 `completedSteps=0`）。显式断言保留。Refactor：抽出 `startExecutionThenHollowWhileFirstStepBlocked`。

### Cycle 4 witnessed red (2026-09-01)

```text
cd backend && ./gradlew test --tests com.archops.executor.ExecutorGrpcHealthAcceptanceTest.healthCheckWithControlPlaneClientCertIsServing
```

```text
> Task :compileTestJava FAILED
ExecutorGrpcHealthAcceptanceTest.java:22: error: cannot find symbol
                            .keyManager(engine.mtls().clientCert().toFile(), ...
                                              ^
  symbol:   method mtls()
BUILD FAILED
```

无 mTLS 夹具 / 无 `grpc.health.v1` SERVING。

### Cycle 4 green + refactor (2026-09-01)

Same test command: BUILD SUCCESSFUL. 引擎 gRPC 要求客户端证书；health 为 SERVING。Compose 增加 executor 服务与 health probe。Refactor：`ExecutorEngineHandle.controlPlaneChannel()`。

### Cycle 5 reuse (2026-09-01)

```text
cd backend && ./gradlew test --tests com.archops.executor.ExecutorGrpcHealthAcceptanceTest.healthCheckWithoutClientCertificateIsRejected
cd backend && ./gradlew test --tests com.archops.executor.ExecutorGrpcHealthAcceptanceTest.healthCheckWithWrongClientCertificateIsRejected
```

First-run BUILD SUCCESSFUL（reuse of `ClientAuth.REQUIRE` from cycle 4）。无证书 / 非 CA 客户端证书均 `StatusRuntimeException`。显式负面断言保留。

### Cycle 6 witnessed red (2026-09-01)

```text
cd backend && ./gradlew test --tests com.archops.plan.ExecutorDownHttpAcceptanceTest.startExecutionFailsWhenExecutorIsDownWithoutControlPlaneMina
```

```text
ExecutorDownHttpAcceptanceTest > startExecutionFailsWhenExecutorIsDownWithoutControlPlaneMina() FAILED
    java.lang.AssertionError at ExecutorDownHttpAcceptanceTest.java:58
    status().isBadRequest()  // start-execution 把引擎 down 当成步失败并 200 VOIDED
BUILD FAILED
```

### Cycle 6 green + refactor (2026-09-01)

Same test command: BUILD SUCCESSFUL. 下发前 `grpc.health.v1` 必须 SERVING，否则 `EXECUTOR_UNAVAILABLE`，计划仍 `APPROVED`，控制面无 `MinaSshPort`。

### Cycle 7 witnessed red (2026-09-01)

```text
cd backend && ./gradlew test --tests com.archops.plan.ExecutorSingleStepDispatchHttpAcceptanceTest.startExecutionFailsWhenEngineHasNoHostCredentialToDecrypt
```

```text
ExecutorSingleStepDispatchHttpAcceptanceTest > startExecutionFailsWhenEngineHasNoHostCredentialToDecrypt() FAILED
    java.lang.AssertionError at ExecutorSingleStepDispatchHttpAcceptanceTest.java:121
    Expected VOIDED / SSH credential, but start-execution still COMPLETED without decrypting host ciphertext
BUILD FAILED
```

### Cycle 7 green + refactor (2026-09-01)

Same test command: BUILD SUCCESSFUL. 引擎 `requireDecrypted(targetHostId)`；代发包/SSH 记录不含明文 secret。测试夹具与控制面共享 DataSource。Compose 引擎 `ARCHOPS_SSH_MODE=mina`。

### Cycle 8 reuse/regression (2026-09-01)

```text
cd backend && ./gradlew test \
  --tests com.archops.plan.ControlledSshExecHttpAcceptanceTest \
  --tests com.archops.slice.VerticalSliceHttpE2eAcceptanceTest \
  --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest \
  --tests com.archops.plan.OperationPlanReviewHttpAcceptanceTest \
  --tests com.archops.observed.ObservedHeartbeatHttpAcceptanceTest
```

```text
ControlledSshExecHttpAcceptanceTest: tests=6 failures=0
VerticalSliceHttpE2eAcceptanceTest: tests=4 failures=0
ConflictDiagnosisHttpAcceptanceTest: tests=9 failures=0
OperationPlanReviewHttpAcceptanceTest: tests=2 failures=0
ObservedHeartbeatHttpAcceptanceTest: tests=4 failures=0
BUILD SUCCESSFUL
```

First-run green（reuse of control-plane `archops.ssh.mode=fake` / 决议 7）. 规则诊断 → 选支 → 人审；竖切 fake 不经引擎；失败即停作废；Host Agent `POST /api/agent/heartbeat` 仍直连控制面。无生产改动。

### Ticket-end suite (2026-09-01)

```text
cd backend && ./gradlew cleanTest test
```

BUILD SUCCESSFUL：184 tests, 0 failures。无新 Flyway。未改 `CONTEXT.md` / ADR-0039 / 0043 / **0044 正文**。Review 后补：`MinaSshPort` 不再是控制面 `@Component`；引擎 MapperScan 排除草案/事实 mapper；Compose 把控制面客户端证书 ENV 钉在 `archops`（引擎探活按 ADR-0045 显式自带）。

### Code-review (Standards + Spec, vs origin/main)

**Standards:** 1 hard finding fixed — engine `@MapperScan("com.archops.curated.mapper")` loaded draft/fact mappers + hub still scanned `MinaSshPort`. Now: exclude draft/fact mappers; `MinaSshPort` is engine-`@Import` only. Judgement left: duplicated HTTP fixtures in dispatch/down tests; `requireDecrypted` then MINA decrypts again; hub client imports `ExecutorMtls`; `HostSshCredentialService.upsert` is imported on the engine but ExecuteStep only calls `requireDecrypted`.

**Spec:** no remaining product gap vs ticket Must. Between-step stop consumes the `VOIDED` flag (空洞 / 失联 / A1 升级 all write it). Client cert files still live at `/mtls` in the shared image (self-signed fixtures); Compose no longer defaults the hub client key as image-wide ENV. No 编排层 process, no thin UI, no CONTEXT/0044 body edits.
