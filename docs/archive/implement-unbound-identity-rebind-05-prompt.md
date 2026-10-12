# 新对话：未绑定 / 身份失联票 05（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

Matt 位置：grilling / to-spec / to-tickets **已完成**。竖切 01–13 与改策展 01–06 已闭合。未绑定票 **01–04 + 08 TDD-done**。本对话只 `/implement` **未绑定刀 frontier = 05**。不要做 06–07，不要做票 09，不要给 `change-curated-draft` 加 07，不要把 01–04 / 08 当 TDD redo。不要拆执行引擎 / AI 编排层。

本票带着代码 vs ADR-0044 只读审计（[`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md)）的 **A2**；**A3 / C-1 是票 09**；**A1**（升级不作废活跃计划）与 **B1–B5**（0044 进程债）禁止写入本票。01–03 合同审计 C-1 仍待票 09，本票不要改 `observedAskValue` 的 mark 优先顺序。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现未绑定 / 身份失联 frontier 工单 05。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。本票是新能力票，不是 TDD redo，也不是 06 那种 suite/tracer。01–04 与 08 已闭合，禁止重做它们的行为（推断失联 / 未绑定 upsert / 问法读模型 / 发起草案 / 逐条确认 / 绑定门禁 / 标签命中收尾）。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围、路径、表设计、错误码或作废时机。下面已钉死。不要用 Playwright、SSH fake、computerUse 或薄 UI 当作完成定义。不要默认开工 06–07 或票 09。不要拆执行引擎 / AI 编排层，不要把 WebClient/密钥加回控制面，不要加 B-live / 步骤断言。

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 06–07 / 09 / A1 / 0044 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/unbound-identity-rebind/issues/05-identity-lost-gates-conflict-pipeline.md

Spec：docs/specs/unbound-identity-rebind.md
审计（只读，不要改）：
- .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md
- .scratch/unbound-identity-rebind/audit-01-03-opus.md

最近一次只读架构审计结论（本对话的边界，不是要你修 0044）：
- A2 = 本票。失联后不得再按旧实际走修实际 / 改理想。
- A3 / 审计 C-1 = 票 09。失联叠加心跳超时时问法仍须说出观测空洞。本票不要动 observedAskValue 的 mark 优先于 staleness。
- A1 = 另开工单。冲突升级（观测 B→C）不作废活跃计划。本票只在「身份失联落地」时作废该合并键计划，不要顺便修 generic upgradeOpen。
- B1–B5 = ADR-0044 过渡债。MINA 仍在控制面、无编排层、无 B-live、无步骤断言。禁止在本票扩大生产直连 SSH，禁止把 DiagnosisLlmClient / WebClient 加回来。

一句话交付：合并键主体已是身份失联时，已有冲突保留为失联态（不新增 ConflictStatus、不 SUSPENDED）；待确认关闭退回 OPEN；禁止对该键选「修实际 / 改理想」、审/执行操作计划；活跃计划受阻即停作废；该键开放的改理想草案作废；诊断不再给出以旧实际为落点的分叉，只读说明身份失联 / 未绑定观测候选 / 现场补标。心跳通道仍新鲜时，不得改走纯空洞「恢复观测通道」分叉集。

本票对应 Spec User Stories 14–16、45–49，以及 Negative 8。Stories 11–13 的问法读模型已由 01 交付（IDENTITY_LOST 只在 ask DTO）。命中收尾是 04。有序总 tracer 是 06，薄 UI 是 07。审计 C-1 是票 09。

本票交付（用户可感知、HTTP 可断言）：
- 主体失联后 GET /api/conflicts/{id} 与 GET /api/conflicts/by-merge-key：status 仍 OPEN（若原先 PENDING_CLOSE 则变为 OPEN）；identityLost=true；observationHollow=false（心跳仍新鲜）；不得把残留 observed_fact 的旧宿主展示为当前可用实际（observedValue.availability 不得为 PRESENT；hostId 为 JSON null）。不要用 HOLLOW / SUSPENDED 冒充失联。
- 未绑定本身仍不新开冲突：仅未打标同名快照 → by-merge-key 仍 400 CONFLICT_NOT_FOUND（竖切 13 / 01 回归）。
- GET /api/conflicts/{id}/diagnosis：READY 的 forks 不含 FIX_ACTUAL_TO_CURATED、CHANGE_CURATED_TO_OBSERVED；summary / 分叉文案使用「身份失联」「未绑定观测候选」「补标」；forks 不得等于空洞的 RESTORE_HEARTBEAT_CHANNEL 集合。允许只读说明分叉（不可选支）。
- 已接受处理人 POST /api/conflicts/{id}/branch-selection 且 forkId 为 FIX_ACTUAL_TO_CURATED 或 CHANGE_CURATED_TO_OBSERVED → 400 IDENTITY_LOST_BLOCKS_BRANCH。非处理人仍 400 PLAN_REQUIRES_ACCEPTED_HANDLER（不要被失联码盖掉）。
- 失联落地时该合并键活跃操作计划（DRAFT_REVIEW / APPROVED / EXECUTING）→ VOIDED；再 approve / start-execution → 400 PLAN_VOIDED。不要新 SSH 步，不要跑 MINA。
- 该对象「运行于」上 OPEN 的 origin=CHANGE_CURATED 草案 → VOIDED；再接受条目 → 400 DRAFT_VOIDED。不要作废 origin=UNBOUND_CANDIDATE 的草案（那是 04 的命中/候选消费，不是本票）。
- 失联清除（04 已有的标签命中）之后，既有诊断/选支恢复：健康冲突上仍可出现修实际 / 改理想。本票不重做命中消费，只断言闸门随清标解除。

本票不做（Out of ticket；发现自己在做就停）：
- 票 09：改 observedAskValue，让失联∧超时改答 HOLLOW
- A1：upgradeOpen 在观测值 B→C 时作废计划（健康对象、未失联）
- HTTP 总 tracer（06）、薄 UI（07）、改策展 07
- 重做 01–04 / 08；不要删 04 的清标/消费
- 新 ConflictStatus；把 IDENTITY_LOST 写入 observed_fact.availability；把失联写成观测空洞或观测消失
- 执行引擎进程、AI 编排层、B-live 代发、步骤断言、控制面 WebClient/LLM、新的生产直连 SSH API
- Y2 策展对齐步骤、网络可达、K8s/数据库对象、自我迭代、LangChain
- 改已有 V*.sql；改 CONTEXT.md / 已有 ADR 正文
- Maven、JPA 当地基、Vue、Neo4j v1 必选、Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内/合同术语写作，不发明同义新词）
================================================================================

按序阅读，读完再写第一个测试：

1. AGENTS.md（一次一张；HTTP 主接缝；/implement 驱动 /tdd；ADR-0044 拆分不写入本票）
2. docs/agents/tdd.md（capability 票：必须 witnessed red；禁止为装红灯删除 01–04 / 08 / 竖切 / 改策展生产）
3. .scratch/unbound-identity-rebind/issues/05-identity-lost-gates-conflict-pipeline.md
4. docs/specs/unbound-identity-rebind.md — 只取：Testing seams、规范问法与冲突投影、失联 vs 改理想/修实际 pipeline、stories 14–16 / 45–49、Negative 8、Prior art。不要实现 Negative 9–12（04 已做）或票 09
5. CONTEXT.md — 只用：身份失联、未绑定观测候选、观测空洞、观测消失、规范问法、冲突、待确认关闭、操作计划、冲突处理人、草案、逐条确认。Avoid：以现场为准、未绑定处理人、把失联当成空洞/消失
6. docs/adr/0039、0043、0044（0044 只为禁止本票拆进程 / 加回控制面 LLM）、0011、0012、0018、0033、0038
7. .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md — 只取 A2 为本票；A1 / A3 / B 全部停
8. docs/dev-handoff.md（确认 frontier = 未绑定 05）
9. 现行样板（读，不重写范围外行为）：
    - ObservedTruthService（upsertIdentityLost / observedAskValue 的 mark 优先级 / 命中清标）
    - ConflictDetectionService（toResponse、onObservationBecameHollow、upgradeOpen、PENDING_CLOSE）
    - ConflictDiagnosisService + DiagnosisRuleEngine（空洞 = RESTORE_HEARTBEAT_CHANNEL；ABSENT 另有 RESTORE 类；健康 mismatch = FIX_ACTUAL + CHANGE_CURATED）
    - BranchSelectionService + OperationPlanService（选支 / 审批 / startExecution / voidActivePlansForConflict）
    - CuratedDraftService.voidOpenForConflict（改理想草案；不要拿它去作废未绑定草案）
    - ConflictCaseResponse（今日无 identityLost 旗标；observationHollow 绑 SUSPENDED）
    - 测试：UnboundIdentityLostIngestHttpAcceptanceTest、UnboundLabelMatchConsumeHttpAcceptanceTest、ConflictDiagnosisHttpAcceptanceTest、HeartbeatTimeoutHollowHttpAcceptanceTest、OperationPlanReviewHttpAcceptanceTest、ChangeCuratedDraftVoidHttpAcceptanceTest、ConflictPendingCloseHttpAcceptanceTest（或等价关单测试）
    - .cursor/rules/backend-java.mdc

接缝已确认：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle/MockMvc 与 bootRun+curl 是同一条接缝。本票定义完成不要求 SSH fake；夹具最多建到 DRAFT_REVIEW / APPROVED，不要为了本票去 start-execution。

用合同术语。未绑定 ≠ 冲突 ≠ 身份失联 ≠ 观测空洞 ≠ 观测消失。

================================================================================
2. 思想与质量条
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 两侧可用且不等。本票 motto：匹配失败不升冲突；失联不是空洞；旧实际不可当作唯一落点。

身份失联 = 线索失效、无法认回对象。原冲突可保留为失联态：不新增枚举，OPEN 保持 OPEN；PENDING_CLOSE 因为「无法再看见相等」必须退回 OPEN。心跳仍新鲜 ⇒ 通道没死 ⇒ 禁止走空洞挂起 / RESTORE_HEARTBEAT_CHANNEL。处理路径是未绑定草案 + 现场补标（02–04 已有），不是修实际迁宿主、也不是改理想对齐到旧宿主。

栈：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway 只增不改历史、PostgreSQL SSOT。Redis 不作这些行的真相。规则驱动。ADR-0044：本票不拆进程、不加控制面 LLM、不扩大 MINA。

分层：Controller → Service → Mapper；DTO record；DO 不当响应；构造器注入；BusinessException；写操作事务在 service。Agent ingest 仍无用户头。失联标写入与「退回 OPEN / 作废计划 / 作废改理想草案 / 重诊」必须在同一次 ingest 事务内完成，避免 GET 读到半截。

持久化（钉死）：
- 不需要新 ConflictStatus。identity_lost_mark 仍是当前是否失联的状态表（01/04 已有）。
- 不要删 observed_fact 来假装空洞；不要把 IDENTITY_LOST 写入 observed_fact.availability。
- 冲突 GET 的 identityLost 旗标是读模型：subject 在 identity_lost_mark 则 true。
- PENDING_CLOSE + 失联 → 更新 conflict_case.status=OPEN，清 pendingCloseAt；追加冲突事件（已有 UPGRADED 或可复用现有 event type；不要发明合同词）。不要 SUSPENDED。
- 作废计划：复用 OperationPlanService.voidActivePlansForConflict(conflictId, reason)。reason 字面量用 identity_lost（或 identity_lost_subject），不要复用 observation_hollow_heartbeat_timeout。
- 作废改理想草案：复用 voidOpenForConflict；只打 origin=CHANGE_CURATED。未绑定草案不动。
- 重诊：scheduleAsyncDiagnosis，使旧诊断 STALE，新诊断按失联规则生成。
- 本票很可能不需要新 Flyway。冲突 GET 加布尔旗标是 API 投影，不是新列。真要新列才 V21+（当前最新是 V20），只增不改。
- 不要改 V*.sql 历史脚本。

HTTP 形状（钉死；本票尽量不加新路由）：
- 读：GET /api/conflicts/{id}、GET /api/conflicts/by-merge-key、GET /api/conflicts/{id}/diagnosis、GET /api/conflicts/{id}/events、GET /api/conflicts/{id}/operation-plans/active、GET /api/operation-plans/{planId}、GET /api/conflicts/{id}/curated-drafts/open（或现行改理想开放草案 GET）、GET /api/observed/asks/actual-where、GET /api/observed/identity-lost/{id}
- 写（既有）：POST /api/agent/heartbeat、POST /api/conflicts/{id}/claim|acknowledge-and-self-appoint、POST /api/conflicts/{id}/branch-selection、POST /api/operation-plans/{id}/approve、POST /api/curated-drafts/.../items/{id}/accept
- 冲突 GET 增加 data.identityLost: boolean（缺省 false）。不要新增 status 枚举值。
- 错误码（400，信封 success=false、data=null）：
  IDENTITY_LOST_BLOCKS_BRANCH     （本票新码：已接受处理人在失联主体上选 FIX_ACTUAL / CHANGE_CURATED）
  PLAN_REQUIRES_ACCEPTED_HANDLER  （已有；非处理人优先于失联码）
  PLAN_VOIDED                     （已有；失联作废后再审/执行）
  DRAFT_VOIDED                    （已有；改理想草案作废后再审条）
  FORK_NOT_SUPPORTED              （已有；诊断里若只剩只读说明分叉，选它也失败。不要让只读分叉开出计划）
  CONFLICT_NOT_FOUND              （已有）
  DIAGNOSIS_NOT_READY             （已有）
- 禁止本票：start-execution 作为完成定义；新 SSH 路由；/api/workbench 扩命令执行；控制面 LLM。

规则（钉死）：
1. 闸门看 identity_lost_mark，不看显示名，不看 runtimeId。
2. 心跳仍新鲜 + 失联 ⇒ 不是空洞。diagnosis 不得返回 DiagnosisRuleEngine.RESTORE_HEARTBEAT_CHANNEL 那一套。
3. 诊断 forks 不得含 FIX_ACTUAL_TO_CURATED / CHANGE_CURATED_TO_OBSERVED。即使客户端仍 POST 这两个 forkId（旧诊断），选支也必须 IDENTITY_LOST_BLOCKS_BRANCH。
4. 只读说明可以有 forks 条目，但 kind 不得是 FIX_ACTUAL / CHANGE_CURATED；选支必须失败。文案禁止「以现场为准」。
5. 未绑定候选不新开冲突（01/竖切已有，保持）。
6. 标签命中清标后闸门解除，走既有比对 / 诊断（04 已清标；本票只测恢复，不重做消费）。
7. 非处理人门禁不变。
8. 本票不作废未绑定草案，不改绑定记忆。
9. 不要为了让冲突 GET 好看而调用 onObservationBecameHollow。
10. A1 不在范围：健康对象观测从 B 变 C 时，upgradeOpen 行为保持今天这样（作废草案 + 重诊，不作废计划），除非那次变化伴随着本主体失联。

测试质量：
- 只测公开 HTTP。期望值用字面量：OPEN、PENDING_CLOSE、VOIDED、true/false、FIX_ACTUAL_TO_CURATED、CHANGE_CURATED_TO_OBSERVED、RESTORE_HEARTBEAT_CHANNEL、IDENTITY_LOST_BLOCKS_BRANCH、PLAN_VOIDED、DRAFT_VOIDED、PLAN_REQUIRES_ACCEPTED_HANDLER、PRESENT、IDENTITY_LOST、「身份失联」。
- 不测 Mapper、Redis key、私有方法。
- 一圈一条行为。禁止先铺完全部测试再实现。
- 新测试类：com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest。不要把 05 塞进 01/04/竖切测试类。
- @HttpAcceptanceTest 库 AFTER_CLASS 才清：每个方法的 host/container/agent/runtime 必须唯一。
- 测试名描述能力。
- 第一圈必须是本票增量的诚实红灯：例如失联后 GET /api/conflicts/{id} 期望 identityLost=true（或等价 observedValue 不再 PRESENT），今天没有该旗标 / 仍报旧宿主。禁止用「未认证 401」「空洞 SUSPENDED 已绿」冒充第一圈。

建议循环顺序（可按 red 教学调整，但每圈仍一条）：
A. 已有 OPEN 冲突的主体随后失联 → 冲突 GET identityLost=true、status=OPEN、非空洞、observedValue 非 PRESENT
B. PENDING_CLOSE 主体随后失联 → status=OPEN（不是 SUSPENDED），identityLost=true
C. 失联后诊断不含修实际/改理想，也不含空洞恢复通道集
D. 已接受处理人选 FIX_ACTUAL → 400 IDENTITY_LOST_BLOCKS_BRANCH
E. 已接受处理人选 CHANGE_CURATED → 400 IDENTITY_LOST_BLOCKS_BRANCH
F. 非处理人选支仍 PLAN_REQUIRES_ACCEPTED_HANDLER
G. 失联前已有活跃计划 → 失联后 GET 计划 VOIDED；再 approve → PLAN_VOIDED
H. 失联前已有开放改理想草案 → 失联后 VOIDED；再审条 DRAFT_VOIDED
I. 未打标同名仍不新开冲突（回归）
J. 标签命中清标后，新诊断再次包含修实际/改理想（闸门解除；不要重做 04 消费细节）

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；只跑这一方法；把完整命令与失败输出追加到票 ## Comments。
2. Green：最少生产代码。
3. Refactor：不改行为；再跑同一条仍绿；提交这一圈（why）。

必须保持绿：
- UnboundIdentityLostIngestHttpAcceptanceTest、UnboundDraftCreateHttpAcceptanceTest、UnboundDraftItemReviewHttpAcceptanceTest、UnboundBindGateHttpAcceptanceTest、UnboundLabelMatchConsumeHttpAcceptanceTest
- HeartbeatTimeoutHollowHttpAcceptanceTest、ConflictDiagnosisHttpAcceptanceTest、OperationPlanReviewHttpAcceptanceTest、ChangeCuratedDraft*HttpAcceptanceTest、VerticalSliceHttpE2eAcceptanceTest、ControlledSshExecHttpAcceptanceTest
- 不要为了红灯删除 01 的 mark 优先、04 的清标、或空洞挂起。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 想改 observedAskValue 让超时压过失联 → 停，那是票 09。
- 想在 upgradeOpen 里无条件 voidActivePlans → 停，那是 A1。
- 想把失联走 onObservationBecameHollow / SUSPENDED → 停。
- 想加 ConflictStatus.IDENTITY_LOST → 停。
- 想做 B-live / 编排层 / MINA 迁出 / WebClient → 停。
- 想做 06 tracer 或 07 UI → 停。
- 想问用户错误码 / 旗标名 → 用本节钉死的 IDENTITY_LOST_BLOCKS_BRANCH 与 identityLost。

Git：从已含票 04 的最新 origin/main 开分支。Cloud 分支名须匹配 cursor/<slug>-<本 run 指定后缀>。建议 slug：tdd-implement-unbound-05。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres。单测：
cd backend && ./gradlew test --tests com.archops.conflict.IdentityLostPipelineGateHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew cleanTest test

认证：Header X-ArchOps-User-Id。一般 = user-general-demo，高级 = user-senior-demo。Agent POST /api/agent/heartbeat 不带头。

夹具提醒：先用标签命中把容器打到非策展宿主以开出 OPEN 冲突并认领；再从范围内宿主上报未打标快照以打失联。PENDING_CLOSE 夹具：先对齐再失联。不要用心跳超时扫描来模拟失联。

Comments 模板（每一圈追加）：
### Cycle <字母> — <行为一句>
Red command:
<gradle 命令>
Red output:
<失败原因摘要 + 关键断言>
Green: <最少改动>
Refactor: <做了什么 / 无>
Commit: <hash 或 message>

票结束：
- 验收清单全部勾掉；Status: done
- 更新 docs/dev-handoff.md：frontier 下一张是 06（tracer）；09 仍待人排期，不要自动做
- /code-review（Standards + Spec），对照本票清单与 Spec Negative 8
- 不要改 CONTEXT / ADR；不要开 06 除非用户下一条明确说 /implement 06
```
