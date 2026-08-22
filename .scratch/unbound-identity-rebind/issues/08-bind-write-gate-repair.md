# 08 — 修复接受绑定的写入门禁：失联判据与目标唯一

**What to build:** 只碰「接受绑定」这一个写入点。第一，绑定门禁不得再把「失联之前留下的 PRESENT 观测行」当成目标健康：判据改为**失联之后是否又标签命中过**——晚于失联标的 PRESENT 才算认回，`ADR-0012` B1（标签被删/被改的既有容器）必须能经草案绑回。第二，同一策展对象不得被两个现场实体分别绑定：接受绑定时目标上已有未消费的绑定记忆则失败，规则夹具也不再重复提供已被绑定的目标（同宿主有多个失联对象时给出各自目标）。

**Blocked by:** 03 — 逐条确认：新建写入对象；绑定只记对应关系

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条 HTTP 测试。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)（故事 30、33、34；ADR-0011 / ADR-0012）。

来源：`.scratch/unbound-identity-rebind/audit-01-03-opus.md` 的 **C-4**（FIX-NOW / major）、**C-2**（FIX-NOW / major）、**S-3**（TEST-GAP / nit）。按审计推荐，本票排在票 04 之前做，不改 04/05 语义。

- [x] 目标在失联之前曾有 PRESENT 观测（标签后来被删/被改）→ 接受绑定成功；容器 ID / 不可变标签不变；不写观测 `运行于`；不承诺升级链
- [x] 目标在失联之后又标签命中（观测 PRESENT 晚于失联标）→ 接受绑定仍 `UNBOUND_BIND_TARGET_HEALTHY`（票 03 Cycle G 不回归）
- [x] 同一失联对象上已有未消费的绑定记忆 → 第二个现场实体接受绑定失败（`UNBOUND_BIND_TARGET_ALREADY_BOUND`），不得把一个策展对象变成两个现场实体的本体
- [x] 同宿主存在多个失联对象 → 各现场实体的草案分别给出尚未被绑的目标，都能接受
- [x] 新增 Flyway（绑定记忆按策展对象唯一），不改历史 `V*.sql`

**Out of this ticket:** 标签命中清失联 / 消费候选与绑定记忆 / 作废未绑定草案（票 04，含审计 C-3、S-4）；`absentObjectIds` 之后解除绑定记忆与失联（票 04 故事 50，审计已确认现行行为不变）；失联时问法在心跳超时下应报观测空洞（审计 C-1，另一张票）；心跳契约文档补绑定记忆段（审计 S-2，随票 04 文档圈）；失联闸门（票 05）；tracer（06）；UI（07）。

## Comments

审计结论经用户批准后开工。一次只做本票；不要顺手做 04/05，不要重构 `CuratedDraftService`。

### Cycle A — 失联之前曾 PRESENT 的对象可以绑回
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundBindGateHttpAcceptanceTest.bindingToTargetThatLostItsLabelAfterAnEarlierMatchSucceeds`
```
UnboundBindGateHttpAcceptanceTest > bindingToTargetThatLostItsLabelAfterAnEarlierMatchSucceeds() FAILED
    java.lang.AssertionError at UnboundBindGateHttpAcceptanceTest.java:62
java.lang.AssertionError: Status expected:<200> but was:<400>
```
（门禁把失联之前留下的 PRESENT 观测行当成目标健康 → `UNBOUND_BIND_TARGET_HEALTHY`。）
Green command: 同上，exit 0。`writeAcceptedBind` 判据从 `observedRunsOnPresent` 换成 `labelMatchedAfterIdentityLoss`（PRESENT 且 `observedAt` 不早于 `markedAt` 才算认回）。绑定仍不写观测 `运行于`、不承诺升级链、不改容器 ID / 不可变标签。
Regression：`UnboundDraftItemReviewHttpAcceptanceTest` 全类绿，含 Cycle G `bindingToLabelMatchedPresentTargetIsRejected`（失联后又命中仍 `UNBOUND_BIND_TARGET_HEALTHY`）。
Refactor: 删掉只有一个调用点的 `observedRunsOnPresent`，判据落在带失联语义注释的 `labelMatchedAfterIdentityLoss`。

### Cycle B — 同一失联对象不得被第二个现场实体绑定
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundBindGateHttpAcceptanceTest.secondFieldEntityCannotBeBoundToAnAlreadyBoundTarget`
```
UnboundBindGateHttpAcceptanceTest > secondFieldEntityCannotBeBoundToAnAlreadyBoundTarget() FAILED
    java.lang.AssertionError at UnboundBindGateHttpAcceptanceTest.java:115
java.lang.AssertionError: Status expected:<400> but was:<200>
```
（两个现场实体先各开一份草案，再依次接受绑定；第二次也成功，X 上出现两条绑定记忆。）
Green command: 同上，exit 0。接受绑定新增 `requireTargetNotAlreadyBound` → `UNBOUND_BIND_TARGET_ALREADY_BOUND`；`V19__unbound_bind_memory_object_unique.sql` 按策展对象加唯一索引，`rememberBind` 把唯一冲突翻成同一业务错误（多副本并发的兜底）。失败条仍 PENDING，策展身份不变，第二个现场实体仍在待并入。
Regression：`UnboundDraftItemReviewHttpAcceptanceTest` 与 `UnboundDraftCreateHttpAcceptanceTest` 全绿。
Refactor: 无结构改动（`alreadyBound` 抽取放在 Cycle C）。

