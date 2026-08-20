# Spec: ArchOps 竖切 MVP（一条冲突闭环）

**Status**: done（01–13 已闭合；tracker = `docs/agents/issue-tracker.md`）  
**Basis**: ADR-0039 领域合同、`CONTEXT.md`、ADR-0043 技术栈、ADR-0041（竖切范围 + AI 出站）、`docs/mvp-vertical-slice.md`  
**Starting point**: 空脚手架已按 ADR-0043 重建（仅 `GET /api/health`、包占位、Flyway `app_meta`）；本 Spec 从该骨架往上长，不复活旧业务模块语义。  
**Testing seams (confirmed)**: 唯一验收主接缝 = 控制面公开 HTTP API（含 Agent ingest）；SSH 执行端口可替换 fake 支撑 CI，不算第二条验收接缝。前端最小 UI 手工/冒烟，不进自动化主接缝。

---

## Problem Statement

运维需要一套「关系真相」系统，但产品要成立必须同时具备：策展/观测双轨与冲突、连接工作台（至少能受控执行计划步骤）、以及 AI/规则诊断后的人审执行闭环。当前仓库只有可启动的空骨架，无法演示「策展理想与现场实际偏差 → 协作处理 → 修现场 → 对齐关单」这一条生命线。若没有这条可验收竖切，团队无法证明绿场栈与冻结合同能落地，也无法安全地继续加宽功能面。

## Solution

交付一条端到端竖切：运维策展物理主机 A/B 与 Docker 容器 X（不可变标签 `archops.object_id`），策展事实为 X `运行于` A；Host Agent 心跳附带状态快照写入观测真相，匹配到 X 且观测为 `运行于` B；系统立即发出冲突警告（不等诊断完成），并异步给出只读诊断/分叉建议（模型不可用时规则兜底）；已接受的冲突处理人选择「修实际回 A」（纯修现场，跳过草案），系统生成操作计划，经处理人审查后由控制面经受控 SSH 在图内主机上逐步执行；执行后刷新观测，策展与观测值相等后进入「已对齐，待确认关闭」，仅处理人可确认关闭。全程禁止旁路直连与绕过计划冻结；心跳超时导致观测空洞时冲突挂起、活跃计划作废。

---

## User Stories

