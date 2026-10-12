# 新对话：改策展票 04（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

01–03 已按 TDD 重做并 `done`。本票是改策展 **第一次** 实现「逐条确认写入 + 立刻比对」，不是重做已有接受/拒绝代码。诚实红灯是 404 / 编译失败，**不要**去恢复或删除 01–03 的生产行为。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现改策展 frontier 工单 04——逐条确认：接受即写策展并立刻比对（相等 → 待确认关闭）。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md
- 票结束再用 code-review skill（Standards + Spec）

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 05–06 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/change-curated-draft/issues/04-itemized-accept-write-compare.md

Matt 位置：主路径 idea → ship 已走完 grilling / to-spec / to-tickets。竖切 MVP 01–13 已闭合。改策展 01–03 TDD-done。本对话是 /implement 的一张 frontier 票，内部驱动 /tdd。只做 04。

示踪混确（本票主路径）：已接受处理人拒绝兄弟 Y、接受合并键 X。接受 X 后不必再发快照：X「应该在哪」为当前可用观测宿主 B；冲突为待确认关闭（不是 CLOSED）；Y「应该在哪」仍为 A。再用既有确认关闭 API 关单。

本票交付（用户可感知、HTTP 可断言）：
- 已接受处理人接受条目：该条立即写入策展真相；条目 ACCEPTED；不等待其余条目、不等待心跳。
- 已接受处理人拒绝条目：不写策展；条目 REJECTED；兄弟条目不受影响。
- 非处理人 / 待接受处理人审条被拒；策展不变。
- 接受合并键 X 后不发新快照：GET 冲突为 PENDING_CLOSE（非 CLOSED）；pendingCloseReminderVisible 对查看者仍为 true（已知悉不静音）。
- 仅已接受处理人可 POST 既有确认关闭 API；成功后 CLOSED（证明相等没有自动关单）。
- 只接受兄弟 / 只拒绝合并键：合并键冲突不得仅因此进入 PENDING_CLOSE。
- 规范问法：接受 X 后「应该在哪」答 B；GET 冲突 / 「实际在哪」仍展示观测轨，不得把单轨说成唯一真相。
- HTTP 可读审计：条目已接受（含写入）、条目已拒绝。
- 按条接受/拒绝与随后比对在同一持久化事务内完成。草案真相在 PostgreSQL；Redis 可作锁，不是 SSOT。
- 薄 UI：处理人可按条接受/拒绝并看到条目状态与「应该在哪」变化。UI 不进自动化主接缝。
- 无整单全接受、无 AI 独自定稿、无策展对齐步骤推迟写入。

本票不做：选支瞬间写策展（03 已禁止，回归由 06 收束）、升级/空洞作废未完成草案（05）、有序总 tracer 套件（06）、修实际 SSH 执行、Y2 策展对齐步骤、改 CONTEXT/ADR 语义、重做竖切 01–13、重做改策展 01–03。

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内术语写作，不发明同义新词）
================================================================================

按序阅读，读完再改代码：