### Cycle C — 同宿主多个失联对象时各现场实体绑各自目标
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundBindGateHttpAcceptanceTest.eachFieldEntityGetsItsOwnIdentityLostTargetOnTheSameHost`
```
UnboundBindGateHttpAcceptanceTest > eachFieldEntityGetsItsOwnIdentityLostTargetOnTheSameHost() FAILED
java.lang.AssertionError:
Expected: is not "ctr-404b0c95-cbb1-41b5-920c-b4f39d30b5cd"
     but: was "ctr-404b0c95-cbb1-41b5-920c-b4f39d30b5cd"
```
（第一份草案绑定已被接受之后，第二份草案的夹具仍提供同一个目标。）
Green command: 同上，exit 0。`findIdentityLostOnHost` 改为在该宿主的失联标里取**尚未被绑**的第一个（`curatedObjectId` 升序，确定性不变）；没有可用目标时 `MISSING_LABEL` 仍是 `UNBOUND_DRAFT_FIXTURE_UNAVAILABLE`、`UNKNOWN_OBJECT_ID` 仍回落到两条（新建 + `运行于`）。
Regression：01/02/03 三个未绑定验收类全绿。
Refactor: `requireTargetNotAlreadyBound` 与夹具共用 `alreadyBound`。

### Cycle D — 绑定不碰观测轨、不点亮升级链（判别性夹具）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundBindGateHttpAcceptanceTest.bindingLeavesTheObservedTrackAndTheUpgradeChainUntouched`
reuse/regression：首跑绿。既有断言由 `UnboundDraftItemReviewHttpAcceptanceTest.acceptingBindToIdentityLostLeavesPrimaryKeyAndDoesNotWriteObservedRunsOn` 与 `unlabeledReheartbeatAfterBindStaysConsumedAndIdentityLost` 覆盖；本圈只是把 Cycle A 那条不具判别力的声明补成可判别的夹具（Spec 轴审查指出：Cycle A 里策展宿主与观测宿主同为 A，泄漏的观测写入会被掩盖）。现在目标的观测宿主 B ≠ 候选所在宿主 A，若绑定泄漏观测 `运行于` 或点亮升级链，冲突会翻成待确认关闭。不另写生产。
Green command: 同上，exit 0。

### `/code-review`（票尾第二道闸门，非 refactor 步）
两轴并行、互不 rerank，结论都是通过：**Standards = no hard violations**（栈未漂、分层未破、构造器注入未动、事务边界仍在 service、无新路由且错误码沿用 `BusinessException`、Flyway 只增、Redis 未参与；TDD 记录与独立复跑一致）；**Spec = spec-faithful**（票内五条验收都在 HTTP 接缝上被钉住；未实现 04 / 05 任何行为；判据放宽到「失联之后未再命中」以及目标唯一性都出自 ADR-0012 B1 / ADR-0011 与 CONTEXT，而不是 Spec 自由裁量）。

按两轴共同点名的 in-scope 项当场处置（均不改验收语义）：

- `rememberBind` 的 `DataIntegrityViolationException` 原先把两条唯一约束混成一个错误码：现在按约束名判别，现场实体键冲突回 `UNBOUND_CANDIDATE_CONSUMED`（新建路径的竞态也不再谎报「目标已被绑定」），策展对象键冲突才回 `UNBOUND_BIND_TARGET_ALREADY_BOUND`。事务在约束失败后已中止，只能靠异常本身判别，故无法在 HTTP 接缝上钉住——确定性路径由 Cycle B 的前置检查覆盖。
- 错误码与文案不再两处重复：`targetAlreadyBound()` / `candidateConsumed()` 各一个工厂。
- `labelMatchedAfterIdentityLoss` 去掉与 `lost.getCuratedObjectId()` 恒等的冗余首参。
- `V19` 的 `DROP INDEX` 补 `IF EXISTS`（对齐 V10 / V11 先例），并写明**不做去重**：脏库上两条记忆哪条为真是人没确认过的并入，让唯一索引响亮失败比替人选一条更诚实。V19 尚未合入 main，本次编辑不算改历史脚本。

审查提出但**未**在本票动的（有意留下，均已记档）：`CuratedDraftService` 的 Divergent Change / 跨模块 mapper 依赖（票内明令不重构）；`findIdentityLostOnHost` 在草案发起路径上的 N+1；验收测试类之间的夹具助手重复（本仓既有风格）；`observedAt` 与 `markedAt` 恰好相等时判为「已再命中」的潜在边界（当前无可达路径，失败方向偏保守）。Spec 轴另点出一条 04 继承项：`curated_object_id` 唯一之后、在标签命中或 `absentObjectIds` 落地之前没有解绑路径，故事 37 与故事 50 的联动在 04 变成必做而非可选（已写进票 04 Comments 的第 3 条附近语境）。

### 票尾
`cd backend && ./gradlew cleanTest test` → exit 0（**22 个测试类 / 133 tests / 0 failures**；含竖切负面与 `ChangeCuratedDraft*`）。
