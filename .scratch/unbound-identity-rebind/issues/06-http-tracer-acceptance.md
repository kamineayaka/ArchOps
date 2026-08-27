# 06 — HTTP 主接缝有序 tracer（happy path + 负面最小集）

**What to build:** 为本刀建立一条以控制面公开 HTTP API（含 Agent 心跳/快照 ingest）为唯一自动化主接缝的有序验收：先策展后缺标 → 未绑定 + 身份失联 → 草案逐条（新建 / 绑定互斥）→ 绑定不写可靠实际 → 补标命中收尾 → 失联闸门负面。不以浏览器自动化或 SSH fake 作为完成定义。前端薄 UI 只作手工/冒烟，不进本票 CI 门槛。

**Blocked by:** 01 — 推断失联与 upsert；02 — 发起草案；03 — 逐条新建/绑定；04 — 标签命中收尾；05 — 失联闸门

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md) 的 **Suite / tracer tickets**。01–05 能力票绿灯之后再开。本票钉有序 HTTP 套件，不实现新产品；禁止删除 01–05 生产来装红灯。Spec tracer：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

从竖切票 13 / 改策展票 06 往上长：那些套件保持独立，不要重写。本票新开本刀套件（`*HttpAcceptanceTest` 风格、统一信封、只断言后续 HTTP 可读状态）。

Happy path（须按序、可在 CI 稳定跑通）对齐 Spec「HTTP tracer」：

1. 建底主机 A/C；策展容器 X `运行于` A（现场未打标）
2. Agent 在 A 上报缺标 `runtimeId=r1` → 待并入 + X 身份失联；「实际在哪」不得报旧宿主为实际；`by-merge-key` 不承诺升级链
3. Agent 在 C 的快照不得给 X 打失联
4. `UNKNOWN_OBJECT_ID` 候选发草案 ≥2 条（新建 + `运行于`）；接受前无该策展对象
5. 拒 `运行于`、接受新建 → 再心跳标签命中写观测；不人造待确认关闭
6. 失联候选草案：绑定 vs 新建；双接受失败；只接受绑定后不写可靠观测 `运行于`，`r1` 离开待并入
7. 再缺标心跳 `r1` → 仍失联、不复活为可新建候选
8. 正确标签命中 → 清失联、消费绑定、可恢复升级链

- [x] 上列有序 happy path 可在 CI 经 HTTP 稳定跑通
- [x] 负面：他机快照不给 X 打失联
- [x] 负面：未打标同名不承诺升级链
- [x] 负面：绑到仍健康命中对象失败
- [x] 负面：同一候选第二份开放草案失败
- [x] 负面：未认证不可写草案
- [x] 负面：`MISSING_LABEL` 新建不是成功路径
- [x] 负面：双接受绑定+新建失败
- [x] 负面：失联时选支失败；诊断不再给旧实际落点；计划 / 改理想草案作废；待确认关闭退回开放
- [x] 负面：`absentObjectIds` 走观测消失并解除待补标绑定记忆
- [x] 负面：同一 `runtimeId` 刷新不作废开放未绑定草案
- [x] 负面：建底 POST 覆盖已有 `运行于` 仍拒绝
- [x] 负面：标签命中作废开放未绑定草案
- [x] 断言只落 HTTP 状态码、统一信封、后续 GET 可读状态；不把前端自动化当完成门槛

**Out of this ticket:** 新产品能力（应已由 01–05 交付）；Playwright；SSH fake 作为第二接缝；薄 UI（见 07）。

## Comments

01–05 + 08 已 TDD-done，本票已 unblocked。开场 prompt：[`docs/implement-unbound-identity-rebind-06-prompt.md`](../../../docs/implement-unbound-identity-rebind-06-prompt.md)。suite 首跑绿记 reuse/regression。不要做 07。不要做票 09。代码 vs ADR-0044 审计 **A2 已由 05 交付**；**A3** 是票 09；**A1** 与 0044 进程债禁止写入本票。见 [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../audit-code-vs-adr-0044.md)。

