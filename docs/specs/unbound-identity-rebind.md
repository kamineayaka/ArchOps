# Spec: 未绑定观测候选 / 身份失联重绑

**Status**: spec published；工单 01–07 已拆，审计后补 08 / 09（**01–07 + 08 已闭合**；**frontier = 09**）  
**Basis**: ADR-0039 领域合同、`CONTEXT.md`、ADR-0043 技术栈、ADR-0006（草案逐条写入）、ADR-0011（对象身份）、ADR-0012（先策展后补标；缺标则身份失联而非同名接龙）、ADR-0015（v1 Must 含未绑定与身份失联）、ADR-0019（待确认关闭；本刀**不含** Y2）、ADR-0038（改策展须草案人审；本刀不在绑定后出操作计划）  
**Predecessor**: [`docs/specs/vertical-slice-mvp.md`](vertical-slice-mvp.md)（01–13 已闭合；未打标仅为负面：不承诺升级链）与 [`docs/specs/change-curated-draft.md`](change-curated-draft.md)（01–06 TDD-done；**本刀不重做**改理想 `运行于` 写入段，不加 07）。从已实现控制面往上长，不从空骨架重写，不复活旧域包。  
**Testing seams (confirmed)**: **唯一验收主接缝 = 控制面公开 HTTP API**（含 Agent 心跳/快照 ingest）。驱动可以是 Gradle/`MockMvc` 或 Compose 上 `bootRun`+`curl`，仍是同一条接缝。前端最小 UI 手工/冒烟，不进自动化主接缝。本刀无 SSH 计划，不引入 SSH fake 或 Playwright 作为接缝或替身。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**；下文 HTTP tracer 是循环顺序，不是一次写完全部测试再实现。

**Confirmed scope pins**

1. 本刀实现已冻结合同里的**未绑定观测候选**、**身份失联**、**草案**、**逐条确认**。不改 `CONTEXT.md`，不重开 ADR-0039 / 0043，**不需要新 ADR**。
2. 对象仅 **Docker 容器** + `运行于`（物理主机只作宿主）。不重绑主机 IP / SSH host key。
3. 并入必须**不挂冲突**的草案逐条确认。绑定不写可靠观测 `运行于`；补标在现场，本刀不出操作计划。
4. 主接缝为 HTTP API only（已确认）。

---

## Problem Statement

竖切只把「未打标容器」做成负面：匹配失败不承诺冲突升级链，HTTP 可列出未绑定与身份失联，但人无法经草案把现场实体**绑定**到已有策展对象或**新建**策展对象。身份失联今天依赖 Agent 自觉填写 `identityLostObjectIds`，缺标不会自动给已有对象打失联；标签稍后命中也不会清失联、不会消费候选。改策展刀只处理已命中对象的 `运行于` 改理想，且草案挂在冲突上。没有这一刀，运维无法完成合同里的「匹配失败 → 未绑定；要并入必须经人（草案）绑定或新建；禁止同名静默合并；先策展后补标」。

---

## Solution

在已实现的竖切与改策展底上，交付 Docker 容器的未绑定 / 身份失联闭环（到标签命中后恢复升级链为止）：

Host Agent 心跳快照仍按 `archops.object_id` 匹配。缺标或标签对不上既有对象 → **未绑定观测候选**（按现场实体 upsert，不是每拍无限插入）。若上报主机是该容器的策展 `运行于` 宿主或当前可用观测宿主，且本快照未标签命中、也未进入 `absentObjectIds` → 原对象 **身份失联**（Agent 显式声明仍有效，且受同一主机范围约束）。未绑定与失联都**不是**冲突，也**不是**观测空洞。

已认证运维从**待并入**列表选一个候选，发起**不挂冲突**的草案；规则夹具给出 ≥2 条独立条目，逐条接受即写入、拒绝则不写。`UNKNOWN_OBJECT_ID`：新建策展容器（不可变标签 = 现场标签）+ 可选策展 `运行于` 候选所在宿主。失联绑定：绑到已有失联对象 vs 新建，互斥。绑定不改 `容器ID` / 不可变标签，不把名称或运行时 ID 写成可靠观测 `运行于`。接受绑定后记住该现场实体已对应目标对象，补标命中前不再当作待并入候选。人在现场补标后，下一次心跳标签命中 → 写观测 `运行于`、清除失联、消费候选与绑定记忆，其后走既有比对 / 升级链。

