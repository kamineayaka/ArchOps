# 新对话：竖切 MVP Spec → to-tickets（Prompt）

将下面整段复制到**新对话**作为第一条用户消息。若客户端支持手动附带 skill，请同时附上 `to-tickets`（路径一般在 `~/.agents/skills/to-tickets/SKILL.md`）。

前置：[`docs/specs/vertical-slice-mvp.md`](specs/vertical-slice-mvp.md) 已发布（`ready-for-agent`）。

---

```text
/to-tickets

请加载并严格遵循 skill：to-tickets（~/.agents/skills/to-tickets/SKILL.md）。
本对话任务：把已发布的 ArchOps「竖切 MVP」Spec 拆成一组 tracer-bullet 工单（含阻塞边）。不要写业务实现代码；不要重开领域 grilling / 技术选型；不要改 CONTEXT 合同语义。

## 背景（已完成）

- 领域合同冻结：CONTEXT.md + ADR-0039
- 技术栈冻结：ADR-0043
- 空脚手架已按 0043 重建（仅 health，无竖切业务）
- 竖切 Spec 已发布：docs/specs/vertical-slice-mvp.md（ready-for-agent；本地发布）
- 测试接缝已确认：唯一验收主接缝 = 控制面公开 HTTP API（含 Agent ingest）；SSH 执行端口 fake 支撑 CI（不算第二验收接缝）；前端最小 UI 手工/冒烟

## 必读后再拆票

1. docs/specs/vertical-slice-mvp.md          ← 拆票唯一主输入
2. CONTEXT.md（只用术语）
3. docs/adr/0043-tech-stack.md
4. docs/adr/0041-vertical-slice-mvp.md
5. docs/adr/0041-ai-egress-controlled-external-api.md
6. docs/mvp-vertical-slice.md（对照范围）
7. docs/dev-handoff.md
8. 快速扫一眼脚手架（health-only），票面须承认「从空骨架往上长」

## 故事范围（票不得扩出 Spec）

策展主机 A/B + 容器 X（archops.object_id）运行于 A
→ Agent 心跳快照报运行于 B → 冲突警告
→ 协作（已知悉/处理人；至少认领/自任）→ 选「修实际回 A」（跳过草案）
→ 操作计划人审 → 受控 SSH 执行 → 观测对齐 → 待确认关闭 → 处理人确认

负面至少覆盖：心跳超时挂起+计划作废；敏感读拒绝；未打标不承诺升级链。

Out of Scope（票不得偷带）：自我迭代、指标大盘、网络可达全矩阵、改策展分支全流程、Neo4j、多租户、完整 xterm 工作台、旧模块兼容、K8s/DB 全对象探测。

## 执行 to-tickets 流程时注意

1. 按 skill：先基于 Spec 起草垂直切片票（每票窄而完整：可演示/可验证；单票适合一个全新上下文窗口；优先垂直而非「先全表再全 API」的横向层）。
2. 每票写清：Title / Blocked by / What it delivers（用户可感知的端到端行为，不要写成纯分层任务清单）。
3. 用 CONTEXT 术语；遵守 ADR-0043（MyBatis-Plus、PG 真相、Redis 锁/队列/多副本、React+Ant、Python Agent systemd、MINA SSHD、WebClient；图库 Later）。
4. 验收对齐 Spec：自动化以 HTTP API 主接缝为准；SSH 用 fake；UI 可在相关票里做最小可演示，但不另开「只做 UI 层」横切片当主路径。
5. Spec 中协作「认领/自任」为 Must；指派/拒绝/转让为 Should——拆票时显式安排（纳入某票或单独 follow-on 票），禁止 silently drop。
6. Prefactor 仅在为竖切真正降阻时单列（空骨架上通常很少）；禁止借 prefactor 复活旧语义。
7. 先把票清单 + 阻塞图画给我确认（粒度 / 边是否对 / 是否要合并拆分）；我批准后再发布。
8. 发布：若未 setup Matt tracker，则本地发布到
   `.scratch/vertical-slice-mvp/issues/NN-<slug>.md`
   （从 01 起按依赖序；每票一个文件；模板见 to-tickets skill；Status: ready-for-agent）。
   并更新 docs/dev-handoff.md 指向该目录。若已配置 tracker，则开 Issue + 原生阻塞关系 + ready-for-agent。
9. 不要实现任何票的业务代码；发布后提示我下一对话从无 blocker 的 frontier 票开工。

开始：先输出建议的票清单（编号、标题、Blocked by、交付的可验证行为）与简图，等我确认粒度后再写入 `.scratch/...` 并收束。
```

---

你确认拆票清单后，助手应写入 `.scratch/vertical-slice-mvp/issues/`。再下一对话：按无 blocker 的票开始实现（可再写「实现单票」prompt）。
