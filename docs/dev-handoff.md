# 新对话接手指南

领域合同（ADR-0039）与技术栈（ADR-0043）已冻结；运行时拓扑 **ADR-0044**（控制面枢纽 / 执行引擎 / AI 编排层）；控制面→执行引擎运输 **ADR-0045**（gRPC）。  
**Cloud / 任意 Agent 必读根目录 [`AGENTS.md`](../AGENTS.md)**（及 [`CLAUDE.md`](../CLAUDE.md)）。  
**脚手架已按 ADR-0043 重建**：`backend/`、`frontend/`、`agent/`、`deploy/`、根 `Dockerfile` 为可启动最小骨架；竖切按工单推进。

## 必读

1. `CONTEXT.md`
2. `docs/adr/0039` … `0045`（栈 **0043**，进程切分 **0044**，控制面→执行引擎运输 **0045**）
3. `docs/mvp-vertical-slice.md`（竖切范围对照）
4. `docs/specs/vertical-slice-mvp.md`（竖切 Spec；01–13 已闭合）
5. `docs/specs/change-curated-draft.md`（改策展/草案逐条确认 Spec；**已闭合**）
6. `docs/specs/unbound-identity-rebind.md`（未绑定 / 身份失联重绑 Spec；**01–09 已闭合**）
7. `docs/specs/conflict-upgrade-void-plans.md`（冲突升级作废活跃计划；审计 A1；**01 TDD-done，本刀闭合**）
8. `docs/specs/control-plane-executor.md`（控制面执行引擎；审计 B 第一刀；**01 TDD-done，本刀闭合**）
9. `docs/agents/tdd.md`（`/implement` 的 TDD overlay：red → green → refactor）
10. `docs/scaffold-bootstrap-prompt.md`（脚手架专用；已完成后可作审计对照）
11. `.cursor/rules/project-map.mdc`

## 当前状态

