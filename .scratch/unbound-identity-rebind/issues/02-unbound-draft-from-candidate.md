# 02 — 从不挂冲突的未绑定候选发起草案

**What to build:** 已认证运维对**一个**待并入未绑定观测候选发起草案。草案不挂冲突、不复用已接受冲突处理人门禁、不发明未绑定处理人。规则夹具给出至少两条独立可确认条目；发起瞬间不写策展真相、不创建操作计划。同一现场实体最多一份开放草案。

**Blocked by:** 01 — 控制面推断身份失联；未绑定按现场实体 upsert；规范问法

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

从改策展往上长：今日草案只挂在冲突上、条目只有 `运行于` 目标变更。本票新开「从候选来」的草案读取/发起；条目仍全部 PENDING。不要把未绑定伪装成冲突。

- [x] `UNKNOWN_OBJECT_ID` 候选发草案 → ≥2 条：新建策展容器（不可变标签 = 现场标签）+ 策展 `运行于` 候选所在宿主；接受前 GET 策展没有该新对象，「应该在哪」不变
- [x] 身份失联 + `MISSING_LABEL`（或错标）候选发草案 → ≥2 条互斥：绑到已有失联对象 vs 新建
- [x] 草案不出现在冲突下的改理想草案 API 上；无活跃操作计划因本发起而产生
- [x] 未认证调用发起被拒绝；已认证一般角色与高级角色均可发起
- [x] 同一 `sourceHostId` + `runtimeId` 第二份开放草案被拒绝
- [x] HTTP 可读开放草案及其 PENDING 条目；无「整单全接受」

**Out of this ticket:** 条目接受/拒绝写入、绑定记忆、标签命中消费、失联选支闸门、UI、把草案挂到冲突上、LLM 起草。

## Comments

01 TDD-done 后再开。不要做 03–07。不要复用 `POST .../branch-selection` 来创建本票草案。

开工 prompt：[`docs/implement-unbound-identity-rebind-02-prompt.md`](../../../docs/implement-unbound-identity-rebind-02-prompt.md)。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：capability 票须 witnessed red；第一圈必须是已认证 POST 创建草案的诚实红灯，不要用未认证 401 或改策展选支仍绿冒充。不要为装红灯删除改策展 `conflict_id` 生产或插入 dummy 冲突。

### Cycle A — general opens OPEN draft from UNKNOWN_OBJECT_ID candidate
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundDraftCreateHttpAcceptanceTest.generalOperatorOpensDraftFromUnknownObjectIdCandidate`
Failure (witnessed): Status expected 200 was 500; No static resource `api/observed/unbound-candidates/.../drafts` (NoResourceFoundException → INTERNAL_ERROR). Honest missing-handler red.
Green: same; exit 0 after V17 + `createFromUnboundCandidate` + `POST /api/observed/unbound-candidates/{id}/drafts`.
Refactor: extended draft response with origin/candidate/sourceHost/runtime + item payload; null-safe item lookups.
Commit: `574f4d5`

### Cycle B — GET /api/curated-drafts/{draftId} reads OPEN unbound draft
Red: Status expected 200 was 500 (NoResourceFoundException for `/api/curated-drafts/{id}`).
Green: `GET /api/curated-drafts/{draftId}` → get-by-id; same OPEN/origin/items as create.
Refactor: none beyond endpoint wiring.
Commit: `59c429c`

### Cycle C — opening draft does not write curated or consume candidate
Red: first run PathNotFound on flat `hostId`; GET runs-on returns nested target. After aligning assertions, green without new production — reuse of Cycle A create.
Green: should-where unchanged; bootstrap objectId still free; unbound list still has runtime.
Refactor: none.
Commit: `13e8e0f`

### Cycle D — unauthenticated open rejected
Red: reuse/regression — security already requires auth on `/api/**`; first run green with `AUTH_REQUIRED`.
Green: same.
Refactor: none.
Commit: `a6d1dbb` (batch D–K)

### Cycle E — senior can open
Red: reuse of Cycle A (no role gate on POST drafts); first run green with senior demo user as createdBy.
Green: same.
Refactor: none.
Commit: `a6d1dbb`

### Cycle F — identity-lost + MISSING_LABEL → BIND vs CREATE
Red: reuse of Cycle A fixture builder (`MISSING_LABEL` branch already present); first run green with `BIND_UNBOUND_TO_EXISTING` + `CREATE_CONTAINER_FROM_UNBOUND` pending.
Green: same; identity-lost and should-where unchanged.
Refactor: none.
Commit: `a6d1dbb`

### Cycle G — second OPEN → UNBOUND_DRAFT_ALREADY_OPEN
Red: reuse of unique open-unbound index + service guard from Cycle A; first run green.
Green: same; first draft remains OPEN with 2 items.
Refactor: none.
Commit: `a6d1dbb`

### Cycle H/I — unbound draft invisible on conflict curated-draft APIs; no operation plan
Red: reuse — conflictId null + conflict-scoped GET requires matching conflictId; `PLAN_NOT_FOUND` for active plan. First run green.
Green: same.
Refactor: none.
Commit: `a6d1dbb`

### Cycle J — DRAFT_CREATED on draft events API
Red: `GET /api/curated-drafts/{id}/events` returned 500/no handler.
Green: `curated_draft_event` write on create + GET events; detail hint contains 草案已创建; origin `UNBOUND_CANDIDATE`.
Refactor: mirrored conflict-event append/list shape.
Commit: `a6d1dbb`

### Cycle K — unknown candidateId
Red: reuse of `UNBOUND_CANDIDATE_NOT_FOUND` guard from Cycle A create; first run green.
Green: same.
Refactor: none.
Commit: `a6d1dbb`

### Cycle L / code-review — mutex + fixture error codes
Red: n/a (review hardening). Spec mutex wording = `sourceHostId`+`runtimeId`; V16 already unique-candidate on that pair — added open-draft unique on host+runtime and service check. Fixture precondition failures now `UNBOUND_DRAFT_FIXTURE_UNAVAILABLE` (not `UNBOUND_CANDIDATE_NOT_FOUND`). Soft smells (Feature Envy / Divergent Change on `CuratedDraftService`) deferred — do not expand into 03–07.
Green: `./gradlew test --tests com.archops.observed.UnboundDraftCreateHttpAcceptanceTest` and full `./gradlew test` exit 0.
Commit: `4f9b965`

/code-review（merge-base `origin/main`）：Standards 无硬栈违规；Spec 开放草案互斥按 sourceHostId+runtimeId 补强；夹具失败码与缺候选分离。气味按 judgement 未扩到 03–07。

Frontier 已指向 03。不要实现 03–07。
