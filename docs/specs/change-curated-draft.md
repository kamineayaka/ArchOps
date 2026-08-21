# Spec: 改策展 / 改理想（草案逐条确认）

**Status**: done（01–06 TDD-done；本刀闭合。tracker = `docs/agents/issue-tracker.md`）  
**Basis**: ADR-0039 领域合同、`CONTEXT.md`、ADR-0043 技术栈、ADR-0006（草案逐条写入）、ADR-0009（双轨偏差）、ADR-0019（相等后待确认关闭；本刀**不含** Y2 策展对齐步骤）、ADR-0038（改策展须草案人审；纯修现场跳过草案）、ADR-0041（竖切范围 + AI 出站）  
**Predecessor**: [`docs/specs/vertical-slice-mvp.md`](vertical-slice-mvp.md)（票 01–13 已实现并经 VM 人工验收）。本 Spec 从该实现往上长，不从空骨架重写，不复活旧域包。  
**Testing seams (confirmed)**: **唯一验收主接缝 = 控制面公开 HTTP API**（含 Agent 心跳/快照 ingest）。驱动可以是 Gradle/`MockMvc` 或 Compose 上 `bootRun`+`curl`，仍是同一条接缝。前端最小 UI 手工/冒烟，不进自动化主接缝。本刀无 SSH 计划，不引入 SSH fake 作为接缝或替身。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**；下文 HTTP tracer 是循环顺序，不是一次写完全部测试再实现。

**Confirmed scope pins**

1. 本刀**不含**操作计划中的「策展对齐步骤」，也**不**在改策展后再生成/人审/执行新的 SSH 操作计划。
2. 本刀规则生成的改策展草案**至少两条**独立可确认条目，以验收「逐条」而非整单全有或全无。
3. 主接缝为 HTTP API only（选项 A）。

---

## Problem Statement

竖切 MVP 只闭合了「修实际回策展宿主」：已接受处理人选 `FIX_ACTUAL`、跳过草案、人审计划、受控 SSH、观测对齐、待确认关闭。合同里的另一条合法收场——**承认实际、更新策展（改理想）**——仍未落地。今日诊断规则引擎没有改策展分叉；选支只创建操作计划；策展 `运行于` 仍可通过建底 API 直接改写。没有这条刀，无法证明：选支瞬间不偷写策展、接受的条目立即写入、拒绝的条目仍只是草案、写策展后走同一套比对与冲突演进（相等则待确认关闭，仍不等则同一合并键升级，空洞则挂起并作废草案）。

## Solution

在已实现的竖切底上交付「改理想」流水线的**草案写入段**（到比对演进为止）：

策展仍为容器 X `运行于` A，可用观测为 `运行于` B，冲突开放且处理人已接受。诊断在两侧可用且不等时同时给出「修实际」与「改理想」分叉（规则引擎即可）。已接受处理人选择改理想后，系统生成**草案**（确认前不是策展真相），且**不**创建操作计划。处理人按条接受或拒绝；接受的条目立即写入策展真相并触发与观测的同一套比对；未接受的仍只是草案。写入后不必再等下一次心跳：策展与当前可用观测相等则进入待确认关闭（不自动关单）；仍不等则同一合并键演进/升级；观测空洞则挂起并作废未完成草案。全程禁止选支瞬间写策展、禁止非已接受处理人审草案、禁止用建底 POST 覆盖已有 `运行于` 来旁路草案。

---

## User Stories

