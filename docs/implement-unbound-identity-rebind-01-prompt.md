# 新对话：未绑定 / 身份失联票 01（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

Matt 位置：grilling / to-spec / to-tickets **已完成**。竖切 01–13 与改策展 01–06 已闭合。本对话只 `/implement` **未绑定刀 frontier = 01**。不要做 02–07，不要给 `change-curated-draft` 加 07。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现未绑定 / 身份失联 frontier 工单 01。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。本票是新能力票，不是 TDD redo，也不是 06 那种 suite/tracer。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围或字段命名。下面已钉死。不要用 Playwright、SSH fake、computerUse 或薄 UI 当作完成定义。不要默认开工 02–07。

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 02–07 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/unbound-identity-rebind/issues/01-infer-identity-lost-unbound-upsert.md

Spec：docs/specs/unbound-identity-rebind.md
一句话交付：观测侧把未绑定认得出、按现场实体去重列得出；控制面按主机范围推断身份失联；规范问法在失联时不得把旧宿主当实际。人不并入、不绑定、不选支。

本票对应 Spec User Stories 1–4、6–13、17，以及 Testing Decisions tracer 步骤 1–3 与 Negative 1–2、9 的观测侧。Stories 5、14–16、18–60 以及 tracer 4 以后是 02–07。

本票交付（用户可感知、HTTP 可断言）：
- 缺标快照 → 未绑定 MISSING_LABEL，upgradeChainPromised=false。
- 标签对不上任何策展 Docker 容器 → 未绑定 UNKNOWN_OBJECT_ID，upgradeChainPromised=false。
- 同一 sourceHostId + runtimeId 再心跳 → upsert（刷新 observedAt、name、labels、reason），GET 列表对该实体仍一条。夹具必须带 runtimeId。
- GET /api/observed/unbound-candidates 含 labels（JSON 对象，至少现场 archops.object_id）、runtimeId、name、reason、sourceHostId；upgradeChainPromised 恒 false。
- 上报主机是该容器的策展「运行于」宿主，或当前可用观测宿主（从未写过观测则只看策展宿主），且本快照未标签命中、未进入 absentObjectIds → 控制面打身份失联。不必等 identityLostObjectIds。
- Agent identityLostObjectIds 在同一主机范围内仍有效；超出该范围的声明不得给 X 打失联。
- 既非策展运行于宿主、也非当前可用观测宿主的快照，不得给 X 打失联。
- absentObjectIds 仍写入观测消失（availability=ABSENT，可用值=不存在），不是身份失联，不是观测空洞。
- 「应该在哪」仍答策展。失联时「实际在哪」同屏策展，不得把失联前宿主当实际。钉死读模型：identityLost=true；observedValue.availability="IDENTITY_LOST"；observedValue.hostId=null。IDENTITY_LOST 只允许出现在这条规范问法读模型上，不得写入 observed_fact.availability，不得新增 ConflictStatus。
- 未打标同名：GET /api/conflicts/by-merge-key 仍 400、code=CONFLICT_NOT_FOUND（竖切票 13 负面不回归）。
- 更新 docs/contracts/agent-heartbeat-snapshot.md：推断失联、upsert、主机范围、问法投影。不改 CONTEXT.md，不新开 ADR。

本票不做（Out of ticket；发现自己在做就停，回到本票清单）：
- 未绑定草案 / 逐条绑定或新建（02–03）；默认列表只显示「待并入」（绑定记忆是 03）
- 标签命中后清失联、消费候选、恢复升级链（04）。本票命中后仍可不清失联；不要写「命中后 GET identity-lost 变 404」当验收
- 失联时禁止选支 / 改诊断分叉 / 作废计划与改理想草案 / 待确认关闭退回开放 / 冲突 GET 失联旗标（05）
- HTTP 总 tracer 套件（06）、薄 UI（07）
- SSH、操作计划、Y2、LLM、网络可达、K8s/数据库对象、物理主机线索重绑
- 把弱线索写成可靠观测「运行于」；把失联写成 SUSPENDED / 观测空洞 / 观测消失
- 给 change-curated-draft 加 07；重做竖切 01–13；重做改策展 01–06
- 改已有 V*.sql；改 CONTEXT.md / ADR-0039 / 已有 ADR 正文
- Maven、JPA 当地基、Vue、Neo4j v1 必选、LangChain、Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内/合同术语写作，不发明同义新词）
================================================================================

