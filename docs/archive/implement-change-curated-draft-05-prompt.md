# 新对话：改策展票 05（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

01–04 已按 TDD 重做并 `done`（04 已合入 `main`，PR #70）。本票是改策展 **第一次** 把竖切已有的「冲突升级 / 观测空洞」接到 **开放草案**：作废未完成草案，而不是重做升级、挂起或计划作废。诚实红灯是「草案仍 OPEN / 作废后仍能审条 / 缺少 GET 作废态」，**不要**去删除 04 的接受写入与比对来制造红灯。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现改策展 frontier 工单 05——升级 / 空洞作废未完成草案；对齐后再漂则同一合并键升级。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md
- 票结束再用 code-review skill（Standards + Spec）

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 06 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/change-curated-draft/issues/05-void-draft-on-upgrade-hollow.md

Matt 位置：主路径 idea → ship 已走完 grilling / to-spec / to-tickets。竖切 MVP 01–13 已闭合。改策展 01–04 TDD-done（04 已合入 main）。本对话是 /implement 的一张 frontier 票，内部驱动 /tdd。只做 05。

示踪（本票主路径，Spec Negative 6–8）：
1. 开放草案、合并键 X 仍 PENDING：快照把 X 的可用观测从 B 改为 C → 同一合并键升级（一条、留脉络），草案作废，策展 X 仍为 A，待确认条目未写入。
2. 开放草案期间心跳超时进入观测空洞 → 冲突挂起（不关闭），草案作废，再接受/拒绝被拒绝。
3. 已接受处理人先接受 X（冲突已待确认关闭、策展已为 B）后再快照 X 运行于 C → 离开待确认关闭，同一合并键升级/重开，不是第二条并行开放冲突；已写入的策展 B 保持；尚未接受的兄弟永远不写。

本票交付（用户可感知、HTTP 可断言）：
- 冲突升级或观测空洞时：改理想选支不再是当前处理路径；开放草案变为 VOIDED；PENDING 条目永远不写策展。
- 作废后不能再接受/拒绝（400，业务码能看出是作废，不是「从来没有草案」）。
- GET 能读到该份草案已作废（status=VOIDED）；GET 开放草案在作废后不再返回 OPEN。
- 已接受条目保持其已写入的策展值；后续比对以新策展值为准（X 已改为 B 后再观测到 C：应该在哪仍为 B，实际在哪为 C）。
- 待确认关闭期间再漂：退出待确认关闭，同一合并键升级/重开；GET /api/conflicts 上该 subject 仍只有一条活跃冲突。
- 空洞路径：冲突 SUSPENDED（不是 CLOSED）；「实际在哪」为 HOLLOW；不得把空洞/观测消失收成「策展改为不存在」。
- HTTP 可读「草案已作废」审计（建议 eventType=DRAFT_VOIDED，hint 含「草案已作废」）。
- 竖切已有的升级脉络、空洞挂起、PLAN_VOIDED 保持绿；本票只把同一触发接到草案。
- 薄 UI：作废后展示 VOIDED，隐藏接受/拒绝。UI 不进自动化主接缝。

本票不做：有序总 tracer 套件（06）、Y2 策展对齐步骤、改策展后再出 SSH 计划、自我迭代、把空洞收成策展「不存在」、重做修实际计划作废、重做升级/挂起/关单产品化、改 CONTEXT/ADR 语义、重做竖切 01–13、重做改策展 01–04。

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内术语写作，不发明同义新词）
================================================================================

按序阅读，读完再改代码：