1. AGENTS.md
2. docs/agents/tdd.md
3. .scratch/change-curated-draft/issues/04-itemized-accept-write-compare.md
4. docs/specs/change-curated-draft.md — 只取：Testing seams (confirmed)、草案 items、Compare and 冲突演进、Testing Decisions 中 HTTP tracer 第 6–10 步与 Negative 第 1–2 条（第 6–8 条是 05，本票不做）
5. CONTEXT.md — 只用：策展真相、观测真相、冲突、运行于、应该在哪、实际在哪、草案、逐条确认、已接受的冲突处理人、待确认关闭
6. docs/adr/0039-domain-contract-frozen.md
7. docs/adr/0043-tech-stack.md
8. docs/adr/0006-curated-writes-via-itemized-proposals.md（接受即写；确认前不是策展）
9. docs/adr/0009-dual-track-ideal-vs-actual-deviation.md（偏差；规范问法）
10. docs/adr/0019-conflict-close-and-curated-align-step.md（相等 → 待确认关闭；本刀不含 Y2）
11. docs/adr/0038-ai-power-vs-capability-and-iteration.md（AI 不能独自定稿策展）
12. docs/dev-handoff.md（确认 frontier = 04）
13. 现行样板（读，不重写 01–03 / 竖切故事）：
    - backend/src/test/java/com/archops/curated/ChangeCuratedDraftHttpAcceptanceTest.java（夹具 openConflictWithSiblingAndClaim：X 在 B 冲突、Y 策展在 A）
    - backend/src/main/java/com/archops/curated/controller/CuratedDraftController.java（目前只有 GET open）
    - backend/src/main/java/com/archops/curated/service/CuratedDraftService.java
    - backend/src/main/java/com/archops/curated/domain/CuratedDraftItemStatus.java（PENDING / ACCEPTED / REJECTED；03 只写 PENDING）
    - backend/src/main/java/com/archops/curated/service/CuratedTruthService.java 的 confirmRunsOn（01：已有运行于则 CURATED_RUNS_ON_EXISTS）
    - backend/src/main/java/com/archops/conflict/service/ConflictDetectionService.java 的 reconcileAfterObservedWrite（今日只在观测写入后触发）
    - backend/src/main/java/com/archops/conflict/controller/ConflictController.java 的 POST /{id}/confirm-close 与 GET /{id}/events
    - backend/src/test/java/com/archops/conflict/ConflictPendingCloseHttpAcceptanceTest.java（关单产品化已交付，复用）
    - backend/src/main/resources/db/migration/V13__curated_draft_and_items.sql
    - frontend/src/pages/ConflictDetailPage.tsx（草案条目目前只读列出）
    - frontend/src/api/drafts.ts
    - .cursor/rules/backend-java.mdc、frontend-react.mdc

接缝已确认，不必再问用户：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle MockMvc 与 bootRun+curl 是同一条接缝。薄 UI 手工/冒烟，排在本票 HTTP 循环全绿之后。本票无新 SSH 接缝。

================================================================================
2. 思想与质量条（完成标准：后续每一步都能对照这一节说「满足」）
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 偏差。草案在确认前不是策展真相。确认单位是条目，不是整份草案。接受的条目立即写入策展；拒绝的仍只是草案。AI 不能独自定稿策展。相等不得自动 CLOSED，只进入待确认关闭。

栈（ADR-0043）：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway、PostgreSQL SSOT。Redis 不作关系真相 / 草案 SSOT。本票不引入 Maven、JPA 当地基、Vue、Neo4j、LangChain。

分层：Controller → Service → Mapper；DTO 用 record；DO 不当响应；构造器注入；业务错误 BusinessException；写操作事务在 service。接受条目的「写策展 + 比对」必须在同一 @Transactional 里。

测试质量（/tdd）：
- 测公开 HTTP 行为：状态码、ApiResponse 信封、后续 GET 可读状态（应该在哪、实际在哪、冲突 status、pendingCloseReminderVisible、草案 item.status、事件列表）。
- 期望值来自独立真相：字面量 PENDING_CLOSE、CLOSED、ACCEPTED、REJECTED、「应该在哪」、宿主 A/B。禁止用实现再算一遍期望。
- 不测 Mapper SQL、Redis key、私有方法、调用图，不打开数据库断言事务。事务的可观察结果是：一次接受 POST 之后，策展写入与比对结果同时可经 GET 读到。
- 一圈一条行为。Spec 的 tracer 第 6–10 步是循环顺序，不是一次写完全部测试再实现。
- 不 mock 本模块协作对象。用现有 @HttpAcceptanceTest。
- 03 的选支出草案测试必须保持绿。01 的建底覆盖拒绝必须保持绿：接受条目不得改道走 POST /api/curated/facts/runs-on。

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；跑命令；失败原因是「缺本圈行为」（本票第一圈典型是 404 或编译失败）。把完整命令与失败输出追加到票 ## Comments。
2. Green：只写让这一条测试通过的最少生产代码。
3. Refactor：不改行为；再跑同一条测试仍绿。然后提交这一圈。

