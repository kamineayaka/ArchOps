# 未绑定 01–03 合同审计（Opus）

- **Date**: 2026-08-22
- **Model**: Claude Opus 5（Cursor Cloud Agent，只读审计；未改生产、未改票 Status、未开 04）
- **Scope**: 未绑定 / 身份失联刀 **票 01–03 only**；04–07 暂停（只用于归属判断）
- **Authority order**: ADR + `CONTEXT.md` > `docs/specs/unbound-identity-rebind.md` > 票 > 票内 Comments / 测试方法名 / 审计 prompt
- **Tests run**（只读证据）:
  1. `cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityLostIngestHttpAcceptanceTest --tests com.archops.observed.UnboundDraftCreateHttpAcceptanceTest --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest` → **exit 0**
  2. `cd backend && ./gradlew cleanTest test` → **exit 0**（21 个测试类 / **129 tests, 0 failures**；含竖切负面与 `ChangeCuratedDraft*`）
  3. 五个**一次性审计探针**（asserts 合同应有行为，故失败即 witnessed red）→ 5/6 红。探针文件已删除，**未提交**；草稿见 §附录。

---

## 世界状态（合同）

票 01–03 跑完之后，控制面应当留下这样一个世界（只用合同词）：

1. 现场缺标或标签对不上既有对象的容器 → **未绑定观测候选**，按 (`sourceHostId`, `runtimeId`) 一行，`upgradeChainPromised=false`；GET 待并入含现场标签 / 运行时 ID / 名称 / 原因 / 来源宿主。
2. 上报主机在范围内（策展 `运行于` 宿主，或当前可用观测宿主）且本快照未标签命中、未声明 `absentObjectIds` → 该 Docker 容器 **身份失联**；他机快照与超范围 `identityLostObjectIds` 无效；心跳-only 不推断。
3. `absentObjectIds` → **观测消失**（可用值 ABSENT），同一快照上压过失联声明；心跳超时仍走 **观测空洞**。
4. **规范问法**：「应该在哪」永远只答策展；失联时「实际在哪」不得报失联前宿主，`availability` 不得为 `PRESENT`，也不得**单因**失联报 `HOLLOW` / `ABSENT`；`IDENTITY_LOST` 只在问法读模型，不入 `observed_fact.availability`、不入 `ConflictStatus`。
5. 未打标同名不承诺**冲突升级**链（`by-merge-key` 仍 400）；未绑定与失联本身不开**冲突**。
6. 已认证运维（一般或高级皆可，无未绑定处理人）对一个待并入候选发起**不挂冲突**的**草案**，同一现场实体最多一份 OPEN，规则夹具 ≥2 条独立条目。
7. **逐条确认**：接受新建 → 策展出现该容器（不可变标签 = 现场标签）；接受 `运行于`（须在新建之后）→ 写第一条策展 `运行于`；拒绝的条不写；`MISSING_LABEL` 新建不是成功路径；占用中的 `archops.object_id` 拒绝。
8. 接受绑定 → 只记住「该现场实体对应策展对象 X」（匹配状态，不是新合同词、不是第四种冲突），不改容器 ID / 不可变标签，不写可靠观测 `运行于`，不承诺升级链；该现场实体离开待并入；绑定与新建互斥。
9. 建底 `POST` 仍能建主机 / 容器与第一条 `运行于`；覆盖已有 `运行于` 仍 `CURATED_RUNS_ON_EXISTS`；没有旁路 POST 把候选映射成对象。

**04/05 尚未承担**：标签命中后清失联、消费候选与绑定记忆、作废命中相关未绑定草案、`runtimeId` 变化算新候选、`absentObjectIds` 解除绑定记忆（04）；失联时闸门选支 / 诊断分叉 / 作废计划与改理想草案、冲突 GET 失联旗标、`PENDING_CLOSE` 退回 `OPEN`（05）。

---

## Contract

### C-1 心跳超时叠加身份失联时，问法把观测空洞说成身份失联
- Axis: Contract
- Tag: FIX-NOW
- Severity: major
- Evidence: 探针 `probe5_identityLostPlusHeartbeatTimeoutIsHollow...` → `JSON path "$.data.observedValue.availability" Expected: "HOLLOW" but: was "IDENTITY_LOST"`。生产：`ObservedTruthService.observedAskValue` 先判 `lostMark != null` 才判 `isObservedFactStale`；票 10 的扫描会**删除**该观测事实，读模型无从区分「通道已死」与「从未观测」。
- Contract quote: `CONTEXT.md` 心跳：「心跳超时 → 相关观测视为观测空洞（旧值不再作为可靠实际）」；Spec：「`observedValue.availability` … Must not be set to `HOLLOW` or `ABSENT` **solely** because of 失联」；票 01 验收：「不得**单因**失联报 `HOLLOW` / `ABSENT`」。
- What is true now: 对象既已被打失联标、其 Agent 又已超时并被扫描空洞化，`GET /api/observed/asks/actual-where` 仍只答 `IDENTITY_LOST`；「通道已死」这一独立事实在问法上消失。
- Why it matters: 五态里的**观测空洞**被**身份失联**吞掉。运维/诊断会被引导去「现场补标」，而真相是 Agent 不在了；05 的「除非心跳确实超时」分支将没有可读的输入。
- Recommend: 在问法读模型里让「通道已超时」优先给出 `HOLLOW`，同时保留 `identityLost=true`（DTO 已有独立布尔，不需要新词）。判定可用上报宿主的 `host_agent` 新鲜度，而非已被删除的观测行。若你更愿意把优先级写成合同，可另立 ADR 议题「失联与空洞并存时的问法优先级」——本审计不代写。

