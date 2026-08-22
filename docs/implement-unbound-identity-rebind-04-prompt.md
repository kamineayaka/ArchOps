# 新对话：未绑定 / 身份失联票 04（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

Matt 位置：grilling / to-spec / to-tickets **已完成**。竖切 01–13 与改策展 01–06 已闭合。未绑定票 **01–03 TDD-done**，审计后补的票 **08 TDD-done**（绑定写入门禁）。本对话只 `/implement` **未绑定刀 frontier = 04**。不要做 05–07，不要做票 09，不要给 `change-curated-draft` 加 07，不要把 01–03 / 08 当 TDD redo。

本票带着 01–03 合同审计（[`.scratch/unbound-identity-rebind/audit-01-03-opus.md`](../.scratch/unbound-identity-rebind/audit-01-03-opus.md)）留下的四条义务，复制区 §0 与 §2 已写死：**C-3**（命中后问法必须翻转）、**S-4**（改掉那条把「命中仍失联」当正确的旧断言）、**S-2**（契约文档补绑定记忆）、以及票 08 之后 **故事 37 + 50 变为必做**（绑定记忆按策展对象唯一，误绑之后没有别的回退路径）。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现未绑定 / 身份失联 frontier 工单 04。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。本票是新能力票，不是 TDD redo，也不是 06 那种 suite/tracer。01–03 与 08 已闭合，禁止重做它们的行为（推断失联 / 未绑定 upsert / 问法读模型 / 发起草案 / 逐条接受拒绝 / 绑定门禁）。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围、路径、表设计、错误码、消费键或作废时机。下面已钉死。不要用 Playwright、SSH fake、computerUse 或薄 UI 当作完成定义。不要默认开工 05–07 或票 09。

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 05–07 / 09 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/unbound-identity-rebind/issues/04-label-match-consume.md

Spec：docs/specs/unbound-identity-rebind.md
审计：.scratch/unbound-identity-rebind/audit-01-03-opus.md（本票的 C-3 / S-4 / S-2 义务与「故事 37+50 变必做」的理由都在里面；只读，不要改它）

一句话交付：现场补标之后，下一次带正确 archops.object_id 的心跳命中策展 Docker 容器 → 写观测「运行于」、**清除身份失联**、消费对应未绑定候选与绑定记忆、作废仍指向该候选或该目标的未完成未绑定草案；此后位置偏差走既有冲突升级链。runtimeId 变化算新候选，禁止按显示名接回原对象。待补标绑定之后出现 absentObjectIds → 走观测消失，解除指向该对象的绑定记忆，仍缺标的现场实体回到待并入。

本票对应 Spec User Stories 29、37、40–43、50，以及 tracer 步骤 8（步骤 9 的「命中后再漂移出冲突」可作同套件姊妹方法，但不要顺手实现 05 的诊断/选支闸门）。Stories 14–16、45–49 是 05。有序总 tracer 是 06，薄 UI 是 07。审计 C-1（失联叠加心跳超时的问法）是票 09，本票不碰。

本票交付（用户可感知、HTTP 可断言）：
- 标签命中 X（快照容器 labels.archops.object_id = X 的不可变 object id）→ 观测「运行于」= 上报主机（既有行为）；且 GET /api/observed/identity-lost/{X} 变成 400 IDENTITY_LOST_NOT_FOUND；GET /api/observed/asks/actual-where?containerId=X 变成 identityLost=false、observedValue.availability=PRESENT、hostId=上报主机。
- 命中即消费：指向 X 的绑定记忆被删除；这些记忆对应的 (sourceHostId, runtimeId) 与本次命中容器自己的 (hostId, runtimeId) 的未绑定候选行一并消费，不再出现在默认待并入列表。
- 命中即作废：仍 OPEN 且指向上述候选、或其 BIND 目标 / 已接受 CREATE 主语是 X 的未绑定草案 → status=VOIDED；再接受条目 → 400 DRAFT_VOIDED；草案 events 可读 DRAFT_VOIDED（detail.hint 含「草案已作废」）。
- 仅刷新同一 (sourceHostId, runtimeId) 的未打标心跳 → **不**作废未绑定草案（03 Cycle E 已有；本票保持）。
- 命中后观测宿主 ≠ 策展「运行于」→ 既有比对开出/升级冲突（GET /api/conflicts/by-merge-key 200、OPEN、两侧值可读、observedLineage 有演变）；命中之前未打标同名仍 400 CONFLICT_NOT_FOUND。
- 新建两条都已接受、随后观测与策展位置相等 → 不人造冲突（by-merge-key 仍 400 CONFLICT_NOT_FOUND）、不进待确认关闭。
- runtimeId 变化（删重建且仍缺标）→ 新的未绑定候选出现在待并入；不得按名称接到 X；原实体已从该宿主快照消失时其绑定记忆随之释放（见 §2「消费与释放」）。
- 待补标绑定之后 absentObjectIds 含 X → 观测消失（availability=ABSENT、hostId 为 JSON null）、GET identity-lost/{X} 400、指向 X 的绑定记忆解除；仍缺标的现场实体回到默认待并入列表。
- 心跳契约文档补上绑定记忆与命中收尾（审计 S-2）。

本票不做（Out of ticket；发现自己在做就停，回到本票清单）：
- 失联时禁止选支 / 诊断分叉过滤 / 作废操作计划与改理想草案 / 待确认关闭退回开放 / 冲突 GET 失联旗标（全部是 05）
- 失联叠加心跳超时时问法改答观测空洞（票 09；本票不要动 observedAskValue 的 mark 优先级）
- HTTP 总 tracer 套件（06）、薄 UI（07）
- 重做 01 的推断/upsert、02 的发起、03 的逐条确认、08 的绑定门禁；不要删除 08 的 labelMatchedAfterIdentityLoss（见 §2）
- 把未绑定草案挂到冲突上；发明未绑定处理人；整单全接受
- SSH / 操作计划 / 策展对齐步骤 / Y2 / LLM 起草 / 网络可达 / K8s 与数据库对象
- 新冲突状态枚举；把 IDENTITY_LOST 写进 observed_fact.availability；把身份失联写成观测空洞或观测消失
- 改已有 V*.sql（含 V18 / V19）；改 CONTEXT.md / ADR-0039 / 已有 ADR 正文
- Maven、JPA 当地基、Vue、Neo4j v1 必选、LangChain、Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内/合同术语写作，不发明同义新词）
================================================================================

