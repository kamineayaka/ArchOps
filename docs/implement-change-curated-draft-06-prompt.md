# 新对话：改策展票 06（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

01–05 已按 TDD 重做并 `done`（05 已合入 `main`，PR #72）。**本票是套件票，不是能力票。** 本刀定义完成 = 一条有序 HTTP tracer（happy path 1–10）+ Spec 负面最小集（1–8）钉在 CI 上。诚实做法是把组合路径写进新套件并 **跑出** 结果：首次即绿记 `reuse/regression`；首次即红才是 01–05 的组合缺口。**禁止**删除 01–05 生产代码或聚焦测试来制造红灯。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD 纪律实现改策展 frontier 工单 06——HTTP 主接缝有序 tracer（happy path + 负面最小集）。质量优先于速度：每一圈都必须先跑测试并留下输出；没有每圈 refactor 的实现不算完成；票外行为一律不做。

本票是套件票。`docs/agents/tdd.md` 的「TDD redo：若已绿则先去掉该票生产行为」只适用于被重开的能力票，不适用于本票。禁止拆掉 01–05 的策展写入、比对、作废、分叉或建底拒绝来装红灯。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md（含 Suite / tracer tickets）
- 票结束再用 code-review skill（Standards + Spec）

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把下一刀 Spec / Y2 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/change-curated-draft/issues/06-http-tracer-acceptance.md

Matt 位置：主路径 idea → ship 已走完 grilling / to-spec / to-tickets。竖切 MVP 01–13 已闭合。改策展 01–05 TDD-done（05 已合入 main，PR #72）。本对话是 /implement 的一张 frontier 票，内部驱动 /tdd。只做 06。本刀在 06 完成后闭合。

本票一句话：用控制面公开 HTTP（含 Agent ingest）把本刀故事按序钉死——已接受处理人选改理想 → 草案逐条确认 → 接受即写策展 → 立刻比对进入待确认关闭 → 复用既有确认关闭；再钉 Spec 负面 1–8。不以浏览器自动化或 SSH fake 作为完成定义。

本票交付（用户可感知、HTTP 可断言、可在 CI 稳定跑通）：
- 新开本刀套件类（建议 com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest）。风格对齐既有 *HttpAcceptanceTest 与 VerticalSliceHttpE2eAcceptanceTest：统一信封、只断言后续 HTTP 可读状态。不要改、不要并进 com.archops.slice.VerticalSliceHttpE2eAcceptanceTest。
- 一条有序 happy path 方法，覆盖 Spec HTTP tracer 1–10（见 §4 步骤 B）。方法内按步注释 1–10，一步一断言。
- 负面各一条独立方法（负面 4 拆成两方法：开放草案挡住 FIX_ACTUAL；活跃计划挡住改理想）。每方法自建夹具，不依赖上一条方法的静态可变状态。
- 断言只落 HTTP 状态码、ApiResponse 信封、后续 GET 可读状态。不测 MyBatis/Redis/私有调用图。不把前端自动化当完成门槛。
- 01–05 聚焦测试与竖切 13 必须保持绿。禁止删除它们「合并进 06」。

本票不做：
- 实现新的业务能力（应已由 01–05 交付）
- Playwright / 浏览器自动化主接缝
- SSH fake 作为第二接缝；不要 @Autowired RecordingFakeSshPort；不要 start-execution / approve 计划来「跑完修实际」
- 重写竖切票 13；不要把改策展故事塞进 VerticalSliceHttpE2eAcceptanceTest
- Y2 策展对齐步骤；选改理想后再出 SSH 计划；自我迭代
- 把空洞/观测消失收成策展「不存在」；给条目增加 VOIDED 状态
- 改 CONTEXT / ADR 语义；改已有 V*.sql
- 本对话结束后自行 /to-spec 下一刀

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内术语写作，不发明同义新词）
================================================================================

按序阅读，读完再改代码：

