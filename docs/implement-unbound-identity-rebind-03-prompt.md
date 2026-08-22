# 新对话：未绑定 / 身份失联票 03（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

Matt 位置：grilling / to-spec / to-tickets **已完成**。竖切 01–13 与改策展 01–06 已闭合。未绑定票 **01–02 TDD-done**。本对话只 `/implement` **未绑定刀 frontier = 03**。不要做 04–07，不要给 `change-curated-draft` 加 07，不要把 01/02 当 TDD redo。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现未绑定 / 身份失联 frontier 工单 03。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。本票是新能力票，不是 TDD redo，也不是 06 那种 suite/tracer。01/02 已闭合，禁止重做 01 的推断/upsert/问法，禁止重做 02 的发起草案/PENDING 夹具（可在本票增量上扩展 UNKNOWN+失联时的 BIND 条，见 §2）。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围、路径、条目 kind、错误码或表设计。下面已钉死。不要用 Playwright、SSH fake、computerUse 或薄 UI 当作完成定义。不要默认开工 04–07。不要开工 05（虽已 unblocked，编号更大）。

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 04–07 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/unbound-identity-rebind/issues/03-itemized-create-and-bind.md

Spec：docs/specs/unbound-identity-rebind.md
一句话交付：已认证运维对未绑定草案按条接受或拒绝。接受新建即写入策展 Docker 容器（及若接受则写入该对象第一条策展「运行于」）。接受绑到已有：不改容器主键 / 不可变标签，不把名称或运行时 ID 写成可靠观测「运行于」，只记住该现场实体已对应目标对象，并让它离开待并入列表。拒绝的条目不写。绑到仍健康标签命中的对象必须失败；同一草案上绑定与新建都接受必须失败。

本票对应 Spec User Stories 19–20（审条认证）、25–28、31–36、44、51–53、59 的条目审计。Stories 5 的「默认列表只显示待并入」在本票因绑定记忆过滤而落地。Stories 29、37、40–43、50 是 04。Stories 14–16、45–49 是 05。Tracer 步骤 5–7 的接受/绑定形状属于本票；步骤 8 的补标命中是 04。

本票交付（用户可感知、HTTP 可断言）：
- POST /api/curated-drafts/{draftId}/items/{itemId}/accept 与 /reject（未绑定 origin=UNBOUND_CANDIDATE 的 OPEN 草案）。不要走 /api/conflicts/{conflictId}/curated-drafts/open/items/...（那是改策展，仍要处理人）。
- 确认单位是条目：无「整单全接受」HTTP。
- UNKNOWN_OBJECT_ID：只接受 CREATE、拒绝 CURATED_RUNS_ON_INSERT → 策展出现该 Docker 容器（不可变标签 = 现场 archops.object_id），GET 该对象「运行于」为 CURATED_RUNS_ON_NOT_FOUND；拒绝条仍是草案（status=REJECTED）。
- 先接受 CURATED_RUNS_ON_INSERT、CREATE 仍 PENDING → 400，策展不变。
- CREATE 所用 archops.object_id 已被占用 → 接受失败，code=CURATED_OBJECT_ID_EXISTS，不写第二条对象。
- 接受 BIND_UNBOUND_TO_EXISTING 到失联对象 X → X 的容器ID / 不可变标签不变；「实际在哪」仍 identityLost=true / availability=IDENTITY_LOST，hostId 不得为旧宿主；不得因绑定写出观测「运行于」PRESENT；该 sourceHostId+runtimeId 不再出现在默认待并入列表。
- 再心跳同一 runtimeId 仍缺标/错标 → 仍不待并入、仍身份失联、GET by-merge-key 仍不承诺升级链。
- 同一草案 BIND 与 CREATE 都接受 → 第二次 400，失败条不写，不得把一个现场实体变成两个策展对象。
- 绑到仍标签命中（观测「运行于」PRESENT）且升级链有效的对象 → 400。
- MISSING_LABEL 的 CREATE 接受不是成功路径：400（无现场标签则无不可变 object id）。拒绝该条可以，且不写对象。
- UNKNOWN_OBJECT_ID 绑到已有失联 X 允许：不把错标签写成 X 的新主键。
- 未认证审条 → 401 AUTH_REQUIRED。已认证一般与高级均可审条。无未绑定处理人，不复用已接受冲突处理人。
- 建底 POST /api/curated/facts/runs-on 插入第一条仍 200；覆盖已有仍 CURATED_RUNS_ON_EXISTS。禁止旁路 POST 把候选映射成对象而不经条目接受。
- GET /api/curated-drafts/{draftId}/events 可读 DRAFT_ITEM_ACCEPTED / DRAFT_ITEM_REJECTED（detail.hint 含「草案条目已接受」/「草案条目已拒绝」）。无操作计划、无策展对齐 SSH 步。

本票不做（Out of ticket；发现自己在做就停，回到本票清单）：
- 标签命中清失联、消费绑定记忆、作废未绑定草案、恢复升级链、absent 释放记忆（04）
- 失联时禁止选支 / 改诊断分叉 / 作废计划与改理想草案 / 待确认关闭退回开放 / 冲突 GET 失联旗标（05）
- HTTP 总 tracer 套件（06）、薄 UI（07）
- 把未绑定草案挂到冲突上；用 branch-selection 审条；发明未绑定处理人
- SSH / 操作计划 / Y2 / LLM 起草 / 网络可达 / K8s/数据库对象
- 给 change-curated-draft 加 07；重做竖切 01–13；重做改策展 01–06；重做未绑定 01–02
- 改已有 V*.sql（含 V17）；改 CONTEXT.md / ADR-0039 / 已有 ADR 正文
- Maven、JPA 当地基、Vue、Neo4j v1 必选、LangChain、Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内/合同术语写作，不发明同义新词）
================================================================================

