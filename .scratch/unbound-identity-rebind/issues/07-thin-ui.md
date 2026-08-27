# 07 — 薄 UI：待并入未绑定 / 身份失联 / 按条确认

**What to build:** 给已认证运维一块最小 React + Ant Design 界面：看到待并入未绑定观测候选与身份失联，发起不挂冲突的草案，按条接受或拒绝，并能看到「应该在哪 / 实际在哪」在绑定后仍不把弱线索当可靠实际、在标签命中后恢复。HTTP 只走前端 `api` 模块。本票不进自动化主接缝。

**Blocked by:** 06 — HTTP 主接缝有序 tracer

**Status:** ready-for-agent

**TDD:** 本票在 06 HTTP 套件绿灯之后做。不把 Playwright / 组件单测当作完成定义；手工/冒烟即可。不要在 HTTP 未绿时先堆页面。

对齐竖切票 12 / 改策展薄 UI：够演示故事，不做完整工作台。

- [ ] 可列出待并入未绑定（含原因、宿主、runtime、标签线索）与身份失联对象
- [ ] 可对一个候选发起草案并按条接受/拒绝；互斥失败与未认证失败有可见提示
- [ ] 绑定接受后 UI 不得把该容器展示为已用弱线索对齐的可靠「实际在哪」
- [ ] 不展示完整 xterm、不接选支修实际流水线重做、不把本页做成冲突处理人工作台替代
- [ ] 前端请求只经既有 API 封装，不直打散落 URL

**Out of this ticket:** 新产品 HTTP；JWT；网络可达；K8s/数据库对象；把 UI 自动化设成 CI 门槛。

## Comments

01–06 + 08 已 TDD-done（PR #97 = 票 06 已合入），本票已 unblocked。开场 prompt：[`docs/implement-unbound-identity-rebind-07-prompt.md`](../../../docs/implement-unbound-identity-rebind-07-prompt.md)。本票是演示层：手工/冒烟 + `npm run build`；不要把 Playwright 或新 HTTP 套件当完成定义。不要做票 09。代码 vs ADR-0044 审计 **A2 已由 05 交付**；**A3** 是票 09；**A1** 与 0044 进程债禁止写入本票。见 [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../audit-code-vs-adr-0044.md)。

### Cycle A — 列出待并入未绑定观测候选（reason / 宿主 / runtime / 标签线索）
Command:
cd frontend && npm run build
Output:
green (`tsc --noEmit && vite build`；vite built in 2.83s). Vite :5173 冒烟：Header「未绑定 / 身份失联」进 `/unbound`，表列出 `UNKNOWN_OBJECT_ID`/`MISSING_LABEL` 两行（demo-rt-unknown / demo-rt-missing），`upgradeChainPromised=false · 不承诺升级链`，刷新后仍在；「开放冲突」仍回 `/` 竖切列表。
Production: `frontend/src/api/observed.ts`、`frontend/src/pages/UnboundCandidatesPage.tsx`、`frontend/src/App.tsx`、`frontend/src/api/types.ts`、`frontend/src/util/format.ts` / 无后端
Refactor: `formatActualWhereValue`；`identityLost` 只读冲突 GET 旁路
Commit: e900b07

### Cycle B — 身份失联 + 应该在哪 / 实际在哪（compose 既有 GET）
Command:
cd frontend && npm run build
Output:
green (vite 2.76s). Vite 冒烟：查询 `ctr-979ca696-a83d-4986-a23f-701c207d79c7` 失联标=身份失联（LABEL_CLUE_LOST），应该在哪=demo-host-07，实际在哪=身份失联 / availability=IDENTITY_LOST 不得为 PRESENT；查询 `does-not-exist` 见 IDENTITY_LOST_NOT_FOUND 与 CURATED_CONTAINER_NOT_FOUND 信封。无「以现场为准」。
Production: `observed.ts` getIdentityLost/getActualWhere、`UnboundCandidatesPage` 问法卡、`types.ts` IdentityLost/ActualWhere/ConflictCase.identityLost、`format.ts` / 无后端
Refactor: `formatActualWhereValue`；冲突 GET 仅作 identityLost=true 旁路
Commit: 96cd4f9

### Cycle C — 对候选发起草案并按条接受/拒绝
Command:
cd frontend && npm run build
Output:
green (vite 2.66s). Vite 冒烟：`demo-rt-unknown` 发起草案 → `/unbound/drafts/draft-21150b15-…`，origin=UNBOUND_CANDIDATE，conflictId=null，3 条；CREATE 接受为 ACCEPTED，CURATED_RUNS_ON_INSERT 拒绝为 REJECTED。本页无 xterm / 选支 / 批准计划 / start-execution。
Production: `observed.ts` createUnboundDraft、`drafts.ts` 未绑定 GET/accept/reject、`UnboundCandidatesPage` 草案卡、`types.ts` CuratedDraft.conflictId null、`App.tsx` `/unbound/drafts/:draftId`；ConflictDetailPage 仅 subjectId 空值守卫 / 无后端
Refactor: `formatUnboundDraftItemKind` / `payloadString` / `describeUnboundItem`
Commit: pending this cycle