### C-2 同一身份失联对象可被两个现场实体分别绑定
- Axis: Contract
- Tag: FIX-NOW
- Severity: major
- Evidence: 探针 `probe2_secondFieldEntityCannotBindToTheSameIdentityLostObject`（同一宿主一快照两条未打标实体 `pb2-rt-1` / `pb2-rt-2`，同一失联 X）→ 第二次接受绑定 `Status expected:<400> but was:<200>`。生产：`CuratedDraftService.requireUnboundCandidateNotConsumed` 只按草案自己的 (`sourceHostId`, `runtimeId`) 查绑定记忆；`V18__unbound_bind_memory.sql` 只对 (`source_host_id`, `runtime_id`) 唯一，`curated_object_id` 无唯一约束；`findIdentityLostOnHost` 会把同一个 X 反复放进夹具。
- Contract quote: ADR-0011「匹配失败产生未绑定观测候选，禁止静默合并」；`CONTEXT.md` 未绑定观测候选 _Avoid_：「静默合并进近似对象」；对象 ID：「一等对象的稳定主键」。
- What is true now: 两条不同的现场实体都被记成「就是 X」。策展侧看不出异常，但匹配状态里 X 有两个「本体」，且 `运行于` 只能有一个目标。
- Why it matters: 票 03 的互斥只防「一个现场实体变两个策展对象」，反向合并无人防。04 的命中收尾要「消费候选与绑定记忆」，届时 X 命中后到底消费哪一条、另一条是否回到待并入，没有合同答案——错误会被 04 当前提。
- Recommend: 在接受绑定时要求目标对象上没有未消费的绑定记忆（并给出与 `UNBOUND_CANDIDATE_CONSUMED` 区分的错误码），必要时加一条**新增** Flyway（`curated_object_id` 唯一）；同时让 `MISSING_LABEL` / UNKNOWN 夹具不再重复提供已被绑定的目标。修复对话范围：`CuratedDraftService.writeAcceptedBind` + 夹具选择 + 一条 HTTP 负面（**不要**顺手重构该类）。

### C-3 标签命中之后失联标仍在：问法与冲突对「实际」互相矛盾
- Axis: Contract
- Tag: TICKET-OWNS（04）
- Severity: major
- Evidence: 探针 `probe3a_labelMatchAfterLossOpensConflictOnMergeKey` **通过**（失联后在 B 主机标签命中 → `GET /api/conflicts/by-merge-key` 200 且 `OPEN`）；探针 `probe3b_askActualWhereAfterLabelMatchReportsMatchedHost` **红**（`Expected: "PRESENT" but: was "IDENTITY_LOST"`）。生产：`ObservedTruthService.upsertObservedPresent` 命中即写 PRESENT 并 `reconcileAfterObservedWrite`，但没有任何路径删除 `identity_lost_mark`；`observedAskValue` 只看 mark。
- Contract quote: `CONTEXT.md` 身份失联：「既有对象的匹配线索失效 … 以致探测无法可靠认回该对象」；规范问法：「『实际在哪』只对应观测」；Spec 故事 40 把「clear 身份失联」写在 04。
- What is true now: 标签已经命中、观测 `运行于` 已是新鲜 PRESENT、冲突已按合并键开出「策展 A / 实际 B」，而同一时刻问法拒绝说出实际，只报身份失联。两条读路径对同一个对象是否「认回」给出相反答案。
- Why it matters: 冲突要求两侧都有可用值；问法说没有可用实际。这不是语义写歪（升级链恢复是对的），而是失联标滞后未清——按 Spec 排序归 04。但滞后已经**外溢成 03 的写门禁依赖**（见 C-4），所以不能再往后拖。
- Recommend: 04 第一圈就把这条钉住：命中 → 清失联 → `GET /api/observed/identity-lost/{id}` 400、问法 `availability=PRESENT` 且 `identityLost=false`、`by-merge-key` 恢复升级链；同时按 S-4 修掉那条把「命中仍失联」当正确的旧断言。本对话不动代码。