按序阅读，读完再写第一个测试：

1. AGENTS.md（一次一张；HTTP 主接缝；/implement 驱动 /tdd）
2. docs/agents/tdd.md（capability 票：必须 witnessed red；禁止为装红灯删除改策展/竖切/01/02 生产）
3. .scratch/unbound-identity-rebind/issues/03-itemized-create-and-bind.md
4. .scratch/unbound-identity-rebind/issues/04-label-match-consume.md（只为划清边界：补标命中是下一张）
5. .scratch/unbound-identity-rebind/issues/02-unbound-draft-from-candidate.md（已 done；夹具与错误码；保持绿）
6. docs/specs/unbound-identity-rebind.md — 只取：Testing seams、未绑定草案、Curated write bypass、Ingest matching 的 bind memory 句、stories 19–20 / 25–28 / 31–36 / 44 / 51–53 / 59、tracer 步骤 5–7、Negative 3 / 5–7 / 11。不要实现 tracer 步骤 8，不要实现 Negative 8–10 / 12
7. CONTEXT.md — 只用：未绑定观测候选、身份失联、草案、逐条确认、策展真相、观测真相、冲突、规范问法、操作计划、已接受处理人。Avoid 栏禁止的词不要用（尤其「未绑定处理人」「待确认策展」「以现场为准」「已确认待补标」）。接受绑定后的现场实体对应关系是匹配状态，不是新合同词，也不是第四种冲突
8. docs/adr/0039-domain-contract-frozen.md
9. docs/adr/0043-tech-stack.md
10. docs/adr/0006-curated-writes-via-itemized-proposals.md（确认前不是策展真相；确认单位是条目；接受即写该条）
11. docs/adr/0011-object-identity-rules.md 与 docs/adr/0012-container-label-bootstrap-and-identity-loss.md
12. docs/dev-handoff.md（确认 frontier = 未绑定 03）
13. 现行样板（读，不重写 01/02/改策展故事）：
    - backend/src/test/java/com/archops/observed/UnboundDraftCreateHttpAcceptanceTest.java（02；保持绿；开草案夹具）
    - backend/src/test/java/com/archops/observed/UnboundIdentityLostIngestHttpAcceptanceTest.java（01；保持绿）
    - backend/src/test/java/com/archops/curated/ChangeCuratedDraftItemHttpAcceptanceTest.java（改策展逐条；已接受处理人；DRAFT_ITEM_ACCEPTED 在冲突事件）
    - backend/src/main/java/com/archops/curated/controller/CuratedDraftController.java（今日 accept/reject 只挂在 /api/conflicts/{id}/curated-drafts/open/items/...）
    - backend/src/main/java/com/archops/curated/service/CuratedDraftService.java（createFromUnboundCandidate；acceptItem 走冲突+处理人+只写 RUNS_ON_TARGET_CHANGE）
    - backend/src/main/java/com/archops/curated/service/CuratedTruthService.java（createContainer / confirmRunsOn / applyAcceptedDraftRunsOn；CURATED_OBJECT_ID_EXISTS、CURATED_RUNS_ON_EXISTS）
    - backend/src/main/java/com/archops/curated/domain/CuratedDraftItemKind.java
    - backend/src/main/java/com/archops/curated/domain/CuratedDraftEventType.java（今日仅 DRAFT_CREATED）
    - backend/src/main/resources/db/migration/V17__unbound_candidate_draft.sql（最新；下一版是 V18+）
    - backend/src/main/java/com/archops/observed/service/ObservedTruthService.java（listUnbound 今日返回全部行；标签命中写观测 PRESENT 但不清失联）
    - backend/src/main/java/com/archops/common/exception/GlobalExceptionHandler.java
    - backend/src/main/java/com/archops/user/security/TempAuthHeaders.java
    - backend/src/test/java/com/archops/support/HttpAcceptanceTest.java
    - .cursor/rules/backend-java.mdc

接缝已确认：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle/MockMvc 与 bootRun+curl 是同一条接缝。

用合同术语写作。未绑定 ≠ 冲突 ≠ 身份失联 ≠ 草案。草案在确认前不是策展真相。绑定记忆不是冲突。不要发明「未绑定处理人」。

================================================================================
2. 思想与质量条（完成标准：后续每一步都能对照这一节说「满足」）
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 两侧可用且不等。匹配失败产生未绑定观测候选，要并入必须经人（草案）绑定或新建，禁止同名静默合并。未绑定不是冲突；复用已接受冲突处理人会把匹配失败推进冲突升级链。

草案（ADR-0006）：确认前不属于策展真相。确认单位是条目，不是整单。02 只把 PENDING 夹具发出来；本票才逐条写入。拒绝 = 该条不写。接受 BIND 或 CREATE 即消费该现场实体（story 44）：剩余 PENDING 条不可再接受成第二次并入。

身份（ADR-0011 / 0012）：Docker 容器以策展容器 ID 为主键，现场靠不可变标签 archops.object_id 匹配。运行时 ID、名称只是线索。CREATE 写入的不可变标签必须抄现场标签。绑到已有不得改 X 的主键，也不得把错标签写成 X 的新 object id。MISSING_LABEL 没有可写的 object id，接受 CREATE 必须失败；夹具里仍保留该互斥条（02 已发），本票拒绝它作为成功路径。绑定不写可靠观测「运行于」；补标命中才恢复升级链（04）。

