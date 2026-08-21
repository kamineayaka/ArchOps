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
| Gradle + Spring Boot + MyBatis-Plus + Flyway | 有；至 V14（改理想开放草案 + 条目逐条确认审计） |
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
| 改策展草案工单 | **TDD-done 04** → [`.scratch/change-curated-draft/issues/`](../.scratch/change-curated-draft/issues/)（**01–04 TDD-done**；05 `ready-for-agent`；**frontier = 05**；06 仍被挡住。从竖切 MVP 往上长，不重拆竖切 01–13） |
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
13. **下一对话：`/implement` `/tdd` 票 05**（升级/空洞作废未完成草案）。票路径 [`.scratch/change-curated-draft/issues/05-void-draft-on-upgrade-hollow.md`](../.scratch/change-curated-draft/issues/05-void-draft-on-upgrade-hollow.md)。**完整开工 prompt** [`docs/implement-change-curated-draft-05-prompt.md`](implement-change-curated-draft-05-prompt.md)（复制区整段贴进新对话；附 implement + tdd skill）。一次只做一张；不要顺手做 06。本票是把竖切已有升级/空洞接到开放草案，诚实红灯是草案仍 OPEN / 作废后仍能审条，不要拆 04、不要重做挂起或计划作废。  

06 被 05 挡住。

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

改策展 / 改理想草案（现行 frontier = **05**）：

```
01 关闭建底覆盖（TDD-done） ─────────────────┐
                                              │
02 诊断改理想分叉（TDD-done） → 03 选支出草案（TDD-done） → 04 逐条确认写入并立刻比对（TDD-done）
                                              │
                                              └→ 05 升级/空洞作废草案
                                                   → 06 HTTP tracer（本刀定义完成）
```

（一次只做一张。05 未完成前，不要开工 06。）

## 本地启动摘要

见根 `README.md`：Compose 起 postgres/redis → `backend` `./gradlew bootRun` → `frontend` `npm run dev` → `GET /api/health`。

## 栈摘要

见 ADR-0043：Gradle / MyBatis-Plus / React+Ant / PG+Redis 多副本 / Python systemd Agent / `archops:latest`。MINA SSHD 已接入（票 08，默认 `archops.ssh.mode=fake`）；WebClient 已启用（票 06）。