按序阅读，读完再写第一个测试：

1. AGENTS.md（一次一张；HTTP 主接缝；/implement 驱动 /tdd）
2. docs/agents/tdd.md（capability 票：必须 witnessed red；禁止为装红灯删除竖切/改策展生产）
3. .scratch/unbound-identity-rebind/issues/01-infer-identity-lost-unbound-upsert.md
4. docs/specs/unbound-identity-rebind.md — 只取：Testing seams (confirmed)、Ingest matching、规范问法 and 冲突 projection、Testing Decisions 中 tracer 1–3 与 Negative 1–2、9。不要实现 tracer 全序（那是 06）
5. CONTEXT.md — 只用：观测真相、未绑定观测候选、身份失联、观测空洞、观测消失、心跳、规范问法、策展真相、运行于、冲突、冲突升级。Avoid 栏禁止的词不要用（尤其「以现场为准」「待确认策展」、把空洞当冲突、把失联当已消失）
6. docs/adr/0039-domain-contract-frozen.md
7. docs/adr/0043-tech-stack.md
8. docs/adr/0011-object-identity-rules.md
9. docs/adr/0012-container-label-bootstrap-and-identity-loss.md
10. docs/adr/0002-dual-track-relationship-truth.md 与 docs/adr/0009-dual-track-ideal-vs-actual-deviation.md（实际只对应观测，且须同屏策展）
11. docs/dev-handoff.md（确认 frontier = 未绑定 01）
12. docs/contracts/agent-heartbeat-snapshot.md（本票要修订的契约，不是 CONTEXT）
13. 现行样板（读，不重写竖切/改策展故事）：
    - backend/src/test/java/com/archops/observed/ObservedHeartbeatHttpAcceptanceTest.java
    - backend/src/test/java/com/archops/slice/VerticalSliceHttpE2eAcceptanceTest.java 的 negative_unlabeledSnapshotDoesNotPromiseUpgradeChain
    - backend/src/main/java/com/archops/observed/service/ObservedTruthService.java（processSnapshot、persistUnbound、upsertIdentityLost、actualWhere）
    - backend/src/main/java/com/archops/observed/controller/ObservedController.java
    - backend/src/main/java/com/archops/observed/dto/UnboundCandidateResponse.java（今日无 labels）
    - backend/src/main/java/com/archops/observed/dto/ActualWhereResponse.java（availability 只有 PRESENT/ABSENT/HOLLOW；无 identityLost）
    - backend/src/main/java/com/archops/observed/domain/ObservedAvailability.java（仅 PRESENT|ABSENT）
    - backend/src/main/resources/db/migration/V4__observed_heartbeat_and_facts.sql（unbound 无 (host,runtimeId) 唯一约束；observed_fact.availability CHECK 只有 PRESENT/ABSENT）
    - backend/src/main/java/com/archops/agent/dto/AgentHeartbeatRequest.java
    - backend/src/test/java/com/archops/support/HttpAcceptanceTest.java
    - backend/src/main/java/com/archops/common/api/ApiResponse.java
    - backend/src/main/java/com/archops/common/exception/GlobalExceptionHandler.java
    - .cursor/rules/backend-java.mdc

接缝已确认：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle/MockMvc 与 bootRun+curl 是同一条接缝。

用合同术语写作。未绑定 ≠ 冲突 ≠ 观测空洞 ≠ 观测消失 ≠ 身份失联。不要发明「未绑定处理人」「已确认待补标」等 CONTEXT 没有的词。

================================================================================
2. 思想与质量条（完成标准：后续每一步都能对照这一节说「满足」）
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 两侧可用且不等。匹配失败产生未绑定观测候选，不自动并入、不承诺升级链。线索失效产生身份失联。观测写入不人审，也不覆盖策展。

