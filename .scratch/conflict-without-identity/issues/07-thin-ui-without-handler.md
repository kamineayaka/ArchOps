# 07 — 薄 UI 不再展示处理人门禁

**What to build:** 冲突详情可以完成诊断选支、批准并冻结、改策展草案逐条确认、确认关闭，页面不再要求选择用户或已接受处理人，也不再提供已知悉、自任、指派、接受、转让。

**Blocked by:** 06 — 协作身份动作退出领域

**Status:** blocked

**TDD:** UI，排在本刀 HTTP 票绿之后。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。本票未开始。

- [ ] 冲突详情不再展示已知悉、归属、处理人，也不提供认领、自任、指派、接受、转让
- [ ] 不选择当前用户即可选支、批准、逐条确认、确认关闭（对应 HTTP 已允许无身份）
- [ ] `npm run build` 通过

**Out of this ticket:** 解决断点、编排层、B-live、完整工作台、新的冲突状态。

## Comments
