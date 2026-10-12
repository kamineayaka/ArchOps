# 新对话：未绑定 / 身份失联票 02（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

Matt 位置：grilling / to-spec / to-tickets **已完成**。竖切 01–13 与改策展 01–06 已闭合。未绑定票 **01 TDD-done**。本对话只 `/implement` **未绑定刀 frontier = 02**。不要做 03–07，不要给 `change-curated-draft` 加 07，不要把 01 当 TDD redo。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现未绑定 / 身份失联 frontier 工单 02。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。本票是新能力票，不是 TDD redo，也不是 06 那种 suite/tracer。01 已闭合，禁止重做 01 的推断/upsert/问法。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围、路径、条目 kind、错误码或表设计。下面已钉死。不要用 Playwright、SSH fake、computerUse 或薄 UI 当作完成定义。不要默认开工 03–07。不要开工 05（虽已 unblocked，编号更大）。

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 03–07 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/unbound-identity-rebind/issues/02-unbound-draft-from-candidate.md

Spec：docs/specs/unbound-identity-rebind.md
一句话交付：已认证运维对一个待并入未绑定观测候选发起不挂冲突的草案；规则夹具给出至少两条独立可确认、全部 PENDING 的条目；发起瞬间不写策展、不创建操作计划；同一现场实体最多一份开放草案。人不接受、不拒绝、不绑定记忆、不消费候选。

本票对应 Spec User Stories 18–24、30（夹具发出 PENDING 条目）、59 的 DRAFT_CREATED。Stories 5、14–16、25–29、31–58、60 以及 tracer 步骤 5 以后是 03–07。Tracer 步骤 4 的形状（POST 草案、GET 策展尚无新对象）属于本票；步骤 5 的接受/拒绝是 03。

本票交付（用户可感知、HTTP 可断言）：
- POST /api/observed/unbound-candidates/{candidateId}/drafts → 200，OPEN 草案，origin=UNBOUND_CANDIDATE，conflictId/diagnosisId/selectedForkId 均为 JSON null。
- UNKNOWN_OBJECT_ID 候选 → ≥2 条独立 PENDING：CREATE_CONTAINER_FROM_UNBOUND（不可变标签 = 现场 archops.object_id）+ CURATED_RUNS_ON_INSERT（宿主 = 候选 sourceHostId）。CREATE 条目尚无策展 subject（subjectId JSON null），禁止为此预插 curated_object。
- 身份失联 + MISSING_LABEL 候选 → ≥2 条互斥 PENDING：BIND_UNBOUND_TO_EXISTING（subjectId = 失联对象策展 id）vs CREATE_CONTAINER_FROM_UNBOUND。CREATE 不得带可成功写入的 immutableObjectId（现场无标签）。
- GET /api/curated-drafts/{draftId} 可读上述 OPEN 草案与全部 PENDING 条目（payload 为 JSON 对象，不是 payload_json 原文字符串）。
- 未认证发起 → 401 code=AUTH_REQUIRED。已认证一般（user-general-demo）与高级（user-senior-demo）均可发起。无未绑定处理人，不复用已接受冲突处理人门禁。
- 同一 sourceHostId + runtimeId 第二份 OPEN → 400 code=UNBOUND_DRAFT_ALREADY_OPEN，data=null。
- 该草案不出现在 GET /api/conflicts/{conflictId}/curated-drafts/open 或 GET /api/conflicts/{conflictId}/curated-drafts/{draftId}。禁止 POST /api/conflicts/{id}/branch-selection 来创建本票草案。
- 发起不写策展：「应该在哪」不变；GET 策展没有该新对象（现场 object_id 仍可被 bootstrap POST /api/curated/containers 占用，不得变 CURATED_OBJECT_ID_EXISTS）。
- 发起不创建操作计划：有冲突夹具时 GET /api/conflicts/{id}/operation-plans/active 仍 400 PLAN_NOT_FOUND；POST 响应不是 BranchSelectionResult（无 branchKind / skipsDraft / planId）。
- 发起后候选仍待并入（GET unbound 按 runtimeId 仍能滤到）。消费是 03。
- GET /api/curated-drafts/{draftId}/events 可读 DRAFT_CREATED（detail.hint 含「草案已创建」）。不要把事件挂到假冲突上。
- 无「整单全接受」HTTP。本票不实现 accept/reject 写入。

本票不做（Out of ticket；发现自己在做就停，回到本票清单）：
- 条目接受/拒绝写入策展或绑定记忆（03）；MISSING_LABEL 新建作为成功路径；双接受绑定+新建；绑到仍健康标签命中对象
- 默认列表只显示待并入（绑定记忆过滤是 03）
- 标签命中清失联、消费候选、作废未绑定草案（04）
- 失联时禁止选支 / 改诊断分叉 / 作废计划与改理想草案 / 待确认关闭退回开放 / 冲突 GET 失联旗标（05）
- HTTP 总 tracer 套件（06）、薄 UI（07）
- 把未绑定草案挂到冲突上；插入 dummy 冲突行好让 conflict_id NOT NULL；复用 POST .../branch-selection
- SSH、操作计划、Y2、LLM 起草、网络可达、K8s/数据库对象
- 给 change-curated-draft 加 07；重做竖切 01–13；重做改策展 01–06；重做未绑定 01
- 改已有 V*.sql；改 CONTEXT.md / ADR-0039 / 已有 ADR 正文
- Maven、JPA 当地基、Vue、Neo4j v1 必选、LangChain、Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内/合同术语写作，不发明同义新词）
================================================================================