1. AGENTS.md
2. docs/agents/tdd.md
3. .scratch/change-curated-draft/issues/05-void-draft-on-upgrade-hollow.md
4. docs/specs/change-curated-draft.md — 只取：Testing seams (confirmed)、Compare and 冲突演进、Voiding、Testing Decisions 中 Negative 第 6–8 条（happy path 1–10 已由 01–04 交付；本票不做 06 的有序总套件）
5. CONTEXT.md — 只用：策展真相、观测真相、冲突、冲突升级、观测空洞、心跳、运行于、应该在哪、实际在哪、草案、逐条确认、已接受的冲突处理人、待确认关闭
6. docs/adr/0039-domain-contract-frozen.md
7. docs/adr/0043-tech-stack.md
8. docs/adr/0006-curated-writes-via-itemized-proposals.md（未确认条目不是策展；已接受的才是）
9. docs/adr/0009-dual-track-ideal-vs-actual-deviation.md（偏差；规范问法）
10. docs/adr/0019-conflict-close-and-curated-align-step.md（待确认关闭后再漂须退出该态；本刀不含 Y2）
11. docs/adr/0038-ai-power-vs-capability-and-iteration.md（AI 不能独自定稿策展）
12. docs/dev-handoff.md（确认 frontier = 05）
13. 现行样板（读，不重写 01–04 / 竖切故事）：
    - backend/src/test/java/com/archops/curated/ChangeCuratedDraftItemHttpAcceptanceTest.java（04 夹具：A/B、X/Y、已接受处理人、开放草案、按条 accept/reject）
    - backend/src/main/java/com/archops/curated/service/CuratedDraftService.java（requireOpen；VOIDED 枚举未使用）
    - backend/src/main/java/com/archops/curated/controller/CuratedDraftController.java（仅 GET open + POST accept/reject）
    - backend/src/main/java/com/archops/curated/domain/CuratedDraftStatus.java（OPEN | VOIDED；注释写明 VOIDED 是票 05）
    - backend/src/main/java/com/archops/conflict/service/ConflictDetectionService.java（upgradeOpen、reopenFromPendingClose、onObservationBecameHollow：今日只作废计划，不作废草案）
    - backend/src/test/java/com/archops/conflict/ConflictWarnUpgradeHttpAcceptanceTest.java（B→C 同一 id、脉络；无草案）
    - backend/src/test/java/com/archops/conflict/HeartbeatTimeoutHollowHttpAcceptanceTest.java（回拨 lastHeartbeatAt + POST /api/observed/scan-heartbeat-timeouts；挂起 + PLAN_VOIDED）
    - backend/src/main/java/com/archops/conflict/diagnosis/ConflictDiagnosisService.java 的 scheduleAsyncDiagnosis（升级时旧诊断 STALE）
    - backend/src/main/resources/db/migration/V13__curated_draft_and_items.sql、V14__draft_item_review_events.sql
    - frontend/src/pages/ConflictDetailPage.tsx、frontend/src/api/drafts.ts
    - .cursor/rules/backend-java.mdc、frontend-react.mdc

接缝已确认，不必再问用户：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle MockMvc 与 bootRun+curl 是同一条接缝。薄 UI 手工/冒烟，排在本票 HTTP 循环全绿之后。本票无新 SSH 接缝。空洞用既有扫描 API，不要睡墙钟。

================================================================================
2. 思想与质量条（完成标准：后续每一步都能对照这一节说「满足」）
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 偏差。草案在确认前不是策展真相。确认单位是条目。已接受的条目已是策展；未接受的仍只是草案，升级/空洞时永远不写。空洞 ≠ 冲突；空洞挂起不关闭。升级是同一合并键一条脉络，禁止并行第二条开放冲突。相等不得自动 CLOSED。

栈（ADR-0043）：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway、PostgreSQL SSOT。Redis 不作关系真相 / 草案 SSOT。本票不引入 Maven、JPA 当地基、Vue、Neo4j、LangChain。

分层：Controller → Service → Mapper；DTO 用 record；DO 不当响应；构造器注入；业务错误 BusinessException；写操作事务在 service。升级/空洞与作废草案必须在同一 @Transactional 的可观察结果里：一次快照或一次扫描 POST 之后，冲突态与草案 VOIDED 同时可经 GET 读到。