### C-4 绑到身份失联对象被「旧 PRESENT 观测行」当健康挡掉
- Axis: Contract
- Tag: FIX-NOW
- Severity: major
- Evidence: 探针 `probe1_bindOntoIdentityLostThatHadEarlierLabelMatch`（X 先标签命中写 PRESENT，随后同宿主未打标快照把 X 打上失联）→ 接受绑定 `$.code` 实际为 **`UNBOUND_BIND_TARGET_HEALTHY`**，`Status expected:<200> but was:<400>`。生产：`CuratedDraftService.writeAcceptedBind` 判 `lost == null || observedRunsOnPresent(targetId)`；`observedRunsOnPresent` 只看 `availability == PRESENT`，不看该观测是否发生在失联之前。01 的现有测试 `identityLostActualWhereDoesNotReportStaleObservedHost` 正是这个状态（同一状态下问法答 `IDENTITY_LOST`）。
- Contract quote: ADR-0012「标签缺失或被篡改按匹配失败处理（B1）：产生未绑定观测候选，原对象标记身份失联」；Spec 故事 34「绑到已有 to succeed only when the target is 身份失联」；`CONTEXT.md` 观测真相 _Avoid_：把不再可靠的旧值「当可信」。
- What is true now: ADR-0012 B1 的主场景（标签被删/被改的既有容器）无法经草案绑回——HTTP 一律 400。能成功绑定的只剩「从未标签命中过」的先策展后补标对象（03 全部绑定用例都是这一种）。
- Why it matters: 本刀的「并入必须逐条」在最常见的失联场景上不可达；同时 03 的门禁与 01 的读模型对同一对象结论相反（读模型忽略旧 PRESENT，门禁信任旧 PRESENT），这正是 C-3 滞后外溢的落点。
- Recommend: 把判别式从「有没有 PRESENT」改成「**失联之后是否又命中过**」——例如比较该观测事实的 `observed_at` 与 `identity_lost_mark.markedAt`，晚于失联的 PRESENT 才算健康（04 清标后这条判别式仍然正确）。修复对话范围：`writeAcceptedBind` + 一条「命中后再绑被拒」+ 一条「失联前曾 PRESENT 仍可绑」HTTP 用例。可与 C-2 合并成一张「绑定写入门禁」修复票（同一方法、同一测试类）。

其余 Contract 抽查（P1–P4、P11）结论见探针表：五态在 ingest 侧未被合并（失联从不写入 `observed_fact.availability`，其 CHECK 仍只有 `PRESENT` / `ABSENT`；`ConflictStatus` 未加枚举），弱线索未点亮升级链（`by-merge-key` 在未打标同名与绑定之后都仍 400），主机范围与心跳-only 规则成立。

---

## Spec

### S-1 只接受新建（拒绝 `运行于`）之后，该对象问不出「实际在哪」
- Axis: Spec
- Tag: TICKET-OWNS（04）
- Severity: nit
- Evidence: 探针 `probe4_askActualWhereOnCreatedObjectWithoutCuratedRunsOn` → 命中标签写完观测后 `GET /api/observed/asks/actual-where` `Status expected:<200> but was:<400>`（`CURATED_RUNS_ON_NOT_FOUND`，来自 `ObservedTruthService.actualWhere` 的竖切前置）。
- Contract quote: Spec 故事 25「accept 新建 and reject `运行于`, so that a new curated object can exist without a curated location yet」；tracer 步骤 5「Further heartbeat with that label → observed `运行于` A」。
- What is true now: 故事 25 造出的对象合法存在且随后有可用观测，但问法整条 400，理由是策展侧没有 `运行于`（策展侧本来就允许为空）。
- Why it matters: 04 的 tracer 步骤 5 会经过这个状态；若 04 想在命中后读问法，会撞到 400。语义上不是撒谎（没有把单轨说成唯一真相），所以不算 01–03 写歪。
- Recommend: 交给 04（或 06 tracer）决定：要么问法在策展空缺时也返回 200 并把策展值留空，要么 tracer 明确不问这条。本对话不改。

### S-2 心跳契约文档未写绑定记忆
- Axis: Spec
- Tag: FIX-NOW
- Severity: nit
- Evidence: `docs/contracts/agent-heartbeat-snapshot.md` 覆盖了推断失联 / 未绑定 upsert / 主机范围 / `IDENTITY_LOST` 仅读模型，但全文没有绑定记忆；Spec Modules：「`observed` / `agent`: … update `docs/contracts/agent-heartbeat-snapshot.md` to describe inference + upsert + **bind memory**」。
- Contract quote: 同上（Spec Implementation Decisions → Modules）。
- What is true now: 票 01 的文档圈只写了 01 的两项；票 03 引入绑定记忆后没有回写契约文档。好消息是文档也**没有**把绑定记忆说成可靠实际（P13 的反向风险不存在）。
- Why it matters: 04 要在同一文档描述「命中即消费绑定记忆」，缺前半段会让读者以为记忆是 04 才有的东西。
- Recommend: 在任一后续对话（04 的文档圈最自然）补一段：绑定记忆 = 逐条确认后的匹配状态，键 (`sourceHostId`, `runtimeId`)，不产生观测 `运行于`，不承诺升级链。