按序阅读，读完再写第一个测试：

1. AGENTS.md（一次一张；HTTP 主接缝；/implement 驱动 /tdd）
2. docs/agents/tdd.md（capability 票：必须 witnessed red；禁止为装红灯删除改策展/竖切/01 生产）
3. .scratch/unbound-identity-rebind/issues/02-unbound-draft-from-candidate.md
4. .scratch/unbound-identity-rebind/issues/03-itemized-create-and-bind.md（只为划清边界：接受/拒绝是下一张）
5. docs/specs/unbound-identity-rebind.md — 只取：Testing seams、未绑定草案、Curated write bypass、stories 18–24 / 30 / 59、tracer 步骤 4、Negative 4–5。不要实现 tracer 全序（那是 06），不要实现步骤 5 的接受
6. CONTEXT.md — 只用：未绑定观测候选、草案、逐条确认、策展真相、观测真相、冲突、身份失联、规范问法、操作计划、已接受处理人。Avoid 栏禁止的词不要用（尤其「未绑定处理人」「待确认策展」「以现场为准」）
7. docs/adr/0039-domain-contract-frozen.md
8. docs/adr/0043-tech-stack.md
9. docs/adr/0006-curated-writes-via-itemized-proposals.md（确认前不是策展真相；确认单位是条目）
10. docs/adr/0011-object-identity-rules.md 与 docs/adr/0012-container-label-bootstrap-and-identity-loss.md
11. docs/dev-handoff.md（确认 frontier = 未绑定 02）
12. 现行样板（读，不重写改策展/01 故事）：
    - backend/src/test/java/com/archops/observed/UnboundIdentityLostIngestHttpAcceptanceTest.java（01；保持绿；夹具风格）
    - backend/src/test/java/com/archops/curated/ChangeCuratedDraftHttpAcceptanceTest.java（冲突草案；selectChangeCuratedWritesDraftCreatedAuditEvent；DRAFT_ALREADY_OPEN）
    - backend/src/main/java/com/archops/curated/controller/CuratedDraftController.java（今日只有 /api/conflicts/{id}/curated-drafts/...）
    - backend/src/main/java/com/archops/curated/service/CuratedDraftService.java（createForChangeCurated 要求冲突+诊断+分叉+已接受处理人）
    - backend/src/main/java/com/archops/curated/dto/CuratedDraftResponse.java（冲突形：conflictId/diagnosisId/selectedForkId 必有；条目 RUNS_ON_TARGET_CHANGE）
    - backend/src/main/java/com/archops/curated/domain/CuratedDraftItemKind.java（今日仅 RUNS_ON_TARGET_CHANGE）
    - backend/src/main/resources/db/migration/V13__curated_draft_and_items.sql（conflict_id 等 NOT NULL；条目 subject/from/to NOT NULL；kind CHECK 仅 RUNS_ON_TARGET_CHANGE）
    - backend/src/main/resources/db/migration/V16__unbound_candidate_host_runtime_unique.sql（最新；下一版是 V17+）
    - backend/src/main/java/com/archops/observed/controller/ObservedController.java（今日无 POST drafts）
    - backend/src/main/java/com/archops/observed/dto/UnboundCandidateResponse.java（有 id/labels/runtimeId/reason）
    - backend/src/main/java/com/archops/plan/controller/OperationPlanController.java（branch-selection 与 active plan）
    - backend/src/main/java/com/archops/common/exception/GlobalExceptionHandler.java（AUTH_REQUIRED = 401）
    - backend/src/main/java/com/archops/user/security/TempAuthHeaders.java
    - backend/src/test/java/com/archops/support/HttpAcceptanceTest.java
    - backend/src/main/java/com/archops/curated/service/CuratedTruthService.java（createContainer 的 CURATED_OBJECT_ID_EXISTS）
    - .cursor/rules/backend-java.mdc

接缝已确认：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle/MockMvc 与 bootRun+curl 是同一条接缝。

用合同术语写作。未绑定 ≠ 冲突 ≠ 身份失联 ≠ 草案。草案在确认前不是策展真相。不要发明「未绑定处理人」。

================================================================================
2. 思想与质量条（完成标准：后续每一步都能对照这一节说「满足」）
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 两侧可用且不等。匹配失败产生未绑定观测候选，要并入必须经人（草案）绑定或新建，禁止同名静默合并。未绑定不是冲突；复用已接受冲突处理人会把匹配失败推进冲突升级链。

草案（ADR-0006）：确认前不属于策展真相。确认单位是条目，不是整单。本票只把草案发出来并让 HTTP 读得到 PENDING 条目；写入发生在 03 的逐条接受。

