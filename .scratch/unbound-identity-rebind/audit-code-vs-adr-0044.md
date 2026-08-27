# 代码 vs CONTEXT / ADR-0044 只读审计

- **Date**: 2026-08-26
- **Model**: Cursor Grok 4.6（Cloud Agent；只读审计当轮未改生产、未改 ADR / CONTEXT、未实施未绑定票 05、未拆执行引擎 / 编排层）
- **Scope**: 当前已构建代码 vs（1）现行指导性/纲领性文件（CONTEXT + 有效 ADR）；（2）最近一次架构 grilling 收口 ADR-0044
- **Authority order**: ADR + `CONTEXT.md` > Spec > 票 > README。不得用闭合竖切 Spec 否定 ADR-0044。出站以 `docs/adr/0041-ai-egress-controlled-external-api.md` + 0044 为准（仓库另有竖切 ADR-0041，不覆盖出站落点）
- **本文件性质**: 冲突报告。不实施、不改语义。后续实现仍一次一张 frontier 票

三类禁止混为一谈：

| 类 | 含义 |
|---|---|
| **A 合同冲突** | 代码行为与 CONTEXT + 有效 ADR 的语义/权力边界不一致 |
| **B 0044 已点名的过渡债** | 控制面仍跑生产 MINA、无执行引擎/编排层进程、无 B-live 代发、无步骤断言、无逐步事件。0044 已承认必须另开工单重写，禁止在偏离上加功能、禁止写入未绑定 05 |
| **C 沉积误报** | 闭合 Spec / 旧票 / README 仍写「单进程、密钥在控制面、WebClient 加料」。不得据此否定 0044 或把 LLM 加回控制面 |

---

## 结论

有 **A 类**，共 3 条：

1. **A1**（新发现，未挂票）：冲突升级不作废活跃操作计划 → **另开工单**，不要写入 05
2. **A2**（= 未绑定票 05）：身份失联后仍把旧观测当可用实际
3. **A3**（= 票 09 / 审计 C-1）：失联叠加心跳超时，规范问法仍只报 `IDENTITY_LOST`

控制面仍是唯一权力中心；Host Agent 仍直写观测；进程内 LLM 出站已删。未发现模型判步、第二条 SSH、LangChain、Maven / JPA 当地基 / Vue 现行前端 / Neo4j 必选、Redis 当真相 SSOT。0044 点名的进程拆分与步骤断言仍是已知过渡债。

**下一步不偏离**：A2 / A3 已由未绑定票 05 / 09 闭合。**A1 已由用户 2026-08-27 排期** → Spec [`docs/specs/conflict-upgrade-void-plans.md`](../../docs/specs/conflict-upgrade-void-plans.md)，frontier 票 [`.scratch/conflict-upgrade-void-plans/issues/01-upgrade-voids-active-plans.md`](../conflict-upgrade-void-plans/issues/01-upgrade-voids-active-plans.md)。B1–B5 / 0044 拆分仍另开，禁止写入 A1 刀。

---

## A. 合同冲突

### A1. 冲突升级不作废活跃操作计划

- **位置**: `ConflictDetectionService.upgradeOpen()`；对比 `onObservationBecameHollow()` → `OperationPlanService.voidActivePlansForConflict()`
- **违背**: CONTEXT「AI 诊断」：升级时选支作废、活跃计划受阻即停取消；ADR-0038；ADR-0027
- **证据**: `upgradeOpen` 只作废开放草案并重诊，不调用 `voidActivePlansForConflict`。空洞路径会作废计划。`startExecution()` 只检查计划仍为 `APPROVED` 且冲突仍 `OPEN`，不检查诊断是否已 `STALE`。升级后旧计划仍可审/执行。验收只覆盖空洞作废计划、升级作废草案（`HeartbeatTimeoutHollowHttpAcceptanceTest`、`ChangeCuratedDraftVoidHttpAcceptanceTest`）
- **建议**: **票 01 TDD-done（2026-08-27）** → [`docs/specs/conflict-upgrade-void-plans.md`](../../docs/specs/conflict-upgrade-void-plans.md)。证据段保留审计当时缺口。不要写入未绑定目录；不要与 0044 拆分混做。

### A2. 身份失联后仍把旧观测当可用实际（冲突 GET / 诊断 / 选支 / 执行）

