# 新对话：未绑定 / 身份失联票 09（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）；本票只取其 overlay 的 **capability / witnessed red**，不要走 06 的 Suite / tracer，也不要走 07 的 UI and helpers
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。本票是 **问法读模型能力票**，不是 suite/tracer，不是薄 UI。循环纪律以 [`docs/agents/tdd.md`](agents/tdd.md) 的 **red → green → refactor** 为准。领域语义以 `CONTEXT.md` 与有效 ADR 为准。

Matt 位置：grilling / to-spec / to-tickets **已完成**。竖切 01–13 与改策展 01–06 已闭合。未绑定票 **01–07 + 08 已闭合**（PR #99 = 票 07 已合入）。本对话只 `/implement` **未绑定刀 frontier = 09**。不要发明未绑定 10，不要给 `change-curated-draft` 加 07，不要把 01–08 / 07 UI / 竖切生产当 TDD redo。不要拆执行引擎 / AI 编排层。

本票带着代码 vs ADR-0044 只读审计（[`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md)）的 **A3**，以及 01–03 合同审计 **C-1**。**A2 已由票 05 交付**。**A1**（升级不作废活跃计划）与 **B1–B5**（0044 进程债）禁止写入本票。若 `AGENTS.md` 仍写「票 09 待人排期」，以 [`docs/dev-handoff.md`](dev-handoff.md) 与本文件为准：frontier = 09。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现未绑定 / 身份失联 frontier 工单 09（失联叠加心跳超时：规范问法仍须说出观测空洞）。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。本票是新能力票，不是 TDD redo，也不是 06 那种 suite/tracer，也不是 07 薄 UI。01–07 与 08 已闭合（PR #99 = 票 07 已合入），禁止删除 01–08 / 07 UI / 竖切 / 改策展生产来装红灯。禁止重做它们的产品行为。票外行为一律不做。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准。若仍写「票 09 待人排期」，以 docs/dev-handoff.md 与本 prompt 为准：frontier = 09）
- implement skill、tdd skill、docs/agents/tdd.md（capability：必须 witnessed red；不要走 Suite / UI and helpers）
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围、路径、表设计、错误码或问法优先级。下面已钉死。不要用 Playwright、SSH fake、computerUse 或薄 UI 当作完成定义。不要发明未绑定 10。不要拆执行引擎 / AI 编排层，不要把 WebClient/密钥加回控制面，不要加 B-live / 步骤断言。不要「顺便」改 05 诊断分叉或 07 页面文案。

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 A1 / 0044 / 未绑定 10 / 诊断分叉 / UI 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/unbound-identity-rebind/issues/09-ask-hollow-when-channel-timed-out.md

Spec：docs/specs/unbound-identity-rebind.md — 只取 Testing seams、规范问法与冲突投影（「Must not be set to HOLLOW or ABSENT solely because of 失联」——反过来说，通道确实超时时 HOLLOW 就是该答的那个值）、User Story 12（失联不得单凭失联编码成空洞）、Out of Scope。
审计（只读，不要改）：
- .scratch/unbound-identity-rebind/audit-01-03-opus.md（C-1 / 探针 5）
- .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md（A3）

最近一次只读架构审计结论（本对话的边界，不是要你修 0044）：
- A3 / 审计 C-1 = 本票。失联叠加心跳超时时，规范问法「实际在哪」仍须说出观测空洞。
- 语义已由票选用（甲）：通道超时优先决定 availability（HOLLOW）；失联仍是旗标 identityLost=true。不要改走（乙）（失联优先、空洞只在诊断里体现）。不要另立 ADR，除非用户明示开议题。
- A2 已由票 05 交付。本票不要重做闸门 / 选支码 / 计划作废。
- A1 = 另开工单。upgradeOpen 在观测 B→C（健康、未失联）时不作废计划。不要顺便修。
- B1–B5 = ADR-0044 过渡债。MINA 仍在控制面、无编排层、无 B-live、无步骤断言。禁止扩大生产直连 SSH，禁止把 DiagnosisLlmClient / WebClient 加回来。

一句话交付：当某 Docker 容器既有身份失联标、其观测通道又确实心跳超时，GET 「实际在哪」不得只答 IDENTITY_LOST：availability=HOLLOW、identityLost=true、observedValue.hostId 为 JSON null、策展「应该在哪」仍同屏。失联不是空洞，但空洞也不能被失联吞掉。只改问法读模型。

本票对应审计 C-1 / 0044 审计 A3。Stories 11–13 的问法读模型已由 01 交付（IDENTITY_LOST 只在 ask DTO）。闸门是 05。HTTP tracer 是 06。薄 UI 是 07。本票不重做它们。

本票交付（用户可感知、HTTP 可断言）：
- 失联标存在 + 该对象策展「运行于」（或失联标 sourceHostId）宿主的 Agent 已超时 → GET /api/observed/asks/actual-where：observedValue.availability=HOLLOW、identityLost=true、observedValue.hostId 为 JSON null；GET /api/curated/asks/should-where 仍返回策展宿主（P2）。GET /api/observed/identity-lost/{id} 仍 200（标还在，不要为了答空洞而清标）。
- 失联标存在 + 心跳仍新鲜 → 仍是 IDENTITY_LOST（票 01 的 neverObservedIdentityLostActualWhereIsNotHollow / identityLostActualWhereDoesNotReportStaleObservedHost 不回归）。
- 无失联标 + 心跳超时 → 仍是 HOLLOW（票 10 HeartbeatTimeoutHollowHttpAcceptanceTest 不回归）。
- 观测消失仍是 ABSENT（票 01 absentObjectIdsRemainUsableAbsentNotIdentityLost 不回归）。
- observed_fact.availability 的 CHECK 不变（仍 PRESENT/ABSENT）；不把 IDENTITY_LOST / HOLLOW 写入事实表；不新增 ConflictStatus；不改 CONTEXT.md / 已有 ADR 正文。

本票不做（Out of ticket；发现自己在做就停）：
- 诊断分叉集在失联 / 空洞下的取舍（票 05 的「除非心跳确实超时」；不要为了问法去改 DiagnosisRuleEngine）
- A1：upgradeOpen 在观测值 B→C 时作废计划（健康对象、未失联）
- 标签命中收尾（04）、冲突挂起与计划作废（票 10 已实现，只做不回归）
- 重做 01–08；不要删 01 的 mark 优先（心跳仍新鲜时仍须 IDENTITY_LOST）；不要改 07 页面当完成定义
- 发明未绑定 10；给 change-curated-draft 加 07
- 新 ConflictStatus；把 IDENTITY_LOST 写入 observed_fact.availability；把失联写成观测消失
- 新路由、新错误码、新 Flyway（真要新列才 V21+，只增不改；本票很可能不需要）
- 执行引擎进程、AI 编排层、B-live、步骤断言、控制面 WebClient/LLM、新的生产直连 SSH API
- Y2 策展对齐步骤、网络可达、K8s/数据库对象、自我迭代、LangChain
- 改已有 V*.sql；改 CONTEXT.md / 已有 ADR 正文
- Maven、JPA 当地基、Vue、Neo4j v1 必选、Redis 当关系真相 SSOT
- Playwright / 组件单测 / 改 07 UnboundCandidatesPage 当作完成定义

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内/合同术语写作，不发明同义新词）
================================================================================

按序阅读，读完再写第一个测试：

1. AGENTS.md（一次一张；HTTP 主接缝；/implement 驱动 /tdd；ADR-0044 拆分不写入本票）
2. docs/agents/tdd.md（capability 票：必须 witnessed red；禁止为装红灯删除 01–08 / 07 UI / 竖切 / 改策展生产）
3. .scratch/unbound-identity-rebind/issues/09-ask-hollow-when-channel-timed-out.md
4. docs/specs/unbound-identity-rebind.md — 只取：Testing seams、规范问法、「不得 solely because of 失联 就答 HOLLOW/ABSENT」、Story 12、Out of Scope。不要实现 05 诊断分叉或 07 UI
5. CONTEXT.md — 只用：身份失联、未绑定观测候选、观测空洞、观测消失、规范问法。Avoid：以现场为准、未绑定处理人、把失联当成空洞/消失、把空洞当成失联
6. docs/adr/0039、0043、0044（0044 只为禁止本票拆进程 / 加回控制面 LLM）、0011、0012
7. .scratch/unbound-identity-rebind/audit-01-03-opus.md — 只取 C-1 / 探针 5；不要改报告
8. .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md — 只取 A3 为本票；A1 / B 全部停
9. docs/dev-handoff.md（确认 frontier = 未绑定 09）
10. 现行样板（读，不重写范围外行为）：
    - ObservedTruthService.actualWhere / observedAskValue（今日：lostMark != null 就 IDENTITY_LOST，staleness 根本走不到）
    - ObservationFreshnessService.isObservedFactStale / scanHeartbeatTimeouts（扫描会删除超时 Agent 写下的 observed_fact，然后 onObservationBecameHollow）
    - IdentityLostMark.sourceHostId（失联标来源宿主）
    - 测试：UnboundIdentityLostIngestHttpAcceptanceTest（neverObservedIdentityLostActualWhereIsNotHollow、identityLostActualWhereDoesNotReportStaleObservedHost、absentObjectIdsRemainUsableAbsentNotIdentityLost）、HeartbeatTimeoutHollowHttpAcceptanceTest（含 staleHeartbeatWithoutScanStillHollowsActualWhere）、IdentityLostPipelineGateHttpAcceptanceTest、UnboundIdentityRebindTracerHttpAcceptanceTest
    - .cursor/rules/backend-java.mdc

接缝已确认：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle/MockMvc 与 bootRun+curl 是同一条接缝。本票定义完成不要求 SSH fake、不要求 Vite、不要求改前端。

用合同术语。未绑定 ≠ 冲突 ≠ 身份失联 ≠ 观测空洞 ≠ 观测消失。

================================================================================
2. 思想与质量条
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 两侧可用且不等。本票 motto：匹配失败不升冲突；失联不是空洞；空洞也不能被失联吞掉。

身份失联 = 线索失效、无法认回对象。观测空洞 = 观测通道死了（心跳超时），当前没有可用观测值。两件事可以同时为真：标还在，通道也死了。问法必须两件事都说出来——availability 走 HOLLOW，identityLost 仍为 true——否则运维会被引去「现场补标」，而真相是 Agent 不在了。心跳仍新鲜时，不得把失联答成空洞（那是 01 已钉死的反例）。

栈：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway 只增不改历史、PostgreSQL SSOT。Redis 不作这些行的真相。规则驱动。ADR-0044：本票不拆进程、不加控制面 LLM、不扩大 MINA。

分层：Controller → Service → Mapper；DTO record；DO 不当响应；构造器注入；BusinessException；本票是只读问法投影，不要在 GET 里删标或删事实。

持久化与判据（钉死）：
- 只改问法读模型。不要改 observed_fact.availability 取值域。不要新 ConflictStatus。不要清 identity_lost_mark 来假装空洞。
- 不要只看「还剩没有 observed_fact」。票 10 扫描会删除超时 Agent 写下的事实；扫描后读模型无法靠观测行区分「通道已死」与「从未观测」。
- 判据：host_agent.lastHeartbeatAt vs archops.observation.heartbeat-timeout。看哪些宿主：策展「运行于」宿主，以及失联标 sourceHostId（若有）。相关上报通道已超时 ⇒ availability=HOLLOW，identityLost 仍 true。
- isObservedFactStale 只在事实行仍在时有用。扫描后 observed == null 且 lostMark != null 时，今日代码直接 IDENTITY_LOST，永远走不到 staleness。本票必须在「有标且事实已空」时仍能根据 host_agent 新鲜度答 HOLLOW。
- 不要改票 10 的扫描删除 / 挂起 / 作废语义。不要在问法路径调用 onObservationBecameHollow。
- 本票很可能不需要新 Flyway。真要新列才 V21+（当前最新 V20），只增不改。不要改 V*.sql 历史脚本。

HTTP 形状（钉死；本票不加新路由、不加新错误码）：
- 读：GET /api/observed/asks/actual-where?containerId=、GET /api/curated/asks/should-where?containerId=、GET /api/observed/identity-lost/{id}
- 写（夹具既有）：POST /api/agent/heartbeat、POST /api/observed/scan-heartbeat-timeouts
- 问法 DTO（本票增量）：data.observedValue.availability 字面量 HOLLOW；data.identityLost=true；data.observedValue.hostId JSON null；data.question=实际在哪；data.track=OBSERVED；data.curatedValue.hostId 仍为策展宿主
- 心跳仍新鲜的失联：availability=IDENTITY_LOST（01 已有；IDENTITY_LOST 只在 ask DTO，不进事实表、不进 ConflictStatus）
- 禁止本票：新 Mapping、新信封 code、start-execution 作为完成定义、新 SSH 路由、/api/workbench 扩命令执行、控制面 LLM、改 07 前端

规则（钉死）：
1. 语义（甲）：通道超时优先决定 availability；失联是旗标。不要改走（乙）。
2. 失联 ∧ 通道新鲜 ⇒ IDENTITY_LOST，不得 HOLLOW / ABSENT / PRESENT。
3. 失联 ∧ 通道超时 ⇒ HOLLOW + identityLost=true，不得只答 IDENTITY_LOST，也不得清标。
4. 无失联 ∧ 通道超时 ⇒ 仍 HOLLOW（票 10）。
5. absentObjectIds 的观测消失 ⇒ ABSENT，不是 HOLLOW，也不是 IDENTITY_LOST（票 01）。
6. 不要用心跳超时扫描来模拟失联。失联仍按 01：范围内宿主上报未打标快照（或不在 absentObjectIds 里的范围内未命中）。
7. 不要为了问法好看去 SUSPEND 冲突或 void 计划。若夹具碰巧开了冲突，票 10 扫描仍可挂起——那是回归，不是本票交付。
8. 不要改 05 诊断 forks。本票不测 RESTORE_HEARTBEAT_CHANNEL。
9. A1 不在范围。
10. 不要发明未绑定 10。

测试质量：
- 只测公开 HTTP。期望值用字面量：HOLLOW、IDENTITY_LOST、ABSENT、true/false、「实际在哪」、「应该在哪」、OBSERVED、CURATED。
- 不测 Mapper、Redis key、私有方法、07 页面。
- 一圈一条行为。禁止先铺完全部测试再实现。
- 新测试类：com.archops.observed.IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest。不要把 09 塞进 01 或 HeartbeatTimeoutHollowHttpAcceptanceTest。
- @HttpAcceptanceTest 库 AFTER_CLASS 才清：每个方法的 host/container/agent/runtime 必须唯一。
- 与票 10 相同的时钟夹具：@TestPropertySource(properties = {"archops.observation.heartbeat-timeout=30s","archops.observation.hollow-scan-interval-ms=3600000"})；HostAgentMapper 把 lastHeartbeatAt 回拨 now-2min；需要扫描时再 POST /api/observed/scan-heartbeat-timeouts。不要真等墙钟。
- 测试名描述能力。
- 第一圈必须是本票增量的诚实红灯：失联 + 回拨该上报宿主 host_agent + POST scan 之后 GET actual-where 期望 HOLLOW，今日生产是 IDENTITY_LOST（审计探针 probe5_identityLostPlusHeartbeatTimeoutIsHollow）。禁止用「未认证 401」「票 10 已绿的无标超时」「01 已绿的新鲜失联」冒充第一圈。

建议循环顺序（可按 red 教学调整，但每圈仍一条）：
A. 范围内宿主未打标快照打失联 → 回拨该 host_agent → POST scan-heartbeat-timeouts → actual-where 为 HOLLOW、identityLost=true、hostId JSON null；should-where 仍策展宿主；identity-lost GET 仍 200。这是第一圈诚实红灯。
B. 失联标存在 + 心跳仍新鲜（不回拨）→ 仍 IDENTITY_LOST，不得变成 HOLLOW（01 语义）
C. 失联标存在 + 回拨 host_agent 但不扫 → actual-where 仍 HOLLOW + identityLost=true（票 10 的 staleHeartbeatWithoutScanStillHollowsActualWhere 在失联叠加下的对应；事实行可能还在）
D. 无失联标 + 超时 → 仍 HOLLOW（保持票 10；已绿则记 reuse/regression，不为装红灯删生产）
E. absentObjectIds → ABSENT，不是 HOLLOW / IDENTITY_LOST（保持票 01；已绿则记 reuse）

夹具提醒：Cycle A 先走 01 的未打标快照打出失联，再回拨那个上报宿主的 host_agent，再扫描。不要先扫再假装失联。不要从范围外宿主打失联（01 已钉 out-of-scope 不打标）。

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；只跑这一方法；把完整命令与失败输出追加到票 ## Comments。
2. Green：最少生产代码。
3. Refactor：不改行为；再跑同一条仍绿；提交这一圈（why）。

必须保持绿：
- UnboundIdentityLostIngestHttpAcceptanceTest、UnboundDraftCreateHttpAcceptanceTest、UnboundDraftItemReviewHttpAcceptanceTest、UnboundBindGateHttpAcceptanceTest、UnboundLabelMatchConsumeHttpAcceptanceTest、IdentityLostPipelineGateHttpAcceptanceTest、UnboundIdentityRebindTracerHttpAcceptanceTest
- HeartbeatTimeoutHollowHttpAcceptanceTest、ConflictDiagnosisHttpAcceptanceTest、OperationPlanReviewHttpAcceptanceTest、ChangeCuratedDraft*HttpAcceptanceTest、VerticalSliceHttpE2eAcceptanceTest、ControlledSshExecHttpAcceptanceTest
- 不要弄红 frontend（本票不应改 07 页）。不要为了红灯删除 01 的新鲜失联 IDENTITY_LOST、04 的清标、或票 10 的空洞挂起。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 想改诊断 forks 让失联+超时改走 RESTORE_HEARTBEAT_CHANNEL → 停，那是 05 留下的「除非超时」，不在本票。
- 想在 upgradeOpen 里无条件 voidActivePlans → 停，那是 A1。
- 想把失联走 onObservationBecameHollow / 清标 / SUSPENDED 来答空洞 → 停。
- 想加 ConflictStatus.IDENTITY_LOST 或把 HOLLOW 写入 observed_fact.availability → 停。
- 想做 B-live / 编排层 / MINA 迁出 / WebClient → 停。
- 想做未绑定 10、重做 06 tracer 或改 07 UI 当完成定义 → 停。
- 想另立「问法优先级」ADR → 停，除非用户明示开议题。本票按（甲）做进读模型。
- 想问用户 availability 与 identityLost 谁优先 → 用本节钉死的（甲）。

Git：从已含票 07 的最新 origin/main（PR #99）开分支。Cloud 分支名须匹配 cursor/<slug>-<本 run 指定后缀>。建议 slug：tdd-implement-unbound-09。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres。单测：
cd backend && ./gradlew test --tests com.archops.observed.IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew cleanTest test

认证：Header X-ArchOps-User-Id。一般 = user-general-demo，高级 = user-senior-demo。Agent POST /api/agent/heartbeat 不带头。

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
- 更新 docs/dev-handoff.md：本刀问法读模型闭合；不要发明未绑定 10；A1 仍另开；不要自动做 A1 / ADR-0044 进程拆分
- /code-review（Standards + Spec），对照本票清单与审计 C-1
- 不要改 CONTEXT / ADR 正文；不要开 A1 或未绑定 10 除非用户下一条明确说
```