### S-3 一台宿主上有多个身份失联对象时，夹具只能提供编号最小的那个
- Axis: Spec
- Tag: TEST-GAP
- Severity: nit
- Evidence: `CuratedDraftService.findIdentityLostOnHost` → `orderByAsc(curatedObjectId).last("LIMIT 1")`；`MISSING_LABEL` / UNKNOWN 夹具都只取这一条。没有任何 HTTP 用例覆盖「同宿主两个失联对象」。
- Contract quote: Spec 故事 30「the rule fixture to emit mutually exclusive items (1) 绑到已有 X and (2) 新建」。
- What is true now: Spec 只要求「绑到已有 X」，没要求穷举，所以生产未违反 Spec；但运维无法把现场实体绑到该宿主上的第二个失联对象。
- Why it matters: 06 的演示与真实运维会撞到；也与 C-2 同源（夹具不看目标是否已被绑）。
- Recommend: 记为待钉断言：「同宿主两个失联对象 → 草案可分别绑到各自目标」。是否实现由你定；若与 C-2 一起修，成本几乎相同。

### S-4 现有测试把「标签命中后仍身份失联」钉成了正确断言（04 预伤）
- Axis: Spec
- Tag: TEST-GAP
- Severity: major
- Evidence: `UnboundDraftItemReviewHttpAcceptanceTest.bindingToLabelMatchedPresentTargetIsRejected` 末尾（该文件第 386–389 行）在标签命中之后仍断言 `GET /api/observed/identity-lost/{id}` **200**。全仓扫描 `identity-lost|identityLost|IDENTITY_LOST` 后确认：这是**唯一**一条「命中之后」的失联断言（`UnboundDraftCreateHttpAcceptanceTest.identityLostMissingLabelDraftOffersBindVersusCreate` 与 01 的 `currentlyUsableObservedHostSnapshotInfersIdentityLost` / `identityLostActualWhereDoesNotReportStaleObservedHost` 都是「先命中后失联」，不受 04 影响）。同时**没有任何**测试钉住命中后问法应翻转。
- Contract quote: Spec 故事 40「after a correct label appears, I want ingest to … clear 身份失联」。
- What is true now: 04 一旦按票实现「命中才清标」，这条断言必红；而如果 04 只让它变绿而不新增断言，问法翻转仍无人看守。
- Why it matters: 这正是「撒谎的问法被当成正确夹具焊死」的具体入口。
- Recommend: 04 的第一圈同时做两件事：改这条断言为 400（或改成断言 `UNBOUND_BIND_TARGET_HEALTHY` 且不再声称仍失联），并新增命中后问法 `PRESENT` / `identityLost=false` 的断言。

### S-5 已被消费的候选仍可再开一份未绑定草案
- Axis: Spec
- Tag: TEST-GAP
- Severity: nit
- Evidence: `CuratedDraftService.createFromUnboundCandidate` 只查「是否已有 OPEN 草案」，不查绑定记忆；候选 id 仍可从早先的 GET 拿到（默认列表已过滤）。后续接受会被 `UNBOUND_CANDIDATE_CONSUMED` 挡住。
- Contract quote: Spec 故事 5「default 未绑定 list to show only **待并入** candidates」+ 故事 44。
- What is true now: 可以开出一份注定无法接受 BIND / CREATE 的 OPEN 草案（其中 `CURATED_RUNS_ON_INSERT` 也会被前置条件挡）。策展不会被写坏。
- Why it matters: 04 要「命中即作废未绑定草案」，多余的 OPEN 草案会增加它要处理的状态组合。
- Recommend: 记为待钉断言（已消费候选发起草案 → 业务错误）。优先级低。

01–03 拥有的其余故事均已核对到 HTTP：01 的 1–13、17、54（问法 / upsert / 推断 / 文档）；02 的 18–24、58（`UNKNOWN` 夹具 CREATE + `运行于`、`MISSING_LABEL` 夹具 BIND vs CREATE、互斥、认证、事件、发起不写策展）；03 的 19–20、25–28、31–36、44、51–53、59 与故事 5 的待并入过滤。缺口只在上列 S-1…S-5 与 Contract 段。

---

## Standards

只列会把合同写歪或把 04 焊死的：