失联期间：规范问法实际在哪不得用旧宿主；已有冲突保留为失联态（不新增冲突状态、不 `SUSPENDED`）；待确认关闭退回开放；禁止对该合并键选支 / 审计划 / 执行；活跃操作计划受阻即停作废；该键上未完成改理想草案作废；诊断不得再给以旧实际为落点的修实际 / 改理想分叉。

---

## User Stories

1. As a Host Agent, I want a snapshot container without `archops.object_id` to become a 未绑定观测候选 with reason `MISSING_LABEL` and `upgradeChainPromised=false`, so that unlabeled field entities are visible without pretending they are a curated object.
2. As a Host Agent, I want a snapshot container whose `archops.object_id` matches no curated Docker 容器 to become a 未绑定观测候选 with reason `UNKNOWN_OBJECT_ID` and `upgradeChainPromised=false`, so that unknown labels are not silently merged.
3. As the control plane, I want 未绑定观测候选 upserted by `sourceHostId` + `runtimeId` (refresh `observedAt`, name, labels, reason), so that each heartbeat does not create an unbounded new row for the same field entity.
4. As an authenticated 运维, I want `GET` 未绑定观测候选 to include labels (at least `archops.object_id` when present), `runtimeId`, `name`, `reason`, `sourceHostId`, and `upgradeChainPromised=false`, so that I can decide 新建 vs 绑定 without reading the database.
5. As an authenticated 运维, I want the default 未绑定 list to show only **待并入** candidates, so that already-bound-pending-补标 entities are not offered as 新建 fodder.
6. As the control plane, when a snapshot is ingested from the host that is the container’s curated `运行于` target **or** its currently usable observed host (if never observed, curated host only), and that snapshot does not label-match the curated Docker 容器 and does not list it in `absentObjectIds`, I want the object marked 身份失联, so that we do not wait for the Agent to remember `identityLostObjectIds`.
7. As the control plane, I want Agent `identityLostObjectIds` to still mark 身份失联 when the reporting host is in the same host scope as story 6, so that explicit Agent declaration remains valid.
8. As the control plane, I must not mark container X 身份失联 from a snapshot whose `hostId` is neither X’s curated `运行于` host nor X’s currently usable observed host, so that Host-B cannot identity-lose objects that live on Host-A.
9. As the control plane, when X is listed in `absentObjectIds`, I want 观测消失 (`ABSENT`, usable value) rather than 身份失联, so that explicit non-existence is not confused with match failure.
10. As a 运维 asking 规范问法「应该在哪」 for an 身份失联 container, I still want the 策展 `运行于` answer, so that the ideal track remains the author’s purpose.
11. As a 运维 asking 规范问法「实际在哪」 for an 身份失联 container, I want the answer to be 身份失联, with 策展同屏 (P2), and I must not be given the pre-loss host as 实际, so that stale `运行于` cannot pretend to be current actual.
12. As the control plane, I must not encode 身份失联 as 观测空洞 (`HOLLOW` / `SUSPENDED` / restore-channel-only diagnosis), so that a fresh heartbeat that cannot match is not treated as a dead observation channel.
13. As the control plane, I must not encode 身份失联 as 观测消失, so that “cannot match” is not “asserted absent”.
14. As any viewer, I want an existing 冲突 on that `运行于` merge key to remain (合同：原冲突可保留为失联态) without a new conflict status enum: `OPEN` stays `OPEN`; `PENDING_CLOSE` exits to `OPEN`, so that we do not confirm equality we can no longer see.
15. As any viewer, I want the 冲突 GET to show that the subject is 身份失联 (alongside existing tracks), so that I do not have to guess from a missing upgrade chain.
16. As the control plane, I must not open a 冲突 from 未绑定 or 身份失联 themselves, and must not continue the upgrade chain by display name or runtime ID, so that 匹配失败 cannot corrupt merge keys.
17. As a QA engineer, I want `GET /api/conflicts/by-merge-key` for an unlabeled same-name container to keep the vertical-slice negative (no promised upgrade chain) until a later label match, so that this knife does not regress ticket 13.
18. As an authenticated 运维, I want to start a 草案 from exactly one 待并入未绑定候选 without attaching it to a 冲突, so that 绑定/新建 is not forced into 冲突处理人协作.
19. As an unauthenticated caller, I want that draft-create and item review to be denied, so that Agent ingest remains the unauthenticated write path and 策展 writes stay operator-gated.
20. As any authenticated 运维 (高级 or 一般), I want to initiate and accept/reject items on that 草案, so that we do not invent a 未绑定处理人 and do not reuse 已接受冲突处理人 for a non-conflict write.
21. As the control plane, I want at most one OPEN 草案 per 未绑定候选 (`sourceHostId` + `runtimeId`), so that two operators cannot review competing 并入 proposals for the same field entity.
22. As an authenticated 运维 opening a second draft for the same candidate, I want a business error, so that the mutex is visible on HTTP.
23. As an authenticated 运维, I want the generated 草案 to contain **at least two independently confirmable items**, so that 逐条确认 is distinguishable from all-or-nothing confirm.
24. As the control plane for an `UNKNOWN_OBJECT_ID` candidate, I want the rule fixture to emit (1) 新建策展 Docker 容器 with immutable label = the field `archops.object_id` and (2) 策展 `运行于` the candidate’s source host, so that create vs relation can be accepted separately.
25. As an authenticated 运维, I want to accept 新建 and reject `运行于`, so that a new curated object can exist without a curated location yet.
26. As an authenticated 运维, I want accepting `运行于` before 新建 to fail, so that we do not write a relation to an object that does not exist.
27. As the control plane, after 新建 is accepted, I want a curated Docker 容器 to exist with that immutable object id, so that the next labeled snapshot can match.
28. As the control plane, I want a duplicate 新建 of an already-used `archops.object_id` to fail, so that identity uniqueness holds.
29. As the control plane, when 新建 and `运行于` are both accepted and later observation matches that location, I must **not** invent a 冲突 or enter 待确认关闭 just because the two tracks agree, so that 待确认关闭 stays on the 冲突 lifecycle.
30. As the control plane for a `MISSING_LABEL` (or wrong-label) candidate plus 身份失联 object X, I want the rule fixture to emit mutually exclusive items (1) 绑到已有 X and (2) 新建, so that the operator cannot turn one field entity into two curated objects by accepting both.
31. As an authenticated 运维, when I accept both 绑定 and 新建 on that draft, I want the second accept to fail and 策展 identity unchanged for the failed item, so that dual-accept is impossible.
32. As an authenticated 运维, I must not 新建 from `MISSING_LABEL` as a Must path (no field label to write as immutable id); unlabeled entities are bound to an existing object or later 补标 into match / `UNKNOWN_OBJECT_ID` 新建, so that we do not mint a second identity scheme.
33. As an authenticated 运维, I want `UNKNOWN_OBJECT_ID` 绑到已有 X to mean “this mislabeled entity is X” without writing the wrong label into X’s immutable id, so that 容器ID stays stable (ADR-0011 / ADR-0012).
34. As the control plane, I want 绑到已有 to succeed only when the target is 身份失联 (including 先策展后补标 objects that have never label-matched and are marked 失联 by story 6), so that a second field entity cannot be merged into a still-healthy matched object.
35. As the control plane, when I accept 绑定, I must not write a reliable observed `运行于` from name or runtime ID, and must not promise the upgrade chain, so that 弱线索 cannot become 实际.
36. As the control plane, after 绑定 is accepted, I want to remember that this field entity (`sourceHostId` + `runtimeId`) corresponds to curated object X until a label match on X, so that the next unlabeled/wrong-label heartbeat does not resurrect a 待并入 candidate for 新建.
37. As the control plane, if `runtimeId` changes because the container was recreated before 补标, I want a **new** 未绑定观测候选, so that we still forbid silent merge by display name.
38. As an authenticated 运维, I do not need a Must HTTP for “already bound, waiting for field 补标” audit listing, so that this knife stays on 待并入 + 失联 + 命中收尾.
39. As a 运维 in the field, I want to 补标 `archops.object_id=<容器ID>` outside this slice’s executor, so that 补标 remains a physical-world act, not an 操作计划 / SSH step of this knife.
40. As a Host Agent, after a correct label appears, I want ingest to match the curated Docker 容器, write observed `运行于`, clear 身份失联, consume the 未绑定候选 and any bind memory, and void related open 未绑定草案, so that 先策展后补标 (ADR-0012 L2) completes.
41. As the control plane, after that label match, I want the existing compare/upgrade engine to run, so that location deviation becomes a normal 冲突升级链 rather than 未绑定.
42. As the control plane, when a label match happens while an unbound 草案 is still open for that candidate or target, I want the draft voided and further item accept denied, so that we do not 并入 a proposal the field already resolved.
43. As the control plane, a heartbeat that only refreshes the same `sourceHostId` + `runtimeId` must not void an open unbound 草案, so that waiting for the operator to review is not treated as 冲突升级.
44. As an authenticated 运维, after a candidate is consumed by an accepted item, I want remaining pending items on that candidate to be un-acceptable, so that consumed entities cannot be 新建’d as well.
45. As an 已接受的冲突处理人, when the merge-key subject is 身份失联, I want `FIX_ACTUAL` and `CHANGE_CURATED` branch selection to fail, so that we do not act on an unreliable 实际.
46. As the control plane, when 身份失联 lands on a merge key with an active 操作计划, I want that plan voided immediately (受阻即停), so that execution cannot continue against a stale host.
47. As the control plane, when 身份失联 lands on a merge key with an open 改理想草案, I want that 草案 voided (same idea as change-curated 05: the assumed fact is no longer reliable), without applying heartbeat-timeout 空洞 rules to **unbound** drafts.
48. As any viewer, I want 诊断 on an 身份失联 冲突 to omit `FIX_ACTUAL` / `CHANGE_CURATED` forks that would use the old actual as a unique site, and to read-only explain 身份失联 (handle via unbound 草案 / field 补标), so that 规范问法 never issues a single-track fix.
49. As the control plane, I must not run the pure-空洞 “restore observation channel” fork set solely because of 身份失联, so that a live Agent is not told the channel is dead.
50. As the control plane, if `absentObjectIds` arrives after a pending 绑定 memory for X, I want 观测消失 to take over: X is no longer 身份失联; bind memory for X is released; if the field entity is still unlabeled it returns to 待并入未绑定, so that “X does not exist” is not stored as “mystery is X”.
51. As a 运维 using bootstrap APIs, I still want `POST /api/curated/hosts` and `POST /api/curated/containers` to create objects, so that 竖切建底 remains.
52. As the control plane, I still want `POST /api/curated/facts/runs-on` to insert the **first** `运行于` and to reject overwrite of an existing one (`CURATED_RUNS_ON_EXISTS`), so that change-curated 01 does not regress.
53. As the control plane, I must reject any non-draft HTTP that binds or creates from a 未绑定观测候选, so that 并入 cannot bypass 逐条确认.
54. As a 运维, I want 未绑定候选 itself not to be asked 「应该在哪」, because it is not a first-class 策展 object until 新建 or 绑定+补标命中.
55. As a QA engineer, I want the happy path and negatives exercisable solely via HTTP API + Agent ingest, so that CI can accept this slice without a browser or SSH.
56. As a demo operator, I want a thin React+Ant UI (last ticket) to list 待并入未绑定 / 身份失联 and accept/reject draft items, so that the story is human-demonstrable even though UI is not the automated seam.
57. As a developer/agent, I want drafts, bind memory, unbound candidates, and 身份失联 marks stored in PostgreSQL (Flyway additive after V15), with Redis unused as relationship-truth SSOT, so that ADR-0043 holds.
58. As a developer/agent, I want rule-templated 草案 generation (no LLM main path), so that model outage cannot block 绑定/新建 review.
59. As any viewer, I want minimal audit events for unbound draft created / item accepted / item rejected / draft voided, readable via HTTP, so that 处理归档 has a seam even without 自我迭代.
60. As a platform operator, I want Compose delivery and temp auth headers unchanged, so that this slice does not reopen JWT, Neo4j, Vue, Maven, JPA, LangChain, Y2, 网络可达, K8s/数据库对象, or 自我迭代.