按序阅读，读完再写第一个测试：

1. AGENTS.md（一次一张；HTTP 主接缝；/implement 驱动 /tdd）
2. docs/agents/tdd.md（capability 票：必须 witnessed red；禁止为装红灯删除 01–03 / 08 / 竖切 / 改策展生产）
3. .scratch/unbound-identity-rebind/issues/04-label-match-consume.md（含 Comments 里审计给本票的四条约束）
4. .scratch/unbound-identity-rebind/audit-01-03-opus.md — 只取 C-3、S-4、S-2、ST-1 与「对开工 04 的含义」一节
5. .scratch/unbound-identity-rebind/issues/08-bind-write-gate-repair.md（已 done；绑定门禁判据与目标唯一性；保持绿）
6. .scratch/unbound-identity-rebind/issues/03-itemized-create-and-bind.md（已 done；条目语义与错误码）
7. .scratch/unbound-identity-rebind/issues/05-identity-lost-gates-conflict-pipeline.md（只为划清边界：闸门是下一刀内容，不是本票）
8. docs/specs/unbound-identity-rebind.md — 只取：Testing seams、Ingest matching、未绑定草案、规范问法与冲突投影、stories 29 / 37 / 40–43 / 50、tracer 步骤 8–9、Negative 9 / 10 / 12。不要实现 Negative 8（那是 05）
9. CONTEXT.md — 只用：未绑定观测候选、身份失联、观测空洞、观测消失、心跳、探测、冲突、冲突升级、待确认关闭、规范问法、策展真相、观测真相、草案、逐条确认、对象 ID、Docker 容器。Avoid 栏禁止的词不要用（尤其「未绑定处理人」「待确认策展」「以现场为准」「已确认待补标」）。绑定记忆是**匹配状态**，不是新合同词，也不是第四种冲突
10. docs/adr/0039-domain-contract-frozen.md
11. docs/adr/0043-tech-stack.md
12. docs/adr/0011-object-identity-rules.md 与 docs/adr/0012-container-label-bootstrap-and-identity-loss.md（先策展后补标 L2 的正向收尾在本票闭合；标签仍能命中时删重建换宿主仍是同一对象，位置变化走冲突升级）
13. docs/adr/0006-curated-writes-via-itemized-proposals.md（作废草案不等于写策展；作废后不得再接受条目）
14. docs/contracts/agent-heartbeat-snapshot.md（本票要补绑定记忆与命中收尾；这是契约文档，不是 CONTEXT）
15. docs/dev-handoff.md（确认 frontier = 未绑定 04）
16. 现行样板（读，不重写）：
    - backend/src/main/java/com/archops/observed/service/ObservedTruthService.java（processSnapshot 的顺序：容器循环 → absentObjectIds → identityLostObjectIds → inferIdentityLost；upsertObservedPresent 已调 reconcileAfterObservedWrite；observedAskValue 的 mark 优先级）
    - backend/src/main/java/com/archops/curated/service/CuratedDraftService.java（createFromUnboundCandidate / acceptUnboundItem / beginUnboundItemReview / voidOpenForConflict / rememberBind / alreadyBound / labelMatchedAfterIdentityLoss）
    - backend/src/main/java/com/archops/curated/domain/CuratedDraftEventType.java（今日只有 DRAFT_CREATED / DRAFT_ITEM_ACCEPTED / DRAFT_ITEM_REJECTED）
    - backend/src/main/java/com/archops/curated/domain/CuratedDraftStatus.java（OPEN / VOIDED 已有）
    - backend/src/main/java/com/archops/conflict/service/ConflictDetectionService.java（reconcileMergeKey：相等且无活跃冲突 → 直接 return，不建案；不等且无活跃 → createOpen；不等且已 OPEN → upgradeOpen）
    - backend/src/main/resources/db/migration/V17__unbound_candidate_draft.sql（curated_draft_event.event_type 是裸 TEXT，无 CHECK）、V18__unbound_bind_memory.sql、V19__unbound_bind_memory_object_unique.sql（最新）
    - backend/src/test/java/com/archops/observed/UnboundIdentityLostIngestHttpAcceptanceTest.java（01）
    - backend/src/test/java/com/archops/observed/UnboundDraftCreateHttpAcceptanceTest.java（02）
    - backend/src/test/java/com/archops/observed/UnboundDraftItemReviewHttpAcceptanceTest.java（03；其中 bindingToLabelMatchedPresentTargetIsRejected 末尾那条断言由本票按 S-4 修改）
    - backend/src/test/java/com/archops/observed/UnboundBindGateHttpAcceptanceTest.java（08）
    - backend/src/test/java/com/archops/curated/ChangeCuratedDraftVoidHttpAcceptanceTest.java（改策展作废的形状；未绑定作废写 curated_draft_event，不写 conflict_case_event）
    - backend/src/test/java/com/archops/conflict/ConflictWarnUpgradeHttpAcceptanceTest.java（升级链断言形状）
    - backend/src/test/java/com/archops/support/HttpAcceptanceTest.java
    - .cursor/rules/backend-java.mdc

接缝已确认：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle/MockMvc 与 bootRun+curl 是同一条接缝。

用合同术语写作。未绑定 ≠ 冲突 ≠ 身份失联 ≠ 观测空洞 ≠ 观测消失。命中不是「空洞恢复」，是探测重新认回对象。

================================================================================
2. 思想与质量条（完成标准：后续每一步都能对照这一节说「满足」）
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 两侧可用且不等。本刀 motto：匹配失败不升冲突；并入必须逐条；绑定不写可靠实际；**补标命中才恢复升级链**——本票交付的正是最后半句。