### ST-1 绑定记忆表分不清「绑定」与「新建」
- Axis: Standards
- Tag: TICKET-OWNS（04）
- Severity: nit
- Evidence: `V18__unbound_bind_memory.sql` 只有 (`source_host_id`, `runtime_id`, `curated_object_id`)；`CuratedDraftService.writeAcceptedCreateContainer` 与 `writeAcceptedBind` 都调 `rememberBind`。
- Contract quote: Spec 故事 50「if `absentObjectIds` arrives after a pending 绑定 memory for X … bind memory for X is released」。
- What is true now: 04 无法只凭该表区分「待补标的绑定」与「新建后的对应关系」。
- Why it matters: 故事 50 只针对绑定；若 04 对两类一视同仁地释放，会把新建对象的对应关系也抹掉。
- Recommend: 04 若需要区分，用**新增** Flyway 加 `origin`（`BIND` / `CREATE`）；不要改 V18。

### ST-2 V17 在同一 PR 内被二次编辑（看着像改历史脚本）
- Axis: Standards
- Tag: FALSE-ALARM
- Severity: nit
- Evidence: `V17__unbound_candidate_draft.sql` 由 `574f4d5` 创建、由 `4f9b965` 追加索引；两者同属 PR #81（`git log --merges --ancestry-path` 对两个提交都指向 `4424221`），main 从未合入过旧版 V17。
- Contract quote: `AGENTS.md` §3「Flyway：**禁止修改**已有 `V*.sql`，只新增下一个」。
- What is true now: main 上的迁移历史一次成型；只有当时那台跑过中间提交的机器可能留下旧校验和。
- Why it matters: 语义与 04 无关；仅当某个长期库跑过中间提交才需要 `flyway repair`。
- Recommend: 不动 V17。若 kamiserver 那台库启动报校验和不符，用 `flyway repair` 而不是编辑脚本。

判断类气味（不建议在本刀展开）：`CuratedDraftService` 已同时持有改理想草案、未绑定草案、观测事实与失联标读写（Divergent Change / Feature Envy，01–03 三轮 code-review 都记过）；`IdentityLost` 构造重复；「无整单全接受」只用 `POST /api/curated-drafts/{id}/accept` 返回 500（无 handler）证明，比 404/405 弱。05 的闸门若继续堆进同一个类会更难读——但这是 05 开工时的判断，不是本刀缺陷。

---

## 探针表

