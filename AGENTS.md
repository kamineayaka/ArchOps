# AGENTS.md — ArchOps Cloud / Coding Agent 合同

本文件是 **Cursor Cloud Agent、本地 Agent、以及其他 AI 编码助手** 的强制入口。改代码前必须遵守。与 `CONTEXT.md` / ADR 冲突时：**领域语义以 CONTEXT + 有效 ADR 为准；技术实现以 ADR-0043 为准；本文件只做执行纪律与防漂移。**

## 0. 先读什么（按顺序）

1. 本文件 `AGENTS.md`
2. `CONTEXT.md` — 领域术语（禁止发明同义新词）
3. `docs/adr/0039-domain-contract-frozen.md` — 合同已冻结
4. `docs/adr/0043-tech-stack.md` — **技术栈唯一真相**
5. `docs/specs/vertical-slice-mvp.md` — 竖切 Spec（01–13 已闭合）
6. `docs/specs/change-curated-draft.md` — 改策展/草案逐条确认 Spec（**01–06 TDD-done，本刀闭合**）
7. `docs/specs/unbound-identity-rebind.md` — 下一刀 Spec（未绑定观测候选 / 身份失联重绑；工单 01–07 已拆）
8. 当前工单：见 `docs/dev-handoff.md`；**frontier = 未绑定票 01**。竖切与改策展工单包已闭合。不要加改策展 07
9. `docs/dev-handoff.md` — 进度与下一票
10. `docs/agents/tdd.md` — ArchOps TDD overlay（`/implement` 必读）
11. `.cursor/rules/project-map.mdc`、`backend-java.mdc`、`frontend-react.mdc`
12. `docs/agents/` — Matt 工作流 tracker / triage / domain 布局（Cloud 已 vendoring `.cursor/skills/`）

**不要**把 git 历史里的旧 ArchOps（Neo4j 必选、Maven、Vue/Naive、JPA 域模型、architecture proposal、旧 Agent 工具）当作现行实现样板。

## 1. 产品一句话

运维**关系真相**系统：策展（理想）/ 观测（实际）双轨；冲突是偏差不是对错。产品成立三件套 = 关系真相 + 连接工作台 + AI 诊断（人审执行）。

## 2. 技术栈（ADR-0043）— 禁止漂移

| 层 | 必须用 | 禁止 / Later |
|---|---|---|
| 控制面 | Java 21、Spring Boot 3、**Gradle**、**MyBatis-Plus**、Flyway | Maven；JPA 当地基 |
| 前端 | **React**、TypeScript、Vite、**Ant Design** | Vue、Naive UI 作为现行前端 |
| 数据 | **PostgreSQL** SSOT；图语义 v1 落 PG（边表/CTE） | Neo4j 当 v1 必选；图库 = Later |
| 缓存/队列 | **Redis**（队列、锁、会话、缓存）；控制面按**多副本**设计 | Redis 当关系真相 SSOT |
| Agent | Python 3.12+；交付主推 **systemd** | 把 Agent 塞进控制面默认 Compose |
| SSH | Apache **MINA SSHD** + 每副本连接池 + Redis 锁互斥计划 | 旁路直连；工作台续跑失败计划 |
| AI | Spring **WebClient** + ADR-0041 白名单 | LangChain 类诊断编排主干 |
| 交付 | `archops:latest`（API+前端静态）+ postgres + redis | 默认可拆多服务（拆分 = Later，须 ADR） |

## 3. 领域硬纪律（不可用代码改语义）

- 策展写入：草案逐条确认，或计划内显式对齐步；AI 不能独自定稿策展
- 观测写入：心跳/探测直写，不人审；不自动覆盖策展
- 冲突：两侧可用且不等才成立；空洞 ≠ 冲突；升级合并键 = 对象+关系类型；升级/空洞作废活跃计划
- 协作：归属 ≠ 处理人；仅**已接受**处理人可开/审/执行计划与确认关闭
- 操作计划：人审后冻结；失败即停作废；禁止改步重试
- 规范问法：应该 / 实际 / 是否一致；冲突时不得给唯一落点指令而不披露双轨
- Flyway：**禁止修改**已有 `V*.sql`，只新增下一个

改语义必须先立新 ADR，禁止「实现时顺手改合同」。

## 4. 代码风格与模块

包根：`com.archops.<module>`，模块：`curated` | `observed` | `conflict` | `plan` | `user` | `agent` | `common`。

- API：`/api/...` + 统一 `ApiResponse`；业务错误 `BusinessException`
- 后端分层：Controller → Service → Mapper（MyBatis-Plus）；DTO 用 `record`；勿把 DO 当响应
- 前端：HTTP 只走 `frontend/src/api/`；薄 UI，竖切票未要求不做完整工作台
- 构造器注入；不引入未使用依赖；不写任务未要求的文档/测试膨胀
- 凭证加密；禁止明文落库、日志、响应

## 5. Cloud Agent 工作方式（防范围漂移）