测试质量（/tdd）：
- 测公开 HTTP 行为：状态码、ApiResponse 信封、后续 GET 可读状态（应该在哪、实际在哪、冲突 status/id/lineage、草案 status、item.status、事件列表、活跃冲突条数）。
- 期望值来自独立真相：字面量 OPEN / PENDING_CLOSE / SUSPENDED / VOIDED / PENDING / ACCEPTED、宿主 A/B/C、「应该在哪」/「实际在哪」。禁止用实现再算一遍期望。
- 不测 Mapper SQL、Redis key、私有方法、调用图，不打开数据库断言事务。
- 一圈一条行为。Spec Negative 6–8 是循环顺序，不是一次写完全部测试再实现。
- 不 mock 本模块协作对象。用现有 @HttpAcceptanceTest。
- 04 的按条接受/比对、03 出草案、01 覆盖拒绝、竖切升级/空洞/计划作废必须保持绿。

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；跑命令；失败原因是「缺本圈行为」。把完整命令与失败输出追加到票 ## Comments。
2. Green：只写让这一条测试通过的最少生产代码。
3. Refactor：不改行为；再跑同一条测试仍绿。然后提交这一圈。

Witnessed red 是硬门。已经绿的测试不能事后称作 TDD 完成。第一圈已绿时：这是复用/回归（竖切升级、待确认关闭再漂、空洞挂起已存在），在 Comments 写明「reuse/regression，未另写第二套引擎」；不要为了「看起来像红灯」去拆 04 或竖切生产行为。/code-review 是票结束第二道门，替代不了每圈 refactor。

04 留下的诚实红灯经验（本票照做，不要假设 404）：
- 缺路由时 Spring 常把路径交给静态资源处理器 → 500 INTERNAL_ERROR，「No static resource …」，不是 404。Comments 照实记录。
- 确认关闭、按 subject 调和等复用圈允许首次即绿。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 全量测试红了 → 先修到绿，再开下一圈。
- 想「顺便」做 06 有序总 tracer → 不做。
- 想复制一套比对/升级/挂起引擎 → 禁止。在 ConflictDetectionService 既有 upgradeOpen / reopenFromPendingClose / onObservationBecameHollow 上接到 CuratedDraftService.voidOpen…（名称自定）。检测服务已 @Lazy 注入 OperationPlanService；作废草案同样避免循环依赖，不要把 void SQL 写进 detection。
- 想拆掉 04 的 accept/reconcileMergeKey 来制造红灯 → 禁止。
- 想给条目增加 VOIDED 状态 → 禁止（V13 item CHECK 只有 PENDING/ACCEPTED/REJECTED）。作废门在草案 status=VOIDED；PENDING 条目保持 PENDING 但不可再审。
- 想把 GET /open 改成返回 VOIDED 当「开放草案」→ 禁止。/open 只表示 OPEN。作废后 GET open = 没有开放草案。该份草案用 GET by id 读 VOIDED。
- 想在空洞时改策展为 ABSENT/「不存在」→ 禁止。
- 想重写 HeartbeatTimeoutHollowHttpAcceptanceTest 或计划作废语义 → 禁止。本票夹具走改理想草案，不要为了测草案去开 FIX_ACTUAL 计划。
- 想在本票实现「作废后再选改理想、针对 C 出第二份草案」→ 禁止（超出 05；03 的「至多一份 OPEN」在 VOIDED 后允许新 OPEN，但本票不写这条故事）。

Git：从最新 main 拉分支 cursor/tdd-implement-change-curated-05-…（Cloud 可能追加后缀如 -3e7a）。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres。空洞测试加 @TestPropertySource：archops.observation.heartbeat-timeout=30s，archops.observation.hollow-scan-interval-ms 设大（例如 3600000），用 POST /api/observed/scan-heartbeat-timeouts 驱动，不要 Thread.sleep。单测：
cd backend && ./gradlew test --tests com.archops.curated.<Class>.<method>
票结束：
cd backend && ./gradlew test

================================================================================
3. 现状（完成标准：你能指出「05 还缺哪条 HTTP」，以及红灯应长什么样）
================================================================================