栈（ADR-0043）：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway 只增不改历史、PostgreSQL SSOT。Redis 可选用锁防双接受竞态，不作关系真相。规则夹具，不用 LLM。不引入 Maven、JPA 当地基、Vue、Neo4j、LangChain。

分层：Controller → Service → Mapper；DTO 用 record；DO 不当响应；构造器注入；业务错误 BusinessException；写操作事务在 service。Agent ingest 仍无 X-ArchOps-User-Id。审条须已认证。add-rest-api 只是绿灯阶段的层清单，不能替代红灯测试。薄 UI 是 07。

持久化（钉死，不要问用户）：
- 禁止改 V1–V17。新行为用 V18+。
- 绑定记忆新表（建议名 unbound_bind_memory）：source_host_id + runtime_id 唯一；curated_object_id NOT NULL REFERENCES curated_object(id)；created_at。这是匹配状态，不是冲突行，不要挂 conflict_id。
- GET /api/observed/unbound-candidates 默认 = 待并入：排除已有 bind memory 的 (source_host_id, runtime_id)。不要物理删 unbound_observation_candidate 行（01 upsert 与 04 还要用）。
- 成功接受 BIND：memory.curated_object_id = X。成功接受 CREATE：memory.curated_object_id = 新对象。CURATED_RUNS_ON_INSERT / REJECT 不写 memory。
- 条目 status 仍用 PENDING/ACCEPTED/REJECTED。草案 status 本票保持 OPEN（不要发明 COMPLETED）。VOIDED 是 04。
- CuratedDraftEventType 在本票加入 DRAFT_ITEM_ACCEPTED、DRAFT_ITEM_REJECTED。写 curated_draft_event，不要把未绑定条目事件塞进 conflict_case_event。
- CREATE 接受后必须回写该条目 subject_id = 新策展容器 id，以便同草案 CURATED_RUNS_ON_INSERT 知道主语。不要预插对象来喂 02 的 NULL subject。
- CURATED_RUNS_ON_INSERT 接受：插入该主语的第一条策展「运行于」（语义同 bootstrap confirmRunsOn 的首次插入，不是 applyAcceptedDraftRunsOn 的改已有目标）。已有「运行于」则 CURATED_RUNS_ON_EXISTS。CREATE 未接受则不可插入。

HTTP 形状（钉死）：
- 审条：POST /api/curated-drafts/{draftId}/items/{itemId}/accept
         POST /api/curated-drafts/{draftId}/items/{itemId}/reject
  body 为 {} 或不传业务字段。不要 forkId / conflictId / handler。
- 改策展路径保持原样：POST /api/conflicts/{id}/curated-drafts/open/items/{itemId}/accept|reject 仍要已接受处理人，只处理 RUNS_ON_TARGET_CHANGE。
- 错误码字面量（400 业务，信封 success=false、data=null）：
  AUTH_REQUIRED                         （401，未认证；已有）
  DRAFT_NOT_FOUND
  DRAFT_ITEM_NOT_FOUND
  DRAFT_ITEM_NOT_PENDING
  CURATED_OBJECT_ID_EXISTS              （新建标签已被占用；已有）
  CURATED_RUNS_ON_EXISTS                （bootstrap 覆盖拒绝；已有）
  CURATED_RUNS_ON_NOT_FOUND             （接受新建后尚未接受「运行于」时 GET 策展「运行于」；已有）
  UNBOUND_RUNS_ON_BEFORE_CREATE         （先接受 CURATED_RUNS_ON_INSERT）
  UNBOUND_CREATE_IMMUTABLE_ID_MISSING   （MISSING_LABEL 接受 CREATE）
  UNBOUND_BIND_TARGET_HEALTHY           （目标已标签命中：观测「运行于」availability=PRESENT）
  UNBOUND_CANDIDATE_CONSUMED            （该现场实体已因 BIND 或 CREATE 被消费；第二次并入）
  UNBOUND_ITEM_KIND_UNSUPPORTED         （未绑定草案上出现本票不审的 kind，或改策展条目被拿到未绑定路径）
  未认证 401 AUTH_REQUIRED

规则（钉死）：
1. 只审 origin=UNBOUND_CANDIDATE 且 status=OPEN 的草案。VOIDED/不存在 → DRAFT_NOT_FOUND 或既有 VOIDED 码（本票不产生 VOIDED）。
2. 条目必须属于该草案且 PENDING。
3. 任何已认证运维可审；不要 requireAcceptedHandler；不要 @PreAuthorize 限 SENIOR。
4. UNKNOWN 无失联对象：夹具仍是 CREATE + CURATED_RUNS_ON_INSERT（02 A/B 必须继续绿，items=2，无 BIND）。
5. UNKNOWN 且同宿主有身份失联对象 X：本票允许夹具多一条 BIND_UNBOUND_TO_EXISTING（subjectId=X）。这是 03 增量，用来交付 story 33。不要改 02 已绿测试的断言；02 C 未断言 kind 个数，保持绿即可。不要把 BIND 加进「无失联」的 UNKNOWN。
6. MISSING_LABEL + 失联：夹具仍是 BIND + CREATE（02 F）。接受 CREATE → UNBOUND_CREATE_IMMUTABLE_ID_MISSING。接受 BIND 后接受 CREATE（或反过来）→ UNBOUND_CANDIDATE_CONSUMED。
7. BIND 接受时目标必须仍是身份失联，且不得已是标签命中（观测「运行于」PRESENT）。健康命中 → UNBOUND_BIND_TARGET_HEALTHY。本票不要为了让目标变健康而清除 identity_lost_mark（那是 04）；用「先失联开草案，再发带正确标签的心跳写出观测 PRESENT」即可。
8. BIND 接受不得：改 X.immutableObjectId / 容器主键；写 observed_fact；清 identity_lost_mark；开冲突；出操作计划。
9. CREATE 接受：name=payload.proposedName；immutableObjectId=payload.immutableObjectId（= 现场 archops.object_id）。然后写 bind memory。
10. 默认 GET 未绑定按 runtimeId 过滤后再断言「不在列表」；禁止对 $.data 做 hasSize(0) 当全库空。
11. 禁止 POST /api/observed/unbound-candidates/{id}/bind 或任何不经条目的并入捷径。
12. 规则模板，禁止 LLM 主路径。

