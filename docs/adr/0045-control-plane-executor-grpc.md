---
status: accepted; 修订 ADR-0044 未写明的「控制面→执行引擎」运输，不改 0044 八条决议与拒绝项
---

# 控制面 → 执行引擎：gRPC + Protobuf；仅控制面可调；健康口为 grpc.health.v1

ADR-0044 规定控制面一次向执行引擎下发一步、引擎不读计划表、编排层不得持引擎长凭证，但未冻运输。公开 API 仍是 ADR-0043 的 REST `/api/...`。内部单步代发选用 **gRPC + Protobuf**（相对 JSON HTTP：强类型、与后续逐步事件同族；相对 Redis 队列：对准同步 `start-execution` 循环，停发=不发下一枪）。执行引擎就绪探活使用标准 **`grpc.health.v1`**。调用方仅控制面：本刀 **mTLS**（Compose/CI 自签；外接 CA Later）；非控制面客户端证书一律拒绝。AI 编排层以后不得持有该客户端证书。

## 考虑过而拒绝的

- 同步 HTTP + JSON：与现有 REST/探活同形、本刀更窄；未选，以免内部契约与公开 REST 混成一种「看起来随便 curl 就能调引擎」的口子，并给后续逐步事件留同族运输。
- Redis 队列 / 引擎自己拉活：诊断作业已用 Redis 异步队列；操作计划执行是同步代发。活一入队，空洞/升级无法「立刻停止下发」。
- 引擎直读操作计划表 / PostgreSQL `SKIP LOCKED`：把游标漏出权力中心。
- Temporal 等第二序列器：与控制面枢纽冲突。
- 本刀外接 CA / 测试明文生产才 mTLS：前者过宽；后者 CI 盖不住「仅控制面可调」。

## 后果

- 不改 CONTEXT / ADR-0039 / 0043 / **0044 正文**。步骤断言 schema、逐步事件给编排层、B-live、编排层进程仍按 0044 后置。
- Compose 探活须能说 gRPC（引擎镜像带探活客户端；mTLS 下探活也带客户端证书）。
- 控制面代发客户端走 gRPC，不是把模型 WebClient/密钥加回控制面。
- 实现见 Spec [`docs/specs/control-plane-executor.md`](../specs/control-plane-executor.md)。
