# 新对话接手指南

领域合同（ADR-0039）与技术栈（ADR-0043）已冻结。  
**Cloud / 任意 Agent 必读根目录 [`AGENTS.md`](../AGENTS.md)**（及 [`CLAUDE.md`](../CLAUDE.md)）。  
**脚手架已按 ADR-0043 重建**：`backend/`、`frontend/`、`agent/`、`deploy/`、根 `Dockerfile` 为可启动最小骨架；竖切按工单推进。

## 必读

1. `CONTEXT.md`
2. `docs/adr/0039` … `0043`（尤其 **0043**）
3. `docs/mvp-vertical-slice.md`（竖切范围对照）
4. `docs/specs/vertical-slice-mvp.md`（竖切 Spec；拆票主输入）
5. `docs/scaffold-bootstrap-prompt.md`（脚手架专用；已完成后可作审计对照）
6. `.cursor/rules/project-map.mdc`

## 当前状态

| 项 | 状态 |
|---|---|
| Gradle + Spring Boot + MyBatis-Plus + Flyway | 有；至 V12（handler assign/transfer events） |
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

## 下一对话建议

1. ~~竖切 Spec~~：已发布  
2. ~~拆票 / to-tickets~~：已发布至 `.scratch/vertical-slice-mvp/issues/`（01–13）  
3. ~~主链 HTTP E2E（票 13）~~：已完成  
4. ~~指派/拒绝/转让（票 11 Should）~~：已完成  
5. **竖切工单包已闭合**。后续仅在新 Spec/ADR 或用户明示票后再开 frontier；勿用代码偷改领域合同。

### 工单阻塞简图

```
01 → 02 → 03 → 04 → 05 → 07 → 08 → 09 → 12（最小演示 UI）
                    ↘     ↑           ↘
                     06 ──┘            10
                     ↑                 ↓
                     └── 13（done）←───┘
05 → 11（指派/拒绝/转让 Should，done）
```

（编号为依赖序。主链 Must + Should 协作票均已完成。）

## 本地启动摘要

见根 `README.md`：Compose 起 postgres/redis → `backend` `./gradlew bootRun` → `frontend` `npm run dev` → `GET /api/health`。

## 栈摘要

见 ADR-0043：Gradle / MyBatis-Plus / React+Ant / PG+Redis 多副本 / Python systemd Agent / `archops:latest`。MINA SSHD 已接入（票 08，默认 `archops.ssh.mode=fake`）；WebClient 已启用（票 06）。
