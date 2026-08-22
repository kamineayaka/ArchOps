# 新对话：未绑定票 01–03 合同审计（Claude Opus 5）

将下面 **「复制区」** 整段作为**新对话**的第一条用户消息。模型选 **Claude Opus 5**（或列表里的 Claude Opus 4.6 / Opus 等价最高档）。不要用 Composer / Auto / 实现向模型跑本对话。

若客户端支持手动附带 skill，同时附上：

- `domain-modeling` — `.cursor/skills/domain-modeling/SKILL.md`（桌面：`.agents/skills/domain-modeling/SKILL.md`）
- `code-review` — `.cursor/skills/code-review/SKILL.md`（或 `.agents/skills/code-review/SKILL.md`）
- `docs/agents/domain.md` — 合同冻结：读 glossary，禁止静默改 `CONTEXT.md` / 已有 ADR

本文件是 **只读合同审计入口**，不是 `/implement`、不是 `/tdd`、不是 `/grill-with-docs`、不是单票结束时那份 400 字 `/code-review`。

Matt 位置：未绑定 Spec / 工单已发布；票 **01–03 TDD-done**。**暂停 04–07 的实现。** 本对话只回答：01–03 落地有没有把冻结合同写歪。审完停；下一刀仍是人决定要不要修、还是开 04。

---

## 复制区