身份（ADR-0011 / 0012）：Docker 容器以策展容器 ID 为主键，现场靠不可变标签 archops.object_id 匹配。运行时 ID、名称只是线索。CREATE 条目的不可变标签必须抄现场标签；绑到已有不得改 X 的主键。MISSING_LABEL 没有可写的 object id，所以本票夹具里的新建条只是互斥选项，03 才拒绝它作为成功路径——本票不要把该条从夹具里删掉，也不要在本票接受它。

栈（ADR-0043）：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway 只增不改历史、PostgreSQL SSOT。Redis 不作关系真相。规则夹具生成草案，不用 LLM。不引入 Maven、JPA 当地基、Vue、Neo4j、LangChain。

分层：Controller → Service → Mapper；DTO 用 record；DO 不当响应；构造器注入；业务错误 BusinessException；写操作事务在 service。Agent ingest 仍无 X-ArchOps-User-Id。发起草案须已认证。add-rest-api / add-frontend-page 只是绿灯阶段的层清单，不能替代红灯测试。薄 UI 是 07。

持久化（钉死，不要问用户）：
- 禁止改 V13–V16。新行为用 V17+。
- 推广现有 curated_draft / curated_draft_item，不要新建平行草案表，也不要为未绑定插入 dummy 冲突。
- origin TEXT，CHECK IN ('CHANGE_CURATED','UNBOUND_CANDIDATE')；既有行默认 CHANGE_CURATED。
- CHANGE_CURATED：conflict_id / diagnosis_id / selected_fork_id 仍 NOT NULL（用 CHECK 保证）。
- UNBOUND_CANDIDATE：上述三列必须为 NULL；candidate_id NOT NULL REFERENCES unbound_observation_candidate(id)；可冗余存 source_host_id + runtime_id 以便唯一约束。
- 部分唯一索引：同一 candidate_id（等价 sourceHostId+runtimeId）最多一份 status=OPEN 的 UNBOUND_CANDIDATE 草案。保留现有「每冲突一份 OPEN」。
- conflict_id / diagnosis_id / selected_fork_id：DROP NOT NULL 后再加上述 CHECK。这是新版本，不是改 V13。
- 条目 kind CHECK 在新版本里加入 CREATE_CONTAINER_FROM_UNBOUND、BIND_UNBOUND_TO_EXISTING、CURATED_RUNS_ON_INSERT（保留 RUNS_ON_TARGET_CHANGE）。不要原地改 V13 的 CHECK。
- CREATE 条目：subject_id / from_host_id / to_host_id 必须允许 NULL（确认前没有策展对象）。不要预插 curated_object 来喂 NOT NULL。
- 审计：新表 curated_draft_event（draft_id、event_type、actor_user_id、detail_json、created_at）。本票只写 DRAFT_CREATED。不要往 conflict_case_event 塞无冲突的行，不要给 ConflictEventType 加未绑定专用枚举。