---

## Implementation Decisions

### Starting posture

- Grow from implemented vertical-slice + change-curated (Flyway through **V15**, modules `curated` / `observed` / `conflict` / `plan` / `user` / `agent` / `common`). Do not revive deleted packages. Do not rewrite 01–13 or change-curated 01–06 except where this Spec adds 身份失联 gates on an existing merge key.
- Current observed tables already store `unbound_observation_candidate` and `identity_lost_mark`, but ingest always inserts unbound rows, 失联 is Agent-declared only, GET unbound omits labels, and there is no bind/create draft path.

### Stack (ADR-0043) — unchanged

- Control plane: Java 21, Spring Boot 3, Gradle, MyBatis-Plus, Flyway, PostgreSQL SSOT.
- Redis: queue/lock/session/cache only; never 关系真相 SSOT.
- Frontend: React + TypeScript + Ant Design; thin screens only, last ticket.
- No Neo4j, no JPA-as-base, no Vue, no Maven, no LangChain backbone.
- Auth: existing temporary identity header + 高级/一般 roles. Unbound 草案: any authenticated 运维. Agent ingest: still no operator user header.
- AI egress: unused as main path for this knife’s 草案.

### Domain objects in play

- Kinds: 物理主机 (host of Agent and `运行于` target only), Docker 容器. Relation: `运行于`.
- No K8s/database objects, no 网络可达, no 属于, no host clue rebind.
- Glossary only: 未绑定观测候选、身份失联、草案、逐条确认、策展真相、观测真相、冲突、观测空洞、观测消失、规范问法、操作计划、冲突升级、待确认关闭. Do not invent 「待确认策展」「以现场为准」「未绑定处理人」「已确认待补标」as CONTEXT terms. “Accepted bind until label match” is matching state after 逐条确认, not a new glossary entry.