| 探针 | 结论 | 标签 | 证据指针 |
|---|---|---|---|
| P1 五态互斥 | 部分失败：缺标 / 未知标签 / 范围内未命中 / `absentObjectIds` / absent 压过声明 均正确；失联从不写 `observed_fact.availability`（V4 CHECK 仍 `PRESENT`/`ABSENT`）；**心跳超时路径被失联吞掉** | C-1 = FIX-NOW | `u01a`/`u01b`/`u01c`/`u01i`/`u01i2`；探针 5 |
| P2 主机范围 | 通过：他机快照与超范围声明均不打失联；心跳-only 不推断 | 通过 | `otherHostSnapshotDoesNotMarkIdentityLost`、`outOfScopeIdentityLostObjectIdsDoNotMarkContainer`、`heartbeatOnlyDoesNotInferIdentityLost`；`reportingHostInIdentityLostScope` |
| P3 规范问法 | 部分失败：「应该在哪」不变、失联不报旧宿主、`IDENTITY_LOST` 仅读模型均成立；命中后仍报失联（C-3）、超时被吞（C-1）、故事 25 对象问法 400（S-1） | C-3 TICKET-OWNS(04)、C-1 FIX-NOW、S-1 TICKET-OWNS(04) | `neverObservedIdentityLostActualWhereIsNotHollow`、`identityLostActualWhereDoesNotReportStaleObservedHost`；探针 3b/4/5 |
| P4 弱线索 | 通过：未打标同名与接受绑定之后 `by-merge-key` 均 400；绑定不写观测 `运行于` | 通过 | `unlabeledSameNameDoesNotPromiseUpgradeChain`、`negative_unlabeledSnapshotDoesNotPromiseUpgradeChain`、`acceptingBindToIdentityLost...` |
| P5 未绑定 upsert | 通过：V16 partial unique + 一行刷新；GET 含 labels / runtimeId / name / reason / sourceHostId，`upgradeChainPromised` 恒 false | 通过 | `sameHostAndRuntimeIdUnboundCandidateIsUpserted`、`unknownObjectIdUnboundCandidateListsFieldLabels` |
| P6 未绑定草案 | 通过（一个 nit）：V17 CHECK 强制 `conflict_id IS NULL`、无 dummy 冲突、双唯一索引互斥、未认证 401、一般与高级皆可、无处理人门禁、不出现在冲突草案 API、无计划；已消费候选仍可发起草案 | 通过 + S-5 TEST-GAP | `V17__unbound_candidate_draft.sql`、`UnboundDraftCreateHttpAcceptanceTest` 全类、`CuratedDraftController` 路由分离 |
| P7 夹具（含 Cycle I 裁决） | 见 §P7 专项：UNKNOWN 在宿主有失联对象时加 BIND = **FALSE-ALARM**；夹具目标选择的缺陷另计（C-2 / S-3） | FALSE-ALARM | `buildUnboundItems`、`unknownBindToExistingDoesNotRewriteWrongLabelAsPrimaryKey`、Spec 故事 33 |
| P8 逐条写入 | 通过：只接受新建 / 先接受 `运行于` 失败 / 占用 id 失败 / 拒绝不写 / 无整单全接受 / 建底第一条成功且覆盖仍 `CURATED_RUNS_ON_EXISTS` / 无旁路 POST | 通过 | `u03a`/`u03b`/`u03c`/`u03n`/`u03k`/`u03h`、`writeAcceptedFirstRunsOn` |
| P9 绑定 | 部分失败：主键 / 不可变标签不变、不写弱线索实际、离开待并入、再心跳仍失联仍不升级、双接受第二次失败、UNKNOWN 绑定不改主键 均成立；**双实体绑同一对象**（C-2）与**失联前曾 PRESENT 无法绑**（C-4）失败 | C-2 / C-4 = FIX-NOW | `u03d`/`u03e`/`u03f`/`u03i`/`u03g`；探针 1、2 |
| P10 CREATE 也写绑定记忆 | 见 §P10 专项：FALSE-ALARM（匹配状态，非偷偷绑定） | FALSE-ALARM | `writeAcceptedCreateContainer` → `rememberBind`；`acceptingCreateAfterBindFailsAsCandidateConsumed` |
| P11 Cycle G 张力 | 见 §P11 专项：(a) TICKET-OWNS(04)、(b) 合同上失联在命中当下结束、允许 04 收尾、(c) 有焊死风险，已定位到唯一一条断言 | TICKET-OWNS(04) + S-4 TEST-GAP | 探针 3a/3b；`UnboundDraftItemReviewHttpAcceptanceTest` 第 386–389 行 |
| P12 04 预伤 | 唯一预伤断言：`bindingToLabelMatchedPresentTargetIsRejected`（第 386 行，命中后仍断言失联 200）；无任何断言钉住命中后问法翻转 | S-4 TEST-GAP | 全仓 `identity-lost` 断言扫描 |
| P13 文档 | 心跳契约文档对推断 / upsert / 主机范围 / `IDENTITY_LOST` 仅读模型写得准，且未把绑定记忆说成可靠实际；缺绑定记忆段落。`CONTEXT.md` 未被 01–03 改动（最后一次改动是 #77 的工单指针行，非语义） | S-2 FIX-NOW（nit） | `docs/contracts/agent-heartbeat-snapshot.md`；`git log -- CONTEXT.md` |
| P14 栈 | 通过：未绑定 / 失联标 / 绑定记忆 / 草案全在 PostgreSQL；测试连 Redis 自动配置都关掉，Redis 不承担这些行；V16–V18 只增（V17 重建条目 kind CHECK 是 Spec 明许的新脚本）；响应全是 `record` DTO | 通过（ST-2 说明） | `V16`–`V18`、`HttpAcceptanceTest`、`UnboundCandidateResponse` 等 |
| P15 回归 | 通过：`./gradlew cleanTest test` 129 tests / 0 failures；竖切未打标负面在；改策展处理人审条路径未被改（独立路由 + `requireAcceptedHandler`）；观测消失仍 ABSENT | 通过 | 命令 2；`CuratedDraftController`、`absentObjectIdsRemainUsableAbsentNotIdentityLost` |

### P7 专项（Cycle I 裁决）

**裁决：FALSE-ALARM。** 03 在 `UNKNOWN_OBJECT_ID` 候选所在宿主存在失联对象时给夹具**前置**一条 `BIND_UNBOUND_TO_EXISTING`，不是 03 改写了 02 的夹具合同：

- Spec 故事 33 明确要求 `UNKNOWN_OBJECT_ID` 可以「绑到已有 X」且不得把错标签写成 X 的主键——没有这条 BIND，故事 33 在 HTTP 上不可达。票 03 验收也写着「`UNKNOWN_OBJECT_ID` 绑到已有允许」。
- 票 02 与 Spec 对 UNKNOWN 夹具的表述是「≥2 条：新建 + `运行于`」；03 加到三条仍满足「≥2 条独立可确认」，且 02 的 `MISSING_LABEL` 夹具（BIND vs CREATE 两条）未被改动（`identityLostMissingLabelDraftOffersBindVersusCreate` 仍断言恰好 2 条且无 `CURATED_RUNS_ON_INSERT`）。
- 互斥没有丢：BIND 与 CREATE 都过 `requireUnboundCandidateNotConsumed`，`unknownBindToExistingDoesNotRewriteWrongLabelAsPrimaryKey` 证明「先 CREATE 再 BIND」得到 `UNBOUND_CANDIDATE_CONSUMED`；接受 BIND 后 `CURATED_RUNS_ON_INSERT` 会被 `UNBOUND_RUNS_ON_BEFORE_CREATE` 挡住，不会给 X 写第二条策展 `运行于`。