### Cycle A — 有序 happy path（Spec tracer 1–8 + 可选 9）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.happyPath_curatedThenMissingLabel_unboundIdentityLost_draftAndBind_labelMatchRestoresUpgradeChain`
Output:
首跑红是夹具断言过严，不是组合缝：`UNKNOWN_OBJECT_ID` 在宿主已有失联时草案还会带 `BIND_UNBOUND_TO_EXISTING`（Spec 只要 ≥2 条含新建+运行于）；拒策展 `运行于` 后 `GET actual-where` 走既有 `CURATED_RUNS_ON_NOT_FOUND`，观测命中改从心跳 `matched` 读。修测试后 **green reuse/regression**。
覆盖它的聚焦测试：`UnboundIdentityLostIngestHttpAcceptanceTest`（01 缺标/失联/问法/他机不打失联）、`UnboundDraftCreateHttpAcceptanceTest`（02 未绑定草案不挂 conflictId）、`UnboundDraftItemReviewHttpAcceptanceTest`（03 拒运行于+接受新建、绑定互斥）、`UnboundBindGateHttpAcceptanceTest`（08 绑定不写可靠实际）、`UnboundLabelMatchConsumeHttpAcceptanceTest`（04 补标收尾）、`IdentityLostPipelineGateHttpAcceptanceTest`（05 命中后诊断可再出 unique-site forks）、`VerticalSliceHttpE2eAcceptanceTest.negative_unlabeledSnapshotDoesNotPromiseUpgradeChain`。
Production: 无
Refactor: 抽出 `unlabeledAndLabeledSnapshot`；UNKNOWN 草案断言改为 `hasItems` 新建+运行于
Commit: 2222a54 / f0d8f88

### Cycle B — 他机快照不给 X 打失联（Neg 1）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.otherHostSnapshotDoesNotMarkIdentityLostOnX`
Output:
**green reuse/regression**。覆盖：`UnboundIdentityLostIngestHttpAcceptanceTest.otherHostSnapshotDoesNotMarkIdentityLost`（01）。
Production: 无
Refactor: 无（复用套件 heartbeat / identity-lost helpers）
Commit: 本圈提交

### Cycle C — 未打标同名不承诺升级链（Neg 2）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.unlabeledSameNameDoesNotPromiseUpgradeChain`
Output:
**green reuse/regression**。覆盖：`VerticalSliceHttpE2eAcceptanceTest.negative_unlabeledSnapshotDoesNotPromiseUpgradeChain`、`UnboundIdentityLostIngestHttpAcceptanceTest.unlabeledSameNameDoesNotPromiseUpgradeChain`、`IdentityLostPipelineGateHttpAcceptanceTest.unlabeledSameNameStillDoesNotOpenConflict`。
Production: 无
Refactor: 无
Commit: cd9d424

### Cycle D — 绑到已绑定目标失败（Neg 3）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.secondFieldEntityCannotBindToAlreadyBoundTarget`
Output:
**green reuse/regression**。覆盖：`UnboundBindGateHttpAcceptanceTest.secondFieldEntityCannotBeBoundToAnAlreadyBoundTarget`（08；`UNBOUND_BIND_TARGET_ALREADY_BOUND`）。标签命中后草案作废是 Neg 12 / 票 04，不在本圈重写门禁。
Production: 无
Refactor: 抽出 `heartbeatTwoUnlabeled`
Commit: 本圈提交

### Cycle E — 同一候选第二份 OPEN 草案失败（Neg 4）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.secondOpenDraftForSameCandidateFails`
Output:
**green reuse/regression**。覆盖：`UnboundDraftCreateHttpAcceptanceTest.secondOpenDraftForSameFieldEntityIsRejected`（02）。
Production: 无
Refactor: `heartbeatUnknown` 委托 `heartbeatLabeled`
Commit: 本圈提交