### Ingest matching (extend existing heartbeat)

- Keep label match → observed `运行于` PRESENT; unknown / missing label → 未绑定; `absentObjectIds` → ABSENT.
- **Upsert** 未绑定 by (`sourceHostId`, `runtimeId`). Fixtures Must send `runtimeId`.
- **Infer 身份失联** when the reporting host is in scope (story 6) and the curated container is not label-matched and not in `absentObjectIds`. Do not infer 失联 from other hosts’ snapshots.
- `identityLostObjectIds` still upserts the mark, but Must also respect host scope (story 7–8).
- Label match on X Must clear X’s 身份失联, consume matching 待并入 / bind memory for that object or field entity, then run existing compare.
- Bind memory after accepted 绑定: (`sourceHostId`, `runtimeId`) → curated object X, until label match on X. Same entity must not reappear on the default 待并入 list. Still no reliable observed `运行于` from that memory.
- `absentObjectIds` for X after bind memory: 观测消失 wins; clear 失联; release bind memory for X; unlabeled field entity becomes 待并入 again.

### 规范问法 and 冲突 projection

- `GET` 「实际在哪」 for 身份失联: `identityLost=true` (or equivalent on the ask DTO); `observedValue.hostId` must not be returned as 实际; `observedValue.availability` Must not be `PRESENT` and Must not be set to `HOLLOW` or `ABSENT` solely because of 失联. Suggested: ask DTO uses `identityLost=true` plus non-PRESENT availability local to this read (e.g. `IDENTITY_LOST` **only on the ask DTO**, never as `observed_fact.availability` and never as `ConflictStatus`).
- `GET` 「应该在哪」 unchanged (策展).
- 冲突 status enum unchanged: `OPEN` / `PENDING_CLOSE` / `CLOSED` / `SUSPENDED`. Add a readable 失联 flag on 冲突 GET. `PENDING_CLOSE` + 失联 → `OPEN`.
- 未绑定 is not a 冲突. Same-name unlabeled snapshot still must not promise `by-merge-key` upgrade chain.

