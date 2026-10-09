# 04 — 两侧相等后确认关闭不经身份、不记操作者

**What to build:** 冲突处于待确认关闭且两侧仍相等时，显式确认关闭可以完成，不需要用户身份，也不需要已接受处理人。关闭事件不记录操作者。未相等不得关闭。

**Blocked by:** 03 — 改策展草案逐条确认不经身份、不记操作者

**Status:** ready-for-agent

**TDD:** capability。主接缝为控制面 HTTP API。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。合同：ADR-0046。本票未开始。票 03 能在无身份下把两侧写成相等并进入待确认关闭，本票接着做确认关闭。

- [ ] 待确认关闭且两侧相等：不带用户身份头的 `POST /api/conflicts/{id}/confirm-close` → 200，状态 `CLOSED`；`CLOSED` 事件 `actorUserId` 为 null
- [ ] 带了用户身份头，`CLOSED` 事件 `actorUserId` 仍为 null
- [ ] 确认瞬间两侧已不等 → `CONFLICT_NOT_ALIGNED`，冲突不关闭
- [ ] 非待确认关闭仍不能确认关闭

**Out of this ticket:** 执行、协作身份路由、薄 UI、解决断点。

## Comments
