# 新对话接手指南

领域合同（ADR-0039）与技术栈（ADR-0043）已冻结。  
**Cloud / 任意 Agent 必读根目录 [`AGENTS.md`](../AGENTS.md)**（及 [`CLAUDE.md`](../CLAUDE.md)）。  
**脚手架已按 ADR-0043 重建**：`backend/`、`frontend/`、`agent/`、`deploy/`、根 `Dockerfile` 为可启动最小骨架；竖切按工单推进。

## 必读

1. `CONTEXT.md`
2. `docs/adr/0039` … `0043`（尤其 **0043**）
3. `docs/mvp-vertical-slice.md`（竖切范围对照）
4. `docs/specs/vertical-slice-mvp.md`（竖切 Spec；01–13 已闭合）
5. `docs/specs/change-curated-draft.md`（改策展/草案逐条确认 Spec；工单见 `.scratch/change-curated-draft/issues/`）
6. `docs/agents/tdd.md`（`/implement` 的 TDD overlay：red → green → refactor）
7. `docs/scaffold-bootstrap-prompt.md`（脚手架专用；已完成后可作审计对照）
8. `.cursor/rules/project-map.mdc`

## 当前状态

| 项 | 状态 |
|---|---|
| Gradle + Spring Boot + MyBatis-Plus + Flyway | 有；至 V15（改理想草案 + 条目确认 + 草案作废审计） |
| React + Ant Design 薄页 | **已完成**（票 12：冲突列表/详情→协作→选支→审计划→确认关闭） |
| Python agent 心跳+快照 stub + systemd 说明 | 有（契约见 `docs/contracts/agent-heartbeat-snapshot.md`） |
| Compose + `archops:latest` 多阶段镜像 | 有 |
| SSH 受控执行 | **已完成**（票 08） |
| 临时身份头 + 高级/一般角色门禁 | **已完成**（票 01） |
| 策展主机 / 容器 / `运行于` / 「应该在哪」 | **已完成**（票 02） |
| Agent 心跳+快照 → 观测 / 「实际在哪」 | **已完成**（票 03） |
| 冲突警告与合并键升级 | **已完成**（票 04） |
| 已知悉 + 认领/自任 → 已接受处理人 | **已完成**（票 05） |
| 异步诊断（规则）+ 可选 LLM + 敏感读拒绝 | **已完成**（票 06） |
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
14. ~~`/implement` `/tdd` 票 06~~：已完成（HTTP 主接缝有序 tracer；suite/tracer 循环；`ChangeCuratedDraftTracerHttpAcceptanceTest`）。**本刀闭合。**  
15. **下一对话不要默认 `/implement`。** 改策展工单包已闭合，不要加 07。下一步是 **`/grill-with-docs` 定下一刀**（不是先拆票、也不是先写代码）。完整开工 prompt：[`docs/grill-next-knife-prompt.md`](grill-next-knife-prompt.md)（复制区整段贴进新对话；附 grill-with-docs + grilling + domain-modeling）。grilling / to-spec / to-tickets 留在同一窗口；每个 `/implement` 另开。不要重拆竖切 01–13。推荐默认候选（可在 grilling 里推翻）：**未绑定观测候选 / 身份失联重绑**。不要默认 Y2：须先解决与「接受即写入」的双写。  

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

（一次只做一张。）

## 本地启动摘要

见根 `README.md`：Compose 起 postgres/redis → `backend` `./gradlew bootRun` → `frontend` `npm run dev` → `GET /api/health`。

## 栈摘要

见 ADR-0043：Gradle / MyBatis-Plus / React+Ant / PG+Redis 多副本 / Python systemd Agent / `archops:latest`。MINA SSHD 已接入（票 08，默认 `archops.ssh.mode=fake`）；WebClient 已启用（票 06）。