### Cycle F — 未认证不可写草案（Neg 5）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.unauthenticatedCannotWriteUnboundDraft`
Output:
**green reuse/regression**。覆盖：`UnboundDraftCreateHttpAcceptanceTest.unauthenticatedOpenDraftIsRejected`、`UnboundDraftItemReviewHttpAcceptanceTest.unauthenticatedItemAcceptIsRejected`（02/03）。
Production: 无
Refactor: 无
Commit: 本圈提交

### Cycle G — MISSING_LABEL 新建不是成功路径（Neg 6）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.missingLabelCreateIsNotASuccessPath`
Output:
**green reuse/regression**。覆盖：`UnboundDraftItemReviewHttpAcceptanceTest.missingLabelCreateAcceptIsNotASuccessPath`（03）。
Production: 无
Refactor: 无
Commit: 本圈提交

### Cycle H — 双接受绑定+新建失败（Neg 7）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.dualAcceptBindAndCreateFailsOnSecond`
Output:
**green reuse/regression**。覆盖：`UnboundDraftItemReviewHttpAcceptanceTest.acceptingCreateAfterBindFailsAsCandidateConsumed`（03）。
Production: 无
Refactor: 无
Commit: 本圈提交

### Cycle I — 失联闸门：选支 / 诊断 / 计划 / 改理想草案 / PENDING_CLOSE（Neg 8）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.identityLostGatesBranchDiagnosisPlanDraftAndPendingClose`
Output:
**green reuse/regression**。覆盖：`IdentityLostPipelineGateHttpAcceptanceTest`（05；夹具：先开 OPEN 再失联；PENDING_CLOSE 先对齐再失联；不用超时扫描冒充失联）。
Production: 无
Refactor: 抽出 `LostPipeline` / `openMismatchClaimed` / `postBranch`
Commit: 本圈提交

### Cycle J — absentObjectIds 走观测消失并解除绑定记忆（Neg 9）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.absentObjectIdsIsObservedAbsenceAndReleasesBindMemory`
Output:
**green reuse/regression**。覆盖：`UnboundIdentityLostIngestHttpAcceptanceTest.absentObjectIdsRemainUsableAbsentNotIdentityLost`（01）、`UnboundLabelMatchConsumeHttpAcceptanceTest.absentObjectIdsAfterBindIsObservedAbsenceAndReturnsTheFieldEntityToPending`（04）。
Production: 无
Refactor: 无
Commit: 本圈提交

### Cycle K — 同一 runtimeId 刷新不作废开放未绑定草案（Neg 10）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.sameRuntimeIdRefreshDoesNotVoidOpenUnboundDraft`
Output:
**green reuse/regression**。覆盖：`UnboundLabelMatchConsumeHttpAcceptanceTest.unlabeledReheartbeatDoesNotVoidOpenUnboundDraft`（04）。
Production: 无
Refactor: 无
Commit: 本圈提交

### Cycle L — 建底 POST 覆盖已有 运行于 仍拒绝（Neg 11）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.bootstrapPostStillRejectsOverwriteOfExistingRunsOn`
Output:
**green reuse/regression**。覆盖：`CuratedTruthHttpAcceptanceTest` / 改策展 01、`ChangeCuratedDraftTracerHttpAcceptanceTest.bootstrapPostRejectsOverwriteOfExistingRunsOn`、`UnboundDraftItemReviewHttpAcceptanceTest.bootstrapFirstRunsOnStillInsertsAndOverwriteStillRejected`。
Production: 无
Refactor: 无
Commit: 本圈提交

### Cycle M — 标签命中作废开放未绑定草案（Neg 12）
Command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.labelMatchVoidsOpenUnboundDraft`
Output:
**green reuse/regression**。覆盖：`UnboundLabelMatchConsumeHttpAcceptanceTest.labelMatchVoidsOpenUnboundDraftAndRejectsFurtherReview`（04）。
Production: 无
Refactor: 无
Commit: 本圈提交
