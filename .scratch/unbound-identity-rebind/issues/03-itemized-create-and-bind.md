# 03 — 逐条确认：新建写入对象；绑定只记对应关系

**What to build:** 已认证运维对未绑定草案按条接受或拒绝。接受新建即写入策展 Docker 容器（及若接受则写入第一条策展 `运行于`）。接受绑到已有：不改容器主键 / 不可变标签，不把名称或运行时 ID 写成可靠观测 `运行于`，只记住该现场实体已对应目标对象，并让它离开待并入列表。拒绝的条目不写。绑到仍健康标签命中的对象必须失败；同一草案上绑定与新建都接受必须失败。

**Blocked by:** 02 — 从不挂冲突的未绑定候选发起草案

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

确认单位是条目（合同逐条确认）。建底 `POST` 新建主机/容器仍可用；覆盖已有 `运行于` 仍拒绝。从候选并入禁止旁路 POST。

- [x] 只接受新建、拒绝 `运行于` → 策展出现该容器（不可变标签 = 现场标签），无该 `运行于`；未接受条目仍是草案
- [x] 先接受 `运行于`、尚未新建 → 失败，策展不变
- [x] 新建所用 `archops.object_id` 已被占用 → 接受失败
- [x] 接受绑到已有失联对象 X → X 的 `容器ID` / 不可变标签不变；「实际在哪」仍不得把弱线索当可靠 `运行于`；该 `runtimeId` 不再出现在待并入列表
- [x] 再心跳同一 `runtimeId` 仍缺标/错标 → 仍不待并入、仍身份失联、仍不承诺升级链
- [x] 绑定与新建都接受 → 第二次失败，不得把一个现场实体变成两个策展对象
- [x] 绑到仍标签命中、升级链有效的对象 → 失败
- [x] `MISSING_LABEL` 新建不是成功路径（无现场标签则无不可变 object id 可写）
- [x] `UNKNOWN_OBJECT_ID` 绑到已有允许，且不得把错标签写成 X 的新主键
- [x] 未认证不可审条；无冲突处理人要求
- [x] 建底插入第一条 `运行于` 仍成功；覆盖已有仍 `CURATED_RUNS_ON_EXISTS`
- [x] HTTP 可读条目已接受 / 已拒绝审计；无整单全接受、无操作计划、无策展对齐步骤

**Out of this ticket:** 标签命中后清失联并写观测（见 04）；选支闸门（见 05）；tracer 总套件（见 06）；UI。

## Comments

02 TDD-done 后再开。接受绑定后的「现场实体对应 X」是匹配状态，不是新合同词，也不是第四种冲突。不要做 04–07。

开工 prompt：[`docs/implement-unbound-identity-rebind-03-prompt.md`](../../../docs/implement-unbound-identity-rebind-03-prompt.md)。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：capability 票须 witnessed red；第一圈必须是已认证 POST 未绑定 CREATE 条目 accept 的诚实红灯，不要用未认证 401 或改策展处理人审条仍绿冒充。不要为装红灯删除 02 发起或改策展 accept 生产。

### Cycle A — 只接受新建、拒绝「运行于」
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.authenticatedOperatorAcceptsCreateAndRejectsRunsOnOnUnknownDraft`
Failure (witnessed): Status expected:<200> but was:<500>; `No static resource api/curated-drafts/{draftId}/items/{itemId}/accept` (NoResourceFoundException → INTERNAL_ERROR). Honest missing-handler red for authenticated CREATE accept.
Green command: same; exit 0 after `POST /api/curated-drafts/{draftId}/items/{itemId}/accept|reject` + CREATE writes curated container + reject does not insert 运行于.
Refactor: import cleanup; controller/service javadoc on the unbound review path.
Commit: `460d63b` feat(unbound): accept CREATE without writing curated 运行于

### Cycle B — 先接受「运行于」、尚未新建 → 失败，策展不变
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.acceptingRunsOnBeforeCreateFailsAndLeavesCuratedUnchanged`
Failure (witnessed): JSON path `$.code` expected `UNBOUND_RUNS_ON_BEFORE_CREATE` but was `UNBOUND_ITEM_KIND_UNSUPPORTED`.
Green command: same; exit 0. Guard refuses RUNS_ON insert until CREATE is ACCEPTED with a subject; items stay PENDING; object id still free for bootstrap POST.
Refactor: extract `postUnboundItem` helper; first 运行于 insert reuses bootstrap `confirmRunsOn` after CREATE.
Commit: `82f565d` feat(unbound): refuse 运行于 insert before CREATE