身份与先策展后补标（ADR-0011 / 0012）：Docker 容器以策展容器 ID 为主键，现场靠不可变标签 archops.object_id 匹配。运行时 ID、名称只是线索。未打标不承诺升级链。禁止按同名把新容器当成原对象。

规范问法：应该在哪只答策展；实际在哪只答当前可用观测；即使只问实际也须同屏策展。失联时通道可以仍新鲜（心跳未超时），所以不是空洞；也没有明确断言不存在，所以不是观测消失。因此不得把失联前的 PRESENT 宿主继续当成实际。从未观测且尚未失联 → 仍是 HOLLOW（竖切 actualWhereWithoutObservationIsHollowWithCuratedOnScreen 必须保持绿）。从未观测但已被推断失联 → 是身份失联，不是 HOLLOW。

栈（ADR-0043）：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway 只增不改历史、PostgreSQL SSOT。Redis 不作关系真相。不引入 Maven、JPA 当地基、Vue、Neo4j、LangChain。

分层：Controller → Service → Mapper；DTO 用 record；DO 不当响应；构造器注入；业务错误 BusinessException；写操作事务在 service。Agent ingest 仍无 X-ArchOps-User-Id；GET 未绑定 / 身份失联 / 实际在哪 / 应该在哪须已认证。

推断规则（钉死，实现时对照）：
1. 只在 snapshot 非 null 时推断。心跳-only（省略 snapshot）只刷新 freshness。
2. 先做本快照的标签匹配与 absentObjectIds，再推断。
3. 候选对象 = 策展 Docker 容器且已有策展「运行于」。
4. 主机范围（快照处理开始时读取，不要用本快照稍后才写成的 PRESENT 回推范围）：上报 hostId 等于策展「运行于」目标，或等于当前可用观测「运行于」目标（observed_fact.availability=PRESENT 且未因心跳超时而空洞）。从未写过观测 → 只认策展宿主。
5. 范围内且本快照未标签命中且未进入 absentObjectIds → upsert identity_lost_mark（reason=LABEL_CLUE_LOST，upgradeChainPromised=false）。
6. 范围内的 identityLostObjectIds 仍走同一 upsert。
7. 范围外的快照，即使带 identityLostObjectIds=[X]，也不得给 X 打失联。
8. absentObjectIds 命中 X 时写 ABSENT，本快照不把 X 标失联。
9. 标签命中 X 时本票不清已有失联标（04）。

测试质量（/tdd）：
- 只测公开 HTTP：状态码、ApiResponse 信封、后续 GET 可读状态。
- 期望值来自独立真相：字面量 MISSING_LABEL、UNKNOWN_OBJECT_ID、LABEL_CLUE_LOST、IDENTITY_LOST、CONFLICT_NOT_FOUND、false、true、「实际在哪」「应该在哪」、CURATED、OBSERVED。禁止用实现再算一遍期望。
- 不测 Mapper SQL、Redis key、私有方法、调用图；不打开数据库当第二接缝。
- 一圈一条行为。禁止先铺完全部测试再实现，也禁止先写完实现再补测。
- 不 mock 本模块协作对象。用现有 @HttpAcceptanceTest（Zonky embedded Postgres）。该类 AFTER_CLASS 才清库：每个方法的 host 名、container objectId、runtimeId、agentId 必须唯一。
- GET /api/observed/unbound-candidates 返回全类共享库里的全部候选。禁止对 $.data 做 hasSize(1) / hasSize(2)。按 runtimeId（或 sourceHostId+runtimeId）过滤后再断言。心跳响应 $.data.unbound 才是「本请求」条数，可以 hasSize。
- 新测试放到新类 com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest。不要把 01 的行为塞进 ObservedHeartbeatHttpAcceptanceTest 的既有多断言方法里。竖切 unlabeled 测试保持独立回归，禁止合并删除。
- 测试名描述能力，不描述 Service 方法名。

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；只跑这一方法；失败原因是「缺本圈行为」（编译失败或断言失败都算）。把完整命令与失败输出追加到票 ## Comments（见下方模板）。
2. Green：只写让这一条测试通过的最少生产代码。
3. Refactor：不改行为，整理命名与结构；再跑同一条测试，仍绿。然后提交这一圈（why）。

