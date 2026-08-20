# 01 — 关闭建底 POST 覆盖已有 `运行于`

**What to build:** 竖切建底仍可通过策展建底 API **插入**某容器的第一条 `运行于` 事实（主机/容器建底保留）。一旦该事实已存在，同一条 POST 再提交必须被拒绝，库中宿主不变。这样冲突路径（以及之后任何路径）不能再靠建底接口旁路草案去改策展真相。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

**TDD redo:** yes — 验收标准不变。先前实现与测试同提交，不算 TDD 完成。按 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md) 从 witnessed red 重做。

从竖切 MVP 往上长：建底 POST 曾对已有 `运行于` 覆盖写入。本票只关掉这条旁路，不引入草案、不改诊断、不改选支。TDD 重做从红灯开始（见 Comments）。

- [ ] 对尚无 `运行于` 的容器，建底 POST 仍可插入第一条事实，随后「应该在哪」可读到该宿主
- [ ] 对已有 `运行于` 的容器，再 POST（同一或不同宿主）被拒绝；事实与「应该在哪」均不变
- [ ] 拒绝行为可经控制面公开 HTTP API 断言（统一信封）；不测 Mapper/Redis 内部
- [ ] 不引入草案、选支或策展对齐步骤；不修改已有 Flyway 历史脚本

**Out of this ticket:** 改理想分叉、草案逐条确认、比对触发、UI、SSH、Y2 策展对齐步骤。

## Comments

HTTP 接缝（先前同提交落地，**不是** TDD 完成证据）：`CuratedTruthHttpAcceptanceTest.bootstrapRunsOnPostRejectsOverwriteOfExistingFact`。`confirmRunsOn` 对已有 `运行于` 抛 `CURATED_RUNS_ON_EXISTS`（统一信封 400）；首次插入与「应该在哪」保留。无草案表、无 Flyway。

TDD 重做：若覆盖拒绝测试对当前生产代码已绿，先恢复「已有则 update target」让第一圈变成诚实红灯（第二下 POST 为 200 且「应该在哪」变成 B；不要只删 throw 去撞 UNIQUE 变 500）。一圈一条测试；红灯输出贴本段；绿灯后重构再提交。开工 prompt：[`docs/implement-change-curated-draft-01-prompt.md`](../../../docs/implement-change-curated-draft-01-prompt.md)。不要做 02–06。

### Step A — restore overwrite (honest red; not product-done)

Restored `confirmRunsOn` to update `targetId` when a `运行于` fact already exists (pre-e009d30 bypass). First insert still inserts. No reject logic in this step.

Red command:

```text
cd backend && ./gradlew test --tests com.archops.curated.CuratedTruthHttpAcceptanceTest.bootstrapRunsOnPostRejectsOverwriteOfExistingFact
```

Red output (honest overwrite, not UNIQUE 500):

```text
CuratedTruthHttpAcceptanceTest > bootstrapRunsOnPostRejectsOverwriteOfExistingFact() FAILED
    java.lang.AssertionError at CuratedTruthHttpAcceptanceTest.java:136

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///workspace/backend/build/reports/tests/test/index.html

BUILD FAILED in 5s

java.lang.AssertionError: Status expected:<400> but was:<200>
	at com.archops.curated.CuratedTruthHttpAcceptanceTest.bootstrapRunsOnPostRejectsOverwriteOfExistingFact(CuratedTruthHttpAcceptanceTest.java:136)
```

Second POST `/api/curated/facts/runs-on` (different host B) returned HTTP 200 `success=true` with `data.target.name=host-ow-b` (same fact id, target overwritten). Not HTTP 500 / UNIQUE.

Regression still green:

```text
cd backend && ./gradlew test --tests com.archops.curated.CuratedTruthHttpAcceptanceTest.createHostsContainerConfirmRunsOnAndAskShouldWhere
BUILD SUCCESSFUL in 5s
```
