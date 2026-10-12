# 01 — 探测任务判决薄页

**What to build:** 人可以在一台主机上保存探测任务（命令 + 策展结构化值），并看到控制面的判决：正常、带原始证据的冲突、空洞。冲突上可以请求修实际（待审计划）或改理想（开放草案，不写策展）。空洞上只有恢复观测通道。同一主机的多条任务各自独立。页面不选原因，不展示操作者。

**Blocked by:** None. Baseline is origin/main at `3b41d29601aeb81bfc14470891dfcb0043006973` (PR #132). ADR-0048 票 01 已闭合，不重开。

**Status:** ready-for-agent

**TDD:** UI。判决 HTTP 已绿，不重测。探测冲突上的改理想 / 修实际若还打不开草案或待审计划，先在 HTTP 上补那一个行为（red → green → refactor），再写页面。页面完成定义与冲突去掉协作身份票 07、未绑定票 07 相同：`npm run build` + 浏览器按人的路径点一遍。Spec：[`docs/specs/probe-task-thin-ui.md`](../../../docs/specs/probe-task-thin-ui.md)。

- [ ] 人保存探测任务后读回命令与策展值；尚无交回时判决为空洞
- [ ] 同一主机两条任务的判决互不影响
- [ ] 相等显示正常，没有修实际 / 改理想
- [ ] 不等显示冲突和原始证据；修实际打开待审计划；改理想打开草案且策展值不变
- [ ] 读不出或逾期显示空洞，唯一动作是恢复观测通道
- [ ] 页面没有原因选择，也没有操作者；人审请求不发送用户身份
- [ ] `npm run build` 通过

**Out of this ticket:** Agent 执行命令、Kubernetes、编排层 gRPC、AI 建议正文、原因种类、「无法归类」、协作身份、重开 ADR-0048 票 01。

## Comments

### Cycle A — 改理想 on a probe conflict opens a draft and does not write 策展
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskResolutionHttpAcceptanceTest.changeCuratedOnProbeConflictOpensDraftWithoutWritingCurated --offline`
`AssertionError: Status expected:<200> but was:<400>` body `{"success":false,"code":"DRAFT_ITEMS_INCOMPLETE","message":"改理想草案规则夹具需要至少两条可独立确认的「运行于」条目","data":null}`
Green command: same test, exit 0. Draft `OPEN` / `CHANGE_CURATED`, item `PENDING`, `createdBy` null, task `curatedExpected.up` still true.
Refactor: runs-on and probe drafts share `insertOpenChangeCuratedDraft` and `appendDraftCreated`. Same test re-run, exit 0.
Commit: `726e5f3` Open a probe 改理想 draft without writing the curated value.

### Cycle B — 修实际 on a probe conflict opens a review plan
Red command: `cd backend && ./gradlew test --tests com.archops.observed.ProbeTaskResolutionHttpAcceptanceTest.fixActualOnProbeConflictOpensReviewPlan --offline`
reuse/regression: first run exit 0. Covered by `OperationPlanService.selectBranch` (`AnonymousBranchSelectionHttpAcceptanceTest.fixActualWithoutIdentityOrHandlerOpensReviewPlan`). Plan `DRAFT_REVIEW` / `FIX_ACTUAL`, `createdBy` null, curated `up` still true.
Refactor: heartbeat helper takes the agent id. Same test re-run, exit 0.

