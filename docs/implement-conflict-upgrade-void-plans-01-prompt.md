# 新对话：冲突升级作废活跃计划票 01（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）；本票只取其 overlay 的 **capability / witnessed red**，不要走 Suite / tracer，也不要走 UI and helpers
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。本票是 **合同对齐能力票**（审计 A1），不是 suite/tracer，不是薄 UI，不是未绑定刀续票。循环纪律以 [`docs/agents/tdd.md`](agents/tdd.md) 的 **red → green → refactor** 为准。领域语义以 `CONTEXT.md` 与有效 ADR 为准；**不改合同、不立新 ADR**。

Matt 位置：竖切 01–13、改策展 01–06、未绑定 01–09 **已闭合**。用户已明示人排期开 A1。本对话只 `/implement` **conflict-upgrade-void-plans frontier = 01**。不要发明未绑定 10，不要做 ADR-0044 进程拆分，不要给 `change-curated-draft` 加 07。

来源：[`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md) **A1**。A2/A3 已由未绑定 05/09 闭合。B1–B5 禁止写入本票。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）实现 conflict-upgrade-void-plans frontier 工单 01（冲突升级作废活跃操作计划）。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。本票是合同对齐能力票（审计 A1），不是 TDD redo，不是 suite/tracer，不是 UI。竖切 / 改策展 / 未绑定生产已闭合，禁止删除它们来装红灯。禁止重做空洞作废 / 失联作废 / 草案作废的产品行为（只做不回归）。票外行为一律不做。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准。frontier = conflict-upgrade-void-plans 01）
- implement skill、tdd skill、docs/agents/tdd.md（capability：必须 witnessed red；不要走 Suite / UI and helpers）
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围、reason 字面量或是否覆盖 PENDING_CLOSE。下面已钉死。不要用 Playwright、SSH fake、computerUse 或薄 UI 当作完成定义。不要发明未绑定 10。不要拆执行引擎 / AI 编排层，不要把 WebClient/密钥加回控制面。

================================================================================
0. 任务边界（完成标准：一句话说出本票交付物，且不把 0044 / 未绑定 10 / UI / 问法算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/conflict-upgrade-void-plans/issues/01-upgrade-voids-active-plans.md

Spec：docs/specs/conflict-upgrade-void-plans.md
审计（只读，不要改报告正文为「已实现」以外的重写；可在手填注释外保持原样）：
- .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md（A1）

一句话交付：健康对象观测 B→C（及待确认关闭后再漂）触发冲突升级时，该冲突上活跃操作计划必须 VOIDED（voidReason=conflict_upgrade），不可再 start-execution；与空洞 / 失联路径同形，复用 voidActivePlans。

本票交付（HTTP 可断言）：
- OPEN + APPROVED（或 IN_REVIEW）计划 + 观测 B→C（无失联）→ 计划 VOIDED / voidReason=conflict_upgrade；start-execution → PLAN_VOIDED；事件含 UPGRADED + PLAN_VOIDED
- 升级后不得用 STALE 旧诊断选支（DIAGNOSIS_NOT_READY）
- PENDING_CLOSE 再漂 → 同键升级同样作废活跃计划
- 同快照重复比对 → 不作废计划
- 不回归 HeartbeatTimeoutHollowHttpAcceptanceTest、IdentityLostPipelineGateHttpAcceptanceTest、ChangeCuratedDraftVoidHttpAcceptanceTest

本票不做：
- ADR-0044 B1–B5；未绑定 10；改策展 07；UI；改 observedAskValue
- 新 ConflictStatus；改 CONTEXT.md / 已有 ADR；新路由；预期无 Flyway
- 重写空洞 / 失联作废逻辑；扩大生产直连 SSH；控制面 LLM/WebClient
- Maven、JPA 当地基、Vue、Neo4j v1 必选、Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。

================================================================================
1. 先读（完成标准：按序读完；用合同术语写作）
================================================================================

1. AGENTS.md
2. docs/agents/tdd.md（capability：witnessed red）
3. .scratch/conflict-upgrade-void-plans/issues/01-upgrade-voids-active-plans.md
4. docs/specs/conflict-upgrade-void-plans.md
5. CONTEXT.md — 「冲突升级」「AI 诊断」（升级后选支作废、活跃计划受阻即停取消）、「待确认关闭」再漂
6. docs/adr/0027、0038、0039、0043、0044（0044 只为禁止本票拆进程）
7. .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md — 只取 A1
8. docs/dev-handoff.md（确认 frontier = conflict-upgrade-void-plans 01）
9. 现行样板：
   - ConflictDetectionService.upgradeOpen / reopenFromPendingClose / onObservationBecameHollow / onIdentityLost / voidActivePlans
   - OperationPlanService.voidActivePlansForConflict
   - HeartbeatTimeoutHollowHttpAcceptanceTest（计划夹具 + void 断言）
   - IdentityLostPipelineGateHttpAcceptanceTest
   - ChangeCuratedDraftVoidHttpAcceptanceTest（升级作废草案，本票不拆）
   - .cursor/rules/backend-java.mdc

接缝：控制面公开 HTTP API。定义完成不要求 Vite / Playwright / MINA。

================================================================================
2. 生产落点与循环建议
================================================================================

生产改动应极窄：
- upgradeOpen：在 voidOpenForConflict 之后（或同段）调用 voidActivePlans(conflictId, "conflict_upgrade")
- reopenFromPendingClose：同样调用 voidActivePlans(conflictId, "conflict_upgrade")
- reason 字面量必须是 conflict_upgrade（与草案 void 理由一致；不要复用 observation_hollow_heartbeat_timeout / identity_lost）
- PLAN_VOIDED 事件已由 voidActivePlans 私有方法追加——复用，不要平行再写一套

建议循环（每圈一条 HTTP 测试 → witnessed red → green → refactor → 提交）：
A. OPEN B→C 作废 APPROVED 计划（主缺口；典型红灯：升级后 start-execution 仍 200 或计划仍 APPROVED）
B. PLAN_VOIDED 事件 + voidReason=conflict_upgrade（若 A 已覆盖可记 reuse，仍须显式断言）
C. PENDING_CLOSE 再漂作废活跃计划
D. 同快照重复比对不作废（负面）
E. 回归：空洞 / 失联 / 改理想草案作废套件仍绿（可 cleanTest 相关类；票结束全量 ./gradlew test）

夹具提示：三主机或两宿主足够——策展 A、先观测 B 开冲突并走完认领→诊断→选 FIX_ACTUAL→审计划，再心跳观测到 C。不要引入失联标。不要走 SSH 真执行（archops.ssh.mode=fake 即可；本票断言停在 start-execution 被拒或计划已 VOIDED）。

必须保持绿（不要改它们来迁就本票）：
- HeartbeatTimeoutHollowHttpAcceptanceTest
- IdentityLostPipelineGateHttpAcceptanceTest
- ChangeCuratedDraftVoidHttpAcceptanceTest
- UnboundIdentityRebindTracerHttpAcceptanceTest、VerticalSliceHttpE2eAcceptanceTest、ControlledSshExecHttpAcceptanceTest
- IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest

质量优先的裁决：
- 想拆执行引擎 / 加步骤断言 → 停（0044）
- 想改问法 / 失联闸门 → 停
- 想发明未绑定 10 或改策展 07 → 停
- 想在无观测变化时也 void 计划 → 停（负面 D）
- 想改 CONTEXT / ADR 正文「澄清」→ 停；本票只实现已有合同

Git：从最新 origin/main 开分支。Cloud 分支名须匹配 cursor/<slug>-<本 run 指定后缀>。建议 slug：tdd-implement-conflict-upgrade-void-01。每圈可提交，信息写 why。不 force push、不 amend。

票结束：
- cd backend && ./gradlew cleanTest test
- 验收清单全勾；Status: done
- 更新 docs/dev-handoff.md：A1 票 01 TDD-done；不要自动做 0044；不要发明未绑定 10
- /code-review（Standards + Spec）
- 不要改 CONTEXT / ADR 正文
```
