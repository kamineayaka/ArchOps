# 06 — HTTP 主接缝有序 tracer（happy path + 负面最小集）

**What to build:** 为本刀建立一条以控制面公开 HTTP API（含 Agent 心跳/快照 ingest）为唯一自动化主接缝的有序验收：先策展后缺标 → 未绑定 + 身份失联 → 草案逐条（新建 / 绑定互斥）→ 绑定不写可靠实际 → 补标命中收尾 → 失联闸门负面。不以浏览器自动化或 SSH fake 作为完成定义。前端薄 UI 只作手工/冒烟，不进本票 CI 门槛。

**Blocked by:** 01 — 推断失联与 upsert；02 — 发起草案；03 — 逐条新建/绑定；04 — 标签命中收尾；05 — 失联闸门

**Status:** ready-for-agent

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

- [ ] 上列有序 happy path 可在 CI 经 HTTP 稳定跑通
- [ ] 负面：他机快照不给 X 打失联
- [ ] 负面：未打标同名不承诺升级链
- [ ] 负面：绑到仍健康命中对象失败
- [ ] 负面：同一候选第二份开放草案失败
- [ ] 负面：未认证不可写草案
- [ ] 负面：`MISSING_LABEL` 新建不是成功路径
- [ ] 负面：双接受绑定+新建失败
- [ ] 负面：失联时选支失败；诊断不再给旧实际落点；计划 / 改理想草案作废；待确认关闭退回开放
- [ ] 负面：`absentObjectIds` 走观测消失并解除待补标绑定记忆
- [ ] 负面：同一 `runtimeId` 刷新不作废开放未绑定草案
- [ ] 负面：建底 POST 覆盖已有 `运行于` 仍拒绝
- [ ] 负面：标签命中作废开放未绑定草案
- [ ] 断言只落 HTTP 状态码、统一信封、后续 GET 可读状态；不把前端自动化当完成门槛

**Out of this ticket:** 新产品能力（应已由 01–05 交付）；Playwright；SSH fake 作为第二接缝；薄 UI（见 07）。

## Comments

01–05 + 08 已 TDD-done，本票已 unblocked。开场 prompt：[`docs/implement-unbound-identity-rebind-06-prompt.md`](../../../docs/implement-unbound-identity-rebind-06-prompt.md)。suite 首跑绿记 reuse/regression。不要做 07。不要做票 09。代码 vs ADR-0044 审计 **A2 已由 05 交付**；**A3** 是票 09；**A1** 与 0044 进程债禁止写入本票。见 [`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../audit-code-vs-adr-0044.md)。
