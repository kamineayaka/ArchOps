# Spec: 冲突升级作废活跃操作计划

**Status**: spec published；工单 01 **TDD-done**；**本刀闭合**  
**Basis**: `CONTEXT.md`「AI 诊断」「冲突升级」；ADR-0027；ADR-0038；ADR-0039（合同已冻结，本刀**不改**合同、不立新 ADR）  
**Source**: [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md) **A1**  
**Predecessor**: 竖切 / 改策展 / 未绑定刀均已闭合。空洞路径（竖切票 10）与身份失联落地（未绑定票 05）已会作废活跃计划。本刀补上健康对象观测 B→C 的 `upgradeOpen`（及待确认关闭后再漂的同键升级）作废活跃计划。  
**Testing seams (confirmed)**: **唯一验收主接缝 = 控制面公开 HTTP API**（含 Agent 心跳/快照 ingest）。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**。前端不进本刀自动化主接缝。

**Confirmed scope pins**

1. 不改 `CONTEXT.md`，不重开 ADR-0039 / 0027 / 0038，**不需要新 ADR**。实现向合同对齐。
2. 只补「冲突升级 → 作废活跃操作计划」；重诊 / 草案作废 / 选支须 READY 诊断已存在，本刀不重做。
3. 主接缝为 HTTP API only。不拆执行引擎 / AI 编排层（ADR-0044）。

---

## Problem Statement

合同与 ADR-0027 要求：冲突升级时旧选支作废、活跃计划受阻即停取消，须基于新诊断重走流水线。现码 `ConflictDetectionService.upgradeOpen()`（及 `reopenFromPendingClose`）只作废 OPEN 改理想草案并 `scheduleAsyncDiagnosis`，**不**调用 `OperationPlanService.voidActivePlansForConflict`。空洞挂起与身份失联落地会作废计划。结果：健康对象观测由 B 变为 C 后，指向过时实际的 APPROVED/待审计划仍可审/执行。

---

## Solution

在既有升级路径上复用 `voidActivePlans` / `voidActivePlansForConflict`（与空洞 / 失联同形）：

- OPEN 冲突上观测实际值变化（B→C）→ `upgradeOpen`：保留升级脉络与 `UPGRADED` 事件、作废 OPEN 草案、重诊，**并**作废该冲突上全部活跃操作计划；`voidReason` 用 `conflict_upgrade`；追加 `PLAN_VOIDED` 事件。
- 待确认关闭期间再漂导致同键升级 → `reopenFromPendingClose`：同样作废活跃计划（合同：退出待确认关闭并按冲突升级处理）。
- 同一观测快照重复比对（未真正升级）不得作废计划。
- 空洞 / 身份失联路径与改策展升级作废草案行为不回归。

---

## User Stories

1. As a 冲突处理人 with an APPROVED 操作计划 targeting observed host B, when a fresh heartbeat moves the usable observed `运行于` to C (curated still A, subject not 身份失联), I want that plan VOIDED with `voidReason=conflict_upgrade`, so that I cannot `start-execution` against the stale actual.
2. As any viewer, I want a `PLAN_VOIDED` conflict event listing the voided plan id and reason `conflict_upgrade`, so that the upgrade audit trail matches the hollow / 失联 paths.
3. As a 冲突处理人, after that upgrade I want the prior diagnosis STALE and a new diagnosis scheduled, and I must not select a branch on the stale diagnosis, so that U1 / W1 from ADR-0027 hold (already largely true; do not regress).
4. As a 冲突处理人, when PENDING_CLOSE drifts (observed leaves equality) and the case returns to OPEN via the same merge key, I want any active 操作计划 on that conflict VOIDED for the same reason, so that pending-close drift is treated as 冲突升级 for plans too.
5. As QA, I want heartbeat-timeout hollow voiding (`observation_hollow_heartbeat_timeout`) and 身份失联 voiding (`identity_lost`) to keep passing, so that this knife only closes the upgrade gap.
6. As QA, I want OPEN 改理想草案 still VOIDED on upgrade (`conflict_upgrade`), so that ChangeCuratedDraftVoidHttpAcceptanceTest does not regress.

---

## Out of Scope

- ADR-0044 执行引擎 / AI 编排层 / B-live / 步骤断言 / 控制面 WebClient
- 发明未绑定 10；改策展 07；重拆竖切 01–13
- 新 `ConflictStatus`；改 `CONTEXT.md` / 已有 ADR 正文
- 前端薄 UI；Playwright；JWT
- 改 `observedAskValue` / 身份失联问法（票 09 已闭合）
- 扩大生产直连 SSH

---

## Further Notes

- **Issue tracker**: [`.scratch/conflict-upgrade-void-plans/issues/`](../../.scratch/conflict-upgrade-void-plans/issues/)  
- **Next Matt step**: 票 01 **TDD-done**；本刀闭合。不要自动做 ADR-0044 进程拆分，不要发明未绑定 10。  
- **Why no new ADR**: A1 is a code–contract gap against already-frozen CONTEXT + ADR-0027.  
- **Prompt for next chat**: 人排期下一刀后再 `/implement`。不要做 ADR-0044 拆分，不要发明未绑定 10。