已有（01–04 / 竖切，保持绿）：
- POST 按条 accept/reject；接受走 applyAcceptedDraftRunsOn（不是建底 POST）；同一事务调用 reconcileMergeKey；相等 → PENDING_CLOSE，永不 auto CLOSED。
- GET /api/conflicts/{id}/curated-drafts/open 只查 status=OPEN。
- CuratedDraftStatus.VOIDED 已在枚举与 V13 CHECK 中，生产从未写入。
- unique index curated_draft_open_conflict_uq：每个冲突至多一份 OPEN；VOIDED 可与后来的 OPEN 并存（本票不必创建第二份）。
- 快照 B→C：upgradeOpen，同一 conflict id，observedLineage 追加，事件 UPGRADED；scheduleAsyncDiagnosis 把旧诊断标 STALE。
- 待确认关闭后再漂：reopenFromPendingClose → OPEN + UPGRADED；仍是同一行，不是第二条。
- 心跳超时：删除过期观测事实 → onObservationBecameHollow → SUSPENDED + 作废活跃计划 + PLAN_VOIDED。今日不碰草案。
- GET /api/conflicts、/by-merge-key；GET 「应该在哪」「实际在哪」；GET /events。
- 事件类型至 V14：有 DRAFT_CREATED / DRAFT_ITEM_ACCEPTED / DRAFT_ITEM_REJECTED，无 DRAFT_VOIDED。
- 薄 UI 能按条接受/拒绝 OPEN 草案。

本票缺口：升级/空洞/待确认关闭再漂都不把 OPEN 草案改为 VOIDED；作废后 GET 看不到 VOIDED；没有 DRAFT_VOIDED 事件；记住 itemId 再 POST accept 仍可能 200 并写入过期目标 B。因此第一圈典型红灯是：B→C 之后 GET open 仍 200 且 status=OPEN（或 accept 仍 200），冲突升级本身可能已绿——那是竖切行为，不是本圈失败原因。不要为了制造红灯去拆 upgradeOpen。

Flyway：禁止改 V13、V14。新事件类型用 V15 扩展 conflict_case_event 的 CHECK（照抄 V14 的 DROP CONSTRAINT + 新 CHECK 写法）。草案/条目表不必为 VOIDED 再开列。无新表则不要空增脚本。

HTTP 形状（Spec 默认，本票采用；不要同时发明第二套）：
- GET  /api/conflicts/{conflictId}/curated-drafts/open
      仍只返回 OPEN。作废后：400，code=DRAFT_NOT_FOUND，data=null。
- GET  /api/conflicts/{conflictId}/curated-drafts/{draftId}
      返回该份草案（含 VOIDED）。成功 200，data.status 字面量 OPEN 或 VOIDED，含 items。找不到：400 DRAFT_NOT_FOUND，data=null。
- POST /api/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/accept|reject
      草案已 VOIDED：400，code=DRAFT_VOIDED，data=null（即使路径仍叫 /open/items/…，也不要另做一套 /voided/items 路由）。
      非处理人仍是 400 PLAN_REQUIRES_ACCEPTED_HANDLER（04 回归）。
- 升级触发：既有 Agent 心跳+快照（第三台宿主 C）。不要新升级 API。
- 空洞触发：回拨 HostAgent.lastHeartbeatAt，再 POST /api/observed/scan-heartbeat-timeouts。

夹具：新建 ChangeCuratedDraftVoidHttpAcceptanceTest（或在 04 测试类增方法，一行为一方法）。复用 04 的 openChangeCuratedDraft 形状（主机 A/B、容器 X/Y、策展皆 A、快照仅 X 在 B、一般角色已接受处理人、选 CHANGE_CURATED_TO_OBSERVED）。可把夹具抽到 support 包，禁止改弱 03/04 断言。对象 id 前缀用 ccd05- 以免碰撞。认证头 X-ArchOps-User-Id；处理人 user-general-demo；非处理人 user-senior-demo。

宿主 C：POST /api/curated/hosts 再对 X 发心跳+快照（学 ConflictWarnUpgradeHttpAcceptanceTest：不同 host 用不同 agentId，例如 agent-{objectX}-c）。空洞圈不要换 agent：回拨夹具里写观测的那一个（04 为 "agent-"+objectX）。

活跃冲突条数：学 countOpenForSubject——GET /api/conflicts，按 mergeKey.subjectId 计数（含 OPEN / PENDING_CLOSE / SUSPENDED）。升级后必须仍为 1。