测试质量（/tdd）：
- 只测公开 HTTP：状态码、ApiResponse 信封、后续 GET 可读状态。
- 期望值来自独立真相：字面量 ACCEPTED、REJECTED、OPEN、PENDING、CREATE_CONTAINER_FROM_UNBOUND、CURATED_RUNS_ON_INSERT、BIND_UNBOUND_TO_EXISTING、CURATED_OBJECT_ID_EXISTS、CURATED_RUNS_ON_NOT_FOUND、CURATED_RUNS_ON_EXISTS、UNBOUND_RUNS_ON_BEFORE_CREATE、UNBOUND_CREATE_IMMUTABLE_ID_MISSING、UNBOUND_BIND_TARGET_HEALTHY、UNBOUND_CANDIDATE_CONSUMED、AUTH_REQUIRED、IDENTITY_LOST、CONFLICT_NOT_FOUND、DRAFT_ITEM_ACCEPTED、DRAFT_ITEM_REJECTED、「应该在哪」、「实际在哪」。禁止用实现再算一遍期望。
- 不测 Mapper SQL、Redis key、私有方法、调用图；不打开数据库当第二接缝。
- 一圈一条行为。禁止先铺完全部测试再实现，也禁止先写完实现再补测。
- 不 mock 本模块协作对象。用现有 @HttpAcceptanceTest（Zonky embedded Postgres）。该类 AFTER_CLASS 才清库：每个方法的 host 名、container objectId、runtimeId、agentId 必须唯一。
- 新测试放到新类 com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest。不要把 03 塞进 UnboundDraftCreateHttpAcceptanceTest、UnboundIdentityLostIngestHttpAcceptanceTest 或 ChangeCuratedDraftItemHttpAcceptanceTest。01/02/改策展测试保持独立回归，禁止合并删除。
- 测试名描述能力，不描述 Service 方法名。
- 第一圈必须是本票增量的诚实红灯：已认证 POST 未绑定 CREATE 条目的 accept。今日无该映射 → 404 或 NoHandler。禁止用「未认证已 401」或「改策展处理人仍能审条」当第一圈。

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；只跑这一方法；失败原因是「缺本圈行为」。把完整命令与失败输出追加到票 ## Comments（见下方模板）。
2. Green：只写让这一条测试通过的最少生产代码。
3. Refactor：不改行为，整理命名与结构；再跑同一条测试，仍绿。然后提交这一圈（why）。

Witnessed red 是硬门。已经绿的新能力测试不能事后称作 TDD 完成。/code-review 是票结束第二道门，替代不了每圈 refactor。

