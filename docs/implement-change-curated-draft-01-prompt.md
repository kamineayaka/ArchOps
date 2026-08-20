# 新对话：改策展票 01 TDD 重做（Prompt）

将下面整段复制到**新对话**作为第一条用户消息。若客户端支持手动附带 skill，请同时附上：

- `implement`（`.cursor/skills/implement/SKILL.md` 或 `.agents/skills/implement/SKILL.md`）
- `tdd`（`.cursor/skills/tdd/SKILL.md` 或 `.agents/skills/tdd/SKILL.md`）

Frontier：`.scratch/change-curated-draft/issues/01-close-bootstrap-runs-on-overwrite.md`。一次只做这一张。这是 **TDD 重做**：验收标准不变；先前同提交落地不算完成。

---

```text
/implement /tdd

请加载并严格遵循 skill：implement 与 tdd，以及 docs/agents/tdd.md。
本对话任务：TDD 重做 ArchOps 改策展 frontier 工单 01——关闭建底 POST 覆盖已有 `运行于`。不要做 02–06；不要重开 to-spec / to-tickets；不要改 CONTEXT 合同语义；不要重做竖切 01–13。

## 背景

- 领域合同冻结：CONTEXT.md + ADR-0039
- 技术栈冻结：ADR-0043
- 竖切 MVP 01–13 已闭合；本刀从该实现往上长
- Spec：docs/specs/change-curated-draft.md（Testing seams 已确认 = HTTP API）
- 工单：.scratch/change-curated-draft/issues/01-close-bootstrap-runs-on-overwrite.md
- 当前生产代码里已有 CURATED_RUNS_ON_EXISTS 行为与测试，但没有 witnessed red → green → refactor。本票要按 TDD 重做，不扩大产品行为。

## 本票（唯一范围）

读并只验收该工单清单。用户可感知行为：

- 尚无 `运行于` 的容器：POST /api/curated/facts/runs-on 仍可插入第一条；随后「应该在哪」可读到该宿主
- 已有 `运行于` 的容器：再 POST（同一或不同宿主）必须拒绝；事实与「应该在哪」均不变
- 拒绝经 HTTP 可断言（统一信封）

## 必读

1. AGENTS.md
2. docs/agents/tdd.md
3. 本票文件
4. docs/specs/change-curated-draft.md 中 Curated write bypass 与 Testing Decisions
5. CONTEXT.md
6. docs/adr/0043-tech-stack.md
7. docs/adr/0006-curated-writes-via-itemized-proposals.md
8. 现行样板：CuratedTruthHttpAcceptanceTest 与 POST /api/curated/facts/runs-on

## TDD 循环（强制）

每一圈：一条测试 → 跑红 → 把红灯命令与输出贴到票 Comments → 只写刚好够用的生产代码 → 跑绿 → 重构（行为不变）→ 提交该切片。

第一圈必须是红灯。若覆盖拒绝测试对当前代码已绿，先去掉该票生产行为再写/跑测试。一圈一条行为（插入成功与覆盖拒绝可以是两个循环）。风格对齐 *HttpAcceptanceTest。业务错误走 BusinessException + 统一信封。Flyway：本票无新表则不要空增脚本。

收尾：cd backend && ./gradlew test，再 /code-review（Standards + Spec）。/code-review 替代不了每圈的重构。

## 完成时必须做

1. 本票验收项全部勾上；Status: done；Comments 里有每一圈的 red 记录
2. 更新 docs/dev-handoff.md：frontier 变为 02（01 TDD-done）；不要顺手实现 02
```

---

完成后下一对话再 **TDD 重做 02**。提示词 [`docs/implement-change-curated-draft-02-prompt.md`](implement-change-curated-draft-02-prompt.md)。
