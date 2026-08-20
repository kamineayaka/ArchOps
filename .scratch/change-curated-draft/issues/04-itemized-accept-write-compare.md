# 04 — 逐条确认：接受即写策展并立刻比对（相等 → 待确认关闭）

**What to build:** 已接受处理人对开放草案内的条目逐条接受或拒绝。接受的那一条立即写入策展真相（不等待其余条目、不等待心跳）；拒绝的那一条不改策展。写入后必须立刻跑与快照 ingest **同一套**策展/观测比对：合并键上两轨可用且相等 → 冲突进入待确认关闭（绝不自动关闭），再用既有处理人确认关闭 API 关单；未接受的兄弟事实保持原策展。非处理人不能审条。冲突详情薄 UI 提供按条接受/拒绝。

**Blocked by:** 01 — 关闭建底 POST 覆盖已有 `运行于`；03 — 选改理想生成 ≥2 条草案：不写策展、不出操作计划

**Status:** done

从竖切 MVP 往上长：今日比对只在观测写入后触发；策展突变不会自己走进待确认关闭。关单、待确认关闭提醒可见、确认时若已漂移则失败——这些已由竖切票交付，本票只复用，不重做关单产品化。合法策展改写在本刀只剩「接受的草案条目」；建底 POST 覆盖已由 01 关闭。

示踪混确：拒绝兄弟 Y，接受合并键 X。接受 X 后（观测仍为 B）不必再等快照，「应该在哪」为 B，冲突为待确认关闭；Y「应该在哪」仍为 A。

- [x] 已接受处理人接受 X 的条目：X「应该在哪」立即为当前可用观测宿主 B；条目状态为已接受
- [x] 已接受处理人拒绝 Y 的条目：Y「应该在哪」仍为 A；条目状态为已拒绝；X 不受影响
- [x] 非处理人接受或拒绝任一条目被拒绝；策展不变
- [x] 接受合并键条目后，**不**再发新快照：GET 冲突为待确认关闭（非 CLOSED）；提醒对查看者保持可见（非已知悉静音）
- [x] 仅已接受处理人可走既有确认关闭 API；确认成功后为 CLOSED（证明第 9 步没有自动关单）
- [x] 若只拒绝合并键、只接受兄弟：合并键冲突不得仅因兄弟被接受而进入待确认关闭
- [x] 规范问法：接受 X 后「应该在哪」答 B；冲突/实际读取仍展示观测轨，不得把单轨说成唯一真相
- [x] HTTP 可读「条目已接受（含写入）」与「条目已拒绝」审计
- [x] 按条接受/拒绝与随后比对在同一持久化事务边界内完成（多副本下草案真相不放副本内存；Redis 可作锁但不是 SSOT）
- [x] 薄 UI：处理人可按条接受/拒绝并看到条目状态与「应该在哪」变化。UI 不进自动化主接缝
- [x] 无「整单全接受」、无 AI 独自定稿、无策展对齐步骤推迟写入

**Out of this ticket:** 选支瞬间写策展（03 已禁止，回归由 06 收束）、升级/空洞作废未完成草案（05）、有序总 tracer（06）、修实际 SSH 执行、Y2 对齐步。

## Comments

HTTP 接缝：`ChangeCuratedDraftHttpAcceptanceTest`。已接受处理人 `POST /api/curated-drafts/{draftId}/items/{itemId}/accept|reject`（统一 `ApiResponse`）。拒绝 Y → 「应该在哪」仍 A、条目 `REJECTED`；接受 X → 「应该在哪」立即 B、条目 `ACCEPTED`，无新快照即 `PENDING_CLOSE`（非 `CLOSED`，`pendingCloseReminderVisible=true`）；既有 `POST /api/conflicts/{id}/confirm-close` 关单。非处理人/待接受：`PLAN_REQUIRES_ACCEPTED_HANDLER`。终态再审：`DRAFT_ITEM_NOT_PENDING`。事件 `ITEM_ACCEPTED`（`wroteCurated=true`，hint「条目已接受（含写入）」）/ `ITEM_REJECTED`（`wroteCurated=false`）。Flyway V14 扩 `conflict_case_event`。写入走 `CuratedTruthService.applyAcceptedRunsOnTarget`（不经建底 POST）；比对 `ConflictDetectionService.reconcileAfterCuratedWrite` 复用同一套 `reconcileAfterObservedWrite`。无整单全接受、无对齐步、无选支写策展。05 不作废草案。