| 项 | 状态 |
|---|---|
| Gradle + Spring Boot + MyBatis-Plus + Flyway | 有；至 V20（命中消费去掉草案→候选 FK；绑定记忆按策展对象唯一至 V19；未绑定逐条确认至 V18；改理想仍至 V15） |
| React + Ant Design 薄页 | **已完成**（票 12：冲突列表/详情→协作→选支→审计划→确认关闭） |
| Python agent 心跳+快照 stub + systemd 说明 | 有（契约见 `docs/contracts/agent-heartbeat-snapshot.md`） |
| Compose + `archops:latest` 多阶段镜像 | 有 |
| SSH 受控执行 | **已完成**（票 08） |
| 临时身份头 + 高级/一般角色门禁 | **已完成**（票 01） |
| 策展主机 / 容器 / `运行于` / 「应该在哪」 | **已完成**（票 02） |
| Agent 心跳+快照 → 观测 / 「实际在哪」 | **已完成**（票 03） |
| 冲突警告与合并键升级 | **已完成**（票 04） |
| 已知悉 + 认领/自任 → 已接受处理人 | **已完成**（票 05） |
| 异步诊断（规则）+ 可选 LLM + 敏感读拒绝 | **已完成**（票 06）。ADR-0044：控制面去掉进程内 LLM 出站，规则兜底保留；编排层出站另拆 |
| 修实际选支 + 操作计划人审 | **已完成**（票 07） |
| 观测对齐 → 待确认关闭 → 处理人确认 | **已完成**（票 09） |
| 心跳超时 → 空洞挂起并作废计划 | **已完成**（票 10） |
| 最小演示 UI | **已完成**（票 12） |
| HTTP 主接缝 E2E 验收（含负面） | **已完成**（票 13：`com.archops.slice.VerticalSliceHttpE2eAcceptanceTest`） |
| 指派 / 接受 / 拒绝 / 转让处理人 | **已完成**（票 11 Should：`ConflictAssignTransferHttpAcceptanceTest`） |
| 竖切 Spec | 已发布 → [`docs/specs/vertical-slice-mvp.md`](specs/vertical-slice-mvp.md) |
| 竖切工单 | 已本地发布 → [`.scratch/vertical-slice-mvp/issues/`](../.scratch/vertical-slice-mvp/issues/)（01–13 均 done；Flyway 至 V12） |
| 改策展草案 Spec | **已发布** → [`docs/specs/change-curated-draft.md`](specs/change-curated-draft.md) |
| 改策展草案工单 | **TDD-done 06 / 本刀闭合** → [`.scratch/change-curated-draft/issues/`](../.scratch/change-curated-draft/issues/)（**01–06 TDD-done**。从竖切 MVP 往上长，不重拆竖切 01–13） |
| 未绑定 / 身份失联 Spec | **已发布** → [`docs/specs/unbound-identity-rebind.md`](specs/unbound-identity-rebind.md) |
| 未绑定 / 身份失联工单 | **01–09 已闭合**（07 = 薄 UI；09 = 失联叠加心跳超时的问法） → [`.scratch/unbound-identity-rebind/issues/`](../.scratch/unbound-identity-rebind/issues/)（不要写进 `change-curated-draft`；不要发明未绑定 10） |
| 未绑定 01–03 合同审计 | **已出报告** → [`.scratch/unbound-identity-rebind/audit-01-03-opus.md`](../.scratch/unbound-identity-rebind/audit-01-03-opus.md)（三轴 + 探针表；票 08 已处置 C-4 / C-2 / S-3，票 04 已处置 C-3 / S-4 / S-2，票 09 已处置 C-1） |
| 代码 vs ADR-0044 只读审计 | **已出报告** → [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md)（A2 = 票 05；A3 = 票 09 已闭合；**A1 = conflict-upgrade-void-plans 01 TDD-done**；**B1–B3 = control-plane-executor 01 TDD-done**；B4/B5/B6 另开） |
| 冲突升级作废活跃计划 | **01 TDD-done / 本刀闭合** → [`docs/specs/conflict-upgrade-void-plans.md`](specs/conflict-upgrade-void-plans.md) / [`.scratch/conflict-upgrade-void-plans/issues/`](../.scratch/conflict-upgrade-void-plans/issues/)（审计 A1；不要写入未绑定目录） |
| 控制面执行引擎（0044 B 第一刀） | **01 TDD-done / 本刀闭合** → [`docs/specs/control-plane-executor.md`](specs/control-plane-executor.md) / [`.scratch/control-plane-executor/issues/01-executor-single-step-dispatch.md`](../.scratch/control-plane-executor/issues/01-executor-single-step-dispatch.md)（ADR-0045。不要写入 unbound / A1 目录；不要自动做编排层 / B-live / 工作台） |
| Matt 工作流 skills / tracker | **已入库**（`.cursor/skills/` + `.agents/skills/` + `docs/agents/`；TDD overlay [`docs/agents/tdd.md`](agents/tdd.md)；Cloud 不依赖本机 `~/.agents`） |
| 国内镜像默认 | **已合并**（PR #53：Gradle 腾讯云 / Maven 阿里云 / npm npmmirror / Docker DaoCloud） |
| kamiserver 人工验收 | **通过**（2026-08：Compose postgres+redis healthy 且宿主机端口已映射 → `./gradlew bootRun` → `GET /api/health`；竖切演示闭环已在该 VM 走通） |

## 下一对话建议

