# 新对话：改策展票 02 TDD 重做（Prompt）

将下面整段复制到**新对话**作为第一条用户消息。若客户端支持手动附带 skill，请同时附上 `implement` 与 `tdd`。

仅在 **01 Status: done（TDD 重做完成）** 之后开工。票路径：`.scratch/change-curated-draft/issues/02-diagnosis-change-curated-fork.md`。

---

```text
/implement /tdd

请加载并严格遵循 skill：implement 与 tdd，以及 docs/agents/tdd.md。
本对话任务：TDD 重做 ArchOps 改策展工单 02——诊断同时给出「修实际」与「改理想」分叉。不要做 01/03–06；不要重开 to-spec / to-tickets；不要改 CONTEXT 合同语义；不要重做竖切 01–13。

## 背景

- 01 TDD 重做已 done（建底 POST 覆盖拒绝）
- Spec：docs/specs/change-curated-draft.md；接缝已确认 = HTTP API
- 当前生产代码已发出 CHANGE_CURATED_TO_OBSERVED，但没有 witnessed red → green → refactor

## 本票（唯一范围）

读并只验收：.scratch/change-curated-draft/issues/02-diagnosis-change-curated-fork.md

- 可用策展 A vs 可用观测 B：GET 诊断同时含 FIX_ACTUAL 与 CHANGE_CURATED；改理想目标为当前可用观测宿主
- 文案用合同术语；空洞与观测消失不含改理想
- 本票不实现选支写入草案
- 修实际 HTTP 验收仍绿（按 fork id，不用 forks[0]）

## TDD 循环（强制）

每一圈一条诊断行为。多断言测试拆成单方法。第一圈必须红：若已绿，先去掉该票生产行为。红灯输出贴票 Comments。绿灯后重构再提交。Flyway：本票无新表则不要空增。

收尾：./gradlew test，然后 /code-review。更新 docs/dev-handoff.md frontier → 03。不要实现 03。
```

---

完成后下一对话再 **TDD 重做 03**。提示词 [`docs/implement-change-curated-draft-03-prompt.md`](implement-change-curated-draft-03-prompt.md)。