身份（ADR-0011 / 0012）：Docker 容器以策展容器 ID 为主键，现场靠不可变标签匹配；运行时 ID 与名称只是线索。标签一命中，线索就重新有效，身份失联在**事实层面当场结束**——所以本票必须在同一次 ingest 里清标，而不是留给读模型去掩盖。删重建、改名、换宿主，只要标签命中就仍是同一对象，位置变化走冲突升级。缺标期间的任何「像是同一个」都不得接龙。

审计 C-3（本票第一圈就要还的债）：今天命中之后 identity_lost_mark 仍在，于是冲突已按合并键开出「策展 A / 实际 B」，而规范问法同时拒绝说出实际（answer 仍 IDENTITY_LOST）。两条读路径对同一对象是否「已认回」给出相反答案。本票第一圈必须把这个矛盾消掉，且必须先看到红灯。

审计 S-4（同一圈必须顺手修的旧断言）：backend/src/test/java/com/archops/observed/UnboundDraftItemReviewHttpAcceptanceTest.java 的 bindingToLabelMatchedPresentTargetIsRejected 末尾断言「命中之后 GET /api/observed/identity-lost/{X} 仍 200」。清标之后它必红。把它改成 400 IDENTITY_LOST_NOT_FOUND；该用例真正要证的是 BIND 被拒，错误码仍是 UNBOUND_BIND_TARGET_HEALTHY（因为门禁在 lost == null 时也抛同一码），所以主断言不动。不要为了让它继续绿而放弃清标，也不要把整条用例删掉。

审计 ST-1 与故事 37+50（票 08 之后变必做）：绑定记忆现在按 curated_object_id 唯一（V19）。因此「误绑之后怎么回退」只有两条路：标签命中收尾，或 absentObjectIds。两者都在本票。若你确实需要区分「绑定来的记忆」与「新建来的记忆」，用**新增** V20 加 origin 列，不要改 V18 / V19；但先确认真的需要——本票的消费与释放都按 curated_object_id / (host, runtime) 键操作，不依赖来源。

不要动的东西（这些是别的票的正确行为）：
- 08 的 labelMatchedAfterIdentityLoss：清标之后它退化为「有没有失联标」，仍是防御纵深（万一将来有路径写出 PRESENT 而没清标）。保留，不要简化掉，也不要给它加新语义。
- 01 的 observedAskValue 里 mark 优先于 staleness：那是审计 C-1 / 票 09 的题目。本票只让 mark 在命中时消失，不要改优先级。
- 01 Cycle M 的「同一快照上 absentObjectIds 压过 identityLostObjectIds」。
- 03 / 08 的错误码字面量与门禁语义。

栈（ADR-0043）：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway 只增不改历史、PostgreSQL SSOT。Redis 不承担这些行。规则驱动，不用 LLM。不引入 Maven、JPA 当地基、Vue、Neo4j、LangChain。

分层：Controller → Service → Mapper；DTO 用 record；DO 不当响应；构造器注入；业务错误 BusinessException；写操作事务在 service。Agent ingest 仍无 X-ArchOps-User-Id。ingest 的清标 / 消费 / 作废与该次写观测必须在同一事务里（同一请求内既写观测又消费）。

持久化（钉死，不要问用户）：
- 本票很可能**不需要新 Flyway**：curated_draft.status 的 CHECK 已含 VOIDED（V13），curated_draft_event.event_type 是裸 TEXT（V17，无 CHECK），所以加 CuratedDraftEventType.DRAFT_VOIDED 不需要迁移。真需要 schema 变更才写 V20+，且只增不改。
- 清标 = 删除 identity_lost_mark 该行（该表就是「当前是否失联」的状态表，不做历史表；审计事件另说，本票不新增事件表）。
- 消费绑定记忆 = 删除 unbound_bind_memory 中 curated_object_id = X 的行。
- 消费候选 = 删除 unbound_observation_candidate 中这些键的行：(a) 上一条删掉的记忆各自的 (source_host_id, runtime_id)；(b) 本次命中容器自己的 (hostId, runtimeId)（错标改对之后原候选就是它）。删除是允许的：01 的 upsert 会在该实体再次以未打标身份出现时重新插入，这正是故事 37 想要的「新候选」。
- 作废未绑定草案 = 把 status 置 VOIDED（复用 CuratedDraftStatus.VOIDED），并写 curated_draft_event 的 DRAFT_VOIDED。范围：origin=UNBOUND_CANDIDATE 且 status=OPEN，且满足其一——candidate_id 指向被消费的候选行、或 (source_host_id, runtime_id) 命中被消费的键、或该草案里 BIND_UNBOUND_TO_EXISTING 的 subject_id = X、或该草案里 CREATE_CONTAINER_FROM_UNBOUND 已接受且 subject_id = X。不要作废 origin=CHANGE_CURATED 的草案（那是 05 的失联闸门要做的事）。不要写 conflict_case_event（未绑定草案没有冲突）。
- 作废后审条：beginUnboundItemReview 对 VOIDED 的未绑定草案必须抛 DRAFT_VOIDED（今天它一律抛 DRAFT_NOT_FOUND），与改策展 requireReviewableDraft 的说法一致：不要假装从来没有过草案。
- absentObjectIds 含 X：观测写 ABSENT（既有）；同时删 X 的 identity_lost_mark 与 curated_object_id = X 的绑定记忆；**不要**删该现场实体的候选行（它仍缺标、仍在现场，必须回到待并入）。观测消失赢，不要顺手把它写成失联或空洞。
- 记忆随实体消失而释放（故事 37 的回退路径）：带快照的心跳是该宿主的完整现场清单。若来自宿主 H 的快照没有报告 runtime r，而存在绑定记忆 (H, r) → X，则该记忆已过期：删除它，并删除 (H, r) 的候选行。这不是观测消失（没人断言 X 不存在），所以**不要**因此清 X 的失联标。心跳-only（无 snapshot）不得释放任何东西。
- 不要删 observed_fact 行；不要把 IDENTITY_LOST 写进 observed_fact.availability；不要新增 ConflictStatus。
- 命中后的比对由既有 upsertObservedPresent → reconcileAfterObservedWrite 完成。不要再补一次 reconcile，不要在本票另写比对引擎。注意清标 / 消费必须发生在该次 reconcile 之前或同一事务内，别让 05 将来读到半截状态。