### Cycle C — 新建所用 archops.object_id 已被占用 → 接受失败
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.acceptingCreateFailsWhenImmutableObjectIdAlreadyExists`
First run: fixture error — occupying `u03c-never` *before* the unknown snapshot label-matches ingest, so no unbound candidate. After occupying *after* the candidate exists (same object id, different runtime), first-run green.
reuse/regression: Cycle A CREATE accept delegates to `CuratedTruthService.createContainer` (`CURATED_OBJECT_ID_EXISTS`); also `CuratedTruthHttpAcceptanceTest.duplicateImmutableObjectIdIsRejected`.
Green command: same; exit 0. CREATE stays PENDING, subjectId JSON null, bootstrap POST same objectId still `CURATED_OBJECT_ID_EXISTS`.
Refactor: 无结构改动（夹具顺序对齐 ingest 匹配）。
Commit: `30ca78f` test(unbound): occupied object id rejects CREATE accept

### Cycle D — 接受绑到已有失联对象 X
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.acceptingBindToIdentityLostLeavesPrimaryKeyAndDoesNotWriteObservedRunsOn`
Failure (witnessed): Status expected:<200> but was:<400>; `UNBOUND_ITEM_KIND_UNSUPPORTED` for `BIND_UNBOUND_TO_EXISTING`.
Green command: same; exit 0 after V18 `unbound_bind_memory`, BIND accept remembers (sourceHostId, runtimeId)→X, default GET 待并入 filters that pair, X id/objectId and 策展「运行于」unchanged, 「实际在哪」 still IDENTITY_LOST, by-merge-key still CONFLICT_NOT_FOUND.
Refactor: bind memory written on CREATE as well so later consume is one matching-state table.
Commit: `868739d` feat(unbound): remember bind without writing observed 运行于

### Cycle E — 再心跳同一 runtimeId 仍缺标 → 仍不待并入、仍失联、仍不升级
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.unlabeledReheartbeatAfterBindStaysConsumedAndIdentityLost`
reuse/regression: Cycle D bind memory + listUnbound filter; 01 unlabeled upsert does not clear identity-lost or promise by-merge-key. First-run green. Refreshing observedAt does not VOID the OPEN unbound draft (04 defines hit-void).
Green command: same; exit 0.
Refactor: 无结构改动
Commit: `8073d0f` test(unbound): unlabeled reheartbeat stays off 待并入

### Cycle F — 绑定与新建都接受 → 第二次失败
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.acceptingCreateAfterBindFailsAsCandidateConsumed`
Failure (witnessed): JSON path `$.code` expected `UNBOUND_CANDIDATE_CONSUMED` but was `UNBOUND_CREATE_IMMUTABLE_ID_MISSING`.
Green command: same; exit 0. BIND/CREATE accept checks bind memory first; CREATE stays PENDING.
Refactor: consume check only on BIND/CREATE so CURATED_RUNS_ON_INSERT after CREATE can still write first 运行于.
Commit: `ad61239` feat(unbound): reject a second 并入 of the same field entity

### Cycle G — 绑到仍标签命中、升级链有效的对象 → 失败
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.bindingToLabelMatchedPresentTargetIsRejected`
Failure (witnessed): Status expected:<400> but was:<200> (BIND accepted because identity-lost mark still exists after labeled heartbeat).
Green command: same; exit 0. BIND refuses when observed 运行于 is PRESENT. Heartbeat matched proves label hit; identity-lost GET still 200 (04 clears the mark). Ask DTO stays IDENTITY_LOST while the mark remains — 01 projection; gate uses observed PRESENT not mark disappearance.
Refactor: 无结构改动
Commit: `fd3d1e5` feat(unbound): refuse BIND onto a label-matched object

### Cycle H — MISSING_LABEL 新建不是成功路径
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.missingLabelCreateAcceptIsNotASuccessPath`
reuse/regression: Cycle A `writeAcceptedCreateContainer` already rejects blank `immutableObjectId` as `UNBOUND_CREATE_IMMUTABLE_ID_MISSING`. First-run green. Reject CREATE → REJECTED, still no new object.
Green command: same; exit 0.
Refactor: 无结构改动
Commit: `e218921` test(unbound): MISSING_LABEL CREATE cannot mint an object id