================================================================================
4. 步骤（按序；每步有完成标准。未完成不准跳到下一步）
================================================================================

### 步骤 A — 读完 §1 再写第一条测试

完成标准：能指出 04 如何拿到 draftId / itemXId / itemYId；能指出 B→C 快照与心跳超时扫描的现网路径；能指出 GET open 只查 OPEN，因此作废后若只改 status、没有 GET by id，HTTP 只能看到 DRAFT_NOT_FOUND，看不到 VOIDED。

### 步骤 B — 第 1 圈：草案待审时快照 B→C → 升级并作废草案（不写策展）

一条测试：开放草案、X 与 Y 皆 PENDING。创建宿主 C，快照 X 在 C。然后：
- GET /api/conflicts/{id}（或 by-merge-key）同一 conflict id，status 仍为 OPEN（不是第二条），observedValue.hostId=C，curatedValue.hostId=A，lineage 含 B 再 C；
- GET /api/conflicts 上该 subject 活跃条数 = 1；
- GET 「应该在哪」X 仍为 A，Y 仍为 A；
- GET open → 400 DRAFT_NOT_FOUND。
本圈不要断言 GET by id（步骤 D）、不要断言 DRAFT_VOIDED 事件（步骤 G）、不要先接受 X。

Red：冲突升级可能已绿；失败应来自 GET open 仍 200 OPEN，或「应该在哪」被错误改写。Comments 贴命令与输出。
Green：upgradeOpen（以及任何「观测目标变了」的 OPEN 升级）调用作废该冲突 OPEN 草案。PENDING 条目不写策展。
Refactor，提交。

完成标准：过期目标 B 的草案不再开放；策展未动；04 accept 测试仍绿。

### 步骤 C — 第 2 圈：作废后不能再审条（DRAFT_VOIDED）

一条测试：步骤 B 的状态下，已接受处理人对记住的 itemXId POST accept、对 itemYId POST reject → 皆 400，code=DRAFT_VOIDED，data=null；GET 「应该在哪」X/Y 仍为 A。
若步骤 B 只让 requireOpen 变成 DRAFT_NOT_FOUND，本测试应红（期望 DRAFT_VOIDED 却得到 DRAFT_NOT_FOUND）——这是诚实红灯，不要把期望改成 DRAFT_NOT_FOUND 来逃。
Green：审条先加载该冲突草案（含 VOIDED）；VOIDED 则 DRAFT_VOIDED。非处理人门禁仍优先或并列保持 04 语义。
Refactor，提交。

完成标准：作废后审条失败码是作废，不是「资源不存在」；策展仍为 A。

### 步骤 D — 第 3 圈：GET 该份草案可见 VOIDED

一条测试：出草案时记下 draftId；B→C 作废后 GET /api/conflicts/{id}/curated-drafts/{draftId} → 200，data.status=VOIDED，items 仍在（X/Y 仍 PENDING），data.id 仍为该 draftId。
Red：典型 500 INTERNAL_ERROR / No static resource …/curated-drafts/{draftId}（与 04 第一圈同类，不是 404）。照实记录。
Green：只加 GET by id（OPEN 与 VOIDED 都可读）。不要把 /open 改成返回 VOIDED。
Refactor，提交。

完成标准：票清单「GET 开放/该份草案可看出已作废」中「该份」可经 HTTP 证明。

### 步骤 E — 第 4 圈：空洞挂起并作废草案

一条测试：开放草案、X 未接受。回拨写 X 观测的 HostAgent.lastHeartbeatAt（减 2 分钟），POST /api/observed/scan-heartbeat-timeouts。然后：
- GET 冲突 status=SUSPENDED（不是 CLOSED、不是 OPEN）；observationHollow=true；
- GET 「实际在哪」X 为 HOLLOW（hostId null）；GET 「应该在哪」X 仍为 A（不是「不存在」）；
- GET open → DRAFT_NOT_FOUND；GET by id → VOIDED；
- POST accept 记住的 itemX → 400 DRAFT_VOIDED。
本圈不要创建操作计划。HeartbeatTimeoutHollowHttpAcceptanceTest 必须保持绿（计划作废语义原样）。

