# 01 — 删掉冲突链上的协作残留

**What to build:** 认领、已知悉、自任、指派、接受、拒绝、转让从冲突 HTTP 与服务上消失（404 或路由不存在），不再以 200 空写留着。冲突 GET 不再返回协作/处理人/归属。冲突列表与详情，以及诊断选支、批准并冻结、改策展逐条确认、确认关闭、启动执行，不带头也能完成，且不记录操作者。冲突页去掉演示身份，刷新不依赖所选用户；解决动作留在页上。

**Blocked by:** None. Baseline is origin/main at the merge of PR #125 (`97bc9fe`) with ADR-0046.

**Status:** ready-for-agent

**TDD:** capability。主接缝为控制面 HTTP API。合同：ADR-0046（不重开）。发现与解决语义不改。

- [ ] `POST` 认领、已知悉、已知悉并自任、指派、接受、拒绝、转让对已存在的冲突返回 404，不写领域事实
- [ ] 冲突 GET 列表与详情不返回 collaboration / owner / handler
- [ ] 冲突列表、详情，以及诊断选支、批准并冻结、改策展逐条确认、确认关闭、启动执行，不发送用户头即成功，且不记录操作者
- [ ] 冲突页没有演示身份，列表与详情刷新不依赖所选用户；解决动作仍在
- [ ] 发现不变：两侧可用且不等仍是冲突；空洞不是冲突；未绑定不挂在冲突上；身份失联仍是标志

**Out of this ticket:** 编排层、B-live、工作台、未绑定 10、执行引擎票 02、ABSENT 分叉、占位 SSH、观测回写、用户微服务、Spring Cloud。领域外的登录若未绑定或策展写入仍要用，留着。

## Comments

### Cycle A — claim route is gone
Red command: `cd backend && ./gradlew test --tests com.archops.conflict.CollaborationLeftoverRemovedHttpAcceptanceTest.claimOnOpenConflictIsNotFoundWithoutUser --offline`
`Status expected:<404> but was:<401>` at `CollaborationLeftoverRemovedHttpAcceptanceTest.java:38`.
Green command: same test, exit 0. Removed `POST /api/conflicts/{id}/claim` and its service method. Unmapped API paths return 404 (`NOT_FOUND`) instead of an internal error, and the old path is not an auth gate.
Refactor: dropped empty claim helpers left in older HTTP tests.
Commit: (filled after commit)