### Cycle I — UNKNOWN 绑到已有：允许，且不得把错标签写成 X 的主键
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.unknownBindToExistingDoesNotRewriteWrongLabelAsPrimaryKey`
Failure (witnessed): `missing item kind BIND_UNBOUND_TO_EXISTING` (UNKNOWN fixture had only CREATE+RUNS_ON).
Green command: same; exit 0. UNKNOWN+same-host 身份失联 adds BIND; accept BIND does not rewrite X.objectId to the wrong label; CREATE-then-BIND on a second runtime is `UNBOUND_CANDIDATE_CONSUMED`.
Refactor: BIND prepended only when an identity-lost object exists on the candidate host; no-lost UNKNOWN stays 2 items.
Commit: `5c7e128` feat(unbound): offer BIND on UNKNOWN when the host has 失联

### Cycle J — 未认证不可审条；一般与高级均可；无冲突处理人
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.unauthenticatedItemAcceptIsRejected --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.seniorOperatorCanAcceptCreateOnUnboundDraft`
reuse/regression: unauthenticated 401 `AUTH_REQUIRED` from existing `/api/**` SecurityConfig; senior accept CREATE 200 reuses Cycle A (no role gate, `@PreAuthorize("isAuthenticated()")` only). First-run green. No 未绑定处理人; change-curated handler gate untouched.
Green command: same; exit 0.
Refactor: 无结构改动
Commit: `db7b049` test(unbound): item review stays authenticated for 一般 and 高级

### Cycle K — 建底插入第一条「运行于」仍成功；覆盖已有仍 CURATED_RUNS_ON_EXISTS
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.bootstrapFirstRunsOnStillInsertsAndOverwriteStillRejected`
reuse/regression: `CuratedTruthHttpAcceptanceTest.bootstrapPostRejectsOverwriteToSameHost` / `createHostsContainerConfirmRunsOnAndAskShouldWhere`. First-run green. Ticket 03 did not disable bootstrap insert or overwrite reject.
Green command: same; exit 0.
Refactor: 无结构改动
Commit: `2fa7600` test(unbound): bootstrap 运行于 insert and overwrite still hold

### Cycle L — HTTP 可读条目已接受/已拒绝审计；无整单全接受；无操作计划
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.itemReviewEventsAreReadableAndWholeDraftAcceptDoesNotExist`
Failure (witnessed): JSON path `$.data[*].eventType` expected collection containing `DRAFT_ITEM_ACCEPTED` but was only `DRAFT_CREATED`.
Green command: same; exit 0. Unbound accept/reject append `curated_draft_event` (not conflict events). Whole-draft POST `/api/curated-drafts/{id}/accept` has no handler. No dummy conflict/plan from this review (`CONFLICT_NOT_FOUND` on a non-existent conflict id).
Refactor: shared unbound item audit detail helper.
Commit: `6f2cdbf` feat(unbound): audit item accept and reject on draft events

### Cycle M / code-review — first 运行于 after CREATE; Standards + Spec
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftItemReviewHttpAcceptanceTest.acceptingRunsOnAfterCreateWritesFirstCuratedRunsOn`
reuse/regression: Cycle B `writeAcceptedFirstRunsOn` (confirmRunsOn after CREATE). First-run green. Spec review noted tracer-adjacent “accept CREATE then 运行于” was unproven on HTTP.
Green command: same; `./gradlew test` exit 0 (includes 01, 02, ChangeCuratedDraft*).
Refactor: 无结构改动
/code-review (merge-base `origin/main` `ddcb1d7`):
- Standards: no hard stack/layering breach. Judgement smells (Divergent Change / Feature Envy on `CuratedDraftService` observed reads; duplicated review records) deferred — do not expand into 04–07.
- Spec: stories 19–20, 25–28, 31–36, 44, 51–53, 59 item audit, tracer 5–7 item review, Neg 3/5–7/11 met. No 04 consume/void, no 05 gates, no UI. PRESENT healthy-gate is ticket G (labeled rematch); leftover PRESENT after 失联 is 01 fact until 04 clears the mark — not expanded here.

Frontier → 04. Do not implement 04–07.

Handoff docs commit: (this slice)