### 未绑定草案 (not hung on 冲突)

- Do **not** reuse `POST /api/conflicts/{id}/branch-selection` or `/api/conflicts/{id}/curated-drafts/...` for this knife’s 并入. Those remain the change-curated path.
- HTTP default (implementers may bikeshed names, behavior Must hold):
  - `POST /api/observed/unbound-candidates/{candidateId}/drafts` → create OPEN 草案 with ≥2 items for that candidate.
  - `GET /api/curated-drafts/{draftId}` (including VOIDED).
  - `POST /api/curated-drafts/{draftId}/items/{itemId}/accept|reject`.
  - `GET /api/observed/unbound-candidates` default = 待并入 only; include labels.
- Item kinds (additive Flyway; do not edit historic `RUNS_ON_TARGET_CHANGE` check in place — new version):
  - `CREATE_CONTAINER_FROM_UNBOUND`
  - `BIND_UNBOUND_TO_EXISTING`
  - `CURATED_RUNS_ON_INSERT` (first curated `运行于` to candidate `sourceHostId`; only valid after 新建 of that object in the same draft, or once the new object exists).
- `UNKNOWN_OBJECT_ID` fixture: items `CREATE_CONTAINER_FROM_UNBOUND` + `CURATED_RUNS_ON_INSERT`.
- `MISSING_LABEL` / 失联 fixture: items `BIND_UNBOUND_TO_EXISTING` + `CREATE_CONTAINER_FROM_UNBOUND` (mutex).
- Accept 绑定: persist bind memory; consume candidate from 待并入; do not change immutable object id; do not write observed `运行于`.
- Accept 新建: insert curated Docker 容器 (name from candidate; immutable id from field label). `MISSING_LABEL` 新建 is not a Must (reject or omit as a successful path).
- Dual-accept bind+create: fail the second write.
- One OPEN draft per (`sourceHostId`, `runtimeId`).
- Persistence: either a new draft table without required `conflict_id`, or generalize the existing draft row so `conflict_id` is nullable and origin is `UNBOUND_CANDIDATE`. Do not attach dummy 冲突 rows.