Witnessed red 是硬门。已经绿的测试不能事后称作 TDD 完成。/code-review 是票结束第二道门，替代不了每圈 refactor。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 全量测试红了 → 先修到绿，再开下一圈。
- 想「顺便」做 05 升级/空洞作废或 06 总 tracer → 不做。
- 想用建底 POST 覆盖已有运行于来实现接受写入 → 禁止（01 已关旁路）。接受写入走策展模块内专供「已接受草案条目」的更新，与 confirmRunsOn 插入/拒绝覆盖分开。
- 想复制一套比对引擎 → 禁止。复用 ConflictDetectionService 已有调和逻辑（今日名为 reconcileAfterObservedWrite；可抽出「按合并键调和」供观测写入与策展写入共用）。05 才把升级/空洞接到作废草案。
- 想加「整单全接受」或推迟到对齐步再写策展 → 禁止。

Git：从最新 main 拉分支 cursor/tdd-implement-change-curated-04-24fb（或同等 cursor/…-24fb）。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres。单测：
cd backend && ./gradlew test --tests com.archops.curated.<Class>.<method>
票结束：
cd backend && ./gradlew test

================================================================================
3. 现状（完成标准：你能指出「04 还缺哪条 HTTP」，以及红灯应长什么样）
================================================================================

已有（01–03 / 竖切，保持绿）：
- POST /api/curated/facts/runs-on 只能插入第一条；已有则 CURATED_RUNS_ON_EXISTS。
- 诊断同时含 FIX_ACTUAL_TO_CURATED 与 CHANGE_CURATED_TO_OBSERVED。
- 已接受处理人 POST /api/conflicts/{id}/branch-selection forkId=CHANGE_CURATED_TO_OBSERVED → 恰好一份 OPEN 草案，条目 ≥2（X：A→B 合并键；Y：A→B 兄弟，皆 PENDING）。选支不写策展、不出操作计划。
- GET /api/conflicts/{id}/curated-drafts/open
- 比对只在观测写入后调用 reconcileAfterObservedWrite。策展突变不会自己走进 PENDING_CLOSE。
- POST /api/conflicts/{id}/confirm-close 仅已接受处理人；相等才 CLOSED。
- GET /api/conflicts/{id} 含 pendingCloseReminderVisible。
- GET /api/curated/asks/should-where、GET /api/observed/asks/actual-where
- GET /api/conflicts/{id}/events；V13 的 event_type CHECK 含 DRAFT_CREATED，不含条目接受/拒绝。
- 薄 UI 能选改理想并只读列出草案条目。

本票缺口：没有按条 accept/reject HTTP；没有「已接受条目写入已有运行于」的合法路径；没有策展写入后触发同一套比对。因此第一圈测试对尚未存在的 POST 应变 404（或编译失败）。不要为了制造红灯去拆掉 03 的出草案行为。

Flyway：禁止改 V13。新事件类型（条目已接受含写入、条目已拒绝）用 V14 扩展 conflict_case_event 的 CHECK。条目状态 ACCEPTED/REJECTED 列已在 V13，不必为状态再开表。无新表则不要空增脚本。

HTTP 形状（Spec 默认，本票采用；不要同时发明第二套）：
- POST /api/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/accept
- POST /api/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/reject
空 JSON body 即可。成功：200，统一信封，data 为更新后的开放草案（含 items[].status、mergeKey）。失败：400，success=false，data=null。
非处理人 / 待接受：复用 PLAN_REQUIRES_ACCEPTED_HANDLER（与 03 选支门禁同一权力边界）。
夹具：复用 ChangeCuratedDraftHttpAcceptanceTest 的 openConflictWithSiblingAndClaim（主机 A/B、容器 X/Y、策展皆运行于 A、快照仅 X 在 B、一般角色已接受处理人）。可把夹具抽到 support 包，或新建 ChangeCuratedDraftItemHttpAcceptanceTest 复制同一形状。禁止改弱 03 已有断言。

认证：Header X-ArchOps-User-Id。一般角色 user-general-demo；非处理人用 user-senior-demo。

================================================================================
4. 步骤（按序；每步有完成标准。未完成不准跳到下一步）
================================================================================

### 步骤 A — 读完 §1 再写第一条测试

完成标准：能指出 GET open 草案如何得到 X/Y 的 itemId 与 mergeKey；能指出 confirm-close 与 events 的现网路径。

### 步骤 B — 第 1 圈：非处理人不能审条

