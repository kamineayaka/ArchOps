# AGENTS.md — ArchOps Cloud / Coding Agent 合同

本文件是 **Cursor Cloud Agent、本地 Agent、以及其他 AI 编码助手** 的强制入口。改代码前必须遵守。与 `CONTEXT.md` / ADR 冲突时：**领域语义以 CONTEXT + 有效 ADR 为准；技术实现以 ADR-0043 为准，进程切分以 ADR-0044 为准；本文件只做执行纪律与防漂移。**

## 0. 先读什么（按顺序）

1. 本文件 `AGENTS.md`
2. `CONTEXT.md` — 领域术语（禁止发明同义新词）
3. `docs/adr/0039-domain-contract-frozen.md` — 合同已冻结
4. `docs/adr/0043-tech-stack.md` — **技术栈**；进程切分见 **ADR-0044**
5. `docs/specs/vertical-slice-mvp.md` — 竖切 Spec（01–13 已闭合）
6. `docs/specs/change-curated-draft.md` — 改策展/草案逐条确认 Spec（**01–06 TDD-done，本刀闭合**）
7. `docs/specs/unbound-identity-rebind.md` — 未绑定 / 身份失联重绑 Spec（**01–09 已闭合**）
8. `docs/specs/conflict-upgrade-void-plans.md` — 冲突升级作废活跃计划（审计 A1；**01 TDD-done，本刀闭合**）
9. 当前工单：见 `docs/dev-handoff.md`。未绑定 01–09 / 竖切 / 改策展 / A1 票 01 均已闭合。不要发明未绑定 10。不要自动做 ADR-0044 进程拆分。
10. `docs/dev-handoff.md` — 进度与下一票
11. `docs/agents/tdd.md` — ArchOps TDD overlay（`/implement` 必读）
12. `.cursor/rules/project-map.mdc`、`backend-java.mdc`、`frontend-react.mdc`
13. `docs/agents/` — Matt 工作流 tracker / triage / domain 布局（Cloud 已 vendoring `.cursor/skills/`）

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
| SSH | 执行引擎内 Apache **MINA SSHD** + Redis 锁（控制面代发前加锁）；ADR-0044 | 旁路直连；工作台续跑失败计划；控制面生产直连 SSH |
| AI | **AI 编排层** WebClient + ADR-0041/0044 白名单；诊断取证经控制面代发 | LangChain 类诊断编排主干；控制面持模型密钥；编排层直连执行引擎 |
| 交付 | `archops:latest`（API+前端静态）+ 执行引擎 + AI 编排层 + postgres + redis | 第三条 SSH 通道；把 Host Agent 塞进控制面默认 Compose |

## 3. 领域硬纪律（不可用代码改语义）

- 策展写入：草案逐条确认，或计划内显式对齐步；AI 不能独自定稿策展
- 观测写入：心跳/探测直写，不人审；不自动覆盖策展
- 冲突：两侧可用且不等才成立；空洞 ≠ 冲突；升级合并键 = 对象+关系类型；升级/空洞作废活跃计划
- 协作：归属 ≠ 处理人；仅**已接受**处理人可开/审/执行计划与确认关闭
- 操作计划：人审后冻结；每步步骤断言；失败即停作废；禁止改步重试；执行期编排层只观察
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

Single-context: frozen `CONTEXT.md` + `docs/adr/` (ADR-0039 / ADR-0043 / ADR-0044). Skills must not silently rewrite the contract. See `docs/agents/domain.md`.

`/to-spec` `/to-tickets` `/implement` `/grill-with-docs` `/tdd` `/code-review` read those files. `/implement` still follows §5: **one unblocked frontier ticket**, HTTP API acceptance seam, **red → green → refactor**. See [`docs/agents/tdd.md`](docs/agents/tdd.md).

## 6. Matt 进度（勿倒退）

- 领域 grilling / 合同冻结 / 技术选型 / 空脚手架 / 竖切 Spec / 竖切 Tickets / 竖切实现：**已完成**
- 改策展 Spec：`docs/specs/change-curated-draft.md`（已发布）；工单 `.scratch/change-curated-draft/issues/`（01–06 已拆）。**01–06 TDD-done。本刀闭合。**
- 未绑定 Spec：[`docs/specs/unbound-identity-rebind.md`](docs/specs/unbound-identity-rebind.md)；工单 `.scratch/unbound-identity-rebind/issues/`（01–07 已拆，审计后补 08 / 09）。**01–09 已闭合**。不要发明未绑定 10。
- 冲突升级作废活跃计划：[`docs/specs/conflict-upgrade-void-plans.md`](docs/specs/conflict-upgrade-void-plans.md) / [`.scratch/conflict-upgrade-void-plans/issues/01-upgrade-voids-active-plans.md`](.scratch/conflict-upgrade-void-plans/issues/01-upgrade-voids-active-plans.md)（审计 A1；**01 TDD-done，本刀闭合**）。
- 01–03 合同审计：[`.scratch/unbound-identity-rebind/audit-01-03-opus.md`](.scratch/unbound-identity-rebind/audit-01-03-opus.md)。票 08 已处置 C-4 / C-2 / S-3；票 04 已处置 C-3 / S-4 / S-2；票 09 已处置 C-1。
- 代码 vs ADR-0044 只读审计：[`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md)。A2 = 票 05；A3 = 票 09（已闭合）；**A1 = conflict-upgrade-void-plans 01 TDD-done**；B1–B5 另开，不要自动做。
- `/implement` 必须走 TDD overlay：[`docs/agents/tdd.md`](docs/agents/tdd.md)
- ADR-0044 已冻结控制面枢纽 / 执行引擎 / AI 编排层拆分。拆分实现另开工单，**不要写入本刀**。控制面不得再加进程内 LLM 出站。
- 不需要再开技术选型或推倒栈，除非用户明示新 ADR

## 7. 云端提示词建议（用户可贴）

未绑定 **01–09 已闭合**。冲突升级作废活跃计划 **01 TDD-done / 本刀闭合**（审计 A1）。下一刀需人排期：**ADR-0044 工单包切面**（grilling，不是 `/implement`）。开场 prompt：[`docs/grill-adr-0044-slice-prompt.md`](grill-adr-0044-slice-prompt.md)。不要发明未绑定 10。不要加改策展 07。不要把 WebClient 加回控制面。

> 读 AGENTS.md、CONTEXT.md、ADR-0044 与 `docs/grill-adr-0044-slice-prompt.md`。只做 `/grill-with-docs` 定 0044 切面。不要 `/implement`。不要复活 Vue/JPA/Neo4j/Maven/LangChain。不要把 LLM 加回控制面。