Witnessed red 是硬门。已经绿的新能力测试不能事后称作 TDD 完成。/code-review 是票结束第二道门，替代不了每圈 refactor。

本票是新能力票：
- unlabeledAndIdentityLostDoNotPromiseUpgradeChain、negative_unlabeledSnapshotDoesNotPromiseUpgradeChain、absentObjectIdIsUsableAbsentNotHollow、heartbeatWithoutAuthPersistsFreshnessAndMatchedRunsOn、actualWhereWithoutObservationIsHollowWithCuratedOnScreen、bootstrap POST 拒绝覆盖，必须保持绿。
- 不要为了 01 的红灯去掉标签匹配、去掉 Agent identityLostObjectIds 写入、或关掉未绑定 insert。
- 若某条新测试因竖切已有行为而首跑绿：只允许作为「声明仍有效」的回归，Comments 写 reuse/regression 并点名来源方法，且必须另有一条真正红的新断言覆盖本票增量。不要用全绿的「Agent 仍能声明失联」当第一圈。
- 负面「不得打失联」在推断落地前也会 404：那种测试必须写在正例推断变绿之后。若首跑绿，记 reuse（证明范围已包含在正例的 green 里）。不要删正例生产来装红灯。
- 「超范围 identityLostObjectIds 不得打失联」对今日生产是红的（现行循环不看主机范围）。这一圈必须独立 witnessed red，不要在推断那一圈顺手改 identityLostObjectIds 循环把红灯提前吃掉。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 全量测试红了 → 先修到绿，再开下一圈。
- 想「顺便」做草案/绑定/选支闸门/清失联 → 做本票清单里的下一圈。
- 想把失联写成 HOLLOW/ABSENT/SUSPENDED 或删掉旧 PRESENT 行来让问法好看 → 改 actualWhere 读模型，保留 observed_fact。
- 想改 V4 加 UNIQUE → 新行为用 V16+。建议部分唯一索引：UNIQUE (source_host_id, runtime_id) WHERE runtime_id IS NOT NULL。本票夹具一律带 runtimeId。
- 想靠 observedAt 毫秒差证明 upsert → 第二次心跳改 name（及 labels/reason），断言名称/原因已换成第二次的字面量，且按 runtimeId 过滤后仍一条。
- 想在 C 圈夹具里带 identityLostObjectIds → 拿掉。那会走竖切旧路径，推断正例会假绿。
- 想用 Thread.sleep 测空洞 → 本票不测超时。
- 想问用户 IDENTITY_LOST 怎么拼 → 用本节钉死的读模型。

Git：从已含本票文件的最新 origin/main 开分支 cursor/tdd-implement-unbound-01-dcbc（Cloud 分支名须匹配 cursor/<slug>-dcbc）。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres，不依赖 Redis。单测：
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew test

认证：Header X-ArchOps-User-Id = user-general-demo（TempAuthHeaders.USER_ID）。Agent POST /api/agent/heartbeat 不带头。不要新造用户体系。

Comments 模板（每一圈追加）：
### Cycle <字母> — <行为一句>
Red command:
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest.<method>
（失败输出贴原文，或 reuse/regression：点名已覆盖它的测试方法全名）
Green command: （同上，exit 0）
Refactor: （一句；无则写「无结构改动」）
Commit: <hash> <message>

================================================================================
3. 现状（完成标准：你能指出「01 增量相对竖切缺哪几条 HTTP 行为」）
================================================================================

现行 ingest（ObservedTruthService.processSnapshot）：
- 无标签 → persistUnbound(MISSING_LABEL)，每次 insert 新 id。
- 未知 object_id → persistUnbound(UNKNOWN_OBJECT_ID)，每次 insert。
- 命中标签 → upsertObservedPresent，比对。
- absentObjectIds → ABSENT。
- identityLostObjectIds → upsertIdentityLost；控制面不会在「本快照未命中」时自动打失联；声明也不看主机范围。
- 命中后不清 identity_lost_mark（04；本票保持「命中仍可不清」）。