### 失联 vs 改理想 / 修实际 pipeline

- While subject is 身份失联: `POST .../branch-selection` for `FIX_ACTUAL_TO_CURATED` and `CHANGE_CURATED_TO_OBSERVED` Must fail.
- Active 操作计划 on that merge key: void (existing PLAN_VOIDED semantics).
- Open change-curated 草案 on that merge key: void (`DRAFT_VOIDED`); item accept denied.
- Diagnosis READY payload Must not include those two forks as actionable unique-site options; copy uses 身份失联 / 未绑定观测候选 / 补标 — not 观测空洞 restore-only set unless the channel is actually timed out.
- Unbound drafts void only per stories 42–44, not because of heartbeat-timeout 空洞 on a different merge key.

### Curated write bypass

- Bootstrap create hosts/containers remains.
- Bootstrap overwrite of existing `运行于` remains rejected.
- No side POST that maps candidate → object without 草案条目接受.

### Modules (logical)

- `observed` / `agent`: ingest upsert, infer 失联, bind memory, list 待并入, 规范问法 失联 projection; update `docs/contracts/agent-heartbeat-snapshot.md` to describe inference + upsert + bind memory (contract doc, not CONTEXT).
- `curated`: unbound 草案 persistence, item accept/reject, 新建 object, first `运行于` insert from accepted item.
- `conflict`: 失联 flag on GET; pending-close exit; diagnosis fork filter; branch-selection gate; void plan + change-curated draft on 失联.
- `plan`: no new SSH steps; void on 失联 via existing stop-and-void.
- `user`: existing auth; unbound draft = authenticated.
- Frontend: last ticket; HTTP only via `frontend/src/api/`.

### Persistence defaults

- Additive Flyway after V15 (bind memory; draft origin without required 冲突; item kinds; unbound unique key if not already unique).
- Postgres is SSOT. Redis locks optional for double-accept / double-create races, not SSOT.
- Per-item accept + subsequent compare/ingest effects in one transaction where both happen on the same request; label-match consume happens on ingest.

### Concurrency

- Multi-replica: no draft/bind truth in replica memory.
- Same candidate second OPEN draft: reject.

### Agent stub