一条测试：夹具出开放草案后，非处理人对 X 的条目 POST accept（或 reject）→ 400 PLAN_REQUIRES_ACCEPTED_HANDLER；GET 「应该在哪」X 与 Y 仍为 A；GET open 两条仍 PENDING。
Red：404 或编译失败。Comments 贴命令与输出。
Green：只加门禁与路由，不写策展、不改条目状态。
Refactor，提交。

完成标准：该测试绿；03 选支测试仍绿。

### 步骤 C — 第 2 圈：已接受处理人拒绝兄弟 Y

一条测试：处理人 POST reject Y → 200；Y 条目 REJECTED；GET 「应该在哪」Y 仍为 A；X 仍 PENDING；X「应该在哪」仍为 A；冲突仍 OPEN。
Red 后最少实现「拒绝 = 改条目状态，不写策展」。Refactor，提交。

完成标准：拒绝不碰 curated_fact；01 覆盖拒绝测试仍绿。

### 步骤 D — 第 3 圈：已接受处理人接受合并键 X（立即写策展）

一条测试：开放草案上处理人 POST accept X（可先 reject Y 作为示踪混确的前置，若 C 已绿则那是 setup 不是本圈新行为）→ X 条目 ACCEPTED；GET 「应该在哪」X 为 B；Y「应该在哪」仍为 A（若 Y 已拒绝或仍 PENDING 皆须仍为 A）。
Green：合法更新该容器已有运行于的 target 为 toHostId（B）。禁止调用会抛 CURATED_RUNS_ON_EXISTS 的建底 POST。本圈若尚未接比对，冲突可以仍是 OPEN——不要提前实现 05。
Refactor，提交。

完成标准：X 策展变为 B 只来自接受条目；建底 POST 再覆盖 X 仍被拒。

### 步骤 E — 第 4 圈：接受 X 后立刻比对 → 待确认关闭（不发新快照、不自动 CLOSED）

一条测试：接受合并键 X 之后，不再发 Agent 快照；GET /api/conflicts/{id} 为 PENDING_CLOSE（不是 CLOSED、不是 OPEN）；pendingCloseReminderVisible 为 true；GET 「实际在哪」X 仍为 B；事件可读 PENDING_CLOSE（复用既有类型即可）。
若步骤 D 的 green 已经把调和接到策展写入，本测试可能已绿：在 Comments 写明「与 D 同一事务内的比对，本方法作回归留下；未另写第二套引擎」。不要为了「看起来像一圈」复制比对。
若仍红：让接受路径在同一事务内调用现有调和（抽出共用方法优于复制）。相等 → PENDING_CLOSE，永不 auto CLOSED。
Refactor，提交。

完成标准：不发新快照也能待确认关闭；status 字面量 PENDING_CLOSE。

### 步骤 F — 第 5 圈：既有确认关闭 API → CLOSED

一条测试：在 E 的状态下，已接受处理人 POST /api/conflicts/{id}/confirm-close → 200，status CLOSED。非处理人确认被既有门禁拒绝（可作同一能力的回归，不必新开产品）。
本圈以复用票 09 为主；不要重做关单产品化。
Refactor，提交。

完成标准：证明第 4 圈没有自动关单。

### 步骤 G — 第 6 圈：只接受兄弟不得推进合并键待确认关闭

一条测试：开放草案上只接受 Y、X 仍 PENDING（不要接受 X）→ Y「应该在哪」为 B；X「应该在哪」仍为 A；GET 该合并键冲突仍为 OPEN，不是 PENDING_CLOSE。
这与示踪混确（拒 Y 接 X）是不同行为，必须有独立红灯。
Refactor，提交。

### 步骤 H — 第 7 圈：HTTP 可读审计

一条测试：拒绝 Y 与接受 X 之后，GET /api/conflicts/{id}/events 含条目已拒绝、条目已接受（含写入）两类事件。文案用合同术语（草案 / 条目 / 策展），detail 可被 HTTP 读到。
V13 CHECK 不含新 event_type 时新增 V14，禁止改 V13。
Refactor，提交。

建议类型名（可沿用）：DRAFT_ITEM_REJECTED、DRAFT_ITEM_ACCEPTED。接受事件须能看出已写入策展（例如 detail.hint 或 written=true），避免与「只改了条目状态」分不清。

### 步骤 I — 薄 UI（HTTP 全绿之后）