1. AGENTS.md
2. docs/agents/tdd.md（Cycle、Witnessed red、TDD redo、Suite / tracer tickets）
3. .scratch/change-curated-draft/issues/06-http-tracer-acceptance.md
4. docs/specs/change-curated-draft.md — 只取：Testing seams (confirmed)、HTTP tracer happy path 1–10、Negative / evolution 1–8、Out of Scope、Acceptance motto。不要把 Out of Scope 做成产品。
5. CONTEXT.md — 只用：策展真相、观测真相、冲突、冲突升级、观测空洞、心跳、运行于、应该在哪、实际在哪、草案、逐条确认、已接受的冲突处理人、待确认关闭、规范问法、操作计划。禁止「以现场为准」「待确认策展」「裁定」。
6. docs/adr/0039-domain-contract-frozen.md
7. docs/adr/0043-tech-stack.md
8. docs/adr/0006-curated-writes-via-itemized-proposals.md（未确认条目不是策展；已接受的才是）
9. docs/adr/0009-dual-track-ideal-vs-actual-deviation.md（偏差；规范问法）
10. docs/adr/0019-conflict-close-and-curated-align-step.md（待确认关闭后再漂须退出该态；本刀不含 Y2）
11. docs/adr/0038-ai-power-vs-capability-and-iteration.md（AI 不能独自定稿策展）
12. docs/dev-handoff.md（确认 frontier = 06）
13. 现行样板（读，不重写 01–05 / 竖切故事）：
    - backend/src/test/java/com/archops/slice/VerticalSliceHttpE2eAcceptanceTest.java（有序 @Order、每方法独立夹具、统一信封；本票学风格，不要抄 FIX_ACTUAL+SSH 闭环）
    - backend/src/test/java/com/archops/curated/ChangeCuratedDraftHttpAcceptanceTest.java（03：出草案、不写策展、无计划、非处理人/待接受不能选、FIX_ACTUAL 仍出计划、挡板）
    - backend/src/test/java/com/archops/curated/ChangeCuratedDraftItemHttpAcceptanceTest.java（04：非处理人不能审条、拒 Y、接受 X、立刻 PENDING_CLOSE、confirm-close）
    - backend/src/test/java/com/archops/curated/ChangeCuratedDraftVoidHttpAcceptanceTest.java（05：B→C 作废、空洞、待确认关闭后再漂、DRAFT_VOIDED）
    - backend/src/test/java/com/archops/curated/CuratedTruthHttpAcceptanceTest.java（01：CURATED_RUNS_ON_EXISTS）
    - backend/src/test/java/com/archops/conflict/ConflictAssignTransferHttpAcceptanceTest.java（PENDING_ACCEPT 夹具：acknowledge + assign-handler；转让）
    - com.archops.conflict.ConflictDiagnosisWait
    - com.archops.support.HttpAcceptanceTest（RefreshMode.AFTER_CLASS → 每方法唯一对象 id）
    - .cursor/rules/backend-java.mdc

接缝已确认，不必再问用户：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle MockMvc 与 bootRun+curl 是同一条接缝。薄 UI 手工/冒烟，不进本票 CI。本票无新 SSH 接缝。空洞用既有扫描 API，不要睡墙钟。

================================================================================
2. 思想与质量条（完成标准：后续每一步都能对照这一节说「满足」）
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 偏差。草案在确认前不是策展真相。确认单位是条目，不是整单。选支不写策展；接受的条目才写；拒绝的不写；写入后立刻比对；相等只进待确认关闭，不得自动 CLOSED。升级/空洞作废未完成草案。空洞 ≠ 冲突；空洞挂起不关闭。升级是同一合并键一条脉络，禁止并行第二条开放冲突。

栈（ADR-0043）：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway、PostgreSQL SSOT。Redis 不作关系真相 / 草案 SSOT。本票不引入 Maven、JPA 当地基、Vue、Neo4j、LangChain。

分层：本票默认不写新 Controller/Service。若某圈红灯证明 01–05 有组合缺口，只补到既有 01–05 语义（同一错误码、同一状态机），禁止发明新业务码、新表、新选支、新条目类型。DTO 仍用 record；DO 不当响应。

测试质量：
- 测公开 HTTP：状态码、ApiResponse、后续 GET（应该在哪、实际在哪、冲突 status/id/lineage、草案 status/items、诊断 forks、活跃计划、事件、活跃冲突条数）。
- 期望值来自独立真相：字面量 OPEN / PENDING_CLOSE / SUSPENDED / CLOSED / VOIDED / PENDING / ACCEPTED / REJECTED / HOLLOW / PRESENT、宿主 A/B/C、forkId FIX_ACTUAL_TO_CURATED 与 CHANGE_CURATED_TO_OBSERVED、kind FIX_ACTUAL 与 CHANGE_CURATED、错误码见 §5。禁止用实现再算一遍期望。
- 不测 Mapper SQL、Redis key、私有方法、调用图，不打开数据库断言事务。
- 一圈一条套件方法。禁止一次写完全部测试再跑。
- 不 mock 本模块协作对象。用现有 @HttpAcceptanceTest。
- 01–05 与竖切升级/空洞/计划作废/HTTP E2E 必须保持绿。