HTTP 形状（钉死，本票不加新路由）：
- Agent ingest：POST /api/agent/heartbeat（无用户头）
- 操作员读：GET /api/observed/identity-lost/{curatedObjectId}、GET /api/observed/asks/actual-where?containerId=、GET /api/observed/unbound-candidates、GET /api/curated-drafts/{draftId}、GET /api/curated-drafts/{draftId}/events、GET /api/conflicts/by-merge-key?subjectId=、GET /api/curated/asks/should-where?containerId=、GET /api/curated/facts/runs-on/{containerId}
- 操作员写（既有）：POST /api/observed/unbound-candidates/{candidateId}/drafts、POST /api/curated-drafts/{draftId}/items/{itemId}/accept|reject、POST /api/curated/hosts|containers|facts/runs-on
- 错误码字面量（400 业务，信封 success=false、data=null；401 为 AUTH_REQUIRED）：
  IDENTITY_LOST_NOT_FOUND               （命中清标之后 GET identity-lost；已有）
  CONFLICT_NOT_FOUND                    （未承诺升级链 / 相等不建案；已有）
  DRAFT_VOIDED                          （命中作废后再审条；改策展侧已有该码，本票让未绑定路径也用它）
  DRAFT_ITEM_NOT_PENDING                （已有）
  UNBOUND_BIND_TARGET_HEALTHY           （已有；清标后由 lost == null 触发）
  UNBOUND_BIND_TARGET_ALREADY_BOUND     （票 08；目标已被别的现场实体绑定）
  UNBOUND_CANDIDATE_CONSUMED            （票 03/08；该现场实体已被并入）
  UNBOUND_DRAFT_ALREADY_OPEN            （已有）
  UNBOUND_DRAFT_FIXTURE_UNAVAILABLE     （已有；夹具无可用失联目标）
- 禁止本票调用：POST /api/conflicts/{id}/branch-selection、任何操作计划审批 / 执行、任何 SSH、POST /api/curated-drafts/{draftId}/accept（整单全接受不存在）。

规则（钉死）：
1. 命中的判定仍只看标签：labels.archops.object_id 命中策展容器的 immutable_object_id。名称、runtimeId 永远不参与判定。
2. 清标 / 消费 / 作废只针对**本次命中的对象 X 与被消费的键**。不要顺手清同宿主其它对象的失联标（它们没被认回）。
3. 同一快照里 X 同时出现在容器命中与 absentObjectIds：观测消失赢（沿用 01 Cycle M 的方向）；此时也清标、也释放记忆，但候选行按 absent 分支处理（不删）。
4. 未打标快照仍走 01 的 upsert 与推断；本票不得让「刷新观察时间」触发作废或释放（释放只在该 runtime 从快照里消失时发生，见上）。
5. 命中后位置与策展相等且此前无活跃冲突 → 什么都不建（既有 reconcileMergeKey 行为）。相等且有 OPEN 冲突 → 既有 markPendingClose，本票不改。
6. 命中后位置与策展不等 → 既有 createOpen / upgradeOpen。断言用 by-merge-key 的 status、curatedValue、observedValue、observedLineage，不要自己算升级链。
7. 作废是草案级；被作废草案里的条目状态不改（PENDING 仍 PENDING），只是不能再接受——这与改策展 05 的说法一致。
8. 不发明合同词。绑定记忆、消费、作废都用现有词描述；报告与注释里不要出现「未绑定处理人」「以现场为准」「已确认待补标」。

测试质量（/tdd）：
- 只测公开 HTTP：状态码、ApiResponse 信封、后续 GET 可读状态。
- 期望值来自独立真相：字面量 PRESENT、ABSENT、IDENTITY_LOST、OPEN、VOIDED、PENDING、DRAFT_VOIDED、IDENTITY_LOST_NOT_FOUND、CONFLICT_NOT_FOUND、UNBOUND_BIND_TARGET_HEALTHY、UNBOUND_CANDIDATE_CONSUMED、「应该在哪」、「实际在哪」、「运行于」。禁止用实现再算一遍期望。
- 不测 Mapper SQL、Redis key、私有方法、调用图；不打开数据库当第二接缝。
- 一圈一条行为。禁止先铺完全部测试再实现，也禁止先写完实现再补测。
- 新测试放到新类 com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest。不要把 04 塞进 01 / 02 / 03 / 08 的测试类（S-4 那一行断言的修改是唯一允许改动别票测试的地方）。
- @HttpAcceptanceTest 的库 AFTER_CLASS 才清：每个方法的 host 名、container objectId、runtimeId、agentId 必须唯一。
- 测试名描述能力，不描述 Service 方法名。
- 第一圈必须是本票增量的诚实红灯：命中之后 GET /api/observed/identity-lost/{X} 期望 400，今天返回 200。禁止用「未认证 401」「命中已写 PRESENT」「absent 已是 ABSENT」这类既有绿灯冒充第一圈。

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；只跑这一方法；失败原因是「缺本圈行为」。把完整命令与失败输出追加到票 ## Comments（模板见下）。
2. Green：只写让这一条测试通过的最少生产代码。
3. Refactor：不改行为，整理命名与结构；再跑同一条测试，仍绿。然后提交这一圈（why）。

Witnessed red 是硬门。已经绿的新能力测试不能事后称作 TDD 完成；首跑绿只能作为 reuse/regression 记录，且必须点名已经覆盖它的测试方法全名。/code-review 是票结束第二道门，替代不了每圈 refactor。

