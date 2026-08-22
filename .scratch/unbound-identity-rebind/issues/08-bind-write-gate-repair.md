# 08 — 修复接受绑定的写入门禁：失联判据与目标唯一

**What to build:** 只碰「接受绑定」这一个写入点。第一，绑定门禁不得再把「失联之前留下的 PRESENT 观测行」当成目标健康：判据改为**失联之后是否又标签命中过**——晚于失联标的 PRESENT 才算认回，`ADR-0012` B1（标签被删/被改的既有容器）必须能经草案绑回。第二，同一策展对象不得被两个现场实体分别绑定：接受绑定时目标上已有未消费的绑定记忆则失败，规则夹具也不再重复提供已被绑定的目标（同宿主有多个失联对象时给出各自目标）。

**Blocked by:** 03 — 逐条确认：新建写入对象；绑定只记对应关系

**Status:** done

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条 HTTP 测试。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)（故事 30、33、34；ADR-0011 / ADR-0012）。

来源：`.scratch/unbound-identity-rebind/audit-01-03-opus.md` 的 **C-4**（FIX-NOW / major）、**C-2**（FIX-NOW / major）、**S-3**（TEST-GAP / nit）。按审计推荐，本票排在票 04 之前做，不改 04/05 语义。

- [x] 目标在失联之前曾有 PRESENT 观测（标签后来被删/被改）→ 接受绑定成功；容器 ID / 不可变标签不变；不写观测 `运行于`；不承诺升级链
- [x] 目标在失联之后又标签命中（观测 PRESENT 晚于失联标）→ 接受绑定仍 `UNBOUND_BIND_TARGET_HEALTHY`（票 03 Cycle G 不回归）
- [x] 同一失联对象上已有未消费的绑定记忆 → 第二个现场实体接受绑定失败（`UNBOUND_BIND_TARGET_ALREADY_BOUND`），不得把一个策展对象变成两个现场实体的本体
- [x] 同宿主存在多个失联对象 → 各现场实体的草案分别给出尚未被绑的目标，都能接受
- [x] 新增 Flyway（绑定记忆按策展对象唯一），不改历史 `V*.sql`

**Out of this ticket:** 标签命中清失联 / 消费候选与绑定记忆 / 作废未绑定草案（票 04，含审计 C-3、S-4）；`absentObjectIds` 之后解除绑定记忆与失联（票 04 故事 50，审计已确认现行行为不变）；失联时问法在心跳超时下应报观测空洞（审计 C-1，另一张票）；心跳契约文档补绑定记忆段（审计 S-2，随票 04 文档圈）；失联闸门（票 05）；tracer（06）；UI（07）。

## Comments

审计结论经用户批准后开工。一次只做本票；不要顺手做 04/05，不要重构 `CuratedDraftService`。

### Cycle A — 失联之前曾 PRESENT 的对象可以绑回
Red command:
`cd backend && ./gradlew test --tests com.archops.observed.UnboundBindGateHttpAcceptanceTest.bindingToTargetThatLostItsLabelAfterAnEarlierMatchSucceeds`
```
UnboundBindGateHttpAcceptanceTest > bindingToTargetThatLostItsLabelAfterAnEarlierMatchSucceeds() FAILED
    java.lang.AssertionError at UnboundBindGateHttpAcceptanceTest.java:62
java.lang.AssertionError: Status expected:<200> but was:<400>
```
（门禁把失联之前留下的 PRESENT 观测行当成目标健康 → `UNBOUND_BIND_TARGET_HEALTHY`。）
Green command: 同上，exit 0。`writeAcceptedBind` 判据从 `observedRunsOnPresent` 换成 `labelMatchedAfterIdentityLoss`（PRESENT 且 `observedAt` 不早于 `markedAt` 才算认回）。绑定仍不写观测 `运行于`、不承诺升级链、不改容器 ID / 不可变标签。
Regression：`UnboundDraftItemReviewHttpAcceptanceTest` 全类绿，含 Cycle G `bindingToLabelMatchedPresentTargetIsRejected`（失联后又命中仍 `UNBOUND_BIND_TARGET_HEALTHY`）。
Refactor: 删掉只有一个调用点的 `observedRunsOnPresent`，判据落在带失联语义注释的 `labelMatchedAfterIdentityLoss`。