套件票的 TDD 循环（每一圈三条全做；红灯不是装的）：
1. Run：写一条套件方法；只跑这一条。把完整命令与输出追加到票 ## Comments。
2. 判定：
   - 红（编译失败或断言失败）且失败原因是缺行为 / 组合缺口 → 这是诚实红灯。最少生产修补到既有 01–05 语义。再跑绿。Comments 标明 gap，点名缺的是哪条 01–05 合同，不要新造产品。
   - 红且失败原因是测试写错（路径笔误、夹具 id 碰撞、等诊断不够）→ 修测试，再跑。修完即绿则记 reuse/regression，不要为此改生产。
   - 绿 → Comments 写 reuse/regression，并点名已覆盖它的 01–05（或竖切）测试方法全名。本圈交付是把该行为钉进本刀 tracer，不是假装红过。禁止为了「看起来像 TDD」去删生产。
3. Refactor：不改行为（抽取本类 private 夹具、命名）。再跑同一条仍绿。然后提交这一圈。
4. 下一圈。禁止把 01–05 聚焦测试删掉合并进 06。

Witnessed run 是硬门。没跑过的套件方法不算完成。已经绿的 01–05 测试不能事后称作本票 TDD 完成——本票要的是新套件里的方法。/code-review 是票结束第二道门，替代不了每圈 refactor。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环（一次一条方法）。
- 全量测试红了 → 先修到绿，再开下一圈。
- 想「顺便」开 Y2 / 新 Spec / 自我迭代 → 不做。
- 想删除 01–05 生产来制造红灯 → 禁止。
- 想把 06 写进 VerticalSliceHttpE2eAcceptanceTest → 禁止。
- 想为 FIX_ACTUAL 负面跑 SSH / start-execution → 禁止。只断言选支 HTTP：skipsDraft、branchKind=FIX_ACTUAL、GET open=DRAFT_NOT_FOUND、GET active plan 200。
- 想用 Thread.sleep 等空洞 → 禁止。@TestPropertySource heartbeat-timeout=30s，hollow-scan-interval-ms=3600000；回拨 lastHeartbeatAt；POST /api/observed/scan-heartbeat-timeouts。
- 想给条目加 VOIDED / 把 GET /open 返回 VOIDED 当开放草案 → 禁止。
- 想把空洞收成策展 ABSENT → 禁止。
- 想跨方法共享静态 conflictId/hostId → 禁止。@HttpAcceptanceTest 是 AFTER_CLASS 刷新。每方法前缀唯一：ccd06-hp-、ccd06-n1- … ccd06-n8-。
- 想抽 support 包削弱 01–05 断言 → 禁止。可把重复夹具抽到本类 private 或 support，但 01–05 测试文件的断言一行都不能变弱。

Git：从最新 main 拉分支 cursor/tdd-implement-change-curated-06-…（Cloud 可能追加后缀）。每圈提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres。单测：
cd backend && ./gradlew test --tests com.archops.curated.ChangeCuratedDraftTracerHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew test

认证：X-ArchOps-User-Id。已接受处理人默认 user-general-demo（认领）。非处理人 user-senior-demo。待接受/第二一般角色 user-general-2-demo（V12 已有）。不要新造用户体系。

================================================================================
3. 现状（完成标准：你能指出「06 还缺哪条套件 HTTP」，以及每圈首次运行预期）
================================================================================

已有（01–05 / 竖切，保持绿；06 套件应复用其 HTTP 形状，不要第二套 API）：

01  CuratedTruthHttpAcceptanceTest
    bootstrapPostRejectsOverwriteToDifferentHost / ToSameHost
    → POST /api/curated/facts/runs-on 已有事实 → 400 CURATED_RUNS_ON_EXISTS

02  诊断同时给出 FIX_ACTUAL 与 CHANGE_CURATED（ConflictDiagnosisHttpAcceptanceTest / 规则引擎）