本票是新能力票，下列必须保持绿（S-4 那一行按上文修改后重新绿）：
- UnboundIdentityLostIngestHttpAcceptanceTest（01）、UnboundDraftCreateHttpAcceptanceTest（02）、UnboundDraftItemReviewHttpAcceptanceTest（03）、UnboundBindGateHttpAcceptanceTest（08）
- ObservedHeartbeatHttpAcceptanceTest、CuratedTruthHttpAcceptanceTest、VerticalSliceHttpE2eAcceptanceTest（含 negative_unlabeledSnapshotDoesNotPromiseUpgradeChain）
- ChangeCuratedDraft*HttpAcceptanceTest、Conflict*HttpAcceptanceTest、HeartbeatTimeoutHollowHttpAcceptanceTest、OperationPlanReviewHttpAcceptanceTest、ControlledSshExecHttpAcceptanceTest、TempAuthHttpAcceptanceTest
- 不要为了红灯删掉 01 的推断、02 的发起、03 的逐条写入、08 的门禁与 V19 唯一索引。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 全量测试红了 → 先修到绿，再开下一圈。
- 想「顺便」做 05 的选支闸门 / 诊断分叉 / 计划作废 / 冲突失联旗标 → 停，做本票清单里的下一圈。
- 想「顺便」修审计 C-1（超时时问法答空洞）→ 停，那是票 09。
- 想让命中走「空洞恢复」的既有 resume 路径 → 停。命中不是通道恢复；观测行一直在，改变的是能否认回对象。
- 想在清标之外再加一个「已认回」状态或事件枚举 → 停。合同里没有第四种态。
- 想按名称把新 runtimeId 接回 X → 停。那正是 ADR-0012 禁止的同名接龙。
- 想在 absent 分支删候选行 → 停。故事 50 要求仍缺标的实体回到待并入。
- 想把 05 的「PENDING_CLOSE + 失联 → OPEN」写进来 → 停，本票只做命中方向。
- 想删掉 08 的 labelMatchedAfterIdentityLoss 简化代码 → 停，那是防御纵深，且会让 08 的负面用例失去判别力。
- 想问用户消费键怎么选 / 何时作废 → 用 §2「持久化」里钉死的规则。

Git：从已含票 08 与本票文件的最新 origin/main 开分支。Cloud 分支名须匹配 cursor/<slug>-<本 run 指定后缀>。建议 slug：tdd-implement-unbound-04。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres，不依赖 Redis。单测：
cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew cleanTest test

认证：Header X-ArchOps-User-Id。一般 = user-general-demo，高级 = user-senior-demo（TempAuthHeaders.USER_ID）。Agent POST /api/agent/heartbeat 不带头。

Comments 模板（每一圈追加）：
### Cycle <字母> — <行为一句>
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest.<method>
（失败输出贴原文，或 reuse/regression：点名已覆盖它的测试方法全名）
Green command: （同上，exit 0）
Regression: （本圈点名重跑的既有测试类/方法）
Refactor: （一句；无则写「无结构改动」）
Commit: <hash> <message>

================================================================================
3. 现状（完成标准：你能指出「04 增量相对今天缺哪几条 HTTP 行为」）
================================================================================

今日 ingest（01 + 竖切）：
- 标签命中 → 写观测「运行于」PRESENT 并调 reconcileAfterObservedWrite（升级链已经会恢复）。
- 但 identity_lost_mark **不会**被清：GET identity-lost/{X} 仍 200，问法仍答 IDENTITY_LOST。这就是审计 C-3 的矛盾：冲突说「实际在 B」，问法说「认不回」。
- 未绑定候选行不会被消费；绑定记忆不会被释放；未绑定草案不会被作废。
- absentObjectIds → 观测 ABSENT（可用值），但若该对象此前已有失联标，标不会被清、记忆不会被释放。
- 心跳-only 不推断、不写观测。

今日未绑定草案（02 + 03 + 08）：
- POST /api/observed/unbound-candidates/{id}/drafts → OPEN；UNKNOWN 夹具 = CREATE + CURATED_RUNS_ON_INSERT（同宿主有失联对象时前置 BIND）；MISSING_LABEL 夹具 = BIND + CREATE。
- 逐条接受/拒绝已实现；BIND 只写绑定记忆，不写观测「运行于」。
- 门禁：目标必须仍失联且失联之后未再标签命中（08 的 labelMatchedAfterIdentityLoss）；目标不得已被别的现场实体绑定（08 的 UNBOUND_BIND_TARGET_ALREADY_BOUND + V19 唯一索引）；夹具只提供尚未被绑的失联目标。
- beginUnboundItemReview 对 VOIDED 的未绑定草案今天抛 DRAFT_NOT_FOUND（本票改成 DRAFT_VOIDED），且今天没有任何路径会把未绑定草案置为 VOIDED。
- CuratedDraftEventType 今天没有 DRAFT_VOIDED。

今日冲突引擎（竖切 04 / 09 / 10）：
- reconcileMergeKey：相等且无活跃 → 不建案；相等且 OPEN → markPendingClose；不等且无活跃 → createOpen；不等且 OPEN → upgradeOpen（保留 observedLineage）；心跳超时另有 onObservationBecameHollow → SUSPENDED。
- 本票不改这些分支，只让命中之前/之后的输入变正确。

Flyway：最新 V19。本票预计无需迁移；若需要则 V20+，禁止改历史脚本。

本票增量是：命中清标 + 消费候选与绑定记忆 + 作废相关未绑定草案（含 DRAFT_VOIDED 事件与再审条拒绝）+ runtimeId 变化的新候选与过期记忆释放 + absent 释放记忆并回到待并入 + 契约文档。

================================================================================
4. 步骤（按序；每步有完成标准。未完成不准跳到下一步）
================================================================================

建议测试类：com.archops.observed.UnboundLabelMatchConsumeHttpAcceptanceTest
风格：MockMvc、统一信封、建底 POST hosts/containers/facts/runs-on、Agent heartbeat（无用户头）、操作员 TempAuthHeaders。