本票是新能力票：
- UnboundDraftCreateHttpAcceptanceTest、UnboundIdentityLostIngestHttpAcceptanceTest、ChangeCuratedDraft*HttpAcceptanceTest、CuratedTruthHttpAcceptanceTest 的 bootstrap 覆盖拒绝、vertical-slice unlabeled 负面，必须保持绿。
- 不要为了 03 的红灯删掉 02 的 POST drafts、不要删改策展 acceptItem 的处理人门禁、不要关掉 01 的 upsert/推断、不要让 CHANGE_CURATED 的 conflict_id 变可空。
- 若某条新测试因「/api/** 已要求登录」而首跑绿：只允许作为 reuse/regression（未认证圈），Comments 点名 SecurityConfig，且必须另有一条真正红的新断言覆盖本票增量。未认证圈不要当第一圈。
- 高级角色可审条：若 A 圈未限制角色，本圈可能首跑绿 → reuse，点名 A。
- 建底覆盖拒绝若首跑绿 → reuse，点名 CuratedTruthHttpAcceptanceTest / 改策展 01。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 全量测试红了 → 先修到绿，再开下一圈。
- 想「顺便」做标签命中清失联 / 选支闸门 / 薄 UI / tracer → 做本票清单里的下一圈。
- 想复用冲突 accept 路径 + 已接受处理人 → 停。给 CuratedDraftController 加按 draftId 的已认证 POST。
- 想接受 BIND 时写观测「运行于」或清失联 → 停。那是把弱线索当实际；命中收尾是 04。
- 想 MISSING_LABEL 新建成功（编一个 object id）→ 停。UNBOUND_CREATE_IMMUTABLE_ID_MISSING。
- 想整单全接受 → 停。
- 想用 LLM 生成/改条目 → 停。
- 想问用户错误码怎么拼 → 用本节钉死的字面量。
- 想靠删候选行证明消费 → 用 GET 待并入按 runtimeId 滤不到 + 再心跳仍滤不到；行可以仍在库里。
- 想为装红灯删除 02 的 createFromUnboundCandidate → 停。第一圈红灯来自缺 accept 映射。

Git：从已含本票文件的最新 origin/main 开分支。Cloud 分支名须匹配 cursor/<slug>-<本 run 指定后缀>。建议 slug：tdd-implement-unbound-03。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres，不依赖 Redis。单测：
cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew test

认证：Header X-ArchOps-User-Id。一般 = user-general-demo，高级 = user-senior-demo（TempAuthHeaders.USER_ID）。Agent POST /api/agent/heartbeat 不带头。不要新造用户体系，不要造未绑定处理人。

Comments 模板（每一圈追加）：
### Cycle <字母> — <行为一句>
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.<method>
（失败输出贴原文，或 reuse/regression：点名已覆盖它的测试方法全名）
Green command: （同上，exit 0）
Refactor: （一句；无则写「无结构改动」）
Commit: <hash> <message>

================================================================================
3. 现状（完成标准：你能指出「03 增量相对 02 发起 / 改策展审条缺哪几条 HTTP 行为」）
================================================================================

今日未绑定草案（02）：
- POST /api/observed/unbound-candidates/{id}/drafts → OPEN，origin=UNBOUND_CANDIDATE，条目全部 PENDING。
- GET /api/curated-drafts/{id} 与 /events（仅 DRAFT_CREATED）。
- UNKNOWN：CREATE + CURATED_RUNS_ON_INSERT；CREATE.subjectId JSON null；确认前不写策展。
- MISSING_LABEL+失联：BIND + CREATE；CREATE 无成功 immutableObjectId。
- 同一现场实体一份 OPEN；未认证 401；一般/高级均可发起。
- 无 accept/reject 映射：对 /api/curated-drafts/{id}/items/{itemId}/accept 今日 = 无处理器（已认证 404）或未认证 401。

今日改策展审条：
- 仅已接受冲突处理人可 POST /api/conflicts/{id}/curated-drafts/open/items/{itemId}/accept|reject。
- 只写 RUNS_ON_TARGET_CHANGE（改已有「运行于」目标）。
- 事件在 GET /api/conflicts/{id}/events。
- 那不是本票红灯。

今日观测（01）：
- listUnbound 返回全部候选行，不过滤 bind memory（表还不存在）。
- 标签命中会写观测 PRESENT，但不清 identity_lost_mark（04 才清）。本票 G 圈正是利用这一点：开草案时失联，再打标心跳让观测变 PRESENT，BIND 接受应失败。

Flyway：最新 V17。本票 schema 用 V18+。禁止改历史脚本。禁止把 IDENTITY_LOST 写进 observed_fact.availability。

01 / 02 / 改策展夹具要保持：ingest 仍 upsert；失联推断仍按主机范围；发起草案仍不写策展；改策展处理人仍能审 RUNS_ON_TARGET_CHANGE；bootstrap 覆盖仍 CURATED_RUNS_ON_EXISTS。

本票增量是：未绑定路径逐条接受/拒绝、新建写入、绑定记忆 + 待并入过滤、互斥与健康目标闸门、条目审计事件。

================================================================================
4. 步骤（按序；每步有完成标准。未完成不准跳到下一步）
================================================================================

建议测试类：com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest
风格：MockMvc、统一信封、建底 POST hosts/containers/facts/runs-on、Agent heartbeat（无用户头）、操作员 TempAuthHeaders。先走 02 的 POST drafts 拿到 draftId/itemId。

每方法唯一前缀（AFTER_CLASS 清库；禁止跨方法复用 id）：
- A  接受新建拒运行于：host u03a-h、runtime u03a-rt-unknown、label u03a-never、agent u03a-ag
- B  先运行于后新建失败：u03b-*
- C  object id 占用：u03c-*（bootstrap 先占 u03c-never）
- D  接受绑定：host u03d-h、容器 objectId u03d-oid、runtime u03d-rt-miss
- E  再心跳仍不待并入：u03e-*
- F  双接受：u03f-*
- G  绑到健康命中：u03g-*（先失联开草案，再带正确标签心跳）
- H  MISSING_LABEL 新建失败：u03h-*
- I  UNKNOWN 绑到已有：host u03i-h、已有失联 X objectId u03i-oid、UNKNOWN runtime u03i-rt-unknown、错标 u03i-wrong
- J  未认证 / 高级角色：u03j-*（未认证可最短夹具；高级独立 u03js-*）
- K  建底覆盖拒绝仍绿：u03k-* 或 reuse 既有测试
- L  条目事件 + 无整单/无计划：u03l-*
- M  票级回归与收尾

### 步骤 A — 第 1 圈：只接受新建、拒绝「运行于」（本票第一条诚实红灯）

夹具：只策展主机 A。UNKNOWN 快照 runtime=u03a-rt-unknown、label=u03a-never。一般角色开草案。记下 CREATE 与 CURATED_RUNS_ON_INSERT 的 itemId。

Red：Header user-general-demo
POST /api/curated-drafts/{draftId}/items/{createItemId}/accept
{}

期望 200，该条 status=ACCEPTED，subjectId 非 null。

同方法随后：
1. POST .../{runsOnItemId}/reject → 200，status=REJECTED
2. POST /api/curated/containers {"name":"u03a-probe","objectId":"u03a-never"} → 400 CURATED_OBJECT_ID_EXISTS
3. GET /api/curated/facts/runs-on/{create.subjectId} → 400 CURATED_RUNS_ON_NOT_FOUND
4. GET /api/curated-drafts/{draftId} → 仍 OPEN；两条分别为 ACCEPTED / REJECTED
5. 不要出现 branchKind / planId

今日无映射 → 404 或 NoHandler。这就是第一圈红灯。不要先写未认证测试来「完成」TDD。不要走冲突 accept 路径。

Green：只打通未绑定 accept/reject + CREATE 写策展容器 + 拒绝不写「运行于」。不要写 bind memory 以外的 04 行为。不要清失联（本夹具无失联）。
Refactor，提交。

完成标准：Comments 有本圈 red；该测试绿；02 发起测试仍绿。

### 步骤 B — 先接受「运行于」、尚未新建 → 失败，策展不变

独立 UNKNOWN 夹具 u03b-*。开草案后先 POST accept 在 CURATED_RUNS_ON_INSERT 上。

期望：
- 400 success=false code=UNBOUND_RUNS_ON_BEFORE_CREATE data=null
- CREATE 仍 PENDING；RUNS_ON 仍 PENDING（不要标 ACCEPTED）
- POST /api/curated/containers objectId=u03b-never → 200（对象仍未写）
- GET 草案仍 OPEN

提交。

完成标准：失败不写策展；条目状态不被部分提交污染。

### 步骤 C — 新建所用 archops.object_id 已被占用 → 接受失败

夹具：先 bootstrap POST containers objectId=u03c-never 得到已有对象。再 UNKNOWN 快照同一 label u03c-never（不同 runtime）。开草案，accept CREATE。

期望：400 CURATED_OBJECT_ID_EXISTS；CREATE 仍 PENDING；不出现第二个容器（再次 POST 同 objectId 仍是 CURATED_OBJECT_ID_EXISTS，且 GET 草案 CREATE.subjectId 仍 null）。

提交。

### 步骤 D — 接受绑到已有失联对象 X

夹具与 02 F / 01 推断正例同形：X 策展运行于 A；Agent 在 A 上报未打标 runtime u03d-rt-miss；确认 GET identity-lost/X = 200。开草案。accept BIND（subjectId=X）。

期望：
- 200，BIND status=ACCEPTED
- GET 策展容器：X 的 id 与 immutable objectId 仍是开草案前的字面量（不要变成 runtime 或 name）
- GET /api/curated/facts/runs-on/X 仍是 A（策展「运行于」本票不改）
- GET actual-where?containerId=X → identityLost=true，availability=IDENTITY_LOST，observedValue.hostId 为 JSON null（不是 A）
- GET /api/observed/identity-lost/X 仍 200
- GET unbound 按 u03d-rt-miss 滤不到（待并入消费）
- GET by-merge-key?subjectId=X → 仍 400 CONFLICT_NOT_FOUND（未打标不承诺升级链）
- 响应/后续 GET 都没有观测「运行于」因本接受而变成 PRESENT

不要发正确标签心跳（那会搅 04）。不要 void 草案。
提交。

完成标准：绑定记忆 + 待并入过滤有 HTTP 证据；弱线索没有变成实际。

### 步骤 E — 再心跳同一 runtimeId 仍缺标 → 仍不待并入、仍失联、仍不升级

独立夹具 u03e-*（不要依赖 D 方法的库行）。接受 BIND 后，再发同一 host+runtime 未打标快照（可改 name 以证明 upsert 仍发生）。

期望：
- GET unbound 按该 runtimeId 仍滤不到
- GET identity-lost/X 仍 200；actual-where 仍 IDENTITY_LOST
- by-merge-key 仍 CONFLICT_NOT_FOUND
- 开放草案仍 OPEN（刷新观察时间不得作废未绑定草案——04 才定义命中作废；本圈证明「仅刷新」不作废即可）

提交。

### 步骤 F — 绑定与新建都接受 → 第二次失败

MISSING_LABEL+失联夹具 u03f-*。先 accept BIND 200，再 accept CREATE。

期望：
- 第二次 400 UNBOUND_CANDIDATE_CONSUMED data=null
- CREATE 仍 PENDING（或至少不是 ACCEPTED）
- POST containers 不得因本失败而占用一个新的现场 object id（本夹具本就没有）
- X 仍只有一个策展对象（bootstrap 的那一个）

再写（或同方法后半若拆圈则下一方法）反过来：先 CREATE（本夹具应已是 H 的失败码）——本圈只测 BIND-then-CREATE。MISSING_LABEL 上 CREATE 本就不是成功路径，所以「先 CREATE 再 BIND」放到 H 之后若 CREATE 不能成功则不必强行；UNKNOWN+BIND 的双接受在 I 圈用 CREATE-then-BIND 补一条即可。

提交。

完成标准：一个现场实体不能变成两个策展对象。

### 步骤 G — 绑到仍标签命中、升级链有效的对象 → 失败

夹具 u03g-*：X 策展运行于 A；先未打标心跳 → 失联 + MISSING_LABEL 候选；开草案记下 bindItemId。然后第二次心跳：同一 host，容器带正确 labels.archops.object_id=X 的 immutable objectId（可用新 runtime；匹配看标签）。竖切生产会写观测 PRESENT。本票禁止在这次心跳里清失联或消费候选（04）。

然后 accept 原先的 BIND。

期望：
- 400 UNBOUND_BIND_TARGET_HEALTHY
- BIND 仍 PENDING
- X 的不可变标签不变
- 不要为了让本圈红灯而删除 upsertObservedPresent

如何证明「升级链有效」：第二次心跳后 GET /api/observed/asks/actual-where?containerId=X 的 observedValue.availability 为 PRESENT（标签命中）。不要断言 04 才有的「失联已清除」。identity-lost GET 在 04 前仍可能 200——本圈闸门看的是观测 PRESENT，不是 mark 消失。

提交。

### 步骤 H — MISSING_LABEL 新建不是成功路径

夹具 u03h-* 失联+缺标。accept CREATE。

期望：400 UNBOUND_CREATE_IMMUTABLE_ID_MISSING；无新 curated 容器（对任意新 objectId 的占用探针不得误伤）；CREATE 仍 PENDING。reject CREATE → 200 REJECTED，仍无新对象。

提交。

### 步骤 I — UNKNOWN 绑到已有：允许，且不得把错标签写成 X 的主键

夹具：A 上已有 X（objectId=u03i-oid）运行于 A；同机 UNKNOWN runtime u03i-rt-unknown、错标 u03i-wrong。开草案。

若 items 无 BIND：本圈红灯来自「缺 BIND 条」——green 时在 createFromUnboundCandidate 为 UNKNOWN+同宿主失联补 BIND，不要改 02 A/B（无失联，仍 2 条）。

accept BIND：
- 200
- GET X：id 与 objectId 仍是 u03i-oid，不是 u03i-wrong
- POST containers objectId=u03i-wrong 仍 200（错标未被写成任何人的主键；若你错误地用错标新建了对象，这一步会 CURATED_OBJECT_ID_EXISTS）
- 待并入滤不到 u03i-rt-unknown
- actual-where/X 仍 IDENTITY_LOST

同方法后半：另开一份独立 UNKNOWN+失联草案（不同 runtime u03i2-*）先 accept CREATE（正确写入错标为新对象），再 accept BIND → 400 UNBOUND_CANDIDATE_CONSUMED。

提交。

完成标准：错标可以成为新对象的主键（CREATE 路径），但 BIND 路径绝不能改写 X。

### 步骤 J — 未认证不可审条；一般与高级均可；无冲突处理人

1. 真实 draft/item，不带用户头 POST accept → 401 AUTH_REQUIRED。Comments 允许 reuse SecurityConfig；不要当第一圈。
2. 独立夹具，Header user-senior-demo accept CREATE → 200。若 A 未限角色而首跑绿：reuse，点名 A。
3. 不要先 claim 冲突。不要 @PreAuthorize 限 SENIOR。

提交。

### 步骤 K — 建底插入第一条「运行于」仍成功；覆盖已有仍 CURATED_RUNS_ON_EXISTS

本圈证明 03 没有关掉改策展 01 / 竖切建底。可写最短 HTTP：新容器无「运行于」时 POST facts/runs-on 200；第二次 400 CURATED_RUNS_ON_EXISTS。

若 CuratedTruthHttpAcceptanceTest 已覆盖且本方法首跑绿：reuse，点名该方法全名。不要删 bootstrap 拒绝来装红灯。

提交。

### 步骤 L — HTTP 可读条目已接受/已拒绝审计；无整单全接受；无操作计划

独立 UNKNOWN 夹具 u03l-*。accept CREATE + reject RUNS_ON 后 GET /api/curated-drafts/{id}/events：
- $.data[*].eventType 含 DRAFT_ITEM_ACCEPTED 与 DRAFT_ITEM_REJECTED
- 对应 detail.hint 含「草案条目已接受」与「草案条目已拒绝」
- actorUserId=user-general-demo；detail.draftId / itemId 对得上
- 仍有 02 的 DRAFT_CREATED

断言：
- 没有 POST /api/curated-drafts/{id}/accept 这类整单路由（可对明显路径 POST 一次期望 404/无处理器，不要实现它）
- GET /api/conflicts/{任意不相关或同宿主冲突}/operation-plans/active 仍 PLAN_NOT_FOUND（本发起/审条不出计划）
- 改策展 ChangeCuratedDraftItemHttpAcceptanceTest 的冲突侧 DRAFT_ITEM_ACCEPTED 仍绿

提交。

### 步骤 M — 票级回归与收尾

cd backend && ./gradlew test
失败则修到全绿（仍不扩范围）。

对照工单清单逐条用 HTTP 证据勾选。
/code-review：Standards + Spec。固定点用本分支相对 origin/main 的 merge-base。Spec 源：本票 + Spec 的「未绑定草案」节与 stories 25–28、31–36、44、51–53、59。审查发现的行为错误要修并回归；气味按 judgement 处理，不借审查塞进 04–07。

薄 UI：本票不接线。add-frontend-page 是 07。
不要添加标签命中清失联、选支闸门、VOIDED 未绑定草案、冲突失联旗标。若审查看到这些，删掉。

更新文档指针（03 完成后 frontier = 04；不要实现 04）：
- 本票：Status: done；验收项全勾；Comments 含每圈 red（或合法 reuse）
- docs/dev-handoff.md（下一对话 = 未绑定 04）
- AGENTS.md 当前工单 / §6 / §7
- CLAUDE.md 工单行
- docs/agents/issue-tracker.md 表
- .cursor/rules/project-map.mdc、domain-contract.mdc
- docs/specs/unbound-identity-rebind.md Further Notes / Status 行的 frontier

完成标准：全量测试绿；票 done；handoff 指向 04；工作区无命中清失联/选支闸门/UI 票外文件。

================================================================================
5. HTTP 契约（本票断言用；完成标准：测试只断言这些可观察值）
================================================================================

Agent ingest（无用户头）：
POST /api/agent/heartbeat
Content-Type: application/json

UNKNOWN：
{"agentId":"u03a-ag","hostId":"<A>","snapshot":{"containers":[{"runtimeId":"u03a-rt-unknown","name":"u03a-unknown","labels":{"archops.object_id":"u03a-never"}}]}}

MISSING_LABEL + 失联（identityLostObjectIds 省略）：
{"agentId":"u03d-ag","hostId":"<A>","snapshot":{"containers":[{"runtimeId":"u03d-rt-miss","name":"u03d-similar","labels":{}}],"absentObjectIds":[]}}

标签命中（仅 G 圈夹具，不清失联）：
{"agentId":"u03g-ag","hostId":"<A>","snapshot":{"containers":[{"runtimeId":"u03g-rt-hit","name":"u03g-x","labels":{"archops.object_id":"<X 的 immutable objectId>"}}]}}

操作员（Header X-ArchOps-User-Id）：
GET  /api/observed/unbound-candidates
POST /api/observed/unbound-candidates/{candidateId}/drafts     body {}
GET  /api/curated-drafts/{draftId}
GET  /api/curated-drafts/{draftId}/events
POST /api/curated-drafts/{draftId}/items/{itemId}/accept       body {}
POST /api/curated-drafts/{draftId}/items/{itemId}/reject       body {}
GET  /api/curated/asks/should-where?containerId=
GET  /api/curated/facts/runs-on/{containerId}
POST /api/curated/containers                                  body {"name":"…","objectId":"…"}
GET  /api/observed/identity-lost/{curatedContainerId}
GET  /api/observed/asks/actual-where?containerId=
GET  /api/conflicts/by-merge-key?subjectId=
GET  /api/conflicts/{conflictId}/operation-plans/active

建底：
POST /api/curated/hosts
POST /api/curated/containers
POST /api/curated/facts/runs-on     （无则插入；已有则仍 CURATED_RUNS_ON_EXISTS）

本票禁止调用：
POST /api/conflicts/{id}/branch-selection
POST /api/conflicts/{id}/curated-drafts/open/items/{itemId}/accept|reject   （不要用冲突路径审未绑定条）
POST /api/curated-drafts/{draftId}/accept                                  （不要实现整单全接受）
任何 SSH / 操作计划批准 / 开始执行

信封：成功 200 success=true；业务拒绝 400 success=false、code 字面量、data=null。未认证 401 AUTH_REQUIRED。
关系文案用「运行于」/ RUNS_ON、「应该在哪」、「实际在哪」。
GET 未绑定按 runtimeId 过滤；candidateId / draftId / itemId 用响应里的 id，不要用 runtimeId 当路径参数。

================================================================================
6. 停工检查（全部为真才许把票标 done）
================================================================================

- [ ] 每圈 Comments 里有独立的 red 命令与失败输出（合法 reuse 写明来源测试名）
- [ ] 第一圈是已认证 POST 未绑定 CREATE accept 的诚实红灯，不是未认证 401，也不是改策展审条仍绿
- [ ] 没有「先实现后补测」或「测试已绿再宣称 TDD」
- [ ] 没有删除 02 发起 / 01 推断 upsert / 改策展处理人审条 / bootstrap 覆盖拒绝 的生产来装红灯
- [ ] 没有 dummy 冲突；未绑定 GET 的 conflictId 仍为 JSON null
- [ ] 没有调用 branch-selection 或冲突 accept 路径来审本票条目
- [ ] UNKNOWN：只接受 CREATE、拒绝 RUNS_ON → 对象存在（CURATED_OBJECT_ID_EXISTS 探针）、无策展「运行于」
- [ ] 先 RUNS_ON 后 CREATE → UNBOUND_RUNS_ON_BEFORE_CREATE；占用 object id → CURATED_OBJECT_ID_EXISTS
- [ ] BIND 失联 X：主键/标签不变；actual-where 仍 IDENTITY_LOST；待并入滤不到；再心跳仍不待并入、仍失联、仍 CONFLICT_NOT_FOUND
- [ ] BIND+CREATE 第二次 UNBOUND_CANDIDATE_CONSUMED
- [ ] 观测 PRESENT 的健康目标 → UNBOUND_BIND_TARGET_HEALTHY；本票未清失联 mark
- [ ] MISSING_LABEL 接受 CREATE → UNBOUND_CREATE_IMMUTABLE_ID_MISSING
- [ ] UNKNOWN BIND 不把错标写成 X 的主键
- [ ] 未认证 401；一般与高级均可审条；无未绑定处理人
- [ ] 建底覆盖仍 CURATED_RUNS_ON_EXISTS；无整单全接受；无操作计划；事件 DRAFT_ITEM_ACCEPTED/REJECTED 在草案 events API
- [ ] 未改任何已有 V*.sql；新表只出现在 V18+；02/改策展测试仍绿
- [ ] 未改 CONTEXT.md / 已有 ADR
- [ ] 未实现 04 命中清失联/作废未绑定草案，未实现 05 选支闸门
- [ ] ./gradlew test 全绿（含 01、02、ChangeCuratedDraft*）
- [ ] /code-review 已跑；行为问题已修
- [ ] 文档 frontier 指向 04；04–07 的产品代码未做
```

---

完成后下一对话：**未绑定票 04**（标签命中收尾：清失联、消费候选、恢复升级链）。票路径 `.scratch/unbound-identity-rebind/issues/04-label-match-consume.md`。03 完成后不要默认做 05–07；05 即使 unblocked 也等编号更小的做完。