1. As an 已接受的冲突处理人, I want the current 诊断 to offer a 改理想 fork (承认实际、把策展对齐到当前可用观测) alongside 修实际, so that I can choose to change 策展真相 instead of the field.
2. As any authenticated viewer, I want that 改理想 fork to be read-only until I am the 已接受处理人, so that 分叉计划 remain suggestions, not an open write path.
3. As the control plane in a pure 观测空洞 diagnosis, I must not offer 改理想 as a unique-site fork, so that we never treat expired or missing actuals as a curated target.
4. As an 已接受的冲突处理人, I want to select the 改理想 branch based on the **current non-stale** 诊断, so that I cannot progress on an outdated fork after 冲突升级.
5. As a non-handler or 待接受处理人, I want 改理想 branch selection to be denied, so that only an 已接受处理人 can open a 草案.
6. As an 已接受的冲突处理人 selecting 改理想, I want the system to create a 草案 immediately, so that the proposed 策展 diff is reviewable before any truth write.
7. As an 已接受的冲突处理人 selecting 改理想, I want **no** 操作计划 to be created, so that this slice does not mix in SSH execution or 策展对齐步骤.
8. As any viewer, after 改理想 selection but before item accept, I want 「应该在哪」 to still return the original curated host, so that an unconfirmed 草案 is not 策展真相 (ADR-0006).
9. As the control plane, I must not write 策展 at the instant of branch selection, so that 「选分支瞬间偷写策展」 stays forbidden.
10. As an 已接受的冲突处理人, I want the generated 草案 to contain **at least two independently confirmable items**, so that 逐条确认 is distinguishable from all-or-nothing confirm.
11. As an 已接受的冲突处理人, I want each 草案 item to be accept or reject on its own, so that the confirmation unit is the item (object or relation), not the whole draft.
12. As an 已接受的冲突处理人, when I **accept** an item, I want that item written to 策展真相 immediately, so that accepted diffs become the new ideal without waiting for a plan step.
13. As an 已接受的冲突处理人, when I **reject** an item, I want 策展真相 unchanged for that fact, so that rejected proposals remain 草案 only.
14. As a non-handler, I want item accept/reject to be denied, so that draft review has the same power boundary as branch selection.
15. As the control plane, after an accepted item writes 策展, I want to run the **same** curated-vs-observed compare used on snapshot ingest, so that conflict evolution does not depend on waiting for the next 心跳.
16. As the control plane, when the merge-key 策展值 equals the currently available 观测值 after a draft write, I want the 冲突 to enter 待确认关闭 (not CLOSED), so that equality alone never auto-closes.
17. As an 已接受的冲突处理人, I want to confirm close via the **existing** confirm-close API while values remain equal, so that this slice reuses 关单 rather than re-productizing it.
18. As any viewer, I want 待确认关闭 reminders to stay visible (not 已知悉-muted), so that alignment pending confirmation remains a team-visible state.
19. As the control plane, when I accept only the merge-key item and reject the sibling item, I want only the accepted fact to change, so that mixed 逐条 confirmation is the tracer.
20. As a 运维 reading 规范问法 after accepting X `运行于` → B, I want 「应该在哪」 for X to answer B and still show the observed track on the conflict/actual read, so that a single-track answer cannot pretend to be the only truth.
21. As the control plane, when observed actual on the same merge key changes (B→C) **before** the merge-key item is accepted, I want 冲突升级 (one case, lineage preserved), the 改理想 选支作废, and the open 草案 voided with **no** curated write from pending items, so that we do not confirm a stale proposal targeting B.
22. As the control plane, when observed actual changes **after** the merge-key item was accepted (curated already B, observed C), I want the same merge key to leave 待确认关闭 (if it was there) and upgrade/re-open, so that drift is not force-closed.
23. As the control plane, when 心跳超时 makes the merge-key observation 空洞 while a 草案 is open, I want the 冲突 挂起, the 草案 voided, and further item accept denied, so that we do not write 策展 from a proposal that assumed a now-unusable actual.
24. As an 已接受的冲突处理人, I want a voided 草案 to be unmodifiable, so that 升级/空洞 cannot be papered over by continuing the old review.
25. As an 已接受的冲突处理人, I want at most one open 草案 per 冲突, so that two competing 改理想 proposals cannot run.
26. As an 已接受的冲突处理人, I must not select 修实际 (`FIX_ACTUAL`) while a 改理想 草案 is still open, and must not select 改理想 while an active 操作计划 exists, so that the two pipelines cannot be live on the same 冲突.
27. As an 已接受的冲突处理人, I want `FIX_ACTUAL` selection to keep skipping 草案 and still create an 操作计划, so that the first slice’s path does not regress.
28. As a 运维 using the bootstrap curated API, I still want to **create** the first `运行于` fact (hosts/containers setup), so that 竖切建底 remains.
29. As the control plane, I want updates to an **already-existing** `运行于` fact via that bootstrap POST to be rejected, so that conflict-path (and any later) curated mutation cannot bypass 草案.
30. As a QA engineer, I want the happy path and negatives exercisable solely via HTTP API + Agent ingest, so that CI can accept this slice without a browser or SSH.
31. As a demo operator, I want a thin React+Ant UI to show the 改理想 fork, list 草案 items, and accept/reject per item, so that the story is human-demonstrable even though UI is not the automated seam.
32. As a developer/agent, I want drafts and curated facts stored in PostgreSQL (Flyway additive only), with Redis unused as relationship-truth SSOT, so that ADR-0043 holds.
33. As a developer/agent, I want rule-templated 草案 generation on branch select (LLM drafting Later), so that model outage cannot block 改理想 review.
34. As any viewer, I want minimal audit events for draft created / item accepted (write) / item rejected / draft voided, readable via HTTP, so that 处理归档 has a seam even without self-iteration.
35. As the control plane, when 观测消失 (available ABSENT) is the current actual, I want this slice to **keep** existing restore-style forks and **not** invent a 改理想「策展改为不存在」item type, so that object-kind and fact-kind stay at hosts/containers/`运行于`.
36. As a platform operator, I want Compose delivery and temp auth headers unchanged, so that this slice does not reopen JWT, Neo4j, Vue, Maven, JPA, or LangChain.