每方法唯一前缀（AFTER_CLASS 清库；禁止跨方法复用 id）：
- A  命中清失联 + 问法翻转：host u04a-h、容器 objectId u04a-oid、未打标 runtime u04a-rt-miss、命中 runtime u04a-rt-hit、agent u04a-ag
- B  命中消费绑定记忆（用「再失联后可再绑」证明）：u04b-*
- C  命中作废未绑定草案 + 再审条 DRAFT_VOIDED + 事件：u04c-*
- D  仅刷新不作废：u04d-*（reuse/regression 候选，点名 03 Cycle E）
- E  命中后位置不等 → 升级链恢复：u04e-*（含策展宿主 A 与观测宿主 B）
- F  两条都接受后相等 → 不人造冲突、不进待确认关闭：u04f-*（reuse/regression 候选，点名 reconcileMergeKey 相等且无活跃 → 不建案）
- G  runtimeId 变化 → 新候选、不按名接回、过期记忆释放：u04g-*
- H  absent 释放记忆并回到待并入：u04h-*
- I  契约文档圈（无测试）
- J  票级回归、S-4 断言、/code-review、文档指针

### 步骤 A — 第 1 圈：标签命中清除身份失联，问法当场翻转（本票第一条诚实红灯）

夹具：策展主机 A；策展容器 X（immutable objectId=u04a-oid）运行于 A；先发未打标快照（runtime u04a-rt-miss）让 X 失联并产生候选。

Red：再发一条命中快照（runtime u04a-rt-hit，labels.archops.object_id=u04a-oid，宿主仍 A），然后
GET /api/observed/identity-lost/{X}
期望 400 success=false code=IDENTITY_LOST_NOT_FOUND data=null。今天返回 200 → 这就是红灯。

同方法随后（同一行为的两面，允许写在一条方法里）：
- GET /api/observed/asks/actual-where?containerId={X} → identityLost=false、observedValue.availability=PRESENT、observedValue.hostId=A、仍同屏 curatedValue.hostId=A（P2）
- GET /api/curated/asks/should-where?containerId={X} → 仍 A（策展不因命中改变）

Green：在 ingest 的命中分支清 identity_lost_mark。不要在本圈做消费 / 作废（后面几圈各自红灯）。
Refactor，提交。

同圈必做（审计 S-4）：跑 UnboundDraftItemReviewHttpAcceptanceTest，把 bindingToLabelMatchedPresentTargetIsRejected 末尾「identity-lost 仍 200」改为 400 IDENTITY_LOST_NOT_FOUND，主断言 UNBOUND_BIND_TARGET_HEALTHY 不动，重新绿。Comments 里写明这是审计 S-4 的处置。

完成标准：Comments 有本圈 red；本圈绿；01 的「先命中后失联」用例（currentlyUsableObservedHostSnapshotInfersIdentityLost、identityLostActualWhereDoesNotReportStaleObservedHost）仍绿；08 全类仍绿。

### 步骤 B — 命中消费绑定记忆

夹具 u04b-*：X 运行于 A；未打标 runtime u04b-rt-1 → 失联 + 候选；开草案接受 BIND（X 上出现绑定记忆）。然后发命中快照。

Red 断言（可观察的消费证据，不要去查表）：
1. 命中后再发一次未打标快照（runtime u04b-rt-2，同宿主）→ X 再次失联、u04b-rt-2 出现在待并入
2. 对 u04b-rt-2 开草案 → BIND 目标是 X（夹具只给未被绑的目标；若旧记忆没被消费，08 的夹具会跳过 X，这里就拿不到 BIND 条）
3. 接受该 BIND → 200（若旧记忆仍在，会是 400 UNBOUND_BIND_TARGET_ALREADY_BOUND）

Green：命中时删除 curated_object_id = X 的绑定记忆，并删除这些记忆键与本次命中键的候选行。
Refactor，提交。

完成标准：误绑之后「补标 → 重新失联 → 重绑」这条回退路径在 HTTP 上走通；票 08 的唯一性没被削弱。

### 步骤 C — 命中作废未绑定草案；再审条 DRAFT_VOIDED；事件可读

夹具 u04c-*：X 运行于 A；未打标 runtime u04c-rt-miss → 失联 + 候选；开草案（BIND + CREATE，全部 PENDING，先不接受）。然后发命中快照。

Red 断言：
- GET /api/curated-drafts/{draftId} → status=VOIDED（今天仍 OPEN）
- POST /api/curated-drafts/{draftId}/items/{bindItemId}/accept → 400 code=DRAFT_VOIDED data=null
- GET /api/curated-drafts/{draftId}/events → eventType 含 DRAFT_VOIDED，detail.hint 含「草案已作废」
- 条目状态仍 PENDING（作废不改条目）
- 策展未被写：GET /api/curated/facts/runs-on/{X} 仍指向 A；X 的 objectId 仍 u04c-oid

Green：命中分支作废符合 §2 范围的 OPEN 未绑定草案；加 CuratedDraftEventType.DRAFT_VOIDED（无需迁移）；beginUnboundItemReview 对 VOIDED 抛 DRAFT_VOIDED。不要动 origin=CHANGE_CURATED 的草案。
Refactor，提交。

### 步骤 D — 仅刷新同一 runtimeId 不作废未绑定草案

夹具 u04d-*：失联 + 候选 + OPEN 草案；再发同一 (host, runtime) 的未打标快照（可改 name 证明 upsert 仍发生）。

期望：草案仍 OPEN；条目仍 PENDING；待并入仍有该 runtime（未绑定过）；X 仍失联。
首跑绿则记 reuse/regression，点名 03 Cycle E 的 unlabeledReheartbeatAfterBindStaysConsumedAndIdentityLost 与 01 的 upsert 用例。不要为它写生产。

提交。

### 步骤 E — 命中后观测宿主 ≠ 策展「运行于」→ 升级链恢复

夹具 u04e-*：策展主机 A 与 B 都建；X 运行于 A；先在 A 上未打标快照 → X 失联（此时 GET by-merge-key 仍 400 CONFLICT_NOT_FOUND，未打标不承诺升级链）；然后在 **B** 上发命中快照。

期望：
- GET /api/conflicts/by-merge-key?subjectId={X} → 200，status=OPEN，curatedValue.hostId=A，observedValue.hostId=B
- GET identity-lost/{X} → 400；actual-where → PRESENT / B
- 若随后再在第三台策展宿主 C 上命中 → 同一冲突升级（不新开第二条），observedLineage 含 B→C