现行 HTTP：
- GET /api/observed/unbound-candidates：无 labels 字段。
- GET /api/observed/identity-lost/{id}：有 mark 才 200（reason=LABEL_CLUE_LOST），否则 400 IDENTITY_LOST_NOT_FOUND。
- GET /api/observed/asks/actual-where：仅 PRESENT / ABSENT / HOLLOW；若库里仍有旧 PRESENT，失联后仍会报宿主；从未观测无 mark 时为 HOLLOW。
- GET /api/conflicts/by-merge-key：无冲突 → 400 CONFLICT_NOT_FOUND。未打标路径须保持。

Flyway：最新 V15。V4 unbound_observation_candidate 无 (source_host_id, runtime_id) 唯一约束。observed_fact.availability CHECK 只有 PRESENT/ABSENT。本票 upsert 用 V16+，禁止改 V4，禁止把 IDENTITY_LOST 加进该 CHECK。

竖切夹具要保持：Agent 显式 identityLostObjectIds 在范围内仍能打失联；未打标不承诺升级链；观测消失仍是 ABSENT；无快照的从未观测仍是 HOLLOW。本票增量是 labels + upsert + 推断 + 主机范围约束声明 + 失联后规范问法。

================================================================================
4. 步骤（按序；每步有完成标准。未完成不准跳到下一步）
================================================================================

建议测试类：com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest
风格：MockMvc、统一信封、建底 POST /api/curated/hosts|containers 与 facts/runs-on、Agent POST /api/agent/heartbeat（无用户头）、操作员 GET 用 TempAuthHeaders.USER_ID = user-general-demo。

每方法唯一前缀（AFTER_CLASS 清库；禁止跨方法复用 id）：
- A  labels：host u01a-h、object u01a-oid、runtime u01a-rt-unknown、agent u01a-ag
- B  upsert：host u01b-h、runtime u01b-rt、agent u01b-ag
- C  infer：hosts u01c-h（策展运行于）、object u01c-oid、container 名可近似、runtime u01c-rt-miss、agent u01c-ag；identityLostObjectIds 省略或 []
- D  他机：X 运行于 u01d-ha；快照来自 u01d-hc；runtime u01d-rt
- E  观测宿主：策展运行于 u01e-ha；先在 u01e-hb 标签命中；再从 hb 发未打标
- F  问法-无观测失联：可接 C 的独立夹具 u01f1-*
- G  问法-旧 PRESENT：接 E 形状 u01g-*
- H  超范围声明：u01h-ha / u01h-hc，identityLostObjectIds=[u01h-oid]
- I  absent：u01i-*，absentObjectIds=[u01i-oid]
- J  未打标升级链：u01j-*（可 reuse 竖切方法，不必新夹具）
- K  心跳-only：u01k-*（写在 C 绿之后）

### 步骤 A — 第 1 圈：GET 未绑定带 labels

Red：UNKNOWN_OBJECT_ID 快照后 GET /api/observed/unbound-candidates，过滤 $.data[?(@.runtimeId=='u01a-rt-unknown')]：
- reason=UNKNOWN_OBJECT_ID
- name、sourceHostId 为本次字面量
- labels['archops.object_id'] 或 labels.archops.object_id = 本次未知标签字面量
- upgradeChainPromised=false
今日 DTO 无 labels → 断言失败或编译失败。

Green：只给 GET DTO / 映射补 labels（JSON 对象，不是 labels_json 原文字符串）。不要在这一圈做 upsert 或推断失联。
Refactor，提交。

完成标准：Comments 有本圈 red；该测试绿；unlabeledAndIdentityLostDoNotPromiseUpgradeChain 仍绿。

### 步骤 B — 第 2 圈：同一现场实体 upsert

Red：同一 host + 同一 runtimeId 连续两次心跳。第一次 MISSING_LABEL、name=u01b-first；第二次改 name=u01b-second，并可带未知标签使 reason 变为 UNKNOWN_OBJECT_ID。GET 过滤该 runtimeId 长度为 1；name 与 reason 为第二次字面量。今日两次 insert → 过滤后长度为 2。