03  ChangeCuratedDraftHttpAcceptanceTest
    acceptedHandlerSelectsChangeCuratedOpensDraftWithTwoPendingRunsOnItems
    selectChangeCuratedDoesNotWriteCuratedShouldWhere
    selectChangeCuratedDoesNotCreateActiveOperationPlan   GET active → 400 PLAN_NOT_FOUND
    nonHandlerCannotSelectChangeCurated                   400 PLAN_REQUIRES_ACCEPTED_HANDLER
    pendingHandlerCannotSelectChangeCurated               acknowledge+assign-handler → PENDING_ACCEPT 后再选
    openDraftBlocksFixActualSelection                     400 OPEN_DRAFT_BLOCKS_FIX_ACTUAL
    fixActualStillSkipsDraftAndCreatesOperationPlan       plan status DRAFT_REVIEW，skipsDraft=true，branchKind=FIX_ACTUAL
    activePlanBlocksChangeCuratedSelection                400 PLAN_ALREADY_ACTIVE
    openDraftRejectsSecondChangeCuratedSelection          400 DRAFT_ALREADY_OPEN（本票清单未单列；不要扩成新产品，也不必新开一圈，除非 happy path 误写出第二份草案）

04  ChangeCuratedDraftItemHttpAcceptanceTest
    nonHandlerCannotAcceptDraftItem / nonHandlerCannotRejectDraftItem
    acceptedHandlerRejectsSiblingDoesNotWriteCurated
    acceptedHandlerAcceptsMergeKeyWritesCuratedShouldWhereToObservedHost
    acceptMergeKeyComparesImmediatelyToPendingCloseWithoutNewSnapshot
    acceptedHandlerConfirmCloseAfterDraftAcceptClosesConflict
    POST /api/conflicts/{id}/confirm-close；非处理人 CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER

05  ChangeCuratedDraftVoidHttpAcceptanceTest
    snapshotBtoCWhileDraftPending…（升级、VOIDED、策展仍 A）
    heartbeatTimeoutWhileDraftOpenSuspendsConflictAndVoidsDraft
    acceptMergeKeyThenSnapshotCLeavesPendingCloseKeepsCuratedBAndVoidsDraft
    GET open 作废后 400 DRAFT_NOT_FOUND；GET by id 200 VOIDED；审条 400 DRAFT_VOIDED

竖切 13  VerticalSliceHttpE2eAcceptanceTest — 修实际 + SSH fake。本票不改它。

本票缺口：没有本刀有序总套件。能力已在 01–05。因此步骤 B 的 happy path **预期首次即绿**（reuse）。若红，优先查夹具（id 碰撞、没等诊断、X/Y item 认错），仍红才是组合缺口。

可能的真缺口（不要预写产品；只在该圈红了再补，语义必须已存在）：
- 负面 2「待接受不能接受/拒绝条目」：03 只钉了不能 select；04 只钉了非处理人。CuratedDraftService.requireAcceptedHandler 要求 HandlerAcceptance.ACCEPTED 且 actor 即处理人，待接受按合同应已拒绝。06 必须在套件里 HTTP 钉死。夹具：已接受处理人出草案后 POST /api/conflicts/{id}/transfer-handler 给 user-general-2-demo → PENDING_ACCEPT，再以其身份 POST accept 与 reject → 400 PLAN_REQUIRES_ACCEPTED_HANDLER，策展仍为 A。不要新错误码。若首次即绿，记 reuse。

Flyway：禁止改已有 V*.sql。本票预期无新脚本。无新表则不要空增版本。

HTTP 形状（Spec 默认，不要同时发明第二套）：
- POST /api/curated/hosts、/containers、/facts/runs-on
- Agent 心跳 + 快照 ingest（X 在 B；负面 6/8 再到 C）
- GET  /api/conflicts/by-merge-key?subjectId=&relationType=RUNS_ON
- POST /api/conflicts/{id}/claim
- GET  /api/conflicts/{id}/diagnosis     等 READY：ConflictDiagnosisWait.waitUntilReady
- POST /api/conflicts/{id}/branch-selection   {"forkId":"CHANGE_CURATED_TO_OBSERVED"} 或 FIX_ACTUAL_TO_CURATED
- GET  /api/conflicts/{id}/curated-drafts/open
- GET  /api/conflicts/{id}/curated-drafts/{draftId}
- POST /api/conflicts/{id}/curated-drafts/open/items/{itemId}/accept|reject
- GET  /api/conflicts/{id}/operation-plans/active
- POST /api/conflicts/{id}/confirm-close
- POST /api/observed/scan-heartbeat-timeouts
- GET  /api/curated/asks/should-where?containerId=
- GET  /api/observed/asks/actual-where?containerId=
- GET  /api/conflicts、/api/conflicts/{id}、/api/conflicts/{id}/events
- POST /api/conflicts/{id}/acknowledge、/assign-handler、/transfer-handler（仅负面 2 夹具）

