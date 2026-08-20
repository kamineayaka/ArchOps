# 02 — 诊断同时给出「修实际」与「改理想」分叉

**What to build:** 当合并键上策展 `运行于` 与**当前可用**观测宿主两侧都有值且不等时，当前诊断对查看者同时给出两条只读分叉：既有「修实际回策展宿主」，以及「改理想」（承认实际、把策展对齐到当前可用观测宿主）。任意已认证查看者都能读到改理想分叉，但本票还不把选支写成可写路径。纯空洞与观测消失不得把改理想当成唯一落点。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

**TDD redo:** yes — 验收标准不变。先前实现与测试同提交，不算 TDD 完成。按 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md) 从 witnessed red 重做。在 01 TDD-done 之后再开（两张 unblocked 时按编号最小）。

从竖切 MVP 往上长：规则诊断曾在宿主不等时只发出 `FIX_ACTUAL`，既有验收按「仅一条 / 第一条即修实际」断言。本票保留修实际分叉，并增加 `CHANGE_CURATED`（建议稳定 id：`CHANGE_CURATED_TO_OBSERVED`，`kind=CHANGE_CURATED`）；把按序号钉死的断言改成按分叉 id 识别，避免打掉竖切回归。TDD 重做从红灯开始（见 Comments）。

- [ ] 可用策展宿主 A、可用观测宿主 B（不等）时，GET 当前诊断同时包含 `FIX_ACTUAL` 与 `CHANGE_CURATED`；改理想目标为当前可用观测宿主
- [ ] 改理想分叉文案使用合同术语（改理想 / 策展 / 观测 / 草案），不出现「以观测为准」「裁定」等 Avoid 词
- [ ] 纯观测空洞：仍只有恢复观测通道/心跳/核验类分叉，不含改理想
- [ ] 观测消失（可用值为不存在）：保持既有恢复/核验类分叉，不发明「策展改为不存在」条目或分叉
- [ ] 非处理人仍可只读看到分叉；本票不实现选支写入草案
- [ ] 既有「修实际」HTTP 验收仍绿（允许改为按 id 认分叉，而不是 `forks[0]` / 长度恒为 1）

**Out of this ticket:** 选支、草案表、逐条确认、关建底覆盖（见 01）、立刻比对、UI 选支按钮文案可留到 03。无 LLM 起草（规则引擎即可）。

## Comments

HTTP 接缝（先前同提交落地，**不是** TDD 完成证据）：`ConflictDiagnosisHttpAcceptanceTest`。宿主 A vs 可用观测 B 时 GET `/api/conflicts/{id}/diagnosis` 同时含 `FIX_ACTUAL_TO_CURATED` 与 `CHANGE_CURATED_TO_OBSERVED`（目标写入改理想 description）。空洞/观测消失仍只恢复/核验类分叉。按 fork id 认修实际，避免 `forks[0]`。无草案表、无选支写入。

TDD 重做：一圈一条诊断行为；多断言测试拆成单行为方法；若已绿则先去掉该票生产行为。不要做 01/03–06。

### Step A — restore FIX_ACTUAL-only mismatch (honest red; not product-done)

Removed `CHANGE_CURATED` emission from `DiagnosisRuleEngine.diagnoseRunsOnMismatch` (pre-0c6e48d: available A vs B only emits `FIX_ACTUAL_TO_CURATED`). Kept `CHANGE_CURATED_TO_OBSERVED` constant for ticket 03 compile. Split the combined HTTP method: ticket 06 keeps 修实际 by fork id; cycle 1 is `mismatchDiagnosisIncludesFixActualAndChangeCuratedForks` only (ids + kinds). No draft write, no Flyway.

Red command 1 (combined method, before split):

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.warningExistsBeforeDiagnosisReadyAndRulesProduceFixActualAndChangeCuratedForks
```

```text
ConflictDiagnosisHttpAcceptanceTest > warningExistsBeforeDiagnosisReadyAndRulesProduceFixActualAndChangeCuratedForks() FAILED
    java.lang.AssertionError at ConflictDiagnosisHttpAcceptanceTest.java:73

java.lang.AssertionError: JSON path "$.data.forks[*].id"
Expected: (a collection containing "FIX_ACTUAL_TO_CURATED" and a collection containing "CHANGE_CURATED_TO_OBSERVED")
     but: a collection containing "CHANGE_CURATED_TO_OBSERVED" mismatches were: [was "FIX_ACTUAL_TO_CURATED"]