Green：按 (sourceHostId, runtimeId) upsert。需要唯一约束则 Flyway V16（部分唯一索引），不改 V4。夹具必须带 runtimeId。
Refactor，提交。

完成标准：两次心跳后该 runtimeId 一条；竖切 unlabeled 测试仍绿（它一次快照两个不同 runtimeId，仍是两条）。心跳响应 $.data.unbound 第二次仍可 hasSize(1)。

### 步骤 C — 第 3 圈：控制面推断身份失联（策展宿主，无 Agent 声明）

夹具：策展容器 X 运行于 A；不发 identityLostObjectIds；Agent 在 A 的快照只有未打标容器（runtimeId 唯一），containers 不含 X 的标签，absentObjectIds 省略或 []。本方法不要再创建第二个运行于 A 的容器。

Red：GET /api/observed/identity-lost/{X 的策展 id} → 200，upgradeChainPromised=false，reason=LABEL_CLUE_LOST。今日无声明则 400 IDENTITY_LOST_NOT_FOUND。

Green：只给推断路径打标。不要改 identityLostObjectIds 循环的主机范围（留给 H）。不要改 actualWhere（留给 F/G）。心跳-only 不在本圈测。
Refactor，提交。

完成标准：无 identityLostObjectIds 也能打失联；不要在本圈改选支或冲突状态。

### 步骤 D — 第 4 圈：他机快照不得给 X 打失联

必须在 C 绿之后写。夹具：X 策展运行于 A，无观测。Agent 在 C（另一台已策展主机）上报未打标快照，无 absent、无 identityLostObjectIds。

断言：GET identity-lost/X 仍 400 IDENTITY_LOST_NOT_FOUND。C 上的未打标实体仍可成为未绑定（过滤 C 的 runtimeId 能看到候选）。

若 C 的 green 已按主机范围实现：本方法可能首跑绿 → Comments 写 reuse/regression，点名与 C 同一规则，不另写生产。若仍红（C 做成了「全图未命中即失联」）：只把推断收窄到主机范围，禁止用同名匹配补救。
提交。

### 步骤 E — 第 5 圈：当前可用观测宿主也可推断

夹具：X 策展运行于 A；先在 B 用正确 archops.object_id 标签命中，写入观测 PRESENT B；再从 B 发未打标、未 absent、未声明的快照。

Red：GET identity-lost/X → 200。若只认策展宿主，B 不会打标 → 本圈应红然后变绿。

命中那一次仍应写 PRESENT（后续 G 要用旧实际）。本圈不改 actualWhere。
提交。

### 步骤 F — 第 6 圈：从未观测、仅推断失联时「实际在哪」不是空洞

独立夹具（不要依赖 C 方法的库行）：与 C 相同形状，然后 GET /api/observed/asks/actual-where?containerId=X
- data.question = 「实际在哪」
- data.track = OBSERVED
- data.identityLost = true
- data.curatedValue.hostId = 策展宿主 A
- data.observedValue.hostId = null
- data.observedValue.availability = IDENTITY_LOST（不是 PRESENT / HOLLOW / ABSENT）

同方法再 GET /api/curated/asks/should-where?containerId=X：question=「应该在哪」，track=CURATED，仍为宿主 A。

今日从未观测 → HOLLOW，且无 identityLost 字段 → 诚实红灯。

Green：只改规范问法读模型与 actualWhere 映射。禁止：把 observed_fact.availability 改成新枚举、把冲突 SUSPENDED、删观测行、把从未观测无 mark 的 HOLLOW 改掉。actualWhereWithoutObservationIsHollowWithCuratedOnScreen 必须仍绿。
Refactor，提交。

### 步骤 G — 第 7 圈：失联后「实际在哪」不得报旧宿主

接 E 形状：先 PRESENT B，再从 B 未打标推断失联。然后 actual-where 与 F 相同断言（identityLost=true，availability=IDENTITY_LOST，hostId=null，策展仍 A）。应该在哪仍为 A。

Green：库内旧 PRESENT 行可以留；问法层不得把它当实际。不要为了本圈去清 identity_lost_mark。
提交。

### 步骤 H — 第 8 圈：超范围 identityLostObjectIds 不得给 X 打失联

