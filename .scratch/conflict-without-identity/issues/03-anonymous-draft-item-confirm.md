# 03 — 改策展草案逐条确认不经身份、不记操作者

**What to build:** 冲突上的改理想草案，以及未绑定草案，都可以逐条接受或拒绝，不需要用户身份，也不需要已接受处理人。接受仍立即写入策展并按合并键比对；未相等不得因此自动关闭。确认事件不记录操作者。

**Blocked by:** 01 — 诊断选支不经身份、不记操作者

**Status:** blocked

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。本票未开始。

- [ ] 不带用户身份头，接受改理想草案中「运行于」条目 → 策展目标改为该条目的目标；冲突事件 `DRAFT_ITEM_ACCEPTED` 的 `actorUserId` 为 null；两侧因此相等时进入待确认关闭，不自动 `CLOSED`
- [ ] 不带身份头拒绝另一条目 → `DRAFT_ITEM_REJECTED`，`actorUserId` 为 null，策展不被该条目改写
- [ ] 带了用户身份头，上述事件的 `actorUserId` 仍为 null
- [ ] 未绑定草案的逐条接受/拒绝同样不需要身份头，且不把用户 id 写入草案事件

**Out of this ticket:** 确认关闭、执行、协作身份路由、薄 UI、解决断点。

## Comments
