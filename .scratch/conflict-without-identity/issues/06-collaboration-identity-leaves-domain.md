# 06 — 协作身份动作退出领域

**What to build:** 冲突上不再有已知悉、归属、自任、指派、接受、拒绝、转让或处理人。这些写入路由不再产生领域事实。冲突 GET 不再把它们呈现为协作身份。旧的「开计划」身份门禁不再返回处理人。

**Blocked by:** 04 — 两侧相等后确认关闭不经身份、不记操作者；05 — 执行已批准计划不再询问处理人、不记操作者

**Status:** blocked

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。本票未开始。先有四类人审与执行不再读取处理人，再撤掉协作身份写入。

- [ ] `POST` 认领、已知悉、已知悉并自任、指派、接受、拒绝、转让不再写入已知悉、归属或处理人；冲突 GET 不再把这些字段当领域事实返回
- [ ] `POST /api/conflicts/{id}/operation-plans` 不再以处理人门禁决定能否开计划，响应也不再带处理人 id
- [ ] 发现不变：两侧可用且不等仍是冲突；空洞不是冲突；未绑定不挂在冲突上；身份失联仍是标志
- [ ] 票 01–05 的无身份选支、批准、逐条确认、确认关闭、执行仍可用

**Out of this ticket:** 薄 UI、解决断点、编排层、B-live、工作台。

## Comments