- **位置**: `ObservedTruthService.upsertIdentityLost()`（只写 `identity_lost_mark`，不退役 `observed_fact`）；`ConflictDetectionService.toResponse()`（无失联旗标，非空洞则展示 `PRESENT` + 旧宿主）；`DiagnosisRuleEngine.diagnoseRunsOnMismatch()`；`BranchSelectionService.select()`；`OperationPlanService.buildFixActualSteps()`
- **违背**: CONTEXT「身份失联」「规范问法」「冲突」（两侧须有可用值）；Spec 故事 11 / 14–16 / 45–49
- **证据**: `GET /api/observed/asks/actual-where` 已投影 `IDENTITY_LOST` 且不返回旧宿主。冲突 GET / 规则诊断仍用残留 `observed_fact` 给出「修实际回策展宿主 / 改理想」。选支与 SSH 计划仍以旧 `fromHostId` 为落点。前端 `ConflictDetailPage` 只标空洞，不标失联
- **建议**: **留给票 05**。禁止在 05 扩大到执行引擎 / 编排层 / B-live

### A3. 失联叠加心跳超时，规范问法仍只报 `IDENTITY_LOST`

- **位置**: `ObservedTruthService.observedAskValue()`（`lostMark != null` 优先于 stale / hollow）
- **违背**: CONTEXT「心跳」/「观测空洞」（超时后旧值不可再用）；与票 09 / `audit-01-03-opus.md` C-1 同一条
- **证据**: 同时有失联标且 sourced agent 已超时，问法仍 `IDENTITY_LOST`，不进入 `HOLLOW`。票 05 明确：通道仍新鲜时不得改走纯空洞分叉；反面（通道已死）不在 05
- **建议**: **留给票 09**。禁止与 05 混做。05 不要改 `observedAskValue` 的 mark 优先顺序

---

## B. ADR-0044 已点名的过渡债（已知债）

### B1. 无独立执行引擎 / 编排层进程，Compose 仍三件套

- **位置**: `deploy/compose/compose.yaml`（仅 `postgres` / `redis` / `archops`）；仓库无 executor / orchestrator 目录
- **条款**: ADR-0044 决议 1；后果「拆这两进程不再是 Later」
- **证据**: 交付仍是控制面镜像 + PG + Redis。Host Agent 不在 Compose（符合「不进默认 Compose」）
- **建议**: **另开工单**。禁止写入未绑定 05

### B2. 生产 MINA 仍在控制面；计划在进程内跑完整份

- **位置**: `MinaSshPort`、`ControlledSshPort`、`OperationPlanService.startExecution()`；`backend/build.gradle.kts` 的 `sshd-core`；`application.yml` `archops.ssh.mode`
- **条款**: ADR-0044 后果「`MinaSshPort` / `ControlledSshPort` 作为生产动手路径 → 迁到执行引擎」；拒绝「整份冻结计划交给执行器内跑完」
- **证据**: `mode=mina` 时控制面直连主机。一次 HTTP 在 Redis 锁内跑完所有步。默认 `fake`，凭证 API 只服务同一适配器，未见新的生产直连 SSH 路由。未扩大第二条通道
- **建议**: **另开工单**。05 不得再加生产直连 SSH

### B3. 无步骤断言、无逐步事件、无控制面→引擎单步代发

- **位置**: `OperationPlanResponse.PlanStep`（仅 `seq` / `action` / `description` / `params`）；`SshExecResult.success` 来自 SSH 退出码；无编排层事件推送
- **条款**: 决议 2、4；后果「无步骤断言、无逐步事件」
- **证据**: 成败不是对工具结构化结果的预先约定。失败即停作废、禁止同计划改步重试，这一段语义已在控制面成立。没有模型读日志判步
- **建议**: **另开工单**。禁止在 05 补断言协议

### B4. 无 B-live 代发；诊断只有规则引擎

- **位置**: `ConflictDiagnosisService.processDiagnosisJob()`；`DiagnosisRuleEngine`
- **条款**: 决议 5；后果「诊断仅规则引擎、无 B-live」
- **证据**: 诊断读策展 / 观测 / 冲突行，不经执行引擎做只读取证，无扩图轮次（ADR-0030 随编排层补）。读结果不写观测 / 策展
- **建议**: **另开工单**。禁止在 05 做 B-live