1. As a 运维（一般角色）, I want to create 策展真相中的物理主机 A 与 B, so that 图上有可定位的运维对象。
2. As a 运维, I want to create 策展真相中的 Docker 容器 X 并写入不可变对象标签约定 `archops.object_id=<容器ID>`, so that 后续探测能可靠匹配同一对象。
3. As a 运维, I want to confirm a 策展事实 that 容器 X `运行于` 主机 A, so that 「应该在哪」有明确理想状态。
4. As a 运维, I want curated writes for this slice to support 人工录入（草案逐条确认的最小形态可先简化为直接确认写入或极简草案）, so that 不必等待 AI 起草也能建立策展。
5. As a Host Agent on a physical host, I want to send periodic 心跳 to the control plane, so that the platform knows the observation channel is fresh.
6. As a Host Agent, I want 心跳 to optionally carry a 状态快照 (at least container list with labels), so that 观测真相 can be updated without human review.
7. As the control plane, I want to match snapshot containers by `archops.object_id` to curated 容器ID, so that identity is stable across Docker runtime ID and name changes.
8. As the control plane, when a matched container’s observed `运行于` differs from curated `运行于`, I want to emit a 冲突警告 immediately, so that operators are not blocked waiting for 诊断.
9. As the control plane, I want 诊断 to run asynchronously after the warning, so that 警告 latency is not tied to model or rule engine latency.
10. As any authenticated viewer, I want to read the open 冲突 (策展值、当前可用观测值、合并键), so that 规范问法「应该/实际/是否一致」 can be answered with both tracks on screen.
11. As any authenticated viewer, I want to read the current 诊断结果 (including 分叉计划 suggestions) as read-only, so that I understand options without being able to select a branch unless I am the handler.
12. As a 一般角色, I want to 已知悉 an unacknowledged 冲突 and become its 冲突处理人 (认领), so that I can drive remediation.
13. As an 高级角色, I want to 已知悉 a 冲突 (taking 冲突归属) and optionally self-appoint as 冲突处理人, so that ownership and handling can start from a supervisor.
14. As an 高级角色, I want to assign a 一般角色 as 冲突处理人 (待接受), so that work can be delegated (if time-boxed in this slice; minimum acceptable is self-appoint/认领 only per ADR-0041 vertical-slice note—assignment is Must if feasible, else explicit Later in Further Notes).
15. As an assigned 一般角色, I want to 接受 / 拒绝(须理由) / 转让 the handler role, so that I am not forced into execution without consent.
16. As a 冲突处理人 in 待接受 state, I must not be able to open an 操作计划, so that only an 已接受 handler can act.
17. As an 已接受的冲突处理人, I want to select the branch「修实际回 A」(纯修现场), so that the pipeline skips 草案 and goes to 操作计划 generation.
18. As a non-handler viewer, I want to be denied branch selection, so that only the handler progresses the pipeline.
19. As an 已接受的冲突处理人, I want the system to generate an 操作计划 for moving the container back to host A, so that steps are explicit and reviewable.
20. As an 已接受的冲突处理人, I want to review and approve the 操作计划 before any execution, so that no unreviewed change hits the physical world.
21. As the control plane, after plan approval I want to execute steps only via 受控 SSH through the control plane (MINA SSHD), so that there is no 旁路直连.
22. As the control plane, I want plan mutual exclusion via Redis distributed locks across replicas, so that two replicas cannot execute the same active plan concurrently.
23. As an 已接受的冲突处理人, I want execution to stop immediately and void the plan if blocked/failed, so that frozen plans are never silently rewritten or auto-retried.
24. As the control plane, after successful structural steps I want to trigger 执行后探测 / snapshot refresh for the affected objects, so that 观测真相 updates and can clear or re-open deviation.
25. As the control plane, when curated value equals currently available observed value for the merge key, I want to enter 待确认关闭, so that equality alone does not silently close the 冲突.
26. As the 已接受的冲突处理人, I want to confirm close only while values remain equal, so that a race drift fails the confirm and keeps/reopens the 冲突.
27. As any viewer, I want 待确认关闭 reminders to remain visible (not 已知悉-muted), so that the whole team sees alignment pending confirmation.
28. As the control plane, when 心跳超时 for relevant observation, I want those observations to become 观测空洞 (not usable as 实际), so that stale actuals are not trusted.
29. As the control plane, when an open 冲突’s observation becomes 空洞, I want to 挂起 the 冲突 (not close it) and void any active 操作计划, so that work does not continue against expired actuals.
30. As a 诊断 component in a pure 空洞 scenario, I want only「恢复观测通道/心跳/核验」style forks, so that we never guess a unique physical fix from stale data.
31. As the control plane, when a snapshot asserts a curated object is absent (观测消失), I want that to count as an available observed value (not 空洞), so that「应存在/应运行于」can conflict with「不存在」.
32. As the control plane, when a container lacks the immutable label, I want matching to fail into 未绑定观测候选 / 身份失联 behavior and not promise the conflict upgrade chain, so that silent rename merges cannot corrupt identity.
33. As a 冲突处理人, if observed actual changes on the same merge key (e.g. B→C) while curated stays A, I want 冲突升级 (merge, keep A→B→C lineage) rather than a second parallel open conflict, so that the team sees one evolving case.
34. As any viewer asking「实际在哪」, I still want the 策展 value shown alongside, so that single-track answers cannot pretend to be the only truth (规范问法 / P2).
35. As the control plane, I want temporary auth via request header mapping to a user with role 高级/一般 (or ADMIN/OPERATOR equivalent), so that collaboration rules can be enforced before JWT arrives.
36. As a platform operator, I want Compose delivery of `archops:latest` + Postgres + Redis with multi-replica design premise, so that locks/queues are exercised under the intended topology.
37. As a Host Agent installer, I want systemd as the primary delivery path for the Python agent, so that production hosts do not depend on an agent container (Later).
38. As a 诊断组件 when the external model is unavailable, I want rule-based 分叉计划 fallback, so that conflict warning, branch selection, human review, and execution still complete (ADR-0041 egress).
39. As the AI egress client, I want calls only to configured OpenAI-compatible HTTPS allowlist endpoints with secrets only on the control plane, so that the 运维隔离区 does not get arbitrary internet access.
40. As the AI egress client, I must not send business DB contents or customer/order/finance data in prompts, so that sensitive-read policy holds for diagnosis payloads.
41. As a workbench/plan executor, I want attempts to read business-sensitive data to be 拒绝 (not approval-gated), so that negative acceptance for 敏感读业务数据 is demonstrable at least in stub/classifier form for this slice.
42. As a non-handler operator, I want to be unable to start an 操作计划 for someone else’s 冲突, while still being able to view diagnostics, so that power boundaries hold.
43. As the control plane, I want at most one active 操作计划 per 冲突 at a time, so that parallel competing plans cannot run.
44. As an 已接受的冲突处理人, after a voided plan I want to be able to generate a new plan only through a fresh human review path (and new draft review if curated changes were needed—out of this slice’s chosen branch), so that failed plans are not patched in place.
45. As a developer/agent implementing this slice, I want Redis used for queue/lock/session/cache only—not as relationship-truth SSOT—so that Postgres remains the source of curated/observed/conflict/plan truth.
46. As a developer/agent, I want graph semantics for this slice stored in Postgres edge/fact tables with limited SQL/CTE—not Neo4j—so that v1 stays operable in constrained environments.
47. As a QA engineer, I want the full happy path exercisable solely via HTTP API + Agent ingest + SSH fake, so that CI can accept the slice without a browser or real lab SSH.
48. As a demo operator, I want a minimal React+Ant UI to list conflicts, acknowledge/handle, select branch, review plan, and confirm close, so that the story is human-demonstrable even though UI is not the automated seam.
49. As a platform operator, I want Flyway-only additive migrations for all new tables in this slice, so that schema history remains append-only.
50. As a security-conscious operator, I want host credentials used for plan SSH to be stored encrypted from day one of this slice, so that plaintext secrets are not introduced as debt.
51. As the control plane under multi-replica deployment, I want async diagnosis work to be safely scheduled (Redis-backed queue or equivalent), so that duplicate storms are bounded when scaled.
52. As an 已接受的冲突处理人 confirming close when values are no longer equal, I want a clear failure asking me to refresh, so that I cannot force-close a still-deviant merge key.
53. As any viewer, I want closed conflicts to remain auditable at least at a minimal level (status history / key events), so that the demo leaves a trail of warning → handle → execute → close.
54. As a Host Agent developer, I want a documented heartbeat+snapshot payload contract for this slice, so that the Python stub can be upgraded from “ping” to real ingest without inventing fields ad hoc.
55. As the control plane, I want curated `运行于` and observed `运行于` compared per merge key = same object + same relation type, so that conflict identity matches the domain contract.
56. As a 运维, I want connection targets for plan SSH to be limited to graph-resident physical hosts, so that ad-hoc off-graph hosts cannot be used in this slice’s executor.
57. As the product owner, I want negative acceptance scripts for (a) heartbeat timeout during plan → 挂起 + void plan, (b) sensitive business read → reject, (c) unlabeled container → no upgrade-chain promise, so that the slice’s “完成定义” is not only the happy path.

