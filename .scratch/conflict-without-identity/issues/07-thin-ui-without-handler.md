# 07 — 薄 UI 不再展示处理人门禁

**What to build:** 冲突详情可以完成诊断选支、批准并冻结、改策展草案逐条确认、确认关闭，页面不再要求选择用户或已接受处理人，也不再提供已知悉、自任、指派、接受、转让。

**Blocked by:** 06 — 协作身份动作退出领域

**Status:** done

**TDD:** UI，排在本刀 HTTP 票绿之后。Spec：[`docs/specs/conflict-without-identity.md`](../../../docs/specs/conflict-without-identity.md)。本票已完成。基线是票 06 分支，diff 只含本票。

- [x] 冲突详情不再展示已知悉、归属、处理人，也不提供认领、自任、指派、接受、转让
- [x] 不选择当前用户即可选支、批准、逐条确认、确认关闭（对应 HTTP 已允许无身份）
- [x] `npm run build` 通过

**Out of this ticket:** 解决断点、编排层、B-live、完整工作台、新的冲突状态。

## Comments

`cd frontend && npm run build` → `tsc --noEmit && vite build` passed (vite 6.4.3). UI 不是自动化主接缝；HTTP 无身份动作已在票 01–06 变绿。

浏览器（Vite `:5173`，控制面 `:8080`）：

- `cnf-5fcd7bf7-0ad3-490d-aa28-7d1db5fbf76d`：选支、批准并冻结、启动执行，再确认关闭。计划 `created_by` / `reviewed_by` 为空。`CLOSED` 事件 `actor_user_id` 为空。页面没有已知悉、归属、处理人、认领、自任、指派、转让。
- `cnf-b0c4d77f-272b-4815-8fb0-f6221eaf1223`：选支生成改理想草案后，未认证下接受合并键条目。`DRAFT_ITEM_ACCEPTED` 的 `actor_user_id` 为空。条目 接受 / 拒绝仍在，只在草案开放且条目 PENDING 时出现。随后把页头切到未认证并点确认关闭：toast「冲突已关闭」，状态 CLOSED，详情没有被认证错误替换。`CLOSED` 事件 `actor_user_id` 为空。

读接口仍要用户。页头原有的演示身份没有改成新的选人器。未认证时页头仍显示「未带头 · AUTH_REQUIRED」；已经打开的详情在匿名写入后的刷新失败时保留。启动执行的返回值会写回当前计划状态，所以未认证时按钮不会停在「已批准」。

未绑定页的逐条确认不在本票验收里，仍走原页头用户。本票没有再做解决断点。

Standards：无硬违规。Spec：冲突详情的选支、批准、逐条确认、确认关闭、启动执行不发送用户；处理人门禁文案已去掉。