Red：冲突挂起可能已绿；失败应来自草案仍 OPEN 或 accept 仍 200。
Green：onObservationBecameHollow 在作废计划之外作废 OPEN 草案。不要复制挂起状态机。
Refactor，提交。

完成标准：空洞 = 挂起 + 草案作废 + 拒审条；策展轨道不变。

### 步骤 F — 第 5 圈：接受 X 待确认关闭后再漂到 C

一条测试：处理人接受合并键 X（可先拒 Y，作为 setup 不是本圈新行为）→ 冲突 PENDING_CLOSE，X「应该在哪」=B。再快照 X 在 C。然后：
- 同一 conflict id；status 离开 PENDING_CLOSE（OPEN，不是 CLOSED）；
- GET 「应该在哪」X 仍为 B（已接受条目保持）；「实际在哪」X 为 C；
- 冲突 GET 双轨同时可读：curatedValue=B，observedValue=C；
- 该 subject 活跃冲突仍 1 条，不是并行第二条；
- 若 Y 仍 PENDING：GET Y「应该在哪」仍为 A；POST accept Y → DRAFT_VOIDED；
- 草案 GET by id = VOIDED。
不要 POST confirm-close（漂后确认应失败是竖切已有语义，本票不重做关单）。

若步骤 B 已把作废接到 reopenFromPendingClose，本圈冲突身份与 VOIDED 可能首次即绿：Comments 写明 reuse；仍必须断言策展保持 B（这是相对步骤 B「保持 A」的不同行为，不可省略）。若 void 只挂在 upgradeOpen、没挂 reopenFromPendingClose，则草案仍 OPEN 是本圈红灯。不要为了红灯去拆 reopenFromPendingClose。
Refactor，提交。

完成标准：新策展 B 是比对基线；未接受兄弟未写；同一合并键升级而非第二条冲突。

### 步骤 G — 第 6 圈：HTTP 可读「草案已作废」审计

一条测试：步骤 B 或 E 之后，GET /api/conflicts/{id}/events 含 DRAFT_VOIDED；detail 能读到 draftId 与合同术语（草案已作废）；可带 reason（conflict_upgrade 或 observation_hollow_heartbeat_timeout 一类字面量）。同时仍能读到既有 UPGRADED 或 SUSPENDED，不要覆盖它们。
V14 CHECK 不含 DRAFT_VOIDED 时新增 V15，禁止改 V13/V14。
Refactor，提交。

完成标准：处理归档能经 HTTP 看见作废，而不必查库。

### 步骤 H — 第 7 圈：作废后的选支不能继续当处理路径

一条测试（独立方法，不要塞进 B）：B→C 作废后，用出草案时的旧 diagnosisId 再 POST /api/conflicts/{id}/branch-selection forkId=CHANGE_CURATED_TO_OBSERVED → 被拒（既有 DIAGNOSIS_NOT_READY / 过时诊断门禁即可，不要新造选支产品）；并且 POST accept 旧 itemId → DRAFT_VOIDED。不要在本圈成功出第二份针对 C 的草案。
若门禁首次即绿（scheduleAsyncDiagnosis 已 STALE），Comments 写 reuse；本圈价值是把「选支作废」与「草案作废」在 HTTP 上钉在一起。
Refactor，提交。

### 步骤 I — 薄 UI（HTTP 全绿之后）

ConflictDetailPage：OPEN 时行为与 04 相同。VOIDED 时展示状态「已作废 / VOIDED」，不渲染接受/拒绝。GET open 失败后，用已记住的 draftId 走 GET by id（扩展 frontend/src/api/drafts.ts）。不要把 UI 写入自动化主接缝。npm run build 通过即可。
提交单独一圈 why：演示作废后不可再审条。

### 步骤 J — 票级回归与收尾

cd backend && ./gradlew test
失败则修到全绿（仍不扩到 06）。

对照工单清单逐条用 HTTP 证据勾选。
/code-review：Standards + Spec。固定点 = 本分支相对 main 的 merge-base。Spec 源：本票 + Spec Voiding / Compare / Negative 6–8。行为错误要修并回归；不要借审查塞进 06 或 Y2。