- Python stub may still send `identityLostObjectIds`; control plane Must infer even when omitted.
- No new Agent SSH 探活 fields (that is 网络可达, out of scope).

---

## Testing Decisions

### What makes a good test

- Assert **external HTTP behavior** only: status codes, `ApiResponse` envelope, and state readable by subsequent HTTP GETs (未绑定列表、身份失联、规范问法、策展对象/`运行于`、冲突 status + 失联旗标、诊断 forks、选支错误码、草案 item states、事件列表).
- Do **not** assert MyBatis mapper internals, Redis key shapes, or private call graphs.
- Prefer one ordered HTTP tracer plus a small negative set over a wide unit pyramid.
- Drive the tracer **one cycle at a time** (red → green → refactor). See [`docs/agents/tdd.md`](../agents/tdd.md).

### Primary seam (confirmed)

- Control-plane public HTTP API, including Agent heartbeat/snapshot ingest.
- No second acceptance seam. No Playwright. No SSH fake required for this slice’s definition of done.

### HTTP tracer (happy path)

1. Setup via existing APIs: hosts A and C; curated Docker 容器 X with immutable id `ctr-x`，策展 `运行于` A（现场尚未打标）。
2. Agent on **A** snapshot: unlabeled container `runtimeId=r1`, name similar to X; no `absentObjectIds`. → 待并入 `MISSING_LABEL`; X 身份失联; 「实际在哪」不得报 A 为可用实际; `by-merge-key` 不承诺升级链。
3. Agent on **C** snapshot without X: Must **not** mark X 身份失联 (host scope).
4. `UNKNOWN_OBJECT_ID` fixture (can be a second runtime on A with label `never-curated`): POST draft from that candidate → ≥2 items (新建 + `运行于` A); GET 策展 has no that object yet.
5. Reject `运行于`, accept 新建 → curated container exists with that label; no curated `运行于`. Further heartbeat with that label → observed `运行于` A; **no** new 冲突 / 待确认关闭 solely from agreement.
6. 失联 fixture: draft from `r1` → items 绑定 X vs 新建. Accept both → second fails. Accept 绑定 only → no reliable observed `运行于` for X; `r1` leaves 待并入 list; X still 身份失联.
7. Repeat unlabeled snapshot for `r1` on A → still not 待并入 新建 fodder; still 失联; still no upgrade chain.
8. Snapshot on A with `archops.object_id=ctr-x` on `r1` (or new runtime — label is the match): match X; clear 失联; consume bind memory; write observed `运行于`; if location equals curated A → existing compare (no 冲突 or pending-close as appropriate); if the snapshot instead places X on another curated host B that exists in setup, existing 冲突升级链 promised (`upgradeChainPromised` path restored).
9. Optional in same tracer or sibling method: labeled X on B after rematch → open 冲突 on merge key, diagnosis may offer 修实际/改理想 again (失联已清除).

### Negative / evolution (minimum)

1. Other-host snapshot does not 失联 X.
2. Unlabeled same-name does not promise upgrade chain (regression of vertical-slice 13).
3. Bind to a still-healthy label-matched object fails.
4. Second OPEN draft for the same candidate fails.
5. Unauthenticated draft create / item accept fails.
6. `MISSING_LABEL` 新建 is not a required success path (accept 新建 without a field label Must fail or the fixture must not offer a succeeding 新建).
7. Dual-accept bind+create fails.
8. 失联 subject: branch-selection `FIX_ACTUAL` / `CHANGE_CURATED` fails; diagnosis omits those unique-site forks; active plan voided; open change-curated 草案 voided; `PENDING_CLOSE` exits to `OPEN`.
9. `absentObjectIds` for X → 观测消失 not 失联; pending bind memory released.
10. Heartbeat refresh of the same `runtimeId` does not void an open unbound 草案.
11. Bootstrap `POST` create hosts/containers still works; overwrite existing `运行于` still `CURATED_RUNS_ON_EXISTS`.
12. Label match voids open unbound 草案 for that candidate/target.

### Modules under acceptance focus

- End-to-end across agent ingest, unbound/identity-lost reads, unbound 草案 item review, curated reads, 规范问法, conflict GET/diagnosis/branch-selection (negative), events.

