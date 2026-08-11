# 新对话：竖切 MVP → to-spec（Prompt）

将下面整段复制到**新对话**作为第一条用户消息。若客户端支持手动附带 skill，请同时附上 `to-spec`（路径一般在 `~/.agents/skills/to-spec/SKILL.md`）。

---

```text
/to-spec

请加载并严格遵循 skill：to-spec（~/.agents/skills/to-spec/SKILL.md）。
本对话任务：把 ArchOps「竖切 MVP」综合成一份可发布的 Spec，作为脚手架之后的 Matt 下一步。不要写业务代码，不要开 to-tickets（拆票另开对话）。

## 背景（已完成，勿重开 grilling / 选型）

- 领域合同已冻结：CONTEXT.md + docs/adr/（尤其 ADR-0039）
- 技术栈已冻结：docs/adr/0043-tech-stack.md
- 空脚手架已按 0043 建好：backend / frontend / agent / deploy（仅 health，无竖切业务）
- 竖切大纲：docs/mvp-vertical-slice.md 与 docs/adr/0041-vertical-slice-mvp.md
- 接手：docs/dev-handoff.md

## 必读后再综合

1. CONTEXT.md（只用术语，不改合同）
2. docs/adr/0039-domain-contract-frozen.md
3. docs/adr/0043-tech-stack.md
4. docs/mvp-vertical-slice.md
5. docs/adr/0041-vertical-slice-mvp.md
6. docs/adr/0041-ai-egress-controlled-external-api.md（诊断出站边界）
7. 快速扫一眼当前脚手架状态（health-only），Spec 须承认「从空骨架往上长」

## 范围钉死（Spec 只覆盖这条故事）

策展主机 A/B + 容器 X（archops.object_id）运行于 A
→ Agent 心跳快照报运行于 B → 冲突警告
→ 协作（已知悉/处理人）→ 选「修实际回 A」（跳过草案）
→ 操作计划人审 → 受控 SSH 执行 → 观测对齐 → 待确认关闭 → 处理人确认

明确 Out of Scope（须写入 Spec）：自我迭代、指标大盘、网络可达全矩阵、改策展分支全流程、Neo4j、多租户、完整 xterm 工作台、旧模块兼容。

## 执行 to-spec 流程时注意

1. 先探索仓库现状（只读），用 CONTEXT 术语写 Spec。
2. 先提出「测试接缝（seams）」并与我确认，再写全文 Spec。接缝尽量少、尽量高。
3. Spec 使用 skill 模板：Problem Statement / Solution / User Stories（尽量全）/ Implementation Decisions / Testing Decisions / Out of Scope / Further Notes。
4. Implementation Decisions 要体现 ADR-0043：MyBatis-Plus、PG+Redis 多副本、React+Ant、Python Agent systemd、MINA SSHD、WebClient；图库 Later；不要写易过期的具体文件路径（除非原型级状态机/表形必须内联）。
5. 按 skill：综合已有讨论即可，不要再面试式 grilling；有歧义用已冻结合同裁决，合同未覆盖的实现细节可在 Implementation Decisions 中给出合理默认并标明。
6. 写完后发布到项目配置的 issue tracker，并打 ready-for-agent（若未 setup tracker，则先把完整 Spec 落到 docs/specs/vertical-slice-mvp.md，并明确告知我下一步应 /setup-matt-pocock-skills 或改用本地发布）。

开始：先用几句话确认范围 + 列出你建议的测试接缝，等我确认接缝后再写出完整 Spec 并发布。
```

---

确认接缝并 Spec 发布后，下一对话粘贴 `docs/to-tickets-vertical-slice-prompt.md` 跑 `/to-tickets` 拆票。
