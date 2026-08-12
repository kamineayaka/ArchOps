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
| Gradle + Spring Boot + MyBatis-Plus + Flyway | 有；至 V7（conflict diagnosis） |
| React + Ant Design 薄页 | 有；展示 health |
| Python agent 心跳+快照 stub + systemd 说明 | 有（契约见 `docs/contracts/agent-heartbeat-snapshot.md`） |
| Compose + `archops:latest` 多阶段镜像 | 有 |
| 计划生成/人审 / SSH | **未做**（按工单实现） |
| 临时身份头 + 高级/一般角色门禁 | **已完成**（票 01） |
| 策展主机 / 容器 / `运行于` / 「应该在哪」 | **已完成**（票 02） |
| Agent 心跳+快照 → 观测 / 「实际在哪」 | **已完成**（票 03） |
| 冲突警告与合并键升级 | **已完成**（票 04） |
| 已知悉 + 认领/自任 → 已接受处理人 | **已完成**（票 05） |
| 异步诊断（规则）+ 可选 LLM + 敏感读拒绝 | **已完成**（票 06） |
| 竖切 Spec | 已发布 → [`docs/specs/vertical-slice-mvp.md`](specs/vertical-slice-mvp.md) |
| 竖切工单 | 已本地发布 → [`.scratch/vertical-slice-mvp/issues/`](../.scratch/vertical-slice-mvp/issues/)（`ready-for-agent`；tracker 未配置） |

## 下一对话建议

1. ~~竖切 Spec~~：已发布  
2. ~~拆票 / to-tickets~~：已发布至 `.scratch/vertical-slice-mvp/issues/`（01–13）  
3. **实现 frontier**：下一张无 blocker 的票为 **[`07-fix-actual-branch-plan-review.md`](../.scratch/vertical-slice-mvp/issues/07-fix-actual-branch-plan-review.md)**（`01`–`06` 已完成）。`11` 仍可在 `05` 后独立推进。勿跳过依赖。

### 工单阻塞简图

```
01 → 02 → 03 → 04 → 05 → 07 → 08 → 09 → 12（最小演示 UI）
                    ↘     ↑           ↘
                     06 ──┘            10
                     ↑                 ↓
                     └── 13 ←──────────┘
05 → 11（指派/拒绝/转让 Should，独立）
```

（编号为依赖序：`06` 诊断阻塞 `07` 选支；`12` UI 不阻塞 `13` HTTP 验收。）

## 本地启动摘要

见根 `README.md`：Compose 起 postgres/redis → `backend` `./gradlew bootRun` → `frontend` `npm run dev` → `GET /api/health`。

## 栈摘要

见 ADR-0043：Gradle / MyBatis-Plus / React+Ant / PG+Redis 多副本 / Python systemd Agent / `archops:latest`。MINA SSHD 在 `build.gradle.kts` 中仍注释预留；WebClient 已启用（票 06 AI 出站）。