BUILD FAILED in 17s
```

GET `/api/conflicts/{id}/diagnosis` body had a single fork `FIX_ACTUAL_TO_CURATED` (kind `FIX_ACTUAL`). Not a compile fail.

Red command 2 (split single-behavior method):

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.mismatchDiagnosisIncludesFixActualAndChangeCuratedForks --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.warningExistsBeforeDiagnosisReadyAndRulesProduceFixActualFork
```

```text
ConflictDiagnosisHttpAcceptanceTest > mismatchDiagnosisIncludesFixActualAndChangeCuratedForks() FAILED
    java.lang.AssertionError at ConflictDiagnosisHttpAcceptanceTest.java:100

java.lang.AssertionError: JSON path "$.data.forks[*].id"
Expected: (a collection containing "FIX_ACTUAL_TO_CURATED" and a collection containing "CHANGE_CURATED_TO_OBSERVED")
     but: a collection containing "CHANGE_CURATED_TO_OBSERVED" mismatches were: [was "FIX_ACTUAL_TO_CURATED"]

2 tests completed, 1 failed
BUILD FAILED in 5s
```

Ticket 06 修实际-by-id still green in the same run. Also:

```text
cd backend && ./gradlew test --tests com.archops.plan.OperationPlanReviewHttpAcceptanceTest.acceptedHandlerSelectsFixActualGeneratesPlanAndApproves
BUILD SUCCESSFUL in 5s
```

### Step C — cycle 1: GET diagnosis includes FIX_ACTUAL and CHANGE_CURATED

Red (same split method as Step A command 2; still only `FIX_ACTUAL_TO_CURATED` before this slice’s production change).

Green: `diagnoseRunsOnMismatch` emits `CHANGE_CURATED_TO_OBSERVED` with `kind=CHANGE_CURATED` alongside 修实际. Copy left empty for later cycles.

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.mismatchDiagnosisIncludesFixActualAndChangeCuratedForks
BUILD SUCCESSFUL in 5s
```

Refactor (no behavior change): HTTP helpers `seedAvailableRunsOnMismatch` / `getDiagnosis`. Same tests still green:

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.mismatchDiagnosisIncludesFixActualAndChangeCuratedForks --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.warningExistsBeforeDiagnosisReadyAndRulesProduceFixActualFork
BUILD SUCCESSFUL in 5s
```

### Step D — cycle 2: 改理想 target is the current available observed host

Red:

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.changeCuratedForkTargetsCurrentAvailableObservedHost
```

```text
ConflictDiagnosisHttpAcceptanceTest > changeCuratedForkTargetsCurrentAvailableObservedHost() FAILED
    java.lang.AssertionError at ConflictDiagnosisHttpAcceptanceTest.java:84

java.lang.AssertionError: JSON path "$.data.forks[?(@.id=='CHANGE_CURATED_TO_OBSERVED')].description"
Expected: a collection containing a string containing "host-..."
     but: mismatches were: [was ""]

BUILD FAILED in 5s
```

Green: CHANGE_CURATED description is the current available observed host label (id from GET setup). Cycle 1 still green.

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.changeCuratedForkTargetsCurrentAvailableObservedHost --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.mismatchDiagnosisIncludesFixActualAndChangeCuratedForks
BUILD SUCCESSFUL in 4s
```

Refactor: no extra production structure; observed host already labeled via existing `label()`.

### Step E — cycle 3: 改理想 copy uses contract terms

Red:

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.changeCuratedForkCopyUsesContractTerms
```

```text
ConflictDiagnosisHttpAcceptanceTest > changeCuratedForkCopyUsesContractTerms() FAILED
    org.opentest4j.AssertionFailedError at ConflictDiagnosisHttpAcceptanceTest.java:97

org.opentest4j.AssertionFailedError: expected: <true> but was: <false>

BUILD FAILED in 4s
```

Copy was the observed host label only; missing 改理想 (and would miss 策展 / 观测 / 草案).

Green: label `改理想`, hypothesis `承认实际、更新策展`, description names 策展 `运行于` → 当前可用观测宿主 and 草案逐条确认. Avoid 以观测为准 / 裁定. Cycles 1–2 still green.

```text
cd backend && ./gradlew test --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.changeCuratedForkCopyUsesContractTerms --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.changeCuratedForkTargetsCurrentAvailableObservedHost --tests com.archops.conflict.ConflictDiagnosisHttpAcceptanceTest.mismatchDiagnosisIncludesFixActualAndChangeCuratedForks
BUILD SUCCESSFUL in 5s
```

Refactor: copy lives on the existing CHANGE_CURATED `ForkSuggestion`; no new types.