HTTP 形状（钉死）：
- 创建：POST /api/observed/unbound-candidates/{candidateId}/drafts ，body 为 {} 或不传业务字段。不要 forkId / diagnosisId / conflictId。
- 读取：GET /api/curated-drafts/{draftId}（含日后 VOIDED；本票只产生 OPEN）。
- 事件：GET /api/curated-drafts/{draftId}/events
- 改策展路径保持原样：GET/POST /api/conflicts/{id}/curated-drafts/... 与 POST /api/conflicts/{id}/branch-selection。未绑定 GET 不要实现 BranchSelectionResult。
- 未绑定 GET DTO 可与改策展共用 record，但 conflictId 必须能序列化 JSON null，并多 origin / candidateId / sourceHostId / runtimeId。条目 payload 为对象。
- 错误码字面量（400 业务，信封 success=false、data=null）：
  UNBOUND_CANDIDATE_NOT_FOUND
  UNBOUND_DRAFT_ALREADY_OPEN
  DRAFT_NOT_FOUND（按 draft id 读取不存在时；可与改策展共用）
  未认证 401 AUTH_REQUIRED（已有 SecurityConfig /api/** authenticated）

规则夹具（钉死）：
1. 只对一个候选发一份草案。candidateId 来自 GET /api/observed/unbound-candidates 按 runtimeId 过滤后的 id。
2. reason=UNKNOWN_OBJECT_ID → 恰好两类 kind：CREATE_CONTAINER_FROM_UNBOUND + CURATED_RUNS_ON_INSERT。即使同机另有失联对象，也不对 UNKNOWN 发 BIND（那是另一现场实体）。
3. CREATE 的 payload.immutableObjectId 与 payload.labels['archops.object_id'] = 现场标签字面量；payload.proposedName = 候选 name；payload.sourceHostId / runtimeId = 现场实体。
4. CURATED_RUNS_ON_INSERT 的 toHostId = 候选 sourceHostId；subjectId JSON null。
5. reason=MISSING_LABEL 且该宿主范围内存在身份失联对象 X → BIND_UNBOUND_TO_EXISTING（subjectId=X 的策展 id）+ CREATE_CONTAINER_FROM_UNBOUND（payload 无 immutableObjectId 或为 null）。不要发 CURATED_RUNS_ON_INSERT 作为本夹具的第三条 Must。
6. 本票不要求「MISSING_LABEL 且无失联对象」的发起路径。撞上可 400，不要为此发明绑定目标。
7. 所有发出的条目 status=PENDING，seq 从 1 递增且稳定。
8. 规则模板，禁止 LLM 主路径。

测试质量（/tdd）：
- 只测公开 HTTP：状态码、ApiResponse 信封、后续 GET 可读状态。
- 期望值来自独立真相：字面量 OPEN、PENDING、UNBOUND_CANDIDATE、CREATE_CONTAINER_FROM_UNBOUND、CURATED_RUNS_ON_INSERT、BIND_UNBOUND_TO_EXISTING、AUTH_REQUIRED、UNBOUND_DRAFT_ALREADY_OPEN、UNBOUND_CANDIDATE_NOT_FOUND、DRAFT_NOT_FOUND、PLAN_NOT_FOUND、CURATED_OBJECT_ID_EXISTS、DRAFT_CREATED、「应该在哪」、DRAFT_ALREADY_OPEN（仅用于证明改策展路径未串台）。禁止用实现再算一遍期望。
- 不测 Mapper SQL、Redis key、私有方法、调用图；不打开数据库当第二接缝。
- 一圈一条行为。禁止先铺完全部测试再实现，也禁止先写完实现再补测。
- 不 mock 本模块协作对象。用现有 @HttpAcceptanceTest（Zonky embedded Postgres）。该类 AFTER_CLASS 才清库：每个方法的 host 名、container objectId、runtimeId、agentId、candidate 过滤键必须唯一。
- GET /api/observed/unbound-candidates 返回全类共享库里的全部候选。禁止对 $.data 做 hasSize(1)/hasSize(2)。按 runtimeId 过滤后再断言。POST 响应 $.data.items 是本请求草案，可以 hasSize。
- 新测试放到新类 com.archops.observed.UnboundDraftCreateHttpAcceptanceTest。不要把 02 塞进 UnboundIdentityLostIngestHttpAcceptanceTest 或 ChangeCuratedDraftHttpAcceptanceTest。改策展与 01 测试保持独立回归，禁止合并删除。
- 测试名描述能力，不描述 Service 方法名。
- 第一圈必须是本票增量的诚实红灯（今日无 POST drafts → 404 或 NoHandler）。禁止用「未认证已 401」或「改策展仍能选支出草案」当第一圈。

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；只跑这一方法；失败原因是「缺本圈行为」（编译失败或断言失败都算）。把完整命令与失败输出追加到票 ## Comments（见下方模板）。
2. Green：只写让这一条测试通过的最少生产代码。
3. Refactor：不改行为，整理命名与结构；再跑同一条测试，仍绿。然后提交这一圈（why）。

Witnessed red 是硬门。已经绿的新能力测试不能事后称作 TDD 完成。/code-review 是票结束第二道门，替代不了每圈 refactor。

本票是新能力票：
- UnboundIdentityLostIngestHttpAcceptanceTest 全部方法、ChangeCuratedDraft*HttpAcceptanceTest、CuratedTruthHttpAcceptanceTest 的 bootstrap 覆盖拒绝、vertical-slice unlabeled 负面，必须保持绿。
- 不要为了 02 的红灯删掉改策展 createForChangeCurated、不要把 conflict_id 改成对 CHANGE_CURATED 也可空、不要关掉 01 的 upsert/推断。
- 若某条新测试因「/api/** 已要求登录」而首跑绿：只允许作为 reuse/regression，Comments 点名 SecurityConfig，且必须另有一条真正红的新断言覆盖本票增量。未认证圈不要当第一圈。
- 高级角色可发起：若 A 圈未限制角色，本圈可能首跑绿 → reuse，点名 A，不要删一般角色生产来装红灯。
- 不要为装红灯把改策展 DRAFT_CREATED 从冲突事件里删掉。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 全量测试红了 → 先修到绿，再开下一圈。
- 想「顺便」做接受/绑定记忆/选支闸门/消费候选/薄 UI → 做本票清单里的下一圈。
- 想复用 branch-selection / 已接受处理人 → 停。给 ObservedController 加已认证 POST，任何已认证运维可调用。
- 想给未绑定插一条 dummy 冲突好让 V13 的 NOT NULL 活下去 → 停。V17+ 把未绑定的 conflict_id 置空。
- 想预建策展容器好让 CREATE 的 subject_id NOT NULL → 停。确认前没有该对象正是本票要证明的。
- 想在创建圈实现 accept → 停。写入是 03。
- 想用 LLM 生成条目 → 停。规则模板。
- 想问用户 origin 怎么拼、错误码怎么拼 → 用本节钉死的字面量。
- 想靠「列表变短」证明发起成功 → 发起不消费候选；用 GET 草案 + kind 字面量证明。

Git：从已含本票文件的最新 origin/main 开分支。Cloud 分支名须匹配 cursor/<slug>-<本 run 指定后缀>。建议 slug：tdd-implement-unbound-02。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres，不依赖 Redis。单测：
cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftCreateHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew test

认证：Header X-ArchOps-User-Id。一般 = user-general-demo，高级 = user-senior-demo（TempAuthHeaders.USER_ID）。Agent POST /api/agent/heartbeat 不带头。不要新造用户体系，不要造未绑定处理人。

Comments 模板（每一圈追加）：
### Cycle <字母> — <行为一句>
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftCreateHttpAcceptanceTest.<method>
（失败输出贴原文，或 reuse/regression：点名已覆盖它的测试方法全名）
Green command: （同上，exit 0）
Refactor: （一句；无则写「无结构改动」）
Commit: <hash> <message>

================================================================================
3. 现状（完成标准：你能指出「02 增量相对改策展草案 / 01 ingest 缺哪几条 HTTP 行为」）
================================================================================

今日草案只走改策展：
- 仅已接受冲突处理人 POST /api/conflicts/{id}/branch-selection + CHANGE_CURATED_TO_OBSERVED 才出 OPEN 草案。
- curated_draft.conflict_id / diagnosis_id / selected_fork_id NOT NULL；每冲突一份 OPEN。
- 条目只有 RUNS_ON_TARGET_CHANGE；subject/from/to 都指向已有 curated_object。
- GET 只有 /api/conflicts/{conflictId}/curated-drafts/open 与 /{draftId}。没有 GET /api/curated-drafts/{id}。
- DRAFT_CREATED 写在 GET /api/conflicts/{id}/events。
- 选改理想明确不写策展、不建计划（改策展 03 已证明）。那是冲突路径，不能拿来冒充本票红灯。

今日未绑定（01）：
- GET /api/observed/unbound-candidates 含 id、labels、runtimeId、reason、sourceHostId；upsert 按 host+runtimeId。
- 控制面可推断身份失联；规范问法 IDENTITY_LOST 读模型。
- ObservedController 无 POST drafts。对 /api/observed/unbound-candidates/{id}/drafts 今日 = 无处理器（已认证 404）或未认证 401。

Flyway：最新 V16。V13 约束仍在。本票 schema 用 V17+。禁止改历史脚本。禁止把 IDENTITY_LOST 写进 observed_fact.availability。

01 / 改策展夹具要保持：ingest 仍 upsert；失联推断仍按主机范围；改策展仍能选支出 RUNS_ON_TARGET_CHANGE 草案并写冲突侧 DRAFT_CREATED；bootstrap POST 容器在 object_id 未占用时仍 200。

本票增量是：从不挂冲突的候选发规则草案、HTTP 读 PENDING 条目、发起不写策展/不建计划、同一实体一份 OPEN、一般与高级均可发起。

================================================================================
4. 步骤（按序；每步有完成标准。未完成不准跳到下一步）
================================================================================

建议测试类：com.archops.observed.UnboundDraftCreateHttpAcceptanceTest
风格：MockMvc、统一信封、建底 POST /api/curated/hosts|containers 与 facts/runs-on、Agent POST /api/agent/heartbeat（无用户头）、操作员用 TempAuthHeaders。candidateId 从 GET unbound 按 runtimeId 取出。

每方法唯一前缀（AFTER_CLASS 清库；禁止跨方法复用 id）：
- A  创建 UNKNOWN：host u02a-h、runtime u02a-rt-unknown、label u02a-never、agent u02a-ag
- B  GET by id：u02b-*（独立夹具，不要依赖 A 方法的库行）
- C  不写策展：host u02c-h、已有容器 objectId u02c-oid、runtime 未知 u02c-rt-unknown、label u02c-never
- D  未认证：可复用最短 UNKNOWN 夹具 u02d-*
- E  高级角色：u02e-*
- F  失联+缺标互斥：host u02f-h、容器 u02f-oid、runtime u02f-rt-miss、agent u02f-ag；identityLostObjectIds 省略
- G  第二份 OPEN：u02g-*
- H  不出现在冲突草案 API：失联/未绑定实体 u02h1-* 与另一套可升级冲突实体 u02h2-*（两宿主 + 标签命中错位）
- I  不建操作计划：可接 H 形状 u02i-*，或与 H 同方法若已覆盖则 reuse
- J  DRAFT_CREATED 事件：u02j-*
- K  未知 candidateId：u02k-missing（无需心跳）

### 步骤 A — 第 1 圈：一般角色对 UNKNOWN 候选发 OPEN 草案（本票第一条诚实红灯）

夹具：只策展主机 A，不需要已有容器。Agent 快照一个带未知 archops.object_id=u02a-never 的容器。GET unbound 过滤 runtimeId=u02a-rt-unknown 得到 candidateId。

Red：Header X-ArchOps-User-Id=user-general-demo
POST /api/observed/unbound-candidates/{candidateId}/drafts
Content-Type: application/json
{}

期望：
- 200 success=true
- data.status=OPEN
- data.origin=UNBOUND_CANDIDATE
- data.conflictId / diagnosisId / selectedForkId 均为 JSON null
- data.candidateId 等于请求路径上的 id
- data.sourceHostId / runtimeId 为本次字面量
- data.createdBy=user-general-demo
- data.items 长度 2
- kinds 恰好为 CREATE_CONTAINER_FROM_UNBOUND 与 CURATED_RUNS_ON_INSERT（顺序以 seq 为准，不要假设数组顺序除非你按 seq 排序后再断言）
- 每条 status=PENDING
- CREATE：subjectId JSON null；payload.immutableObjectId 与 payload.labels.archops.object_id = u02a-never；payload.proposedName 为本次 name
- RUNS_ON_INSERT：toHostId = A 的策展 host id；subjectId JSON null
- 没有 RUNS_ON_TARGET_CHANGE，没有 BIND_UNBOUND_TO_EXISTING
- 响应不是 branchKind/skipsDraft

今日无映射 → 404 或编译失败。这就是第一圈红灯。不要先写未认证测试来「完成」TDD。

Green：只打通创建 + 规则夹具 + 可空 conflict 的 schema。不要写策展对象。不要实现 accept。不要改 branch-selection。
Refactor，提交。

完成标准：Comments 有本圈 red（404/NoHandler/断言失败皆可，须是缺本圈行为）；该测试绿；ChangeCuratedDraftHttpAcceptanceTest 里选改理想仍绿。

### 步骤 B — 第 2 圈：GET /api/curated-drafts/{draftId} 可读同一份 OPEN 草案

独立夹具 u02b-*。POST 创建后记下 data.id，再 GET /api/curated-drafts/{draftId}（不要走 /api/conflicts/...）。

断言与 A 相同的 origin/status/items/kind/payload 字面量，且 GET 与 POST 的 id 一致。

今日无该 GET → 诚实红灯。若 A 的 green 已顺手挂上 GET 且本方法首跑绿：Comments 写 reuse，点名 A 的生产，不另扩行为。不要为此去删 GET。
提交。

完成标准：不经过 conflictId 也能读到草案。

### 步骤 C — 第 3 圈：发起瞬间不写策展，候选仍待并入

夹具：A 上已有策展容器 X（objectId=u02c-oid）运行于 A；同机再上报 UNKNOWN runtime u02c-rt-unknown / label u02c-never。对该 UNKNOWN 候选发草案。

同方法随后：
1. GET /api/curated/asks/should-where?containerId=X → question=应该在哪，host 仍是 A
2. GET /api/curated/facts/runs-on/X → 仍是 A
3. POST /api/curated/containers {"name":"u02c-probe","objectId":"u02c-never"} → 200（不得 400 CURATED_OBJECT_ID_EXISTS）
4. GET /api/observed/unbound-candidates 过滤 u02c-rt-unknown 仍存在（发起不消费）

今日若错误地在创建时 insert 策展容器，第 3 步会 CURATED_OBJECT_ID_EXISTS → 本圈应红然后修到「确认前不写」。不要在本圈实现接受新建。
提交。

完成标准：X 的应该在哪不变；u02c-never 仍可被 bootstrap 占用；候选仍在列表。第 3 步会留下一个 bootstrap 容器，AFTER_CLASS 清库即可，不要再写删除 API。

### 步骤 D — 未认证发起被拒绝

对真实 candidateId POST 同一路径，不带 X-ArchOps-User-Id。

期望：401，code=AUTH_REQUIRED，data=null。不要 200。

SecurityConfig 已要求 /api/** 认证：本方法在 A 之前也会 401。必须写在 A 之后；Comments 允许 reuse/regression，点名 SecurityConfig 与 TempAuthHttpAcceptanceTest / CuratedTruthHttpAcceptanceTest 的未认证例。不要删认证过滤器来装红灯。不要把 Agent ingest permitAll 扩到这条 POST。
提交。

### 步骤 E — 高级角色也可发起

独立 UNKNOWN 候选 u02e-*。Header user-senior-demo。期望 200、createdBy=user-senior-demo、OPEN、≥2 PENDING。

不要要求该用户先认领冲突。不要 @PreAuthorize 限 SENIOR。若 A 未限角色而本圈首跑绿：reuse，点名 A。若你误做成「仅已接受处理人」或「仅高级」：本圈红，去掉该门禁。
提交。

完成标准：一般与高级两条路径都有 HTTP 证据；无未绑定处理人。

### 步骤 F — 身份失联 + MISSING_LABEL：互斥 BIND vs CREATE，皆 PENDING

夹具与 01 推断正例同形：策展容器 X 运行于 A；Agent 在 A 上报未打标 runtime u02f-rt-miss，无 absentObjectIds，无 identityLostObjectIds。先用 GET /api/observed/identity-lost/{X} = 200 确认失联（01 生产，本圈当夹具，不要重写推断）。再对该 MISSING_LABEL 候选 POST 草案（一般角色）。

期望：
- 200 OPEN
- items 长度 2
- kinds 恰好 BIND_UNBOUND_TO_EXISTING 与 CREATE_CONTAINER_FROM_UNBOUND
- BIND.subjectId = X 的策展 id（POST containers 返回的 data.id）
- CREATE.subjectId JSON null；payload 没有可成功写入的 immutableObjectId（缺字段或 JSON null）
- 全部 PENDING
- GET identity-lost/X 仍 200；GET actual-where 仍 identityLost=true / availability=IDENTITY_LOST（发起不清除失联）
- should-where 仍为 A
- 没有 CURATED_RUNS_ON_INSERT 作为本夹具 Must（绑定 vs 新建，不是新建 vs 运行于）

不要 POST accept。不要写绑定记忆。不要从待并入列表拿掉该 runtimeId。
提交。

完成标准：互斥两条都在且 PENDING；策展与失联标未被本发起改写。

### 步骤 G — 同一现场实体第二份 OPEN 被拒绝

夹具 u02g-*：对同一 candidateId 连续两次 POST drafts。第一次 200。第二次：
- 400 success=false
- code=UNBOUND_DRAFT_ALREADY_OPEN
- data=null
- GET /api/curated-drafts/{第一次的 id} 仍 OPEN，items 仍两条（不要把第一份作废或复制）

code 必须是本票字面量，不要复用改策展的 DRAFT_ALREADY_OPEN（那是「每冲突一份」）。改策展 ChangeCuratedDraftHttpAcceptanceTest 里 DRAFT_ALREADY_OPEN 仍须绿。
提交。

### 步骤 H — 未绑定草案不出现在冲突下的改理想草案 API

夹具分两套实体，禁止把未绑定挂到冲突上：
1. 未绑定：与 F 类似，A 上 X 失联 + MISSING_LABEL 候选，对其发草案，记下 unboundDraftId。
2. 冲突画布：另一容器 Y，策展运行于 A，现场标签命中在已策展主机 B（竖切升级链）。得到 Y 的 conflictId（GET /api/conflicts/by-merge-key?subjectId=Y 200）。不要对该冲突 claim/选支。

断言：
- GET /api/conflicts/{Y 的 conflictId}/curated-drafts/open → 400 DRAFT_NOT_FOUND（该冲突没有改理想草案）
- GET /api/conflicts/{Y 的 conflictId}/curated-drafts/{unboundDraftId} → 400 DRAFT_NOT_FOUND
- GET /api/curated-drafts/{unboundDraftId} → 200，conflictId JSON null，origin=UNBOUND_CANDIDATE
- GET /api/conflicts/by-merge-key?subjectId=X → 仍 400 CONFLICT_NOT_FOUND（未打标不承诺升级链；未绑定不是冲突）

本方法禁止调用 POST .../branch-selection。禁止为了让冲突侧 GET 200 而给未绑定草案填上 Y 的 conflictId。
提交。

完成标准：同一 HTTP 证据既证明可读未绑定草案，又证明冲突草案 API 看不见它。

### 步骤 I — 发起不创建操作计划

写在 H 绿之后。可用独立夹具或与 H 同形。对未绑定候选发草案后：
- GET /api/conflicts/{Y 的 conflictId}/operation-plans/active → 400 PLAN_NOT_FOUND
- POST 未绑定 drafts 的响应 JSON 没有 branchKind、skipsDraft、planId

若 H 已含 PLAN_NOT_FOUND 且首跑绿：reuse，点名 H。不要为了本圈去走 FIX_ACTUAL 选支。
提交。

### 步骤 J — DRAFT_CREATED 可经草案事件 HTTP 读取

独立 UNKNOWN 夹具 u02j-*。创建后 GET /api/curated-drafts/{draftId}/events：
- 200
- $.data[*].eventType 含 DRAFT_CREATED
- 该事件 detail.hint 含「草案已创建」
- 该事件 actor 可映射到 user-general-demo（字段名与冲突事件对齐：actorUserId 或现有冲突事件 DTO 的等价字段；选定后全票一致，不要两种拼法）
- detail.draftId 等于该草案 id
- origin 或 detail.origin = UNBOUND_CANDIDATE（任选一处，测试锁定一处）

不要写到 GET /api/conflicts/{id}/events。改策展 selectChangeCuratedWritesDraftCreatedAuditEvent 必须仍绿。不要实现 DRAFT_ITEM_ACCEPTED / DRAFT_ITEM_REJECTED / DRAFT_VOIDED 的写入（03/04）。
提交。

### 步骤 K — 未知候选发起为业务错误

POST /api/observed/unbound-candidates/u02k-missing/drafts （已认证，无心跳）。
期望：400，code=UNBOUND_CANDIDATE_NOT_FOUND，data=null。不要 500，不要 401。

今日无映射时可能 404；A 绿之后若未做缺 id 分支则会 500 或 NPE → 诚实红灯。
提交。

### 步骤 L — 票级回归与收尾

cd backend && ./gradlew test
失败则修到全绿（仍不扩范围）。

对照工单清单逐条用 HTTP 证据勾选。
/code-review：Standards + Spec。固定点用本分支相对 origin/main 的 merge-base。Spec 源：本票 + Spec 的「未绑定草案」节与 stories 18–24、30、59。审查发现的行为错误要修并回归；气味按 judgement 处理，不借审查塞进 03–07。

薄 UI：本票不接线。add-frontend-page 是 07。
不要添加 POST /api/curated-drafts/{id}/items/{itemId}/accept|reject。若审查看到这些映射，删掉（那是 03）。

更新文档指针（02 完成后 frontier = 03；不要实现 03）：
- 本票：Status: done；验收项全勾；Comments 含每圈 red（或合法 reuse）
- docs/dev-handoff.md（下一对话 = 未绑定 03）
- AGENTS.md 当前工单 / §6 / §7
- CLAUDE.md 工单行
- docs/agents/issue-tracker.md 表
- .cursor/rules/project-map.mdc、domain-contract.mdc
- docs/specs/unbound-identity-rebind.md Further Notes / Status 行的 frontier

完成标准：全量测试绿；票 done；handoff 指向 03；工作区无 accept/绑定记忆/选支闸门/UI 票外文件。

================================================================================
5. HTTP 契约（本票断言用；完成标准：测试只断言这些可观察值）
================================================================================

Agent ingest（无用户头）：
POST /api/agent/heartbeat
Content-Type: application/json

UNKNOWN 快照：
{"agentId":"u02a-ag","hostId":"<策展主机 id>","snapshot":{"containers":[{"runtimeId":"u02a-rt-unknown","name":"u02a-unknown","labels":{"archops.object_id":"u02a-never"}}]}}

MISSING_LABEL + 失联夹具（identityLostObjectIds 省略）：
{"agentId":"u02f-ag","hostId":"<A>","snapshot":{"containers":[{"runtimeId":"u02f-rt-miss","name":"u02f-similar","labels":{}}],"absentObjectIds":[]}}

操作员（Header X-ArchOps-User-Id）：
GET  /api/observed/unbound-candidates
POST /api/observed/unbound-candidates/{candidateId}/drafts     body {}
GET  /api/curated-drafts/{draftId}
GET  /api/curated-drafts/{draftId}/events
GET  /api/curated/asks/should-where?containerId=
GET  /api/curated/facts/runs-on/{containerId}
POST /api/curated/containers                                  body {"name":"…","objectId":"…"}
GET  /api/observed/identity-lost/{curatedContainerId}
GET  /api/observed/asks/actual-where?containerId=
GET  /api/conflicts/by-merge-key?subjectId=
GET  /api/conflicts/{conflictId}/curated-drafts/open
GET  /api/conflicts/{conflictId}/curated-drafts/{draftId}
GET  /api/conflicts/{conflictId}/operation-plans/active

建底：
POST /api/curated/hosts
POST /api/curated/containers     body {"name":"…","objectId":"…"}
POST /api/curated/facts/runs-on   body {"containerId":"…","hostId":"…"}  （无则插入；已有则仍 CURATED_RUNS_ON_EXISTS）

本票禁止调用：
POST /api/conflicts/{id}/branch-selection
POST /api/conflicts/{id}/curated-drafts/open/items/{itemId}/accept|reject
POST /api/curated-drafts/{draftId}/items/{itemId}/accept|reject

信封：成功 200 success=true；业务拒绝 400 success=false、code 字面量、data=null。未认证 401 AUTH_REQUIRED。
关系文案用「运行于」/ RUNS_ON、「应该在哪」、「实际在哪」。
GET 未绑定按 runtimeId 过滤；candidateId 用列表项 id，不要用 runtimeId 当路径参数。

================================================================================
6. 停工检查（全部为真才许把票标 done）
================================================================================

- [ ] 每圈 Comments 里有独立的 red 命令与失败输出（合法 reuse 写明来源测试名）
- [ ] 第一圈是已认证 POST 创建草案的诚实红灯，不是未认证 401，也不是改策展选支仍绿
- [ ] 没有「先实现后补测」或「测试已绿再宣称 TDD」
- [ ] 没有删除改策展 conflict 草案 / 01 推断 upsert / bootstrap 覆盖拒绝 的生产来装红灯
- [ ] 没有 dummy 冲突行；未绑定 GET 的 conflictId 为 JSON null；origin=UNBOUND_CANDIDATE
- [ ] 没有调用 branch-selection 来创建本票草案
- [ ] UNKNOWN：CREATE + CURATED_RUNS_ON_INSERT，皆 PENDING；CREATE.subjectId 为 null
- [ ] MISSING_LABEL+失联：BIND + CREATE，皆 PENDING；CREATE 无成功 immutableObjectId
- [ ] 发起后 should-where 不变；现场 object_id 仍可 bootstrap 占用；候选仍待并入
- [ ] 一般与高级均可发起；未认证 401 AUTH_REQUIRED
- [ ] 第二份 OPEN → UNBOUND_DRAFT_ALREADY_OPEN；改策展 DRAFT_ALREADY_OPEN 语义未串台
- [ ] 冲突侧 curated-drafts GET 看不见该草案；active plan 仍 PLAN_NOT_FOUND
- [ ] GET /api/curated-drafts/{id}/events 有 DRAFT_CREATED；冲突侧原 DRAFT_CREATED 仍绿
- [ ] 未实现 accept/reject 写入；无整单全接受；无绑定记忆；无 LLM
- [ ] 未改任何已有 V*.sql；新约束只出现在 V17+；CHANGE_CURATED 行仍要求 conflict_id 等非空
- [ ] 未改 CONTEXT.md / 已有 ADR
- [ ] ./gradlew test 全绿（含 01 与 ChangeCuratedDraft*）
- [ ] /code-review 已跑；行为问题已修
- [ ] 文档 frontier 指向 03；03–07 的产品代码未做
```

---

完成后下一对话：**未绑定票 03**（逐条确认：新建写入对象；绑定只记对应关系）。票路径 `.scratch/unbound-identity-rebind/issues/03-itemized-create-and-bind.md`。02 完成后不要默认做 04–07；05 即使 unblocked 也等编号更小的做完。