1. ~~竖切 Spec~~：已发布  
2. ~~拆票 / to-tickets~~：竖切已发布至 `.scratch/vertical-slice-mvp/issues/`（01–13）  
3. ~~主链 HTTP E2E（票 13）~~：已完成  
4. ~~指派/拒绝/转让（票 11 Should）~~：已完成  
5. ~~kamiserver 人工验收~~：已通过  
6. ~~竖切工单包已闭合 / 下一刀 to-spec~~：改策展 Spec 已发布  
7. ~~改策展 `/to-tickets`~~：已发布至 `.scratch/change-curated-draft/issues/`（01–06）  
8. ~~Agent 约束文档对齐 TDD~~：`docs/agents/tdd.md` + `/tdd` skill 为 **red → green → refactor**（本轮只改文档，未改业务代码）  
9. ~~`/implement` `/tdd` 票 01（TDD 重做）~~：已完成（关闭建底 POST 覆盖已有 `运行于`；witnessed red → green → refactor；`CURATED_RUNS_ON_EXISTS`）  
10. ~~`/implement` `/tdd` 票 02（TDD 重做）~~：已完成（诊断同时给出「修实际」与「改理想」分叉；witnessed red → green → refactor；`CHANGE_CURATED_TO_OBSERVED`）  
11. ~~`/implement` `/tdd` 票 03（TDD 重做）~~：已完成（选改理想生成开放草案；witnessed red → green → refactor；`CHANGE_CURATED` 出草案、不写策展、不出计划）  
12. ~~`/implement` `/tdd` 票 04~~：已完成（逐条确认：接受即写策展并立刻比对 → 待确认关闭；witnessed red → green → refactor；`DRAFT_ITEM_ACCEPTED` / `DRAFT_ITEM_REJECTED`）  
13. ~~`/implement` `/tdd` 票 05~~：已完成（升级/空洞作废未完成草案；待确认关闭后再漂同一合并键升级；witnessed red → green → refactor；`DRAFT_VOIDED`）  
14. ~~`/implement` `/tdd` 票 06~~：已完成（HTTP 主接缝有序 tracer；suite/tracer 循环；`ChangeCuratedDraftTracerHttpAcceptanceTest`）。**改策展刀闭合。**  
15. ~~`/grill-with-docs` 定下一刀~~：已定为 **未绑定观测候选 / 身份失联重绑**（A；HTTP only；不改合同）。  
16. ~~下一刀 `/to-spec`~~：已发布 [`docs/specs/unbound-identity-rebind.md`](specs/unbound-identity-rebind.md)。  
17. ~~下一刀 `/to-tickets`~~：已发布至 `.scratch/unbound-identity-rebind/issues/`（01–07）。  
18. ~~`/implement` `/tdd` 未绑定票 01~~：已完成（推断身份失联 + 未绑定 upsert + 规范问法 `IDENTITY_LOST` 读模型；witnessed red → green → refactor）。  
19. ~~`/implement` `/tdd` 未绑定票 02~~：已完成（从不挂冲突的候选发 OPEN 草案；规则夹具 PENDING 条目；witnessed red → green → refactor；`UNBOUND_CANDIDATE` origin）。  
20. ~~`/implement` `/tdd` 未绑定票 03~~：已完成（逐条确认：新建写入对象；绑定只记对应关系；witnessed red → green → refactor；`UNBOUND_CANDIDATE_CONSUMED` / bind memory）。  
21. ~~未绑定 01–03 合同审计~~：已完成（三轴只读审计；报告 [`.scratch/unbound-identity-rebind/audit-01-03-opus.md`](../.scratch/unbound-identity-rebind/audit-01-03-opus.md)；未改生产）。  
22. ~~`/implement` `/tdd` 未绑定票 08~~：已完成（绑定写入门禁：判据改「失联之后是否又标签命中」；同一策展对象只能是一个现场实体的本体；夹具给出未被绑的目标；witnessed red → green → refactor；`UNBOUND_BIND_TARGET_ALREADY_BOUND` / V19）。  
23. ~~`/implement` `/tdd` 未绑定票 04~~：已完成（标签命中收尾：清失联、消费候选与绑定记忆、作废相关未绑定草案、恢复升级链；witnessed red → green → refactor；审计 C-3 / S-4 / S-2；V20）。  
24. ~~`/implement` `/tdd` 未绑定票 05~~：已完成（失联闸门：冲突 GET `identityLost`；PENDING_CLOSE→OPEN；诊断不含修实际/改理想/空洞恢复通道集；选支 `IDENTITY_LOST_BLOCKS_BRANCH`；活跃计划与 OPEN CHANGE_CURATED 草案作废；未打标同名仍不新开冲突；清标后闸门解除。witnessed red → green → refactor）。  
25. ~~`/implement` `/tdd` 未绑定票 06~~：已完成（HTTP 主接缝有序 tracer；suite/tracer 循环；`UnboundIdentityRebindTracerHttpAcceptanceTest`）。  
26. ~~`/implement` `/tdd` 未绑定票 07~~：已完成（薄 UI：待并入列表 / 身份失联问法 / 不挂冲突的逐条确认；`npm run build` + Vite 冒烟）。本刀演示层闭合。  
27. ~~`/implement` `/tdd` 未绑定票 09~~：已完成（失联叠加心跳超时时问法仍须说出观测空洞；`availability=HOLLOW` + `identityLost=true`；审计 C-1 / 0044 审计 A3；witnessed red → green → refactor；`IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest`）。本刀问法读模型闭合。不要发明未绑定 10。  
28. ~~人排期开 A1~~：用户 2026-08-27 明示。已发布 Spec + 票 01 → [`docs/specs/conflict-upgrade-void-plans.md`](specs/conflict-upgrade-void-plans.md) / [`.scratch/conflict-upgrade-void-plans/issues/01-upgrade-voids-active-plans.md`](../.scratch/conflict-upgrade-void-plans/issues/01-upgrade-voids-active-plans.md)。  
29. ~~`/implement` `/tdd` 冲突升级作废计划票 01~~：已完成（审计 A1；`upgradeOpen` / `reopenFromPendingClose` 复用 `voidActivePlans`，`voidReason=conflict_upgrade`；witnessed red → green → refactor；`ConflictUpgradeVoidsActivePlanHttpAcceptanceTest`）。**本刀闭合。**  
30. ~~`/grill-with-docs` 定 ADR-0044 工单包切面~~：已定为 **执行引擎成真 + 单步 gRPC 代发 + MINA/凭证迁出**（B；slug `control-plane-executor`；ADR-0045）。  
31. ~~`/to-spec`~~：已发布 [`docs/specs/control-plane-executor.md`](specs/control-plane-executor.md) + [`docs/adr/0045-control-plane-executor-grpc.md`](adr/0045-control-plane-executor-grpc.md)。  
32. ~~`/to-tickets`~~：已发布票 01 → [`.scratch/control-plane-executor/issues/01-executor-single-step-dispatch.md`](../.scratch/control-plane-executor/issues/01-executor-single-step-dispatch.md)。  
33. ~~`/implement` `/tdd` 控制面执行引擎票 01~~：已完成（单步 gRPC 代发；空洞停发/丢弃在途成功；`grpc.health.v1` SERVING；mTLS；凭证由引擎解密；MINA 仅执行引擎 `@Import`）。witnessed red → green → refactor；`ExecutorSingleStepDispatchHttpAcceptanceTest` / `ExecutorGrpcHealthAcceptanceTest` / `ExecutorDownHttpAcceptanceTest`。**本刀闭合。**  
34. **下一对话：不要自动做 B-live / 编排层 / 工作台三档 / 步骤断言 schema**。不要发明未绑定 10。人排期后再 `/implement`。  