---

## Implementation Decisions

### Starting posture

- Grow from the existing ADR-0043 scaffold only: health endpoint, unified API envelope, security stub, empty module packages (`curated` / `observed` / `conflict` / `plan` / `user` / `agent` / `common`), Flyway baseline. Do not revive deleted pre-wipe domain modules or semantic compatibility shims.

### Stack (ADR-0043)

- Control plane: Java 21, Spring Boot 3, Gradle (Kotlin DSL), MyBatis-Plus (explicit SQL), Flyway, PostgreSQL 16 as SSOT for curated/observed/conflict/plan/user.
- Redis: required for queue, distributed locks, session/cache; multi-replica control plane design premise; never relationship-truth SSOT.
- Frontend: React + TypeScript + Ant Design; production embedded as static assets in `archops:latest`.
- Host Agent: Python 3.12+; systemd primary delivery; source/manual run for dev; not in default control-plane Compose.
- SSH: Apache MINA SSHD in-process pool per replica; plan mutex via Redis lock; no bypass direct connect from agents/UI.
- AI egress: Spring WebClient to allowlisted OpenAI-compatible HTTPS endpoints (ADR-0041); no LangChain-style orchestration framework as backbone.
- Graph DB (Neo4j etc.): Later; this slice expresses hosts/containers/`运行于` in Postgres tables + limited CTE/SQL as needed.
- Auth for this slice: temporary identity header → mapped user + role (高级/一般 or ADMIN/OPERATOR). JWT Later.

### Domain scope pinned to the story