ConflictDetailPage：已接受处理人对 PENDING 条目可接受/拒绝；列出 ACCEPTED/REJECTED；接受 X 后能看到「应该在哪」变为 B。HTTP 只走 frontend/src/api/（扩展 drafts.ts）。不把 UI 写入自动化主接缝。npm run build 通过即可。
提交单独一圈 why：演示按条确认。

### 步骤 J — 票级回归与收尾

cd backend && ./gradlew test
失败则修到全绿（仍不扩到 05–06）。

对照工单清单逐条用 HTTP 证据勾选。
/code-review：Standards + Spec。固定点 = 本分支相对 main 的 merge-base。Spec 源：本票 + Spec 草案 items / Compare / tracer 第 6–10 步。行为错误要修并回归；不要借审查塞进 05–06。

更新文档指针（04 完成后 frontier = 05；不要实现 05）：
- 本票：Status: done；验收项全勾；Comments 含每圈 red
- docs/dev-handoff.md
- AGENTS.md 当前工单 / §6
- CLAUDE.md 工单行
- docs/agents/issue-tracker.md 表
- .cursor/rules/project-map.mdc、domain-contract.mdc
- docs/specs/vertical-slice-mvp.md 的 Next Matt step（04 TDD-done，frontier 05）

完成标准：全量测试绿；票 done；handoff 下一对话指向 05；工作区无票外文件。

================================================================================
5. HTTP 契约（本票断言用）
================================================================================

建底与选支（已有，setup 用）：
- POST /api/curated/hosts、/containers、/facts/runs-on
- Agent 心跳/快照（仅让 X 出现在 B）
- POST /api/conflicts/{id}/claim
- POST /api/conflicts/{id}/branch-selection  {"forkId":"CHANGE_CURATED_TO_OBSERVED"}
- GET /api/conflicts/{id}/curated-drafts/open  → items[].id、mergeKey、fromHostId、toHostId、status

本票新写：
- POST .../curated-drafts/open/items/{itemId}/accept → 200；该 item status=ACCEPTED
- POST .../curated-drafts/open/items/{itemId}/reject → 200；该 item status=REJECTED
- 非处理人审条 → 400 PLAN_REQUIRES_ACCEPTED_HANDLER；data=null

读（已有）：
- GET /api/curated/asks/should-where?containerId= → data.question 为「应该在哪」，data.track 为 CURATED，data.curatedValue.hostId
- GET /api/observed/asks/actual-where?containerId= → 观测轨；接受 X 后仍为 B
- GET /api/conflicts/{id} → status、pendingCloseReminderVisible、curatedValue、observedValue
- GET /api/conflicts/{id}/events
- POST /api/conflicts/{id}/confirm-close

关系文案用「运行于」/ RUNS_ON。条目状态字面量 PENDING | ACCEPTED | REJECTED。冲突状态字面量 OPEN | PENDING_CLOSE | CLOSED。

================================================================================
6. 停工检查（全部为真才许把票标 done）
================================================================================

- [ ] 每圈 Comments 里有独立的 red 命令与失败输出
- [ ] 没有「先实现后补测」或「测试已绿再宣称 TDD」
- [ ] 03 出草案、01 覆盖拒绝、竖切 HTTP E2E 仍绿
- [ ] 接受 X 走草案条目写入，而不是建底 POST 覆盖
- [ ] 拒绝 Y 不改 Y 的策展；接受 X 后 X「应该在哪」为 B
- [ ] 接受 X 后无新快照即 PENDING_CLOSE；confirm-close 后才 CLOSED
- [ ] 只接受兄弟时合并键冲突不是 PENDING_CLOSE
- [ ] 非处理人审条被拒
- [ ] 规范问法仍双轨可读
- [ ] 审计事件 HTTP 可读；未改已有 V*.sql
- [ ] 比对与接受在同一事务的可观察结果已由 HTTP 证明
- [ ] 薄 UI 在 HTTP 绿之后接线；未进自动化主接缝
- [ ] ./gradlew test 全绿
- [ ] /code-review 已跑；行为问题已修
- [ ] 文档 frontier 指向 05；05 的代码未改
```

---

完成后下一对话：升级/空洞作废未完成草案（票 05）。票路径 `.scratch/change-curated-draft/issues/05-void-draft-on-upgrade-hollow.md`。