```text
你是 ArchOps 的合同审计官，不是编码 Agent。本对话只做一件事：对未绑定 / 身份失联刀已闭合的票 01–03，做只读、三轴、可证伪的合同审计。质量优先于覆盖率：没有 HTTP 或代码引证的判断不算发现；把 04/05 范围硬说成本票缺口也不算发现；改合同语义来迁就实现更不算完成。

加载并遵守：
- AGENTS.md（执行纪律；一次一张票；本对话例外：不实现任何票）
- docs/agents/domain.md（合同冻结）
- domain-modeling skill：只用 CONTEXT 已有术语；发现缺口就标 NEEDS-ADR，禁止改 CONTEXT.md / 已有 ADR 正文
- code-review skill 的「两轴分开、禁止互相 rerank」；本对话在 Spec 轴之外再加 Contract 轴，三轴并列
- 冲突优先级：ADR 与 CONTEXT > Spec > 票 > 票内 Comments / 本 prompt。票或测试注释与合同冲突时，以合同为准，并把冲突写进报告

不要问我接缝、范围、路径、表设计、要不要开 04。下面已钉死。事实（仓库、测试、Flyway、HTTP 形状）由你查。决策（是否修、是否新 ADR、是否开工 04）由我做；你只给分类后的发现与推荐，等我确认。

================================================================================
0. 任务边界（完成标准：你能用一句话说出本对话交付物，且不把 /implement 04 算进范围）
================================================================================

一句话交付：判断票 01–03 的 HTTP 行为与持久化，是否忠实于冻结合同里的「未绑定观测候选 / 身份失联 / 草案逐条确认 / 绑定不写可靠实际」，有没有把 身份失联 写成 观测空洞 或 观测消失，有没有按弱线索静默续上升级链，以及这些偏差会不会在开工 04 之后被水泥封死。

本对话交付（唯一）：
- 写入 `.scratch/unbound-identity-rebind/audit-01-03-opus.md`（不存在则新建）
- 按本 prompt §9 的报告结构；三轴分开；每条发现带分类标签与证据
- 结束时停下来等我。确认前不要修代码、不要开 04、不要 /to-spec

本对话不做：
- `/implement` `/tdd` 票 04、05、06、07，以及任何业务生产代码、Flyway、UI
- 为「装红灯」删除 01–03 / 竖切 / 改策展生产
- 重开 ADR-0039 / 0043，改 CONTEXT.md，改已有 `V*.sql`，改已有 ADR 正文
- `/grill-with-docs` 定下一刀产品切面（本刀 Spec 已发布；下一刀 grilling 在本刀闭合之后）
- 用单票 400 字 code-review 交差（那份审查已做过；本对话要跨 01–03 组合语义）
- 发明合同词：待确认策展、以现场为准、未绑定处理人、已确认待补标、第四种冲突
- Maven / JPA 当地基 / Vue / Neo4j v1 必选 / LangChain / Redis 当关系真相 SSOT
- Playwright、SSH fake、computerUse 作为完成定义

工单（已闭合，只读对照，不要改 Status 为 ready-for-agent）：
- `.scratch/unbound-identity-rebind/issues/01-infer-identity-lost-unbound-upsert.md`
- `.scratch/unbound-identity-rebind/issues/02-unbound-draft-from-candidate.md`
- `.scratch/unbound-identity-rebind/issues/03-itemized-create-and-bind.md`

边界票（只为划清「谁该拥有」；不要实现）：
- `.scratch/unbound-identity-rebind/issues/04-label-match-consume.md`
- `.scratch/unbound-identity-rebind/issues/05-identity-lost-gates-conflict-pipeline.md`

Canonical spec：`docs/specs/unbound-identity-rebind.md`
本刀 motto（Spec Further Notes）：匹配失败不升冲突；并入必须逐条；绑定不写可靠实际；补标命中才恢复升级链。

================================================================================
1. 先读（完成标准：按序读完；报告只用合同术语，不发明同义新词）
================================================================================

按序阅读，读完再下第一条判断：

1. AGENTS.md
2. docs/agents/domain.md
3. CONTEXT.md — 只用下列词，并读各自 _Avoid_：策展真相、观测真相、探测、未绑定观测候选、对象 ID、Docker 容器、身份失联、观测空洞、观测消失、心跳、冲突、冲突升级、规范问法、草案、逐条确认、操作计划、已接受的冲突处理人、待确认关闭、运行于
4. docs/adr/0039-domain-contract-frozen.md
5. docs/adr/0043-tech-stack.md（只确认栈未漂；本审计不以分层气味当主轴）
6. docs/adr/0011-object-identity-rules.md
7. docs/adr/0012-container-label-bootstrap-and-identity-loss.md
8. docs/adr/0006-curated-writes-via-itemized-proposals.md
9. docs/adr/0015-v1-must-scope.md（未绑定与身份失联是 v1 Must；本刀实现已有术语，不新开 ADR）
10. docs/specs/unbound-identity-rebind.md（全文；尤其 Problem、Solution、User Stories、Implementation Decisions、Testing Decisions、Out of Scope、Further Notes）
11. 票 01、02、03 全文（含 ## Comments 里每圈 red/green 与 code-review 附注）
12. 票 04、05 的 What to build / Acceptance / Out of this ticket（只为归属：某行为是「01–03 已写歪」还是「04/05 尚未做」）
13. docs/contracts/agent-heartbeat-snapshot.md（01 要求跟上推断/upsert/主机范围；03 后绑定记忆若已存在，核对文档是否把记忆写成可靠实际）
14. docs/dev-handoff.md 当前状态表（Flyway 版本、frontier；本对话不把它改回实现 04）

读代码与测试时按需打开，不要凭票注释相信行为。至少覆盖这些接缝（可用 Grep / 读测试类，不必先读完全仓库）：

- `backend/` 下 `UnboundIdentityLostIngestHttpAcceptanceTest`
- `UnboundDraftCreateHttpAcceptanceTest`
- `UnboundDraftItemReviewHttpAcceptanceTest`
- ingest / 规范问法 / 未绑定列表 / 草案条目接受 的 Service（`observed`、`curated`；必要时 `conflict` 只看是否被 01–03 误用）
- Flyway `V16`–`V18`（只读；禁止改历史脚本）
- 竖切负面：`VerticalSliceHttpE2eAcceptanceTest.negative_unlabeledSnapshotDoesNotPromiseUpgradeChain`
- 改策展仍绿对照：`ChangeCuratedDraft*` 不必重跑全部，但若发现未绑定路径挂了 dummy `conflict_id`，必须记

================================================================================
2. 权威栈与术语锁（完成标准：报告里每个对象都用 CONTEXT 词；Avoid 词只出现在「发现了谁用了它」）
================================================================================

权威顺序：CONTEXT + 有效 ADR > Spec > 票验收清单 > 票 Comments / 测试方法名 / 本 prompt。

五态互斥（写进报告前先在心里过一遍；发现代码把其中两态写成一态，就是 Contract 轴发现）：

| 态 | 是什么 | 不是什么 |
|---|---|---|
| 未绑定观测候选 | 现场实体匹配失败；提醒；要并入必须草案 | 冲突；策展对象；升级链主体 |
| 身份失联 | 既有对象认不回；弱线索不得续升级链 | 观测空洞；观测消失；已对齐 |
| 观测空洞 | 无当前可用观测值（从未写或心跳超时） | 匹配失败；明确不存在 |
| 观测消失 | 心跳新鲜且断言不存在；可用值 | 失联；空洞 |
| 冲突 | 两侧可用且不等 | 空洞；未绑定自身 |

绑定记忆（票 03）：接受绑定后记住 `(sourceHostId, runtimeId) → 策展对象 X`，直到标签命中。这是匹配状态，不是 CONTEXT 新词，不是第四种冲突，不是可靠观测 `运行于`。

规范问法：应该 = 策展；实际 = 当前可用观测。失联时实际不得报失联前宿主，availability 不得为 PRESENT，也不得单因失联报 HOLLOW / ABSENT。Spec 建议：`IDENTITY_LOST` 只出现在问法读模型，不写入 `observed_fact.availability`，不做 `ConflictStatus`。

并入：确认单位是条目。草案确认前不是策展真相（ADR-0006）。未绑定草案不挂冲突、不发明未绑定处理人、不复用已接受冲突处理人。

身份（ADR-0011 / 0012）：Docker 容器主键 = 策展容器 ID + 现场不可变标签。Docker 运行时 ID、名称只是线索。先策展后补标期间不承诺升级链。缺标/错标 → 未绑定 + 原对象身份失联；禁止按同名静默合并。

================================================================================
3. 三轴（完成标准：每条发现只落在一轴；禁止用 Standards 气味覆盖 Contract 失败）
================================================================================

## Contract
对照 CONTEXT + ADR-0011/0012/0006/0039。问：实现有没有用代码习惯改语义？
典型失败：失联当空洞；失联当消失；绑定写出观测 PRESENT；按 name/runtimeId 承诺 `by-merge-key` 升级链；未绑定当冲突；问法把旧宿主当实际。

## Spec
对照 `docs/specs/unbound-identity-rebind.md` 里 **01–03 拥有的** User Stories 与 Implementation Decisions。
01 拥有：Stories 1–13、17、54 中与问法/upsert/推断相关的部分；心跳契约文档。
02 拥有：Stories 18–24、58 规则夹具（PENDING；发起不写策展）。
03 拥有：Stories 19–20（审条）、25–28、31–36、44、51–53、59 条目审计；Story 5 的「默认列表只显示待并入」因绑定记忆过滤而在 03 落地。
04 拥有（不要当 01–03 缺口）：Stories 29（命中后位置相等不人造冲突）、37（runtimeId 变化新候选）、40–43、50（absent 释放记忆）。
05 拥有（不要当 01–03 缺口）：Stories 14–16 冲突投影/旗标、45–49 闸门。
06/07 拥有：有序 tracer 套件、薄 UI。

Spec 轴只报：01–03 该有而缺/错；01–03 做了 Spec 没要且会改变合同语义的行为。分层气味、重复构造函数放 Standards。

## Standards
对照 AGENTS.md §4、`.cursor/rules/backend-java.mdc`、ADR-0043，以及 code-review skill 的 Fowler 气味基线（判断，不是硬违规）。
本对话 Standards 轴降级：只收 **会把合同写歪或把 04 焊死** 的气味（例如把失联标写进 `observed_fact.availability` CHECK；Redis 当绑定真相；dummy 冲突行）。纯命名/重复代码若不影响语义，标 judgement 即可，不要扩成重构票。

三轴数字不要合成「总分」。Contract 一条致命发现大于 Standards 十条气味。

================================================================================
4. 发现分类（完成标准：每条发现恰好一个标签；标签决定下一动作，不是严重性形容词）
================================================================================

对每一条发现只打下列之一：

- `FIX-NOW` — 01–03 已经违反 CONTEXT/ADR，或违反本刀 01–03 拥有的 Spec。开工 04 会把错误当前提。需要我另开修复对话（仍一次一张；不是本对话修）。
- `TICKET-OWNS` — 行为不完整，但归属 04 或 05（或 06/07），且 01–03 的现行语义与合同兼容。写出「归哪张票、为何兼容」。
- `FALSE-ALARM` — 看起来像缺口，对照权威栈后其实正确（例如 03 Cycle G 用观测 PRESENT 拒绑健康对象，而清失联标是 04）。写清为什么正确。
- `NEEDS-ADR` — 代码与合同互相打架，且 Spec/票无法在不改语义的前提下裁决。只建议新 ADR 的题目与两种备选语义；不要起草 ADR 文件，不要改 CONTEXT。
- `TEST-GAP` — 合同/Spec 要求的行为在生产里可能对，但 01–03 HTTP 测试没有钉住；04 若只靠现有测试会漏。记录应补的断言与接缝（HTTP），不要在本对话补绿。

严重性（与标签正交，可并行）：`blocker` / `major` / `nit`。
blocker = 合同五态被合并或升级链被弱线索点亮。
major = 01–03 拥有的 Story 错了，但五态仍可区分。
nit = 文档/命名/判断气味。

================================================================================
5. 必查探针（完成标准：每个探针都有结论：通过 / 失败+标签 / 归属某票；禁止跳过）
================================================================================

下面不是灵感列表，是审计清单。每个探针必须在报告里出现。证据优先 HTTP 测试方法 + 生产代码路径；必要时跑指定测试（见 §7）。

P1. 五态：缺标/未知标签 → 未绑定；范围内未命中且非 absent → 身份失联；`absentObjectIds` → 观测消失（可用 ABSENT）；心跳超时路径仍是空洞。同一快照上 `absentObjectIds` 与 `identityLostObjectIds` 并存时，消失赢（01 Cycle M）。失联不得把 `observed_fact.availability` 写成 HOLLOW 或 ABSENT，也不得当 PRESENT。

P2. 主机范围：策展 `运行于` 宿主或当前可用观测宿主可推断失联；他机快照与超范围 `identityLostObjectIds` 不得给 X 打失联。心跳-only（无快照）不推断。

P3. 规范问法：失联时「应该在哪」仍是策展；「实际在哪」不得报旧宿主；`identityLost=true`（或等价）；availability 非 PRESENT / 非单因失联的 HOLLOW/ABSENT。IDENTITY_LOST 若出现，确认只在问法 DTO。

P4. 弱线索：未打标同名不得让 `GET /api/conflicts/by-merge-key` 承诺升级链（竖切 13 负面）。绑定记忆不得把 name/runtimeId 写成观测 `运行于` PRESENT。

P5. 未绑定 upsert：同一 `sourceHostId`+`runtimeId` 一行；GET 默认待并入含 labels / runtimeId / name / reason / sourceHostId；`upgradeChainPromised=false`。

P6. 未绑定草案：不挂 `conflict_id`（可空，禁止 dummy 冲突）；不出现在冲突下改理想草案 API；发起不写策展、不建操作计划；同一现场实体最多一份 OPEN；未认证拒绝；一般与高级均可；无未绑定处理人。

P7. 夹具：UNKNOWN → ≥2 条，新建（不可变标签=现场标签）+ 策展 `运行于` 候选宿主。MISSING_LABEL/错标+失联 → 绑定 vs 新建互斥。核对 03 Cycle I：UNKNOWN 在「该宿主已有失联对象」时是否额外插入 BIND——这是 Spec「UNKNOWN 允许绑到已有」还是 02 夹具合同被 03 改写。必须裁决：FIX-NOW / TICKET-OWNS / FALSE-ALARM，不得含糊。

P8. 逐条写入：只接受新建、拒绝 `运行于` → 有容器无该 `运行于`。先接受 `运行于`、尚未新建 → 失败且策展不变。占用中的 `archops.object_id` → 接受失败。拒绝条不写。无整单全接受 HTTP。建底 POST 第一条 `运行于` 仍成功；覆盖仍 `CURATED_RUNS_ON_EXISTS`。禁止旁路 POST 把候选映射成对象。

P9. 绑定：接受绑到失联 X → 容器ID / 不可变标签不变；实际仍不得是弱线索；该 runtimeId 离开待并入；再心跳仍缺标 → 仍不待并入、仍失联、仍不升级。绑到仍标签命中（观测 `运行于` PRESENT）→ 失败。MISSING_LABEL 新建不是成功路径。UNKNOWN 绑到已有不得把错标签写成 X 主键。双接受绑定+新建 → 第二次失败。

P10. 绑定记忆写在 CREATE 上：03 Cycle D refactor「CREATE 也写 bind memory，方便日后 consume」。Spec 写的是接受**绑定**后记对应关系。裁决：这是「消费候选」的合法匹配状态，还是把新建偷偷写成绑定、并在 04 用错消费键。必须给标签。

P11. 03 Cycle G 已知张力（本对话的核心判断，禁止用「04 会清标」一句话打发）：
    标签已命中、观测 `运行于` 已是 PRESENT，但 `identity_lost_mark` 仍在，问法 DTO 仍 IDENTITY_LOST。
    03 用观测 PRESENT 拒绝再 BIND（健康对象），清标留给 04。
    你必须回答三个分开的问题：
    (a) 问法在「观测已 PRESENT」时仍报 IDENTITY_LOST，是否已经违反规范问法（实际轨撒谎）——这是 FIX-NOW 还是 TICKET-OWNS(04)？
    (b) GET 身份失联仍 200，是否已经把「认回」说成「仍失联」——合同上失联是否必须在标签命中当下结束，还是允许 01 的 mark 滞后到 04 的 ingest 收尾？
    (c) 若 (a) 或 (b) 是 FIX-NOW，04 若按「命中才清标」实现，会不会把撒谎的问法当成正确夹具焊死？
    给推荐（修 01 读模型 / 修 ingest 命中清标并入 03 / 明确 TICKET-OWNS 且 04 第一圈必须覆盖问法翻转），不要在本对话改代码。

P12. 04 预伤：现有 01–03 测试是否把「标签命中仍失联」写成了正确断言，以致 04 一清标就红、或 04 不敢清标？列出具体测试方法名。这是 TEST-GAP 或 FIX-NOW 的附件，不是实现 04。

P13. 文档：`docs/contracts/agent-heartbeat-snapshot.md` 是否把推断/upsert/主机范围写对；是否把绑定记忆写成可靠实际。CONTEXT.md 必须未被 01–03 改语义（允许确认未改）。

P14. 栈：绑定记忆 / 未绑定 / 失联标在 PostgreSQL；Redis 不是这些行的 SSOT。Flyway 只增（V16–V18）未改历史。DO 未当 HTTP 响应。

P15. 回归：竖切未打标负面仍在；改策展处理人审条路径未被未绑定 accept 改门禁；观测消失用例仍是 ABSENT 不是失联。

================================================================================
6. 证据规则（完成标准：每条非 nit 发现都能用「命令或文件:行」复述）
================================================================================

- 主接缝是控制面公开 HTTP API（含 Agent ingest）。断言外部行为：status、`ApiResponse`、后续 GET 可读状态。不要用 Mapper 私有结构当合同证据。
- 票 Comments 里的 red/green 是线索，不是证据。必须回到测试方法与生产代码核对。
- reuse/regression（首跑绿）的圈要加倍怀疑：02 D–K 批量、03 若干 first-run green。问：Spec 要求的行为是否真的被该圈钉住，还是搭了别的绿。
- 允许跑只读测试作证据，命令限定：
  `cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest --tests com.archops.observed.UnboundDraftCreateHttpAcceptanceTest --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest`
  若为 P11/P12 需要对照竖切负面，可加：
  `--tests com.archops.slice.VerticalSliceHttpE2eAcceptanceTest.negative_unlabeledSnapshotDoesNotPromiseUpgradeChain`
  不要为审计跑无关的全量 suite（可在收束时跑一次 `./gradlew test` 作回归，非必须）。
- 禁止新增生产代码。禁止把新测试做成绿灯。若某 FIX-NOW 需要「缺失断言」的证明：可以写出测试方法草稿（报告里的代码块），或本地加测试并留下 **witnessed red**，但不得为它写生产、不得提交该测试除非我事后要求。默认：把「应有的 HTTP 断言」写进报告的 TEST-GAP，不改仓库测试。
- 引用代码用仓库要求的 citation 格式；术语用中文合同词，错误码/路径用反引号原文。

================================================================================
7. 工作步骤（完成标准：每步有可检查的 bound；后一步不得吞掉前一步）
================================================================================

Step A — 术语与范围锁定。
完成：用合同词写一段「01–03 应该留下的世界状态」（未绑定列表、失联标、问法、OPEN 草案、绑定记忆、策展对象/`运行于`、升级链承诺）。明确写出 04/05 尚未承担的部分。不超过 25 行。

Step B — 对照权威读实现。
完成：P1–P15 每个探针都有笔记（通过 / 问题草稿）。先读后跑测试。

Step C — 跑 §6 限定测试。
完成：记录命令与 exit code。失败则区分「环境」与「合同破坏」。环境问题记下来，不要假装通过。

Step D — 分类。
完成：每条进入报告的发现都有且仅有一个标签 + 一个严重性 + 证据 + 推荐下一动作（人确认后才执行）。P7、P10、P11 即使结论是 FALSE-ALARM 也必须单列。

Step E — 写报告文件。
完成：`.scratch/unbound-identity-rebind/audit-01-03-opus.md` 存在且符合 §9。不要改票 Status。不要改 `docs/dev-handoff.md` 去开工 04。可在报告末尾给手填建议：「若 FIX-NOW=0 且 P11 为 TICKET-OWNS/FALSE-ALARM，下一对话才是票 04」——这是建议，不是自行改 frontier。

Step F — 停。
完成：向用户提交报告摘要（三轴计数 + blocker 列表 + P11 结论）。等待确认。确认前不修、不开 04、不拆新票。

================================================================================
8. 质量条（完成标准：交卷前用此表自检；任一项否决则回去补）
================================================================================

- [ ] 没有改生产代码、测试绿灯补丁、Flyway、CONTEXT、ADR、票 Status
- [ ] 没有把 04 的「命中清失联 / 消费候选 / 作废未绑定草案 / 恢复升级链」写成 01–03 的 FIX-NOW，除非现行 01–03 行为已经让命中后的世界在合同上撒谎（见 P11）
- [ ] 没有把 05 的选支/诊断闸门写成 01–03 缺口
- [ ] 没有发明 CONTEXT 没有的词来描述发现
- [ ] Contract / Spec / Standards 三节分开；未把气味当合同失败，也未把合同失败当气味
- [ ] P1–P15 均有结论
- [ ] P7、P10、P11 均有独立裁决
- [ ] 每条 FIX-NOW / NEEDS-ADR / major TEST-GAP 都有 HTTP 或代码证据
- [ ] 推荐下一动作可执行且一次一件（修哪条语义 / 开哪张票 / 需要哪条 ADR 议题），没有「顺便重构 CuratedDraftService」

================================================================================
9. 报告结构（完成标准：文件按此标题存在；可被下一对话当输入）
================================================================================

写入 `.scratch/unbound-identity-rebind/audit-01-03-opus.md`：

# 未绑定 01–03 合同审计（Opus）

- Date / model
- Scope: tickets 01–03 only; 04–07 paused
- Tests run: 命令 + exit code

## 世界状态（合同）
Step A 那段。

## Contract
发现列表。无则写「无 Contract 发现」并说明抽查了 P1–P4、P11。

## Spec
发现列表。覆盖 01–03 拥有的 Story；缺的标 TEST-GAP 或 FIX-NOW。

## Standards
只列会焊死合同或 04 的。其余 judgement 可附短清单。

## 探针表
P1–P15：结论 | 标签 | 证据指针。

## P11 专项
三个子问题 (a)(b)(c) 分开答；最后给一个推荐。

## 对开工 04 的含义
- 可以按现票 04 开工，或
- 必须先修 FIX-NOW（列出建议的最小修复对话范围，仍禁止本对话修），或
- 必须先立 ADR（只给题目与备选，不写文件）

## 非目标
明确列出你考虑过但拒绝写成发现的项（例如 05 闸门、06 tracer、UI、Y2）。

每条发现格式：

### <ID> <标题>
- Axis: Contract | Spec | Standards
- Tag: FIX-NOW | TICKET-OWNS | FALSE-ALARM | NEEDS-ADR | TEST-GAP
- Severity: blocker | major | nit
- Evidence: 测试方法或文件路径
- Contract quote: 一句 CONTEXT/ADR/Spec 原文
- What is true now: 现行 HTTP/代码行为
- Why it matters: 对五态 / 升级链 / 04 的影响
- Recommend: 下一动作（人确认后）

================================================================================
10. 文风
================================================================================

用合同中文词。路径、错误码、测试类名用反引号。不要用「以现场为准」「待确认策展」「未绑定处理人」「已确认待补标」。不要把冲突写成对错。质量优先：宁可少报一条没有证据的 intuitions，也不要漏 P11。

现在从 Step A 开始。不要先写修复。
```