- Object kinds in play: 物理主机, Docker 容器. Relation in play: `运行于`. Other v1 kinds/edges may exist as schema placeholders but need not be operable.
- Curated path: create hosts A/B, container X with object id/label expectation, curated `运行于` A. AI draft of proposals is Later; human entry is enough.
- Observed path: Agent heartbeat + snapshot ingest; label match; write observed `运行于`; freshness via heartbeat; timeout → 观测空洞; suspend open conflict; void active plan.
- Conflict: create on available curated≠observed; warn before diagnosis; merge key = object + relation; upgrade on actual change; close only via 待确认关闭 + handler confirm when equal.
- Collaboration minimum: 已知悉, 冲突归属, 冲突处理人 with 已接受 before plan. Prefer supporting 认领 + self-appoint; full assign/accept/reject/transfer should be included if it fits one agent context window after core path—otherwise implement 认领/自任 as Must and document assign/transfer as immediate follow-on (still Spec-visible). Default for this Spec: **Must include 已知悉 + 认领/自任成为已接受处理人**; assign/reject/transfer are **Should** (implement if low-cost, else explicitly defer in ticket breakdown—not silently dropped from product intent).
- Diagnosis: async after warn; read curated/observed/conflict metadata + limited B-live reads as needed; never guess; rule-engine forks Must; LLM optional via WebClient allowlist; model outage must not block warn → select → review → execute.
- Chosen branch for MVP demo: 「修实际回 A」pure field fix → **skip 草案** → generate 操作计划 → human review → execute.
- Execution: frozen ordered steps; stop-and-void on failure; post-exec observation refresh; no workbench light-confirm continuation of a failed plan; full xterm workbench Later/out.
- Credentials: encrypted storage for SSH credentials used by the executor from the first implementation of real SSH (fake mode may omit secrets).

### API / module boundaries (logical)

- `user`: actor resolution from temporary header; roles for collaboration gates.
- `curated`: objects + curated facts (`运行于`); reads for 规范问法.
- `agent` + `observed`: heartbeat/snapshot ingest; observed facts; freshness/空洞 detection.
- `conflict`: detect/warn/upgrade/suspend; collaboration state; query APIs.
- `plan`: branch selection binding, plan create/review/approve/start/step/void; ties to conflict single-active rule.
- `common`: API envelope, errors, security, Redis lock helpers, SSH port abstraction, WebClient egress config.
- Frontend: thin screens for conflict list/detail, acknowledge/claim, branch select, plan review, confirm close; may call the same HTTP APIs as the test seam.

### Persistence defaults (contract-aligned, implementation-default where ADR silent)

- Store curated objects, curated facts, observed facts (with freshness metadata), conflict cases (status, merge key, lineage of observed values, acknowledgement/owner/handler fields), operation plans (frozen steps JSON, status), minimal users/roles, host agents last-heartbeat, encrypted credential records for hosts.
- Use additive Flyway migrations only after the scaffold `app_meta` baseline.
- Comparison and neighborhood queries via SQL; no Neo4j client in dependencies.

### Concurrency / replicas

- Assume ≥2 control-plane replicas in design; use Redis locks for plan execution critical sections and avoid double-processing of the same diagnosis job where practical.
- Relationship writes and conflict state transitions remain transactional in Postgres.

### SSH testability

- Introduce an SSH execution port/interface: production adapter = MINA SSHD; test adapter = recording fake returning scripted success/failure. Acceptance tests use the fake unless a lab profile is explicitly enabled.

### Diagnostics defaults

- First diagnosis payload for the demo branch can be rule-templated: e.g. “actual host ≠ curated host → fork: move container back to curated host”.
- If LLM is configured, WebClient may enrich text; failure falls back to rules. Warning emission never waits on LLM.

### Delivery

- Keep single image `archops:latest` (API + static UI) + Postgres + Redis Compose. Agent remains separate (systemd). No requirement to finish offline packaging polish beyond what scaffold already provides.

---

## Testing Decisions

### What makes a good test

- Test **external behavior** at the confirmed seam only: HTTP responses, resource states readable via HTTP, and side effects observable through subsequent HTTP reads (and fake SSH recorded commands).
- Do **not** assert MyBatis mapper internals, Redis key shapes, or private method call graphs.
- Prefer one ordered acceptance flow (tracer) plus a small set of negative HTTP scenarios over a wide unit pyramid for this slice.