---

## Implementation Decisions

### Starting posture

- Grow from the implemented vertical-slice MVP (Flyway through V12, modules `curated` / `observed` / `conflict` / `plan` / `user` / `agent` / `common`, HTTP E2E for 修实际). Do not revive deleted packages. Do not rewrite 01–13 behavior except where this Spec explicitly closes a bypass (existing `运行于` overwrite).

### Stack (ADR-0043) — unchanged

- Control plane: Java 21, Spring Boot 3, Gradle, MyBatis-Plus, Flyway, PostgreSQL SSOT.
- Redis: queue/lock/session/cache only; never 关系真相 SSOT.
- Frontend: React + TypeScript + Ant Design; thin screens only.
- No Neo4j, no JPA-as-base, no Vue, no Maven, no LangChain backbone.
- Auth: existing temporary identity header + 高级/一般 roles.
- AI egress: existing WebClient allowlist; this slice’s 草案 is **rule-templated**. LLM may later enrich item prose; failure must not block draft creation.

### Domain objects in play

- Kinds: 物理主机, Docker 容器. Relation: `运行于`. Merge key = 同一容器 + `运行于`.
- Tracer fixture **Must** include: hosts A and B; container X curated `运行于` A; **container Y** curated `运行于` A (sibling, same kinds); Agent snapshot matching X on B (Y need not conflict).
- No K8s/database objects, no 网络可达, no 未绑定观测候选 binding via draft.

### Diagnosis forks

- On available curated≠observed `运行于` (X: A vs B): rule engine **Must** emit both:
  - existing `FIX_ACTUAL` / 修实际回策展宿主 (skip 草案);
  - `CHANGE_CURATED` / 改理想（承认实际、更新策展）targeting the **current available** observed host.
- Suggested stable ids (implementers may use these): `FIX_ACTUAL_TO_CURATED` (already shipped), `CHANGE_CURATED_TO_OBSERVED` with `kind=CHANGE_CURATED`.
- Pure 空洞: only restore-channel forks (existing). 观测消失/ABSENT: keep existing restore-style forks; do **not** add 改理想「改为不存在」in this slice.
- Fork labels/copy use CONTEXT terms (改理想 / 策展 / 观测 / 草案); do not invent 「以观测为准」「裁定」.

### Branch selection vs 操作计划 vs 草案

- Keep a single select-branch **gate** (existing collaboration checks: 已接受处理人、当前未过时诊断、每冲突一条活跃处理路径).
- `FIX_ACTUAL`: existing contract — skip 草案, create 操作计划. Do not change V8 `branch_kind IN ('FIX_ACTUAL')` history; do not add `CHANGE_CURATED` as an operation-plan branch kind.
- `CHANGE_CURATED`: create **exactly one open 草案** for that 冲突; create **zero** 操作计划 rows; do not start SSH; do not enqueue 策展对齐步骤.
- HTTP shape is an implementation default: reuse `POST .../branch-selection` with a discriminated body **or** pair it with draft resources, so long as (a) `FIX_ACTUAL` HTTP responses remain valid for 01–13 tests, and (b) `CHANGE_CURATED` clients can GET the open draft and POST per-item accept/reject under `/api/...` + `ApiResponse`.
- Selecting `CHANGE_CURATED` twice while a draft is open: reject (one open draft).
- Open draft blocks `FIX_ACTUAL` selection; active 操作计划 blocks `CHANGE_CURATED` selection.

### 草案 items (this slice’s rule fixture)

- Confirmation unit = item, never whole-draft-only (CONTEXT 逐条确认; _Avoid_ 整单全有或全无).
- Item kind for this slice: `运行于` target change (fromHost → toHost) on a curated Docker 容器.
- Rule-templated draft for the demo conflict **Must** contain ≥2 items:
  1. **Merge-key item**: container X `运行于` A → B (B = current available observed host).
  2. **Sibling item**: container Y `运行于` A → B (independent fact; tracer setup provides Y). Rejecting it must not affect X.