必须独立 witnessed red。夹具：X 策展运行于 A，无观测。Agent 在 C 的快照 containers 可空或未打标，identityLostObjectIds=[X 的 immutable object id]。C 不是策展宿主也不是观测宿主。

Red：今日会 200 打标。本圈期望 GET identity-lost/X 仍 400 IDENTITY_LOST_NOT_FOUND。

Green：identityLostObjectIds 循环复用与推断相同的主机范围。范围内的声明仍有效——不要改 unlabeledAndIdentityLostDoNotPromiseUpgradeChain（它从策展/观测宿主上报，必须仍绿）。
提交。

范围内声明若另写聚焦测试且首跑绿：记 reuse，点名 unlabeledAndIdentityLostDoNotPromiseUpgradeChain，不要删竖切测试。

### 步骤 I — 第 9 圈：absentObjectIds 仍是观测消失，不是失联

夹具：X 策展运行于 A。Agent 在 A 的快照 containers=[]，absentObjectIds=[X 的 immutable object id]。

断言：actual-where availability=ABSENT，hostId=null，curated 仍在，identityLost 不是 true；GET identity-lost/X 因本快照而为 400 IDENTITY_LOST_NOT_FOUND。existing absentObjectIdIsUsableAbsentNotHollow 须仍绿。不要发明「策展改为不存在」。

若首跑绿且新断言已满足：reuse。若推断实现把「未出现在 containers」一律标失联、压过 absent：本圈红，修正为 absent 优先。
提交。

### 步骤 J — 第 10 圈：未打标同名不承诺升级链

优先 reuse VerticalSliceHttpE2eAcceptanceTest.negative_unlabeledSnapshotDoesNotPromiseUpgradeChain。若写聚焦测试：缺标快照后 GET /api/conflicts/by-merge-key?subjectId=X → 400、code=CONFLICT_NOT_FOUND、data=null。禁止为了让冲突出现而按 name 匹配。
提交。

### 步骤 K — 心跳-only 不推断（回归）

写在 C 绿之后。夹具：X 运行于 A；POST heartbeat 无 snapshot 字段。GET identity-lost/X 仍 400。本测试在推断落地前也会绿；作为回归留下，Comments 写 reuse（推断只发生在 processSnapshot）。不要为它删推断。
提交。

### 步骤 L — 契约文档

修订 docs/contracts/agent-heartbeat-snapshot.md：
- 控制面在主机范围内推断身份失联；identityLostObjectIds 可选且受同一范围约束
- 未绑定按 sourceHostId+runtimeId upsert；夹具须带 runtimeId
- GET unbound-candidates 含 labels
- 规范问法在身份失联时：identityLost=true，observedValue.availability=IDENTITY_LOST（仅读模型），hostId 不得为旧宿主
- Status 行改为涵盖本刀 01，不要写成「只竖切 03」
- 心跳-only 仍只刷新 freshness

不改 CONTEXT.md。不新开 ADR。Python stub 仍可发送 identityLostObjectIds；控制面在省略时也须推断——改 stub 不是本票 Must。
可与最近一圈生产提交合并或单独提交（why：契约跟上推断与 upsert）。

### 步骤 M — 票级回归与收尾

cd backend && ./gradlew test
失败则修到全绿（仍不扩范围）。

对照工单清单逐条用 HTTP 证据勾选。
/code-review：Standards + Spec。固定点用本分支相对 origin/main 的 merge-base。Spec 源：本票 + Spec 的 Ingest matching 与规范问法 projection。审查发现的行为错误要修并回归；气味按 judgement 处理，不借审查塞进 02–07。

薄 UI：本票不接线。add-frontend-page 是 07。

更新文档指针（01 完成后 frontier = 02；不要实现 02）：
- 本票：Status: done；验收项全勾；Comments 含每圈 red（或合法 reuse）
- docs/dev-handoff.md（下一对话 = 未绑定 02；02 与 05 均 unblocked 时先做 02）
- AGENTS.md 当前工单 / §6
- CLAUDE.md 工单行
- docs/agents/issue-tracker.md 表
- .cursor/rules/project-map.mdc、domain-contract.mdc
- docs/specs/unbound-identity-rebind.md Further Notes / Status 行的 frontier