夹具：新建 ChangeCuratedDraftTracerHttpAcceptanceTest。
- @HttpAcceptanceTest
- @TestMethodOrder(MethodOrderer.OrderAnnotation.class) 只表达 Spec 顺序，不表达共享状态
- 空洞相关方法需要 HostAgentMapper 回拨；类上 @TestPropertySource 与 05/竖切 13 相同
- 不要注入 RecordingFakeSshPort
- 对象 id 前缀 ccd06- 加方法短码，避免 AFTER_CLASS 刷新前碰撞
- 识别条目：GET open 的 data.items[] 用 subjectId 对应容器 X / Y，不要用列表下标赌顺序
- 宿主 C：POST /api/curated/hosts 再心跳+快照；不同 host 用不同 agentId（例如 agent-{objectX}-c），学 ConflictWarnUpgradeHttpAcceptanceTest / 05 snapshotXOnHostC
- 空洞圈不要换 agent：回拨夹具里写观测的那一个（"agent-"+objectX）
- 活跃冲突条数：GET /api/conflicts，按 mergeKey.subjectId 计数（含 OPEN / PENDING_CLOSE / SUSPENDED）。升级后必须仍为 1

================================================================================
4. 步骤（按序；每步有完成标准。未完成不准跳到下一步）
================================================================================

### 步骤 A — 读完 §1 再写第一条套件方法

完成标准：能指出 03 如何出草案、04 如何认 itemX/itemY 与 confirm-close、05 如何 B→C 与空洞扫描、竖切 13 如何 @Order 却每方法自建对象；能背出本票「不删生产装红、不抄 SSH」。尚未写测试。

### 步骤 B — 第 1 圈：有序 happy path 1–10（一条方法）

一条 @Order(1) 方法，建议名：
happyPath_hostsAB_curatedRunsOnA_snapshotXOnB_claim_changeCurated_rejectY_acceptX_pendingClose_confirmClose

独立前缀例如 ccd06-hp-。方法内按下列 10 步顺序断言（可作注释编号；不要拆成 10 个 @Test）：

1. 建底：主机 A/B；容器 X、Y（带 archops.object_id / 对象标签）；策展 X/Y 皆 运行于 A。GET 「应该在哪」X、Y 均为 A。
2. Agent 快照：仅 X 在 B（Y 不必冲突）。GET by-merge-key：冲突 OPEN，curatedValue.hostId=A，observedValue.hostId=B，availability=PRESENT，relationLabel=运行于。诊断可仍 PENDING；冲突警告在诊断完成前即可存在。
3. 一般角色 POST claim → 已接受处理人（handlerAcceptance=ACCEPTED，handlerUserId=user-general-demo）。
4. ConflictDiagnosisWait 等到 READY。GET diagnosis：forks 同时含 FIX_ACTUAL（id=FIX_ACTUAL_TO_CURATED）与 CHANGE_CURATED（id=CHANGE_CURATED_TO_OBSERVED）。
5. 处理人 POST branch-selection forkId=CHANGE_CURATED_TO_OBSERVED。GET open：status=OPEN，items ≥2，X 与 Y 均为 PENDING、kind 为 运行于目标变更、fromHost=A、toHost=B。GET 「应该在哪」X 仍为 A。GET operation-plans/active → 400 PLAN_NOT_FOUND。
6. 非处理人 user-senior-demo POST accept 合并键 X 的条目 → 400 PLAN_REQUIRES_ACCEPTED_HANDLER；GET 「应该在哪」X 仍为 A。
7. 处理人 POST reject Y 的条目 → 200，该条 REJECTED；GET 「应该在哪」Y 仍为 A。
8. 处理人 POST accept X 的条目 → 200，该条 ACCEPTED；GET 「应该在哪」X 为 B；Y 仍为 A。
9. 不发新快照。GET 冲突 → status=PENDING_CLOSE（不是 CLOSED）。双轨可读：策展 B = 观测 B。
10. 处理人 POST confirm-close → 200，status=CLOSED。再 GET 冲突仍 CLOSED。证明第 9 步未自动关单；复用竖切关单，不重做产品化。

不要在本方法里：选 FIX_ACTUAL、发宿主 C、扫空洞、非处理人选支（那是负面圈）。

首次运行：预期绿（reuse 03+04 组合）。Comments 必须贴命令与 BUILD SUCCESSFUL（或意外的红灯）。若红：先修夹具；夹具正确仍红 → 最少补生产到 01–05 语义。
Refactor（本类 helper：建底、心跳、认领、等诊断、按 subject 取 item），提交。