比对由既有引擎完成，本圈大概率首跑绿 → 记 reuse/regression 并点名 ConflictWarnUpgradeHttpAcceptanceTest 的对应方法与步骤 A 的清标。若红灯是因为清标顺序把 reconcile 挤到了错误的状态，那是真缺口，按最小生产修。

提交。

### 步骤 F — 新建两条都接受、随后观测与策展相等 → 不人造冲突、不进待确认关闭

夹具 u04f-*：UNKNOWN 候选（label u04f-never）→ 接受 CREATE 与 CURATED_RUNS_ON_INSERT（策展「运行于」= 候选所在宿主 A）→ 再发带该 label 的命中快照（宿主 A）。

期望：
- GET /api/conflicts/by-merge-key?subjectId={新对象} → 400 CONFLICT_NOT_FOUND（相等且无活跃冲突不建案，也不进 PENDING_CLOSE）
- GET actual-where → PRESENT / A；GET should-where → A
- 待并入不再有该 runtime

首跑绿 → reuse/regression，点名 reconcileMergeKey「相等且 active == null 直接 return」与 03 的 acceptingRunsOnAfterCreateWritesFirstCuratedRunsOn。不要为了造红灯去改比对引擎。

提交。

### 步骤 G — runtimeId 变化 → 新候选；不按名接回；过期记忆释放

夹具 u04g-*：X 运行于 A；未打标 runtime u04g-rt-1（name 与 X 同名）→ 失联 + 候选；接受 BIND（记忆 (A, u04g-rt-1) → X）。随后该容器被删重建：同宿主快照只报告 runtime u04g-rt-2（仍缺标、仍同名），不再报告 u04g-rt-1。

期望：
- 待并入出现 u04g-rt-2（新候选）
- GET by-merge-key?subjectId={X} → 仍 400 CONFLICT_NOT_FOUND（同名弱线索不得点亮升级链）
- GET identity-lost/{X} → 仍 200（没人认回 X，也没人断言它不存在）
- 对 u04g-rt-2 开草案 → BIND 目标可为 X，且接受 200（说明 (A, u04g-rt-1) 的过期记忆已释放）
- 待并入不再有 u04g-rt-1

Green：按 §2「记忆随实体消失而释放」实现——只在带快照的心跳里、只针对该宿主未报告的 runtime；不清失联标；心跳-only 不释放。
Refactor，提交。

完成标准：故事 37 与票 08 的唯一性共存；名称仍然只是线索。

### 步骤 H — 待补标绑定之后 absentObjectIds 含 X → 观测消失、释放记忆、实体回到待并入

夹具 u04h-*：X 运行于 A；未打标 runtime u04h-rt-miss → 失联 + 候选；接受 BIND。随后同宿主快照：containers 仍报告 u04h-rt-miss（仍缺标），absentObjectIds 含 X 的 objectId。

期望：
- GET actual-where?containerId={X} → observedValue.availability=ABSENT、hostId 为 JSON null、identityLost=false、curatedValue.hostId=A
- GET identity-lost/{X} → 400 IDENTITY_LOST_NOT_FOUND
- 待并入重新出现 u04h-rt-miss（记忆已释放、候选行仍在）
- 该 runtime 可再次开草案（原草案若仍 OPEN 则按 §2 判断是否在作废范围；本圈夹具可直接不开草案以避免混淆）

Green：absent 分支清标 + 释放该对象的记忆；不删候选行。
Refactor，提交。

完成标准：观测消失赢；「X 不存在」没有被存成「谜底是 X」。

### 步骤 I — 心跳契约文档跟上命中收尾与绑定记忆（审计 S-2）

docs/contracts/agent-heartbeat-snapshot.md 增补：
- 绑定记忆 = 逐条确认后的匹配状态，键 (sourceHostId, runtimeId) → 策展对象，按策展对象唯一；不产生观测「运行于」，不承诺升级链
- 标签命中收尾：写观测「运行于」→ 清身份失联 → 消费该对象的绑定记忆与相应未绑定候选 → 作废相关 OPEN 未绑定草案 → 走既有比对
- absentObjectIds 含该对象：观测消失赢，清失联、释放记忆、仍缺标的现场实体回到待并入
- 该宿主快照不再报告某 runtime 时，其绑定记忆与候选行过期释放；心跳-only 不释放、不推断
不改 CONTEXT.md，不新开 ADR。

提交。

### 步骤 J — 票级回归与收尾

cd backend && ./gradlew cleanTest test
失败则修到全绿（仍不扩范围）。

对照工单清单逐条用 HTTP 证据勾选（七条验收项）。
/code-review：Standards + Spec 两轴分开，互不 rerank。固定点用本分支相对 origin/main 的 merge-base。Spec 源：本票 + Spec 的 stories 29 / 37 / 40–43 / 50 + tracer 步骤 8。审查发现的行为错误要修并回归；气味按 judgement 处理，不借审查塞进 05–07。审计 C-3 / S-4 / S-2 的处置要在票内可追溯。

不要添加：05 的闸门与冲突失联旗标、票 09 的问法超时优先级、06 的总 tracer、07 的 UI。若审查看到这些，删掉。

更新文档指针（04 完成后按编号最小 = 05；票 09 由人决定何时插队，本票不要替人改顺序）：
- 本票：Status: done；验收项全勾；Comments 含每圈 red（或合法 reuse）与审计三项处置
- docs/dev-handoff.md（当前状态表 + 下一对话）
- AGENTS.md §0 第 8 条 / §6 / §7
- CLAUDE.md 工单行
- docs/agents/issue-tracker.md 表
- .cursor/rules/project-map.mdc、domain-contract.mdc
- docs/specs/unbound-identity-rebind.md 的 Status 行与 Further Notes 的 frontier

完成标准：全量测试绿；票 done；handoff 指向 05（并提到票 09 待人排期）；工作区没有 05/06/07/09 的产品代码。

================================================================================
5. HTTP 契约（本票断言用；完成标准：测试只断言这些可观察值）
================================================================================

Agent ingest（无用户头）：
POST /api/agent/heartbeat
Content-Type: application/json