### Supporting double (not a second acceptance seam)

- None. No SSH fake.

### Prior art

- `ObservedHeartbeatHttpAcceptanceTest` (unlabeled / identity-lost GET today).
- `VerticalSliceHttpE2eAcceptanceTest.negative_unlabeledSnapshotDoesNotPromiseUpgradeChain`.
- `ChangeCuratedDraft*HttpAcceptanceTest` (逐条、作废、tracer style) — **do not** hang unbound drafts on `conflictId`.
- `ConflictDiagnosisHttpAcceptanceTest` / `HeartbeatTimeoutHollowHttpAcceptanceTest` — 失联 must not reuse 空洞 fork set.
- Unified `ApiResponse`. Do not start a new test pyramid style.

---

## Out of Scope

- 操作计划中的「策展对齐步骤」（ADR-0019 Y2）以及改策展后再生成/人审/执行 SSH 计划
- 给 `change-curated-draft` 加 07；重做改策展 01–06 的「接受即写 `运行于`」产品化
- 重做竖切修实际闭环、协作、关单、心跳超时挂起（只复用；本刀仅在失联时闸门选支/作废计划）
- 物理主机 IP / SSH host key 重绑
- `MISSING_LABEL` 新建为 Must（现场无标签则无不可变 object id 可写）
- 把弱线索写成可靠观测 `运行于` / 承诺升级链
- 新冲突状态枚举；把身份失联写成观测空洞或观测消失
- AI 起草草案的 LLM 主路径、自我迭代、权限白名单自动写入
- 网络可达全矩阵 / N² / 源主机 SSH 核验
- K8s / 数据库对象；`属于`
- 把观测消失收成「策展改为不存在」
- JWT、G2 运营化、高危名单全表、完整 xterm、指标大盘
- Neo4j / 专用图库、多租户、Vue / JPA 当地基 / Maven / LangChain / Redis 作为关系真相 SSOT
- Playwright 或 SSH fake 作为完成定义

---

## Further Notes

- **Issue tracker**: 本地 markdown，见 `docs/agents/issue-tracker.md`。Canonical spec：`docs/specs/unbound-identity-rebind.md`。Tracker 指针：[`.scratch/unbound-identity-rebind/spec.md`](../../.scratch/unbound-identity-rebind/spec.md)。工单：[`.scratch/unbound-identity-rebind/issues/`](../../.scratch/unbound-identity-rebind/issues/)（01–07）。
- **Next Matt step**: 未绑定 **07 薄 UI 已闭合**。**frontier = 09**（失联叠加心跳超时的问法；审计 C-1）。不要发明未绑定 10。不要重拆竖切 01–13 或改策展 01–06。不要加改策展 07。
- **Why no new ADR**: grilling Q4/Q12 pinned 实现已有术语；「接受绑定后的现场实体对应关系」不是新合同词，也不是第四种冲突生命周期。
- **Why drafts are not on 冲突**: 未绑定不是冲突；复用处理人门禁会把匹配失败推进冲突升级链。
- **Why bind does not write observed `运行于`**: ADR-0011/0012 — 运行时 ID / 名称只是线索；未打标不承诺升级链。可靠实际仍等标签命中。
- **Why not Y2 / SSH 补标**: 与改策展「接受即写入」双写张力未在本刀打开；补标是现场，不是策展对齐步骤。
- **Glossary**: 未绑定观测候选、身份失联、草案、逐条确认、策展真相、观测真相、冲突、观测空洞、观测消失、规范问法、操作计划、待确认关闭、冲突升级。不要发明合同已 Avoid 的词。
- **Acceptance motto (this knife)**: 匹配失败不升冲突；并入必须逐条；绑定不写可靠实际；补标命中才恢复升级链。
- **Prompt for next chat**: `/implement` `/tdd` 未绑定票 09。开场 prompt：[`docs/implement-unbound-identity-rebind-09-prompt.md`](../implement-unbound-identity-rebind-09-prompt.md)。进度见 [`docs/dev-handoff.md`](../dev-handoff.md)。不要发明未绑定 10。代码 vs ADR-0044 审计：[`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md)。