完成标准：CI 上存在一条从建底到 CLOSED 的改理想有序 HTTP 路径；01–05 测试未删。

### 步骤 C — 第 2 圈：负面 1 — 选择改理想不改变策展

@Order(2)。独立夹具。已接受处理人选 CHANGE_CURATED 之后、任何 accept 之前：GET 「应该在哪」X 与 Y 仍为 A；GET open 两条都 PENDING。不要在本方法里 reject/accept。

对应 03 selectChangeCuratedDoesNotWriteCuratedShouldWhere。预期 reuse。仍须在 06 套件里钉死（本刀定义完成不能只靠 03 文件名）。
Refactor，提交。

### 步骤 D — 第 3 圈：负面 2 — 非处理人 / 待接受不能选、也不能审条

本圈允许同一方法内两段夹具，或拆成两个 @Test（若拆，本圈只做完两个再进入步骤 E；不要把「不能选」与「不能审」合成一条含糊断言）。

必须覆盖：
- 非处理人 senior 对已认领冲突 POST CHANGE_CURATED → PLAN_REQUIRES_ACCEPTED_HANDLER（03 已有）。
- 待接受：senior acknowledge + assign-handler 给 general → handlerAcceptance=PENDING_ACCEPT；该 general POST CHANGE_CURATED → PLAN_REQUIRES_ACCEPTED_HANDLER（03 已有）。
- 非处理人不能 accept/reject（04 已有；可在已有 OPEN 草案上用 senior 再钉一次 reject，避免只测 accept）。
- 待接受不能 accept/reject：见 §3 转让夹具。这是最可能的诚实红灯。红了只许复用 PLAN_REQUIRES_ACCEPTED_HANDLER，不许新码。绿了写 reuse，点名 requireAcceptedHandler / 04。

完成标准：合同「仅已接受处理人可开草案与审条」在 06 套件上可经 HTTP 读到；没有第三种角色语义。

### 步骤 E — 第 4 圈：负面 3 — FIX_ACTUAL 仍跳过草案并仍创建操作计划

@Order 下一个。独立夹具。处理人 POST FIX_ACTUAL_TO_CURATED：
- 200；data.branchKind=FIX_ACTUAL；data.skipsDraft=true；data.status=DRAFT_REVIEW（或今日计划创建后的既有字面量，与 03 测试一致，不要发明新状态）
- GET open → 400 DRAFT_NOT_FOUND
- GET operation-plans/active → 200，同一计划，branchKind=FIX_ACTUAL

不要 approve、不要 start-execution、不要断言 SSH、不要把计划跑到 PENDING_CLOSE。本刀无新 SSH 接缝。
对应 03 fixActualStillSkipsDraftAndCreatesOperationPlan。预期 reuse。
Refactor，提交。

### 步骤 F — 第 5–6 圈：负面 4 — 挡板（必须拆成两方法）

圈 5a：开放草案挡住 FIX_ACTUAL。先选 CHANGE_CURATED，再选 FIX_ACTUAL_TO_CURATED → 400 OPEN_DRAFT_BLOCKS_FIX_ACTUAL；GET open 仍 OPEN；GET active plan 仍 PLAN_NOT_FOUND。
圈 5b：活跃操作计划挡住改理想。先选 FIX_ACTUAL（出计划即可），再选 CHANGE_CURATED_TO_OBSERVED → 400 PLAN_ALREADY_ACTIVE；GET open 仍 DRAFT_NOT_FOUND。

不要把两挡板写进同一方法。不要执行计划。对应 03 两条测试。预期 reuse。
每圈 refactor + 提交。

### 步骤 G — 第 7 圈：负面 5 — 建底 POST 覆盖已有运行于 → 拒绝

独立夹具，不必出冲突。主机 A/B，容器 Z 策展运行于 A 后，再 POST /api/curated/facts/runs-on 指向 B 或再指向 A → 皆 400 CURATED_RUNS_ON_EXISTS，data=null；GET 「应该在哪」仍为 A。

对应 01。预期 reuse。本圈证明 06 套件覆盖 Spec 负面 5，不是重做 01。
Refactor，提交。

### 步骤 H — 第 8 圈：负面 6 — 草案待审时快照 X 到 C

