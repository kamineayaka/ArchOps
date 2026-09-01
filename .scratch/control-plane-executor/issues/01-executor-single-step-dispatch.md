# 01 — 执行引擎成真：单步 gRPC 代发 + MINA/凭证迁出

**What to build:** 已接受冲突处理人仍打控制面 `start-execution`。控制面按游标 **一次向执行引擎 gRPC 下发一步**（ADR-0045）；执行引擎（独立进程，Java 21 + MINA）持主机凭证、连图内物理主机。空洞（及既有身份失联 / 升级 `VOIDED` 旗标）后 **不再下发下一步**；在途步若已成功返回则 **丢弃成功、计划保持 `VOIDED`**。Compose 只加执行引擎；`grpc.health.v1` SERVING；mTLS 自签，非控制面证书拒绝。不把整份操作计划交给引擎内跑完。不改 CONTEXT / ADR-0044 正文。

**Blocked by:** （无）

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条测试（主接缝 HTTP；进程/mTLS 接缝见 Spec）。Spec：[`docs/specs/control-plane-executor.md`](../../../docs/specs/control-plane-executor.md)。合同：`CONTEXT.md`「操作计划」「执行引擎」「控制面代发」；ADR-0044 决议 1–3、7；ADR-0045。

来源：`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md` **B1–B3**（第一刀）。用户 grilling 已钉切面 B、slug `control-plane-executor`、票 01 = 整段 B Must。**不要写入** unbound / 改策展 / A1 目录。

现码缺口：Compose 仅 postgres/redis/archops；`MinaSshPort` 在控制面；`startExecution` 一次 Redis 锁内跑完整份计划，步间不重读 `VOIDED`。无执行引擎进程、无 gRPC ExecuteStep、无步骤断言（本票也不做断言 schema）。

- [ ] Compose（或同形测试夹具）起执行引擎；`grpc.health.v1` 在自签客户端证书下为 SERVING
- [ ] 已接受处理人 `POST .../start-execution`：逐步 gRPC 代发；引擎侧 fake 记录 seq/action/`targetHostId`；计划可 `COMPLETED`；**控制面生产 MINA 未执行**
- [ ] 主机凭证由引擎解密；代发包无明文秘密；控制面代发路径不解密
- [ ] 多步执行中心跳超时 → 观测空洞作废计划：不再下发下一步；GET 计划 `VOIDED`（既有 hollow `voidReason`）；对该 id `start-execution` → `PLAN_VOIDED`
- [ ] 在途步脚本化成功返回时若计划已 `VOIDED`：丢弃成功，计划保持 `VOIDED`（不 `COMPLETED`）
- [ ] 无/错客户端证书调引擎 gRPC → 拒绝；引擎 down / 非 SERVING → `start-execution` 失败且不回退控制面生产 SSH
- [ ] 不回归：既有规则诊断 → 选支 → 人审 HTTP；竖切可用控制面 `archops.ssh.mode=fake` **不经引擎**；失败即停作废、禁止改步重试；Host Agent 仍 POST `/api/agent/heartbeat` 直连控制面
- [ ] 不改 `CONTEXT.md` / ADR-0039 / 0043 / **0044 正文**；不把整份计划交给引擎；引擎不读操作计划表、不写策展/观测/冲突；无编排层进程；无薄 UI

**Out of this ticket:** 打断 MINA 会话；步骤断言 schema；逐步事件给编排层；AI 编排层 / 模型出站；B-live；工作台三档；未绑定 10；改策展 07；重开 A1；把 WebClient/密钥加回控制面；外接 CA；Playwright；真 SSH 公网机。

## Comments

开场 prompt：[`docs/implement-control-plane-executor-01-prompt.md`](../../../docs/implement-control-plane-executor-01-prompt.md)。一次只做本票。票内 TDD 按故事圈（夹具起引擎 → 一步代发 → 迁 MINA/凭证 → 空洞停发/丢弃 → health/mTLS 负面），不要先交只探活的空骨架。样板：`ControlledSshExecHttpAcceptanceTest`、`HeartbeatTimeoutHollowHttpAcceptanceTest`、`VerticalSliceHttpE2eAcceptanceTest`。