真正的夹具缺陷不在「加了 BIND」，而在**选谁**：`findIdentityLostOnHost` 取编号最小的一条且不看该目标是否已被绑（→ C-2、S-3）。

### P10 专项（CREATE 也写绑定记忆）

**裁决：FALSE-ALARM。** 这是「消费候选」的合法匹配状态，不是把新建偷偷写成绑定：

- 接受新建之后，该现场实体确实已对应一个策展对象（其现场标签就是新对象的不可变 ID），所以它离开待并入是故事 5 与故事 44 要的效果；这也正是「双接受」互斥的实现方式（`acceptingCreateAfterBindFailsAsCandidateConsumed`）。
- 绑定与新建都不写观测 `运行于`（探针与 `u03d` 都确认可靠实际仍等标签命中），所以记忆没有变成弱线索实际。
- 命名是 Standards 层的 nit：`unbound_bind_memory` 存了两类对应关系。给 04 的提醒见 ST-1：故事 50 的「解除绑定记忆」只针对待补标的**绑定**，04 需要区分手段（新增 Flyway 加 `origin`），不要按表名一刀切。

### P11 专项

**(a) 观测已 PRESENT 时问法仍报 `IDENTITY_LOST`，是否已违反规范问法？**
是撒谎，但属于**滞后**而非语义写歪 → **TICKET-OWNS（04）**。证据是成对的：探针 3a 显示标签命中后升级链已按合同恢复（冲突 `OPEN`，两侧值都在），探针 3b 显示同一时刻问法拒绝给出实际。两条读路径矛盾，说明失联标此刻已经不成立；而「命中即清标」由 Spec 故事 40 与 tracer 步骤 8 明确交给 04。把它算成 01 的 FIX-NOW 会等于把 04 的工作量记成 01–03 的缺陷。**但**这份滞后已经不只是显示问题：03 的绑定门禁反过来信任旧 PRESENT（C-4），所以 04 不能无限期后推。

**(b) 合同上失联是否必须在标签命中当下结束？**
是。`CONTEXT.md` 把身份失联定义为「匹配线索失效，以致探测无法可靠认回」；标签一命中，线索就有效了，失联在事实层面当场结束——ADR-0012 也只在「标签仍能命中」时恢复同一对象与升级链。所以 `identity_lost_mark` 在命中之后属于**过期记录**，不是一种「已认回但仍失联」的合法状态（合同里没有这种态）。允许 01 的标滞后到 04 的 ingest 收尾，只在一个条件下成立：滞后期间**没人把这枚过期标当真相消费**。今天不满足这个条件（C-4），这是本次审计最值得你决策的一点。

**(c) 若 04 按「命中才清标」实现，会不会把撒谎的问法焊死？**
会有这个风险，但入口只有一个，已定位：`UnboundDraftItemReviewHttpAcceptanceTest.bindingToLabelMatchedPresentTargetIsRejected` 第 386–389 行断言命中后 `GET /api/observed/identity-lost/{id}` 仍 200。若 04 为了让它继续绿而不清标，撒谎就固化；若 04 清标而只是把它改绿、不补问法断言，问法翻转仍无人看守（S-4）。

**推荐（等你确认，本对话不改代码）：**
1. **先修 C-4（可与 C-2 并成一张「绑定写入门禁」修复票）**：门禁改用「失联之后是否又命中过」判别，不再把旧 PRESENT 当健康；顺带补 C-2 的目标唯一性。这条不依赖 04，且能立刻停止对过期标的错误消费。
2. **再按现票开工 04**，第一圈就覆盖问法翻转（`availability=PRESENT`、`identityLost=false`、`identity-lost` GET 400、`by-merge-key` 升级链），并按 S-4 修那条旧断言。
3. **C-1 单独一件**（问法在通道超时时给 `HOLLOW` 并保留 `identityLost=true`）。可排在 04 之前或之后，但不要和 04 混在一张票里。

---

## 对开工 04 的含义