完成标准：全量测试绿；票 done；handoff 指向 02；工作区无草案/选支/UI 票外文件。

================================================================================
5. HTTP 契约（本票断言用；完成标准：测试只断言这些可观察值）
================================================================================

Agent ingest（无用户头）：
POST /api/agent/heartbeat
Content-Type: application/json

有快照：
{"agentId":"u01c-ag","hostId":"<策展主机 id>","snapshot":{"containers":[{"runtimeId":"u01c-rt-miss","name":"…","labels":{}}],"absentObjectIds":[],"identityLostObjectIds":[]}}

心跳-only：
{"agentId":"u01k-ag","hostId":"<策展主机 id>"}

操作员读（Header X-ArchOps-User-Id: user-general-demo）：
GET /api/observed/unbound-candidates
GET /api/observed/identity-lost/{curatedContainerId}
GET /api/observed/asks/actual-where?containerId=
GET /api/curated/asks/should-where?containerId=
GET /api/conflicts/by-merge-key?subjectId=

建底（本票只用来夹具，不改 01 改策展关闭覆盖的语义）：
POST /api/curated/hosts
POST /api/curated/containers     body {"name":"…","objectId":"…"}
POST /api/curated/facts/runs-on   body {"containerId":"…","hostId":"…"}  （无则插入；已有则仍 CURATED_RUNS_ON_EXISTS）

信封：成功 200 success=true；业务拒绝 400 success=false、code 字面量、data=null。
失联 GET 无 mark：400 IDENTITY_LOST_NOT_FOUND。
关系文案用「运行于」/ RUNS_ON、「实际在哪」、「应该在哪」。
identityLostObjectIds 与 absentObjectIds 填 immutable object id（与建底 objectId 相同），GET identity-lost 的路径参数是策展容器 id（POST containers 返回的 data.id）。

心跳响应 $.data.identityLost 可列出本请求新打的失联，但完成定义是后续 GET，不要把响应数组形状当成第二套合同。

================================================================================
6. 停工检查（全部为真才许把票标 done）
================================================================================

- [ ] 每圈 Comments 里有独立的 red 命令与失败输出（合法 reuse 写明来源测试名）
- [ ] 没有「先实现后补测」或「测试已绿再宣称 TDD」
- [ ] 没有删除竖切未打标 / 观测消失 / 命中运行于 / 无观测 HOLLOW 的生产来装红灯
- [ ] C 圈夹具没有 identityLostObjectIds
- [ ] upsert：同一 host+runtimeId 两条心跳 → 过滤后一条；name/reason 为第二次
- [ ] GET 未绑定含 labels JSON 对象；没有对全表 $.data 做 hasSize
- [ ] 无 identityLostObjectIds 时，策展宿主与可用观测宿主快照可推断失联
- [ ] 他机快照与超范围 identityLostObjectIds 不给 X 打失联
- [ ] 失联后 actual-where 为 identityLost=true 且 availability=IDENTITY_LOST、hostId=null；should-where 不变
- [ ] 无 mark 的从未观测仍是 HOLLOW
- [ ] absent 仍是 ABSENT；未打标仍 CONFLICT_NOT_FOUND；心跳-only 不推断
- [ ] 未改任何已有 V*.sql；新约束只出现在新版本；observed_fact.availability 仍只有 PRESENT/ABSENT
- [ ] 未新增草案/绑定记忆/选支闸门/命中清失联/冲突失联旗标/UI/SSH
- [ ] 未改 CONTEXT.md / 已有 ADR
- [ ] 心跳契约文档已更新
- [ ] ./gradlew test 全绿
- [ ] /code-review 已跑；行为问题已修
- [ ] 文档 frontier 指向 02；02–07 的产品代码未做
```

---

完成后下一对话：**未绑定票 02**（从不挂冲突的未绑定候选发起草案）。票路径 `.scratch/unbound-identity-rebind/issues/02-unbound-draft-from-candidate.md`。01 完成后 02 与 05 均 unblocked，按编号先做 02。