- Item states: pending / accepted / rejected (voided draft items are terminal and not writable).
- Accept of an item: write that curated fact **immediately** (ADR-0006), then run compare. Do not wait for remaining items.
- Reject of an item: no write.
- Draft in confirmation-before-write is **not** 策展真相: GET 「应该在哪」 / curated `运行于` still returns prior values for pending items.

### Curated write bypass (Must close)

- `POST /api/curated/facts/runs-on` remains valid to **insert** the first `运行于` fact (竖切建底, ticket 02).
- Updating an **already-existing** `运行于` fact through that POST **Must** be rejected. The legal mutation path in this slice is accepted 草案 items only.
- AI cannot finalize 策展. No “auto-accept all”.

### Compare and 冲突演进 (reuse one engine)

- Curated writes from accepted items **Must** trigger the same compare/upgrade/pending-close/suspend engine as Agent snapshot ingest (today compare is ingest-driven; this slice makes curated mutation a first-class trigger).
- Equal available values on the merge key → `PENDING_CLOSE`, never auto `CLOSED`.
- Still unequal → same merge key stays/returns `OPEN` with upgrade/lineage; **no** second parallel open 冲突.
- 空洞 → `SUSPENDED`, void open 草案 (and any active 操作计划, already specified by slice 1).
- Confirm-close: existing handler-only API; confirm must still fail if values drifted.

### Voiding

- 冲突升级 or 空洞 while 草案 open: 选支作废, 草案作废, pending items never write.
- Already-accepted items stay in 策展 (they are already truth); subsequent compare uses the new curated value.
- HTTP must expose that the draft is voided (status on GET); accept/reject on a voided draft fails.

### Modules (logical)

- `curated`: 草案 persistence, item accept/reject, curated fact write; refuse bootstrap overwrite of existing `运行于`.
- `conflict`: compare trigger on curated write; 挂起/升级/待确认关闭; diagnosis forks include `CHANGE_CURATED`.
- `plan`: unchanged `FIX_ACTUAL` path; select-branch gate coordinates “one active pipeline”.
- `user`: existing auth header / role gates.
- `observed` / `agent`: ingest unchanged; still used to set actual B and to drive B→C / timeout negatives.
- Frontend: thin conflict detail — show 改理想 fork, draft items, per-item actions; HTTP only via `frontend/src/api/`.

### Persistence defaults

- Additive Flyway after V12. New tables for drafts + items (and optional draft events if not folded into existing conflict events). Postgres JSON for item payload is acceptable; do not use Redis as draft SSOT.
- Comparison remains SQL against curated/observed fact tables.

### Concurrency

- Draft accept/reject and compare in a Postgres transaction per item.
- Multi-replica: do not put draft truth in replica memory; Redis locks optional if needed to prevent double-accept races, not as SSOT.

---

## Testing Decisions

### What makes a good test

- Assert **external HTTP behavior** only: status codes, `ApiResponse` envelope, and state readable by subsequent HTTP GETs (策展「应该在哪」、观测「实际在哪」、冲突 status/merge key/lineage、草案 item states、诊断 forks、事件列表).
- Do **not** assert MyBatis mapper internals, Redis key shapes, or private call graphs.
- Prefer one ordered HTTP tracer plus a small negative set over a wide unit pyramid.
- Drive the tracer **one cycle at a time** (red → green → refactor). See [`docs/agents/tdd.md`](../agents/tdd.md).

### Primary seam (confirmed)

- Control-plane public HTTP API, including Agent heartbeat/snapshot ingest.
- No second acceptance seam. No Playwright. No SSH fake required for this slice’s definition of done.

### HTTP tracer (happy path)

1. Setup via existing APIs: hosts A/B; containers X and Y with `archops.object_id`; curated X `运行于` A, Y `运行于` A.
2. Agent snapshot: X on B → 冲突 warning exists (diagnosis may complete asynchronously; wait as today’s tests do).
3. 一般角色 认领 → 已接受处理人.
4. GET diagnosis: forks include `FIX_ACTUAL` **and** `CHANGE_CURATED`.
5. Select `CHANGE_CURATED` → draft exists with **≥2** items (X and Y `运行于` A→B); GET curated X still A; **no** active 操作计划.
6. Non-handler item accept → denied; curated still A.
7. Reject Y’s item → Y 「应该在哪」 still A.
8. Accept X’s item → X 「应该在哪」 is B; Y still A.
9. Without a new snapshot, GET 冲突 → `PENDING_CLOSE` (策展 B = 观测 B).
10. Confirm close via existing API → `CLOSED` (proves no auto-close at step 9; reuses 票 09, does not re-spec 关单).