### B5. AI 编排层进程不存在（出站落点未建）

- **位置**: 控制面已无 `DiagnosisLlmClient` / `AiEgress*` / `WebClient`（`application.yml` 无模型密钥）
- **条款**: 决议 6；点名删除控制面出站 — **删除已完成**；编排层进程仍缺
- **证据**: `DiagnosisSource.RULES_WITH_LLM` 仅历史枚举，现码只写 `RULES`。规则兜底可用
- **建议**: 忽略枚举残留；编排层 **另开工单**。不要把 LLM 加回控制面

### B6. 工作台与计划尚未「共用执行引擎」（但还没有第二条 SSH）

- **位置**: 唯一动手口 `ControlledSshPort.exec`，只被 `OperationPlanService` 调用；`/api/workbench` 只有 `SensitiveReadController` 分类器 stub
- **条款**: 决议 3「工作台与操作计划共用同一执行引擎，不得另开 SSH 旁路」；CONTEXT 工作台三档 / ADR-0036 / ADR-0018
- **证据**: 无工作台 SSH，故无旁路续跑失败计划。三档与图结构导流未建。非敏感读返回 200 但不执行命令。这是产品缺口，不是已存在的第二通道
- **建议**: **另开工单**（工作台 + 共用引擎）。禁止 05

---

## C. 沉积误报（旧文案，不得用来否定 0044）

### C1. 闭合竖切 Spec 仍写单进程 MINA + 控制面 WebClient + 密钥在控制面

- **位置**: `docs/specs/vertical-slice-mvp.md`（SSH in-process、WebClient 加料、Compose 无引擎）；`.scratch/vertical-slice-mvp/issues/06-async-diagnosis-egress-sensitive-deny.md`（「密钥只在控制面」）
- **条款**: 优先级 ADR+CONTEXT > Spec。0044 已修订 0043「拆服务 Later」与 0041「密钥仅存控制面」
- **证据**: 历史竖切已闭合。现码已删控制面 LLM。不得用该 Spec 要求把 WebClient 加回控制面，也不得用它否定四进程
- **建议**: **忽略**（对实现）。文档对齐可另开，不进 05

### C2. `docs/mvp-vertical-slice.md` 仍写「拆服务 Later」

- **位置**: 技术栈节「交付：`archops:latest` + postgres + redis；拆服务 / 图库 / 反向代理 Later」
- **条款**: 0044「拆这两进程不再是 Later」
- **证据**: 预 0044 范围对照。若当现行拓扑，会直接否定 0044
- **建议**: **忽略** 作为实现依据

### C3. 根 `README.md` / Cloud 终端仍单控制面；并残留脚手架句

- **位置**: `README.md` Compose 说明、`「当前无 ingest API」`；`.cursor/environment.json` 终端仅 `bootRun` + Vite
- **条款**: 0044 目标形态 vs 过渡交付。未声称「拆分仍是 Later」
- **证据**: 与 0044 目标差一截，属交付文档滞后。`agent/heartbeat.py` 已 POST `/api/agent/heartbeat`，README 那句是过期脚手架
- **建议**: **忽略** / 文档工单。不是合同冲突

### C4. `docs/deployment.md`、`docs/graph-ssot-design.md` 仍写 Neo4j 必选、OPENAI_API_KEY、前后端分镜像

- **位置**: `docs/deployment.md`「Neo4j 与 Postgres、Redis 同级，为必选依赖」；`OPENAI_API_KEY`
- **条款**: ADR-0043 图库 Later、禁止 Neo4j v1 必选；0044 控制面不持模型密钥
- **证据**: `backend/build.gradle.kts` 无 Neo4j / JPA / LangChain / WebClient。现 Compose 无 neo4j。旧部署文不是现行拓扑
- **建议**: **忽略**。禁止按该文加 Neo4j 或把密钥放回控制面

---

## 代码 vs ADR-0044 枢纽协议（8 条决议）

