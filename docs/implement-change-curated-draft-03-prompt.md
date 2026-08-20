# 新对话：改策展票 03 TDD 重做（Prompt）

将下面整段复制到**新对话**作为第一条用户消息。若客户端支持手动附带 skill，请同时附上 `implement` 与 `tdd`。

仅在 **02 Status: done（TDD 重做完成）** 之后开工。票路径：`.scratch/change-curated-draft/issues/03-select-change-curated-draft.md`。

---

```text
/implement /tdd

请加载并严格遵循 skill：implement 与 tdd，以及 docs/agents/tdd.md。
本对话任务：TDD 重做 ArchOps 改策展工单 03——已接受处理人选改理想后生成 ≥2 条开放草案：不写策展、不出操作计划。不要做 04–06；不要重开 to-spec / to-tickets；不要改 CONTEXT 合同语义；不要重做竖切 01–13；不要重做已 TDD-done 的 01 与 02。

## 背景

- 01、02 TDD 重做已 done
- Spec：docs/specs/change-curated-draft.md；接缝已确认 = HTTP API
- 当前生产代码已有 Flyway V13 与选支出草案，但 ChangeCuratedDraftHttpAcceptanceTest 与实现同提交，没有 witnessed red → green → refactor

## 本票（唯一范围）

读并只验收：.scratch/change-curated-draft/issues/03-select-change-curated-draft.md

用户可感知行为按该票清单。本票只做到 tracer：选出草案且策展仍为 A、无活跃计划。按条接受/拒绝与立刻比对是 04。

## TDD 循环（强制）

把多行为测试拆成一圈一条（选支出草案、策展未写、无计划、非处理人拒绝、重复选支拒绝、FIX_ACTUAL 回归……）。第一圈必须红：若已绿，先去掉该票生产行为。不要修改已有 V13；新 schema 用下一号。HTTP 循环全绿后再接线薄 UI。红灯输出贴票 Comments。每圈绿灯后重构并提交。

收尾：./gradlew test，然后 /code-review。更新 docs/dev-handoff.md frontier → 04。不要实现 04。
```

---

完成后下一对话再实现 **04**（仍一次一张，仍走 TDD）。票路径 `.scratch/change-curated-draft/issues/04-itemized-accept-write-compare.md`。