### Negative / evolution (minimum)

1. Select `CHANGE_CURATED` does not change curated (assert before any item accept).
2. Non-handler / 待接受 cannot select `CHANGE_CURATED` or accept/reject items.
3. `FIX_ACTUAL` still skips 草案 and still creates an 操作计划 (regression; can be a focused HTTP test, not a full SSH run).
4. Open draft blocks `FIX_ACTUAL`; active plan blocks `CHANGE_CURATED`.
5. Bootstrap `POST` overwrite of an existing `运行于` → rejected; fact unchanged.
6. While draft pending (X not accepted), snapshot X on C → same merge key 升级, draft voided, curated X still A, pending items did not write.
7. Heartbeat timeout / 空洞 while draft open → 冲突 挂起, draft voided, item accept denied.
8. After X accepted (pending close), snapshot X on C → not a parallel new 冲突; leaves pending-close / upgrades on the same merge key.

### Modules under acceptance focus

- End-to-end across diagnosis forks, select-branch gate, draft item review, curated read APIs, conflict GET/events, agent ingest.
- Frontend: manual demo checklist only.

### Prior art

- Follow `*HttpAcceptanceTest` / `VerticalSliceHttpE2eAcceptanceTest` style and unified `ApiResponse`. Do not start a new test pyramid style for this slice.

---

## Out of Scope

- 操作计划中的「策展对齐步骤」（ADR-0019 Y2）以及改策展后再生成/人审/执行 SSH 计划
- 选改策展后自动再出一份「仅对齐策展」的操作计划（与「接受即写入」重复，本刀明确不做）
- AI 起草草案的 LLM 主路径、自我迭代、权限白名单自动写入
- 指标 / 告警大盘
- 网络可达全矩阵 / N² 探测
- Neo4j / 专用图库、多租户、完整 xterm 工作台、工作台轻确认产品化
- Vue / JPA 当地基 / Maven / LangChain / Redis 作为关系真相 SSOT
- K8s / 数据库对象；未绑定候选经草案绑定或新建；身份失联重绑
- 把 观测消失 收成「策展改为不存在」的条目类型
- JWT、G2 渐行渐远运营化、高危名单全表
- 重做协作、心跳、修现场执行、关单产品化（只复用）
- 旧 ArchOps 模块语义兼容

---

## Further Notes

- **Issue tracker**: 本地 markdown，见 `docs/agents/issue-tracker.md`。本 Spec 已发布为 `docs/specs/change-curated-draft.md`。工单已写入 [`.scratch/change-curated-draft/issues/`](../../.scratch/change-curated-draft/issues/)（01–06）。
- **Next Matt step**: 改策展 01–06 已 TDD-done，**本刀闭合**。下一对话不要默认 `/implement`，不要加 07。先 [`docs/grill-next-knife-prompt.md`](../grill-next-knife-prompt.md) 跑 `/grill-with-docs` 定下一刀，再同一窗口 `/to-spec` → `/to-tickets`。不要重拆竖切 01–13。
- **Why no 策展对齐步骤 in this knife**: CONTEXT 同时写了「接受的条目立即写入策展」与「计划内对齐步为另一合法路径」。本刀按用户钉死的故事走前者。Y2 是后续 Spec，不得在实现里用对齐步推迟写入，也不得在选支瞬间写入。
- **Why ≥2 items**: 合同禁止整单全有或全无作为确认单位；单条草案无法在 HTTP 上把「逐条」与「整单确认」分开。1-item drafts 仍符合合同，但本刀规则夹具必须给出两条。
- **Glossary**: 策展真相、观测真相、冲突、草案、逐条确认、已接受的冲突处理人、待确认关闭、冲突升级、观测空洞、规范问法、操作计划、策展对齐步骤（仅在 Out of Scope 中出现）。不要发明「以现场为准」「待确认策展」等合同已 Avoid 的词。
- **Acceptance motto (this knife)**: 选支不写策展；接受的条目才写；拒绝的不写；写入后立刻比对；相等只进待确认关闭；升级/空洞作废未确认草案。
- **Prompt for next chat**: [`docs/grill-next-knife-prompt.md`](../grill-next-knife-prompt.md)。进度见 [`docs/dev-handoff.md`](../dev-handoff.md)。票 01–06 已 done，本刀闭合。