| # | 决议 | 判定 |
|---|---|---|
| 1 | 四进程：控制面 / 执行引擎 / AI 编排层 / Host Agent | **过渡债**。控制面 + Host Agent（心跳直连 `/api/agent/heartbeat`）在；引擎与编排层进程不在 |
| 2 | 枢纽：作业派编排层；单步下发引擎；游标步进/作废；逐步事件；空洞/升级立刻停发 | **过渡债**。游标与作废在控制面；整份计划一请求跑完；无派工、无逐步事件。升级不作废计划见 **A1**（合同，不是 0044 点名债） |
| 3 | 工具全经控制面代发；禁止编排层持引擎长凭证；工作台与计划共用引擎；引擎不写真相 | **过渡债**（引擎未拆，MINA 在控制面）。**符合**：无第二 SSH；执行路径不写 `observed` / `curated` / `conflict` 表；观测仍由 Agent 心跳写入 |
| 4 | 步骤断言由引擎判定；禁止模型判步/发下一步；失败即停作废 | **过渡债**（无断言）。**符合**：无模型判步；失败即停 `VOIDED`，拒绝改步重试 |
| 5 | B-live 只读经代发；预算封顶；读结果不写真相 | **过渡债**。无 B-live。规则诊断不写观测/策展 |
| 6 | 密钥与出站在编排层；控制面不持密钥、不直连 LLM；禁 LangChain | **符合**（控制面侧：出站已删，无 LangChain）。**过渡债**（编排层进程未建） |
| 7 | 编排层不可用时规则分叉仍能警告→选支→人审→执行 | **符合**。`scheduleAsyncDiagnosis` 提交后入队；警告不绑诊断完成；`DiagnosisRuleEngine` 为唯一 in-process 源 |
| 8 | 扩资料与规划词汇，不扩执行期变异面；白名单仍人批 | **符合**（权力闸门在控制面：选支/人审/关单/处理人）。能力侧资料/事件/工具目录随编排层，属 B4/B5 |

---

## 审计轴摘要

**权力中心**: `ConflictController` / `ConflictCollaborationService` / `BranchSelectionService` / `OperationPlanController` 仍是选支、人审、关单、协作闸门。未见模型/Agent 直连主机或执行期 function call。无工作台 SSH 旁路。升级不作废计划见 A1。

**双轨**: `AgentIngestController` → `ObservedTruthService.ingestHeartbeat` 直写观测、不人审、不覆盖策展。`REFRESH_OBSERVATION` 只发 SSH 占位命令，注释写明观测刷新靠心跳。空洞 / 消失 / 失联在问法 DTO 上已分词（`HOLLOW` / `ABSENT` / `IDENTITY_LOST`）；冲突管道仍把失联残留 `PRESENT` 当可用，见 A2/A3。

**诊断与出站**: 控制面无模型密钥、无 WebClient。规则兜底不挡警告。无 LangChain。B-live 未建（B4）。

**执行**: 失败即停作废已落地。MINA 在控制面（B2）。无模型判步。

**工作台**: 无三档命令闸门、无图结构工作台执行；计划路径上的 `MIGRATE_CONTAINER` 走已审计划。同进程仅一套 `ControlledSshPort`，不是两套适配器旁路。

**栈与模块**: Gradle + MyBatis-Plus + React/Ant + PG SSOT；Redis 只用于诊断队列与计划锁。无 `com.archops.ai`、无 Neo4j SSOT 包。未复活旧域。

**0044 未拆进程**: 只记 B1。不要据此改 05 或偷偷拆服务。

G2（ADR-0037）未做时钟提醒：`firstWarnedAt` 有字段无消费。这是合同能力未落地，不是现码把时钟算错；竖切对照曾写「G2 可后置」。不记 A。

---

## 对开工的含义

| 项 | 归属 | 本审计要求 |
|---|---|---|
| 未绑定票 05 | A2 | 下一张 frontier。Prompt：`docs/implement-unbound-identity-rebind-05-prompt.md` |
| 未绑定票 09 | A3 / 01–03 审计 C-1 | 待人排期。不要与 05 混做 |
| 升级作废活跃计划 | A1 | 另开工单。不要塞进 05 |
| 执行引擎 / 编排层 / B-live / 步骤断言 | B1–B5 | 另开 0044 工单包。禁止写入未绑定 05–09 |
| 工作台三档 | B6 / ADR-0014 产品缺口 | 另开工单 |
| 闭合竖切 Spec 的 WebClient / 单进程句 | C | 忽略为实现依据 |

05→09 **不会偏离** 当前刀与 ADR-0044，只要不把 A1 或进程拆分写进这两张票。