- **可以按现票 04 开工**：04 的验收清单本身与合同一致，且 Spec tracer 步骤 6 绑定的是「从未标签命中过」的 X，不会踩到 C-4。
- **但建议先做一张最小修复票**（范围：`CuratedDraftService.writeAcceptedBind` + 夹具目标选择 + 两条 HTTP 用例；不重构该类、不动 Flyway 历史、必要时只新增 V19）：修 **C-4** 与 **C-2**。理由：这两条都在「接受绑定」这一个写入点上，且 C-4 让 ADR-0012 B1 主场景今天不可达；越晚修，04 的夹具越可能把「只有从未命中过的对象才能绑」固化成前提。
- **C-1** 与 **S-2** 可独立排期（一条问法读模型 + 一段契约文档），不阻塞 04。
- **不需要新 ADR**：C-1 到 C-4 都能在现有 `CONTEXT.md` / ADR-0011 / ADR-0012 / ADR-0006 与本刀 Spec 内裁决。若你希望把「失联与空洞并存时问法的优先级」写死成合同，那是唯一值得考虑的 ADR 议题，备选语义为：(甲) 通道超时优先报空洞、失联降为旗标；(乙) 失联优先报失联、空洞只在诊断分叉里体现。本审计推荐 (甲)，但不代写 ADR。

---

## 非目标（考虑过，拒绝写成本票发现）

- **05 的闸门**：失联主体上 `FIX_ACTUAL` / `CHANGE_CURATED` 选支今天不失败、诊断仍可能给旧实际落点、活跃计划与改理想草案不因失联作废、冲突 GET 没有失联旗标、`PENDING_CLOSE` 不退回 `OPEN`。全部是 05 的验收项，01–03 没有相反语义。
- **04 的其余收尾**：命中清标 / 消费候选与记忆 / 作废未绑定草案（故事 42–43）/ `runtimeId` 变化算新候选（故事 37）/ `absentObjectIds` 释放绑定记忆（故事 50）/ 命中后位置相等不人造冲突（故事 29）。今天不做不是 01–03 的缺口。
- **06 有序 tracer 与 07 薄 UI**。
- **Y2 策展对齐步骤、SSH 补标、LLM 起草草案、自我迭代**：Spec Out of Scope。
- **分层气味的重构提案**（`CuratedDraftService` 拆分等）：本对话 Standards 轴降级，只保留会焊死合同或 04 的项。
- **无证据的直觉**：例如「未绑定候选可能需要分页 / 过期清理」「`findIdentityLostOnHost` 的 `LIMIT 1` 在多副本下可能抖动」——没有 HTTP 或代码证据支撑到「会改变合同结论」，不写成发现。

---

## 附录：一次性探针草稿（未提交）

探针文件在取证后即删除（`git status` 干净）。要复现，请把下列方法放进 `backend/src/test/java/com/archops/observed/` 下一个 `@HttpAcceptanceTest` 类（辅助方法可从 `UnboundDraftItemReviewHttpAcceptanceTest` 抄），断言写的是**合同应有行为**，所以红灯即证据。

```java
// C-4：X 先标签命中（写下 PRESENT），随后同宿主未打标快照把 X 打上失联 → 绑定应当成功
heartbeatLabeled(hostA, "pb1-ag", "pb1-rt-hit", "pb1-x", "pb1-oid");
heartbeatMissingLabel(hostA, "pb1-ag", "pb1-rt-miss", "pb1-similar");
postUnboundItem(draftId, bindItemId, "accept").andExpect(status().isOk());
// 实际：400 UNBOUND_BIND_TARGET_HEALTHY

// C-2：同一快照两条未打标实体，同一失联 X → 第二次绑定应当失败
postUnboundItem(firstDraft, firstBind, "accept").andExpect(status().isOk());
postUnboundItem(secondDraft, secondBind, "accept").andExpect(status().isBadRequest());
// 实际：200（X 上出现两条绑定记忆）

// C-3：失联后在别的宿主标签命中 → 问法应当报命中宿主
heartbeatMissingLabel(hostA, ...);                 // X 失联
heartbeatLabeled(hostB, "pb4-agb", "pb4-rt-hit", "pb4-x", "pb4-oid");
get("/api/observed/asks/actual-where")
    .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
    .andExpect(jsonPath("$.data.identityLost", is(false)));
// 实际：IDENTITY_LOST / true，而同时 GET /api/conflicts/by-merge-key 已是 OPEN

// C-1：失联 + 心跳超时（@TestPropertySource heartbeat-timeout=30s，回拨 host_agent 后跑扫描）
post("/api/observed/scan-heartbeat-timeouts");
get("/api/observed/asks/actual-where")
    .andExpect(jsonPath("$.data.observedValue.availability", is("HOLLOW")));
// 实际：IDENTITY_LOST

// S-1：只接受新建、拒绝「运行于」，随后标签命中 → 问法应当可读
get("/api/observed/asks/actual-where").andExpect(status().isOk());
// 实际：400 CURATED_RUNS_ON_NOT_FOUND
```

---

## 手填建议（不代你改 frontier）

若你判定 **FIX-NOW 处置完毕**（C-1 / C-2 / C-4 / S-2）**且** P11 的处置按上文 (a)(b)(c) 接受为 TICKET-OWNS(04)，则下一对话才是 `/implement` `/tdd` **未绑定票 04**。本对话未改任何票 `Status`、未改 `docs/dev-handoff.md`。