开放草案、X 与 Y 皆 PENDING。创建宿主 C，快照 X 在 C。然后：
- 同一 conflict id；status 仍 OPEN（不是第二条）；observedValue.hostId=C；curatedValue.hostId=A；lineage 含 B 再 C
- GET /api/conflicts 上该 subject 活跃条数 = 1
- GET 「应该在哪」X、Y 仍为 A
- GET open → 400 DRAFT_NOT_FOUND
- GET by id → 200 status=VOIDED；items 仍 PENDING
- POST accept 记住的 itemX → 400 DRAFT_VOIDED

不要先接受 X。不要 POST confirm-close。
对应 05 snapshotBtoC… 预期 reuse。若红，先查 agentId 是否与 B 撞车。
Refactor，提交。

### 步骤 I — 第 9 圈：负面 7 — 心跳超时 / 空洞且草案开放

开放草案、X 未接受。回拨写 X 观测的 HostAgent.lastHeartbeatAt（减 2 分钟），POST /api/observed/scan-heartbeat-timeouts。然后：
- GET 冲突 status=SUSPENDED（不是 CLOSED、不是 OPEN）；observationHollow=true
- GET 「实际在哪」X 为 HOLLOW（hostId null）；GET 「应该在哪」X 仍为 A（不是「不存在」）
- GET open → DRAFT_NOT_FOUND；GET by id → VOIDED
- POST accept 记住的 itemX → 400 DRAFT_VOIDED

本圈不要创建操作计划。HeartbeatTimeoutHollowHttpAcceptanceTest 与 05 空洞测试必须保持绿。
对应 05 heartbeatTimeoutWhileDraftOpen… 预期 reuse。
Refactor，提交。

### 步骤 J — 第 10 圈：负面 8 — 接受 X 待确认关闭后再快照到 C

处理人接受合并键 X（可先拒 Y 作为 setup，不是本圈新产品）→ 冲突 PENDING_CLOSE，X「应该在哪」=B。再快照 X 在 C。然后：
- 同一 conflict id；status 离开 PENDING_CLOSE（OPEN，不是 CLOSED）
- GET 「应该在哪」X 仍为 B；「实际在哪」X 为 C
- 冲突 GET 双轨同时可读：curatedValue=B，observedValue=C
- 该 subject 活跃冲突仍 1 条
- 草案 GET by id = VOIDED；若 Y 仍 PENDING，POST accept Y → DRAFT_VOIDED，Y「应该在哪」仍为 A
- 不要把本圈做成第二条并行冲突

不要在漂后还指望 confirm-close 成功（竖切已有「不再相等不能关单」）。
对应 05 acceptMergeKeyThenSnapshotC… 预期 reuse。
Refactor，提交。

### 步骤 K — 票级回归与收尾（禁止在此前标 done）

cd backend && ./gradlew test
失败则修到全绿（仍不扩到 Y2 / 新 Spec）。不要为了套件去改弱 01–05 断言。

对照工单清单逐条用本套件方法名勾选。Comments 里每一圈都有命令与输出（绿也要贴）。

/code-review：Standards + Spec。固定点 = 本分支相对 main 的 merge-base。Spec 源：本票 + Spec Testing seams / HTTP tracer 1–10 / Negative 1–8 / Out of Scope。行为错误要修并回归。审查发现「缺新产品」而票写着 Out of this ticket → 不要做。审查发现待接受审条未钉 → 补步骤 D，不要新错误码。

更新文档指针（06 完成后本刀闭合；不要实现下一刀）：
- 本票：Status: done；验收项全勾；Comments 含每圈 run
- docs/dev-handoff.md：改策展 01–06 TDD-done；下一对话不要默认 /implement；写明须用户明示下一刀 Spec
- AGENTS.md 当前工单 / §6：frontier 不再指向 06
- CLAUDE.md 工单行
- docs/agents/issue-tracker.md 表
- .cursor/rules/project-map.mdc、domain-contract.mdc
- docs/specs/change-curated-draft.md 与 docs/specs/vertical-slice-mvp.md 的 Next Matt step（本刀闭合；不要重拆竖切）

完成标准：全量测试绿；票 done；handoff 不再把 06 当 frontier；工作区无票外文件；未开新 Spec；未改竖切 13。

本票无薄 UI 圈。06 验收明确前端不进 CI。不要改 ConflictDetailPage「顺便美化」。

================================================================================
5. HTTP 契约（本票断言用）
================================================================================