更新文档指针（05 完成后 frontier = 06；不要实现 06）：
- 本票：Status: done；验收项全勾；Comments 含每圈 red
- docs/dev-handoff.md
- AGENTS.md 当前工单 / §6
- CLAUDE.md 工单行
- docs/agents/issue-tracker.md 表
- .cursor/rules/project-map.mdc、domain-contract.mdc
- docs/specs/change-curated-draft.md 与 docs/specs/vertical-slice-mvp.md 的 Next Matt step（05 TDD-done，frontier 06）

完成标准：全量测试绿；票 done；handoff 下一对话指向 06；工作区无票外文件。

================================================================================
5. HTTP 契约（本票断言用）
================================================================================

建底与选支（已有，setup 用）：
- POST /api/curated/hosts、/containers、/facts/runs-on
- Agent 心跳/快照（先让 X 在 B，再让 X 在 C）
- POST /api/conflicts/{id}/claim
- POST /api/conflicts/{id}/branch-selection  {"forkId":"CHANGE_CURATED_TO_OBSERVED"}
- GET  /api/conflicts/{id}/curated-drafts/open
- POST .../curated-drafts/open/items/{itemId}/accept|reject
- POST /api/observed/scan-heartbeat-timeouts

本票新写：
- GET  /api/conflicts/{conflictId}/curated-drafts/{draftId} → 200，status=OPEN|VOIDED
- 作废后 GET open → 400 DRAFT_NOT_FOUND；data=null
- 作废后 POST accept|reject → 400 DRAFT_VOIDED；data=null

读（已有）：
- GET /api/curated/asks/should-where?containerId=
- GET /api/observed/asks/actual-where?containerId=
- GET /api/conflicts/{id}、/api/conflicts、/api/conflicts/by-merge-key
- GET /api/conflicts/{id}/events
- GET /api/conflicts/{id}/diagnosis（步骤 H 可选，等 READY 的方式与今日 ConflictDiagnosisWait 相同）

关系文案用「运行于」/ RUNS_ON。草案状态字面量 OPEN | VOIDED。条目状态字面量 PENDING | ACCEPTED | REJECTED。冲突状态字面量 OPEN | PENDING_CLOSE | SUSPENDED | CLOSED。空洞观测 availability=HOLLOW。

================================================================================
6. 停工检查（全部为真才许把票标 done）
================================================================================

- [ ] 每圈 Comments 里有独立的 red 命令与失败输出（复用圈写明 reuse/regression，未拆生产代码装红）
- [ ] 没有「先实现后补测」或「测试已绿再宣称 TDD」
- [ ] 01–04 与竖切升级/空洞/计划作废/HTTP E2E 仍绿
- [ ] 未接受条目在升级/空洞后未写入策展；已接受条目保持已写入值
- [ ] B→C 待审草案：同一 conflict id、策展仍 A、草案 VOIDED、不能再审条
- [ ] 空洞：SUSPENDED 非 CLOSED；草案 VOIDED；应该在哪不是「不存在」
- [ ] 接受 X 后再漂 C：离开 PENDING_CLOSE、策展 B、观测 C、仍一条冲突
- [ ] GET by id 可读 VOIDED；GET open 不作废充开放
- [ ] DRAFT_VOIDED 事件 HTTP 可读；未改已有 V*.sql；新事件若需要则 V15
- [ ] 未复制比对/升级/挂起引擎；未重做计划作废
- [ ] 薄 UI 在 HTTP 绿之后接线；未进自动化主接缝
- [ ] ./gradlew test 全绿
- [ ] /code-review 已跑；行为问题已修
- [ ] 文档 frontier 指向 06；06 的代码未改
```

---

完成后下一对话：本刀 HTTP 主接缝有序 tracer（票 06）。开工贴 [`docs/implement-change-curated-draft-06-prompt.md`](implement-change-curated-draft-06-prompt.md)。票路径 `.scratch/change-curated-draft/issues/06-http-tracer-acceptance.md`。05 已合入 main；不要在读本文件时再做 05。