### Primary seam

- Control-plane public HTTP API, including Agent heartbeat/snapshot ingest.
- Drive: curated setup → agent snapshot (X on B) → assert conflict warning exists before diagnosis completion → claim/acknowledge handler → select fix-actual branch → review/approve plan → execute via SSH fake → agent/refresh observed on A → pending close → confirm close.
- Assert: no second parallel conflict on same merge key for B→C style upgrade if included; cannot execute without approval; cannot confirm close when unequal; timeout/空洞 suspends and voids active plan.

### Supporting double (not a second acceptance seam)

- SSH fake adapter records intended host/command and returns configured results so CI needs no real SSH host.

### Modules under acceptance focus

- End-to-end behavior across curated, observed/agent ingest, conflict (+ collaboration), plan (+ execution orchestration), user/auth header gate.
- Frontend: manual demo checklist only for this Spec’s automated definition of done (optional smoke later).

### Prior art

- Scaffold currently has essentially no domain tests; establish the HTTP acceptance style as the first prior art for the greenfield repo. Reuse the unified API envelope (`success/code/message/data`) for assertions.

### Negative acceptance (minimum)

1. Heartbeat timeout / 空洞 while plan active → conflict 挂起, plan void, no further step execution.
2. Sensitive business-data read attempt → 拒绝.
3. Unlabeled container snapshot → no reliable upgrade-chain / identity merge promise (未绑定 or 身份失联 path).

---

## Out of Scope

- AI 自我迭代、权限白名单自动写入、处理归档驱动的策略晋升
- 指标 / 告警大盘、可观测性产品化（非关系冲突）
- 网络可达边的全矩阵核验与 N² 探测
- 改策展分支全流程（冲突处理中选「改理想」→ 草案逐条确认 → 策展对齐步骤）——本竖切固定纯修现场、跳过草案。后续刀见 [`change-curated-draft.md`](change-curated-draft.md)（草案逐条写入 + 比对演进；仍不含对齐步骤）
- Neo4j / 专用图库引入或双写
- 多租户 / 租户隔离
- 完整 xterm 连接工作台、工作台轻确认日常探查产品化（本切片只需计划步骤受控执行）
- 旧 ArchOps 模块语义兼容层、旧提案/RAG/JPA/Vue 复活
- K8s 集群/节点、数据库对象的完整探测与冲突（模型位可留，不实现）
- 像素/多模态视觉诊断（V-vision）
- JWT 完备认证、反向代理统一 TLS、K8s Operator、agent 容器镜像交付
- LangChain 类编排框架、Redis 作为关系真相 SSOT、Maven 替换 Gradle
- G2 渐行渐远全量产品化可后置默认（本切片可不做超时再提醒运营化，但不得与合同冲突；若实现时钟，遵守「自警告起算、空洞不暂停」）
- 高危命令正则全表运营化（计划整单人审已覆盖本切片执行审批）

---

## Further Notes

- **Issue tracker**: 本地 markdown，见 `docs/agents/issue-tracker.md`。本 Spec 与 `.scratch/vertical-slice-mvp/issues/` 01–13 均已闭合。
- **Next Matt step**: 竖切 01–13 已闭合。改策展 Spec：[`change-curated-draft.md`](change-curated-draft.md)；工单 [`.scratch/change-curated-draft/issues/`](../../.scratch/change-curated-draft/issues/)（**01–03 TDD-done**；frontier = **04**）。不要重拆本 Spec。`/implement` 走 [`docs/agents/tdd.md`](../agents/tdd.md)。
- **Acceptance motto** (from ADR-0041 vertical-slice): 没有心跳快照就不能假装有实际；没有人审就不能执行；计划失败不能改步重试；没对齐不能关单。
- **Glossary authority**: prefer `CONTEXT.md` terms (策展真相、观测真相、冲突、已知悉、冲突归属、冲突处理人、操作计划、待确认关闭、观测空洞、心跳、规范问法, etc.). Do not invent parallel vocabulary in APIs/UI copy.
- **Scaffold honesty**: until domain migrations and APIs land, only health is live; implementers must add schema and modules incrementally without treating health-only code as domain precedent.
- **Assign/transfer**: product intent includes full协作; if a single implementation pass must cut, keep 认领/自任, defer assign/reject/transfer explicitly in tickets rather than claiming the collaboration story done.