### 工单阻塞简图

竖切 MVP（已闭合）：

```
01 → 02 → 03 → 04 → 05 → 07 → 08 → 09 → 12（最小演示 UI）
                    ↘     ↑           ↘
                     06 ──┘            10
                     ↑                 ↓
                     └── 13（done）←───┘
05 → 11（指派/拒绝/转让 Should，done）
```

改策展 / 改理想草案（**01–06 TDD-done；本刀闭合**）：

```
01 关闭建底覆盖（TDD-done） ─────────────────┐
                                              │
02 诊断改理想分叉（TDD-done） → 03 选支出草案（TDD-done） → 04 逐条确认写入并立刻比对（TDD-done）
                                              │
                                              └→ 05 升级/空洞作废草案（TDD-done）
                                                   → 06 HTTP tracer（TDD-done；本刀定义完成）
```

未绑定 / 身份失联重绑（01–07 已拆，审计后补 08 / 09；**01–09 已闭合**）：

```
01 推断失联 + 未绑定 upsert + 规范问法（TDD-done）
   ├→ 02 从不挂冲突的候选发草案（TDD-done） → 03 逐条新建/绑定（TDD-done）
   │                                              └→ 08 绑定写入门禁修复（TDD-done） → 04 标签命中收尾（TDD-done） ─┐
   ├→ 09 失联叠加心跳超时的问法（审计 C-1；TDD-done）                                                              │
 └→ 05 失联闸门修实际/改理想（TDD-done） ───────────────────────────────────────────────────────────────┴→ 06 HTTP tracer（TDD-done） → 07 薄 UI（done）
```

冲突升级作废活跃计划（审计 A1；**01 TDD-done，本刀闭合**）：

```
01 upgradeOpen / reopenFromPendingClose 作废活跃操作计划（TDD-done）
```

控制面执行引擎（审计 B 第一刀；**01 TDD-done，本刀闭合**）：

```
01 执行引擎成真：单步 gRPC 代发 + MINA/凭证迁出（TDD-done）
```

（一次只做一张。）

## 本地启动摘要

见根 `README.md`：Compose 起 postgres/redis → `backend` `./gradlew bootRun` → `frontend` `npm run dev` → `GET /api/health`。

## 栈摘要

见 ADR-0043 + **ADR-0044** + **ADR-0045**：Gradle / MyBatis-Plus / React+Ant / PG+Redis 多副本 / Python systemd Host Agent / 控制面 `archops:latest` + 执行引擎 + AI 编排层。生产 MINA SSHD 在执行引擎（Compose `ARCHOPS_SSH_MODE=mina`）；控制面代发 `dispatch`（gRPC）；遗留 HTTP 测试仍可用控制面 `fake`（0044 决议 7）。控制面进程内 WebClient 出站已按 ADR-0044 删除。