未打标（造失联 + 候选）：
{"agentId":"u04a-ag","hostId":"<A>","snapshot":{"containers":[{"runtimeId":"u04a-rt-miss","name":"u04a-similar","labels":{}}],"absentObjectIds":[]}}

标签命中（补标之后）：
{"agentId":"u04a-ag","hostId":"<A>","snapshot":{"containers":[{"runtimeId":"u04a-rt-hit","name":"u04a-x","labels":{"archops.object_id":"u04a-oid"}}]}}

命中在别的策展宿主（造升级链）：
{"agentId":"u04e-agb","hostId":"<B>","snapshot":{"containers":[{"runtimeId":"u04e-rt-hit","name":"u04e-x","labels":{"archops.object_id":"u04e-oid"}}]}}

观测消失（绑定之后）：
{"agentId":"u04h-ag","hostId":"<A>","snapshot":{"containers":[{"runtimeId":"u04h-rt-miss","name":"u04h-similar","labels":{}}],"absentObjectIds":["u04h-oid"]}}

删重建换 runtimeId（仍缺标、同名）：
{"agentId":"u04g-ag","hostId":"<A>","snapshot":{"containers":[{"runtimeId":"u04g-rt-2","name":"u04g-x","labels":{}}],"absentObjectIds":[]}}

心跳-only（不得释放/推断/写观测）：
{"agentId":"u04d-ag","hostId":"<A>"}

操作员（Header X-ArchOps-User-Id）：
GET  /api/observed/identity-lost/{curatedObjectId}
GET  /api/observed/asks/actual-where?containerId=
GET  /api/observed/unbound-candidates
POST /api/observed/unbound-candidates/{candidateId}/drafts      body {}
GET  /api/curated-drafts/{draftId}
GET  /api/curated-drafts/{draftId}/events
POST /api/curated-drafts/{draftId}/items/{itemId}/accept|reject  body {}
GET  /api/curated/asks/should-where?containerId=
GET  /api/curated/facts/runs-on/{containerId}
POST /api/curated/hosts | /api/curated/containers | /api/curated/facts/runs-on
GET  /api/conflicts/by-merge-key?subjectId=

信封：成功 200 success=true；业务拒绝 400 success=false、code 字面量、data=null。未认证 401 AUTH_REQUIRED。
关系文案用「运行于」/ RUNS_ON、「应该在哪」、「实际在哪」。
GET 未绑定按 runtimeId 过滤后断言「在 / 不在列表」；禁止对 $.data 做 hasSize 当全库计数。candidateId / draftId / itemId 用响应里的 id，不要用 runtimeId 当路径参数。

================================================================================
6. 停工检查（全部为真才许把票标 done）
================================================================================

- [ ] 每圈 Comments 里有独立的 red 命令与失败输出（合法 reuse 写明来源测试名）
- [ ] 第一圈是「命中后 GET identity-lost 期望 400」的诚实红灯，不是未认证、不是既有 PRESENT / ABSENT 绿灯
- [ ] 没有「先实现后补测」或「测试已绿再宣称 TDD」
- [ ] 没有删除 01 推断/upsert、02 发起、03 逐条写入、08 门禁与 V19 的生产来装红灯
- [ ] 命中 → identity-lost 400；问法 identityLost=false / PRESENT / 上报宿主；「应该在哪」不变
- [ ] 命中 → 指向该对象的绑定记忆与相应候选被消费，且「补标 → 重新失联 → 重绑」在 HTTP 上走通
- [ ] 命中 → 相关 OPEN 未绑定草案 VOIDED；再审条 DRAFT_VOIDED；事件含 DRAFT_VOIDED 与「草案已作废」；条目状态未被改写
- [ ] 仅刷新同一 runtimeId 不作废草案、不释放记忆
- [ ] 命中后位置不等 → by-merge-key 200 / OPEN / 两侧值正确 / 再漂移走升级不新开第二条；命中之前同名仍 CONFLICT_NOT_FOUND
- [ ] 新建两条都接受后相等 → 仍 CONFLICT_NOT_FOUND，未进待确认关闭
- [ ] runtimeId 变化 → 新候选可绑；旧记忆释放；没有按名称接回；X 的失联标未被误清
- [ ] absentObjectIds 含该对象 → ABSENT + identity-lost 400 + 记忆释放 + 仍缺标实体回到待并入；候选行未被删
- [ ] 心跳-only 不清标、不释放、不作废、不写观测
- [ ] 审计 S-4 的旧断言已改（bindingToLabelMatchedPresentTargetIsRejected 末尾 400），主断言 UNBOUND_BIND_TARGET_HEALTHY 未动
- [ ] 审计 S-2 的契约文档已补（绑定记忆 + 命中收尾 + absent 释放 + 过期释放）
- [ ] 未新增冲突状态枚举；未把 IDENTITY_LOST 写进 observed_fact.availability；未改 observedAskValue 的 mark 优先级（票 09）
- [ ] 未改任何已有 V*.sql；若确有 schema 变更则只在 V20+
- [ ] 未改 CONTEXT.md / 已有 ADR
- [ ] 未实现 05 的闸门 / 诊断分叉 / 计划作废 / 冲突失联旗标；未实现 06 tracer；未接线 07 UI；未做票 09
- [ ] ./gradlew cleanTest test 全绿（含 01、02、03、08、竖切负面、ChangeCuratedDraft*、HeartbeatTimeoutHollow）
- [ ] /code-review 两轴已跑；行为问题已修；气味按 judgement 未扩范围
- [ ] 文档指针指向 05，并写明票 09 待人排期
```

---

完成后下一对话：按编号最小 = **未绑定票 05**（失联闸门修实际 / 改理想路径）。票路径 `.scratch/unbound-identity-rebind/issues/05-identity-lost-gates-conflict-pipeline.md`。审计 C-1 对应的 **票 09**（`.scratch/unbound-identity-rebind/issues/09-ask-hollow-when-channel-timed-out.md`）与 04 / 05 相互独立，由人决定何时插队。04 完成后不要默认做 06–07。