1. **一次只做一张工单**（默认下一 frontier：见 `docs/dev-handoff.md`）。读票内 Acceptance，做完更新票状态或 handoff，勿顺手做下一张。两张都 unblocked 时按编号最小的做。
2. Spec / 票 / ADR 冲突时：**ADR 与 CONTEXT > Spec > 票**；票过宽则缩到验收标准。
3. 不要「顺便」引入：Neo4j、Vue、Maven、JPA 全域、LangChain、完整 xterm、网络可达矩阵、自我迭代、多租户。
4. 不要从旧提交恢复已删除的 `ai/` `asset/` `graph/` 等旧域包当业务基础。
5. `/implement` 驱动 `/tdd`：**red → green → refactor**，一圈一条测试。先在确认接缝上跑出 **witnessed red**，再写该圈生产代码。ArchOps overlay：[`docs/agents/tdd.md`](docs/agents/tdd.md)。Skill：`.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）。与 skill 冲突时以本文件与 overlay 为准。
6. 测试主接缝：控制面 HTTP API（含 Agent ingest）；SSH 可用 fake；前端最小冒烟，且排在该票 HTTP 循环变绿之后。
7. 提交信息聚焦 why；每个绿灯切片可提交；不要提交 `.env`、密钥、`node_modules`、`build/`。

## 5.1 Cursor Cloud specific instructions

Cloud Agents run in an **Ubuntu VM** configured by [`.cursor/environment.json`](.cursor/environment.json) (see [`.cursor/CLOUD.md`](.cursor/CLOUD.md)). Not the developer's Windows laptop.

- After boot, `start` brings up **Postgres + Redis** via Compose. App env defaults: `POSTGRES_HOST=localhost`, `REDIS_HOST=localhost`, user/db/password `archops`.
- Backend: `cd backend && ./gradlew bootRun` (also started as terminal `backend`). Health: `curl -s http://127.0.0.1:8080/api/health`
- Frontend: `cd frontend && npm run dev -- --host 0.0.0.0 --port 5173` (terminal `frontend`)
- Tests: `cd backend && ./gradlew test` (many HTTP tests use embedded Postgres; still keep Compose Redis up if the app under test needs Redis)
- Warm deps already ran in Build `install` (`scripts/cloud-install.sh`). Re-run install commands only when deps change.
- Secrets (AI keys, etc.): Cursor Dashboard Secrets — never commit `.env`
- After editing `.cursor/Dockerfile` or `environment.json`, push and wait for a new Environment **Build** before relying on the change.
- Matt engineering skills are vendored in `.cursor/skills/` (Cloud Agent discovery) and `.agents/skills/` (skills.sh / desktop Cursor). Do not expect `~/.agents/skills/` on this VM.

## Agent skills

### Issue tracker

Local markdown under `.scratch/<feature-slug>/`; canonical specs in `docs/specs/`. Cloud `gh` is read-only — do not publish GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical roles on each ticket `Status:` line (`ready-for-agent`, `done`, …). See `docs/agents/triage-labels.md`.

### TDD

`/implement` drives `/tdd` (**red → green → refactor**). Overlay: [`docs/agents/tdd.md`](docs/agents/tdd.md).

### Domain docs

Single-context: frozen `CONTEXT.md` + `docs/adr/` (ADR-0039 / ADR-0043). Skills must not silently rewrite the contract. See `docs/agents/domain.md`.

`/to-spec` `/to-tickets` `/implement` `/grill-with-docs` `/tdd` `/code-review` read those files. `/implement` still follows §5: **one unblocked frontier ticket**, HTTP API acceptance seam, **red → green → refactor**. See [`docs/agents/tdd.md`](docs/agents/tdd.md).

## 6. Matt 进度（勿倒退）

- 领域 grilling / 合同冻结 / 技术选型 / 空脚手架 / 竖切 Spec / 竖切 Tickets / 竖切实现：**已完成**
- 改策展 Spec：`docs/specs/change-curated-draft.md`（已发布）；工单 `.scratch/change-curated-draft/issues/`（01–06 已拆）。**01–06 TDD-done。本刀闭合。**
- 下一刀 Spec：[`docs/specs/unbound-identity-rebind.md`](docs/specs/unbound-identity-rebind.md)；工单 `.scratch/unbound-identity-rebind/issues/`（01–07 已拆）。**frontier = 01**（推断身份失联 + 未绑定 upsert + 规范问法）。`/implement` 走 TDD overlay，一次一张。不要加改策展 07。
- `/implement` 必须走 TDD overlay：[`docs/agents/tdd.md`](docs/agents/tdd.md)
- 不需要再开技术选型或推倒栈，除非用户明示新 ADR

## 7. 云端提示词建议（用户可贴）

改策展 01–06 已闭合。未绑定 / 身份失联工单已拆；**frontier = 01**。一次一张 `/implement` `/tdd`，不要加改策展 07。

> 读 AGENTS.md、CONTEXT.md、ADR-0043 与 `docs/specs/unbound-identity-rebind.md`。只做 `.scratch/unbound-identity-rebind/issues/01-infer-identity-lost-unbound-upsert.md`。不要复活 Vue/JPA/Neo4j/Maven/LangChain。
