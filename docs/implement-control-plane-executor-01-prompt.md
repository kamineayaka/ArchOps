# 新对话：控制面执行引擎票 01（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`
- `tdd` — `.cursor/skills/tdd/SKILL.md`；本票是 **capability**（必须 witnessed red）；不要走 Suite / tracer，也不要走 UI and helpers
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。本票实现 ADR-0044 **B 第一刀**（执行引擎成真 + 单步代发 + MINA/凭证迁出）。循环纪律以 [`docs/agents/tdd.md`](agents/tdd.md) 为准。**不改** `CONTEXT.md` / ADR-0044 正文。运输以 **ADR-0045** 为准。

Matt 位置：竖切 / 改策展 / 未绑定 01–09 / A1 **已闭合**。本对话只 `/implement` **control-plane-executor frontier = 01**。不要发明未绑定 10，不要做编排层 / B-live / 工作台 / 断言 schema。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现 control-plane-executor frontier 工单 01（执行引擎成真：单步 gRPC 代发 + MINA/凭证迁出）。质量优先：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。本票是 capability 票，不是 suite/tracer，不是 UI。禁止删除已闭合竖切 / 改策展 / 未绑定 / A1 生产来装红灯。禁止先交只探活、不迁 MINA 的空骨架当作完成。

加载并遵守：
- AGENTS.md（frontier = control-plane-executor 01）
- implement skill、tdd skill、docs/agents/tdd.md（capability：必须 witnessed red）
- docs/agents/domain.md（禁止静默改 CONTEXT.md / 已有 ADR 正文；可实现已发布的 ADR-0045）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、运输、是否拆 health/mTLS。下面已钉死。不要用 Playwright 或真 SSH 公网机当完成定义。不要发明未绑定 10。不要起 AI 编排层。不要把 WebClient/模型密钥加回控制面。

================================================================================
0. 任务边界
================================================================================

工单（唯一验收清单）：
.scratch/control-plane-executor/issues/01-executor-single-step-dispatch.md

Spec：docs/specs/control-plane-executor.md
ADR：docs/adr/0044-control-plane-hub-executor-and-ai-orchestrator.md（实现，不改正文）
     docs/adr/0045-control-plane-executor-grpc.md（gRPC / health / 仅控制面 mTLS）

一句话交付：处理人 start-execution 时控制面逐步 gRPC 代发到独立执行引擎（引擎持凭证 + MINA）；空洞作废后停发下一步并丢弃在途成功；Compose 引擎 grpc.health.v1 SERVING；非控制面证书拒绝。

本票不做：打断 MINA 会话；步骤断言 schema；编排层；B-live；工作台；整份计划交给引擎；引擎读计划表；LLM 加回控制面。

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。竖切 Spec 里「单进程 MINA / 密钥在控制面」是沉积误报，不得用来否定 0044/0045。

================================================================================
1. 先读
================================================================================

1. AGENTS.md、docs/agents/tdd.md
2. 工单 01 与 docs/specs/control-plane-executor.md
3. ADR-0044、ADR-0045
4. CONTEXT.md「操作计划」「执行引擎」「控制面代发」「心跳」
5. docs/dev-handoff.md（frontier = control-plane-executor 01）
6. 样板：OperationPlanService.startExecution、MinaSshPort / ControlledSshPort、RecordingFakeSshPort、HostSshCredentialService、HeartbeatTimeoutHollowHttpAcceptanceTest、ControlledSshExecHttpAcceptanceTest、VerticalSliceHttpE2eAcceptanceTest、deploy/compose/compose.yaml

接缝：主 = 控制面 HTTP；进程 = grpc.health.v1；窄负面 = 无/错客户端证书。CI 新测试经代发打到引擎 fake；旧竖切可继续控制面 fake。

================================================================================
2. TDD 循环
================================================================================

一圈一条测试。建议顺序（可按红灯调整，但不要把 health-only 当票完成）：
- start-execution 经代发打到引擎 fake（至少一步）且控制面生产 MINA 未用
- 多步 + 空洞停发下一步 + VOIDED
- 在途成功丢弃
- 引擎 health SERVING；无/错证书拒绝；引擎 down 不回退控制面 MINA
- 凭证不在代发包；既有规则诊断/竖切 fake 不回归

每圈：witnessed red → 最小生产 → 同测绿灯 → refactor → 提交。票末 ./gradlew test + /code-review。

禁止：空骨架先合；把整份计划一次丢给引擎；扩大控制面生产直连 SSH；改 CONTEXT / 0044 正文。
```
