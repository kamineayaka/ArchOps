# 01 — 解决动作不带原因种类

**What to build:** 诊断不再当原因目录。两侧可读且不等时，人可请求修实际与改理想；观测空洞只可请求恢复观测通道；身份失联仍是标志，不是诊断种类。案件不带原因种类，不增加「无法归类」，不增加新的原因枚举。改理想仍出逐条草案且接受前不写策展；修实际仍出待审计划。

**Blocked by:** None. Baseline is origin/main at or after `ec73bed`, which contains ADR-0047.

**Status:** ready-for-agent

**TDD:** capability。主接缝为控制面 HTTP API。合同：ADR-0047。Spec：[`docs/specs/conflict-evidence-not-cause-kind.md`](../../../docs/specs/conflict-evidence-not-cause-kind.md)。

- [x] 两侧可读且不等：诊断列出 `FIX_ACTUAL_TO_CURATED` 与 `CHANGE_CURATED_TO_OBSERVED`，文案含修实际与改理想；响应没有原因种类 `kind`，也没有「无法归类」
- [ ] 请求改理想仍打开逐条草案，且在条目接受前不写策展；请求修实际仍打开待审操作计划
- [ ] 观测空洞：诊断只有恢复观测通道（`RESTORE_HEARTBEAT_CHANNEL`，文案「恢复观测通道」），不提供凭过期实际的修现场
- [ ] 身份失联仍是冲突标志；诊断没有 `EXPLAIN_IDENTITY_LOST`，也不把修实际 / 改理想当成唯一落点
- [ ] 不新增原因枚举，不新增「无法归类」

**Out of this ticket:** 探测任务 CRUD、Agent 拉取、Kubernetes 清单、编排层进程、Spring Cloud、拆控制面、用户抽取、ABSENT 执行、占位 SSH、观测回写、ADR-0048 / ADR-0049 的实现。

## Comments

### Cycle A — readable unequal lists 修实际 and 改理想 without a cause kind
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.ResolutionActionHttpAcceptanceTest.readableUnequalListsRepairActualAndChangeIdealWithoutCauseKind --offline`
`AssertionFailedError` at line 68: fork `FIX_ACTUAL_TO_CURATED` still had `"kind":"FIX_ACTUAL"` (expected kind to be absent).
Green command: same test, exit 0. Diagnosis forks no longer carry `kind`. Branch selection matches the action id.
Refactor: engine comment states it lists resolution actions, not a cause catalog. Older HTTP tests stop requiring the removed kind field.
Commit: pending.