建底 / 观测 / 协作（已有）：
- POST /api/curated/hosts、/containers、/facts/runs-on
- Agent 心跳/快照
- POST /api/conflicts/{id}/claim
- POST /api/conflicts/{id}/acknowledge
- POST /api/conflicts/{id}/assign-handler   {"assigneeUserId":"..."}
- POST /api/conflicts/{id}/transfer-handler {"toUserId":"..."}
- GET  /api/conflicts/{id}/diagnosis
- POST /api/conflicts/{id}/branch-selection {"forkId":"CHANGE_CURATED_TO_OBSERVED"|"FIX_ACTUAL_TO_CURATED"}
- GET  /api/conflicts/{id}/curated-drafts/open
- GET  /api/conflicts/{id}/curated-drafts/{draftId}
- POST .../curated-drafts/open/items/{itemId}/accept|reject
- GET  /api/conflicts/{id}/operation-plans/active
- POST /api/conflicts/{id}/confirm-close
- POST /api/observed/scan-heartbeat-timeouts

读（已有）：
- GET /api/curated/asks/should-where?containerId=
- GET /api/observed/asks/actual-where?containerId=
- GET /api/conflicts/{id}、/api/conflicts、/api/conflicts/by-merge-key
- GET /api/conflicts/{id}/events

字面量（独立真相，不要用枚举名拼字符串）：
- 关系文案「运行于」/ RUNS_ON
- 草案 status：OPEN | VOIDED     GET /open 只表示 OPEN
- 条目 status：PENDING | ACCEPTED | REJECTED
- 冲突 status：OPEN | PENDING_CLOSE | SUSPENDED | CLOSED
- 观测 availability：PRESENT | HOLLOW
- 协作 handlerAcceptance：NONE | PENDING_ACCEPT | ACCEPTED
- 诊断 fork：FIX_ACTUAL_TO_CURATED（kind FIX_ACTUAL）、CHANGE_CURATED_TO_OBSERVED（kind CHANGE_CURATED）
- 业务码：PLAN_NOT_FOUND、PLAN_REQUIRES_ACCEPTED_HANDLER、CURATED_RUNS_ON_EXISTS、OPEN_DRAFT_BLOCKS_FIX_ACTUAL、PLAN_ALREADY_ACTIVE、DRAFT_NOT_FOUND、DRAFT_VOIDED、DRAFT_ALREADY_OPEN
- 计划创建（FIX_ACTUAL）：branchKind=FIX_ACTUAL，skipsDraft=true

本票不新造路由。缺路由时 Spring 常给 500 INTERNAL_ERROR「No static resource …」，不是 404——那只应出现在你误写了新路径时；改测试路径，不要借机加产品 API。

================================================================================
6. 停工检查（全部为真才许把票标 done）
================================================================================

- [ ] 每圈 Comments 里有独立的命令与输出（绿写 reuse/regression 并点名 01–05 方法；红写 gap 并修到既有语义）
- [ ] 没有删除 01–05 或竖切生产/聚焦测试来装红灯
- [ ] 没有「先实现新产品后补测」；没有把 06 写成第二套状态机
- [ ] 有序 happy path 1–10 在本刀套件一条方法内 CI 稳定绿：选改理想不写策展、非处理人不能接受、拒 Y 不写、接受 X 立刻「应该在哪」=B、无新快照即 PENDING_CLOSE、confirm-close 才 CLOSED
- [ ] 负面 1–8 均有独立套件方法（负面 4 为两方法）；每方法独立夹具与 ccd06- 前缀
- [ ] FIX_ACTUAL 负面未跑 SSH / start-execution
- [ ] 未改 VerticalSliceHttpE2eAcceptanceTest；竖切 13 仍绿
- [ ] 空洞未把「应该在哪」变成不存在；升级后仍一条冲突
- [ ] 待接受审条（若本票才首次 HTTP 钉死）使用 PLAN_REQUIRES_ACCEPTED_HANDLER，无新码
- [ ] 未改已有 V*.sql；无无故新迁移
- [ ] 未做 Playwright、未做 Y2、未改 CONTEXT/ADR、未开下一刀 Spec
- [ ] 薄 UI 未作为本票完成门槛（也未「顺便」大改前端）
- [ ] ./gradlew test 全绿
- [ ] /code-review 已跑；行为问题已修
- [ ] 文档本刀闭合；06 Status: done；handoff 不再把 06 当下一张实现票
```

---

完成后本刀闭合。不要在 06 的实现对话里 `/to-spec` 下一刀（Y2 策展对齐步骤或其它）。下一刀须用户明示新 Spec。
