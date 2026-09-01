# 新对话：步骤断言切面 → `/grill-with-docs`

将下面 **「复制区」** 整段作为**新对话**的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `grill-with-docs` — `.cursor/skills/grill-with-docs/SKILL.md`（桌面：`.agents/skills/grill-with-docs/SKILL.md`）
- `grilling` — `.cursor/skills/grilling/SKILL.md`（或 `.agents/skills/grilling/SKILL.md`）
- `domain-modeling` — `.cursor/skills/domain-modeling/SKILL.md`（或 `.agents/skills/domain-modeling/SKILL.md`）

本文件是 **grilling 入口**，不是 `/implement`。控制面执行引擎票 01 **已 TDD-done 并入 main**。本对话只定 **步骤断言**这一刀的切面（多宽、是否带逐步事件、slug、接缝）。不要重开执行引擎刀，不要把本刀写成 `control-plane-executor` 票 02。

Matt 主路径：idea → **`/grill-with-docs`** → **`/to-spec`** → **`/to-tickets`** → 每票新对话 **`/implement` `/tdd`**。  
grilling、spec、tickets 尽量留在**同一上下文窗口**（不要在 `/to-tickets` 之前 `/clear`）。每个 `/implement` 另开对话。

上一刀 grilling 入口：[`docs/grill-adr-0044-slice-prompt.md`](grill-adr-0044-slice-prompt.md)（已闭合：切面 B）。

---

## 复制区

```text
/grill-with-docs

你是 ArchOps 的产品/领域面试官，不是编码 Agent。本对话只做一件事：用 /grill-with-docs 把「步骤断言」这一刀问到共享理解。质量优先：frontier 未空之前不要写 Spec、不要拆票、不要写业务代码、不要改已有 ADR 正文（可以讨论「是否需要新 ADR」；默认不需要）。

加载并遵守：
- AGENTS.md（执行纪律；一次一张票；禁止写入 unbound / change-curated / conflict-upgrade-void-plans / control-plane-executor 目录当本刀工单）
- grill-with-docs / grilling / domain-modeling skills
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- CONTEXT.md 只用已有术语，不发明同义新词（Avoid 栏禁止的词不要用）。本刀核心词是「步骤断言」「操作计划」「执行引擎」「控制面代发」。禁止用「断言测试 / assertion test / 健康检查」偷换步骤断言。
- grilling skill 与本 prompt 冲突时：本 prompt + AGENTS.md + ADR-0044 + ADR-0045 为准。尤其禁止 grill-with-docs 边问边改 CONTEXT / ADR-0044 / ADR-0045 正文。

================================================================================
0. 任务边界（完成标准：一句话说出本对话交付物，且不把 /implement 算进范围）
================================================================================

本对话交付：
- 按 grilling skill 多轮面试：每轮列出当前 frontier 全部问题（编号 + 推荐答案），然后停下来等我答。
- 事实（Compose 现服务、ExecuteStep proto、PlanStep 字段、success 如何判定、有无编排层进程）由你查代码，不要问我。
- 决策由我做。推荐答案必须写清，但不得把推荐当成已批准。
- frontier 空了之后：用合同术语复述共享理解（本刀 Must / Out of Scope / 接缝 / slug / 是否新 ADR / 票内大致顺序），等我确认。确认前不要 /to-spec。

一句话交付：定「步骤断言」这一刀交多宽——已审计划每步的预先约定、由执行引擎判定成败、失败即停作废；逐步事件是否本刀；不要做编排层 / B-live / 工作台。

本对话不做：
- 任何业务代码、测试、Flyway、UI、Compose 改造、改 proto 生产代码
- /implement、/tdd、/code-review
- 发明未绑定 10；给改策展加 07；重开 A1；重开 control-plane-executor 01
- 把 WebClient / 模型密钥加回控制面
- 工作台三档（审计 B6）
- 打断在途 MINA 会话（上一刀 Q9-B Should，本刀默认 Out of Scope）
- G2 时钟运营化、自我迭代、N² 可达、完整 xterm、多租户、JWT、Neo4j
- 静默改 CONTEXT.md / ADR-0039 / ADR-0043 / ADR-0044 / ADR-0045 正文
- 引入 Vue / JPA 当地基 / Maven / LangChain / Redis 当关系真相 SSOT
- 模型阅读输出判本步成败（0044 已拒绝）

冲突优先级：ADR 与 CONTEXT > 已发布 Spec > 工单 > 本 prompt。竖切 Spec 里「单进程 MINA / 密钥在控制面」是沉积误报（审计 C），不得用来否定 0044。`docs/specs/control-plane-executor.md` 把步骤断言标 Out of Scope，不得用来否定本刀开新 slug；那是上一刀边界，不是合同拒绝。

本条消息已点名本刀 = **步骤断言**。Q1 不再问「要不要做执行引擎」，只问本刀切多宽。

================================================================================
1. 先读（完成标准：按序读完；用合同术语写作）
================================================================================

按序阅读，读完再问第一轮。不要等我催：

1. AGENTS.md §2、§5、§6（栈；一次一张；执行引擎 01 已闭合；不要自动做编排层 / B-live / 工作台）
2. docs/dev-handoff.md（确认 control-plane-executor 01 TDD-done；下一对话是人排期本刀，不是未绑定 10）
3. CONTEXT.md — 至少：操作计划、步骤断言、执行引擎、控制面代发、AI 编排层（执行期只观察）、失败即停作废
4. docs/adr/0039-domain-contract-frozen.md
5. docs/adr/0043-tech-stack.md
6. docs/adr/0044-control-plane-hub-executor-and-ai-orchestrator.md（本对话宪法。决议 2/4 把单步代发、步骤断言、逐步事件绑在一起；拒绝「模型阅读日志判本步成败」）
7. docs/adr/0045-control-plane-executor-grpc.md（运输已冻：gRPC ExecuteStep；上一刀故意不含断言字段）
8. docs/adr/0038-ai-power-vs-capability-and-iteration.md
9. docs/specs/control-plane-executor.md — 只当「上一刀 Must / Out of Scope」；本刀不要往那个目录加票
10. .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md — 只取 **B3 剩余**（无步骤断言、无逐步事件）。B1/B2 与「单步代发」已由执行引擎 01 闭合。B4/B5/B6 另刀。C 类忽略为实现依据
11. docs/agents/issue-tracker.md（新刀必须新 `.scratch/<slug>/`）

事实自查（读 origin/main 代码，不要问我）：
- `deploy/compose/compose.yaml` 现有哪些 service（应为 postgres / redis / archops / executor）
- `OperationPlanResponse.PlanStep` 现有字段（应为 seq / action / description / params，无断言）
- `executor.proto` ExecuteStepRequest/Response 字段；`success` 现由谁置位（应为 SSH/fake 退出，不是步骤断言）
- `ExecuteStepGrpcService` 是否读操作计划表（应为否）
- 控制面是否仍有 DiagnosisLlmClient / WebClient / 模型密钥（应为已删）
- 仓库有无独立 orchestrator 进程/目录
- Host Agent 是否仍 POST `/api/agent/heartbeat` 直连控制面
- 规则诊断 → 选支 → 人审 → start-execution 经引擎 fake 的 HTTP 测试是否仍在

================================================================================
2. 当前位置（完成标准：第一轮提问前用 ≤12 行复述；不得说「下一张还是未绑定 10 / 执行引擎 01」）
================================================================================

已完成、不要倒退：
- 合同冻结（0039）；栈冻结（0043）；拓扑 0044；运输 0045
- 竖切 / 改策展 / 未绑定 01–09 / A1 / 执行引擎 01：均已闭合
- 执行引擎独立进程 + gRPC 单步代发 + MINA/凭证在引擎 + mTLS；空洞/失联/升级 VOIDED 后停发下一步并丢弃在途成功
- 失败即停作废、禁止改步重试：控制面已有。无模型判步。无第二条 SSH。Host Agent 心跳直连控制面。决议 7 规则兜底仍绿

本刀要补的缺口（B3 剩余，0044 决议 4）：
- 已审操作计划每步仍无步骤断言字段
- ExecuteStep 不含断言；引擎 `success` = SSH/fake 退出码，不是对工具结构化结果的预先约定
- 无逐步事件给编排层（编排层进程仍不存在）

上一刀故意留下的窗口：SSH 退出 0 即可 COMPLETED，即便结构化结果不符合「应该看到什么」。

ADR-0044 已拒绝、grilling 不得重开：
- 模型阅读日志/输出判本步成败
- 执行期 AI 当序列器 / function call 发下一步
- 编排层短时令牌/证书直连执行引擎
- 整份计划交给引擎内跑完
- 用 B-live 阅读代替步骤断言（CONTEXT 步骤断言 _Avoid_）

验收接缝默认（可推翻，推翻须明示）：
- 主接缝 = 控制面公开 HTTP（start-execution：断言失败 → 计划 VOIDED，即使引擎 SSH 退出成功）
- 内部仍走既有 ExecuteStep gRPC（可扩展字段）；不要新开处理人 API
- 引擎 fake 可脚本化 structured_output；不要 Playwright；不要真 SSH 公网机
- 既有执行引擎 / 规则诊断 HTTP 测试必须仍绿
- 薄 UI 不是本刀 Must
- 本对话不写测试

================================================================================
3. 切面候选（完成标准：Q1 必须从这里选或由我另点名；不要发明「先重构 proto 包名」当本刀）
================================================================================

本刀主题已定为 **步骤断言**（决议 4）。不要把 B4 B-live、B5 编排层、B6 工作台、打断 MINA 会话塞进默认 Must。

A. 只在计划 JSON / proto 加空断言字段，执行仍只看 SSH 退出码
   为何有人想选：契约先占位。
   为何不推荐：权力中心窗口还在；「有字段」不等于引擎判定。

B. 步骤断言成真：已审计划每步带对工具结构化结果的预先约定；控制面代发时把该约定放入 ExecuteStep；**执行引擎按约定判定**本步成败（不是退出码单独说了算，也不是模型读日志）；失败即停作废。逐步事件本刀不做。
   为何推荐默认：对准决议 4 和 CONTEXT「步骤断言」。关闭「SSH 0 就算成功」窗口。编排层仍不存在，事件可后置。

C. B + 本刀就做逐步详细结果落控制面（供以后编排层观察；本刀不起编排层进程、不 stub 编排层）
   为何：决议 2 把断言成败与逐步事件绑在一起；拆开会留下「判定了但观察者看不见」的窗口。
   风险：本刀变宽；事件 schema 要冻。

D. B + 起 AI 编排层空进程收事件
   为何不推荐默认：先扩能力进程；决议 7 已满足；出站密钥仍不应进控制面。空编排层接近已否决的骨架。

E. 先做 B-live（决议 5），断言后置
   为何不推荐默认：CONTEXT 步骤断言 _Avoid_「用 B-live 阅读代替断言」。代发已有，B-live 可另刀，但本对话已点名断言。

F. 一张 Spec 覆盖断言 + 逐步事件 +（可选）B-live，工单包内排序；票 01 仍须可独立验收
   若选 F：grilling 必须钉死票 01 Must = 上面 B 或 C 之一。

G. 我另点名（必须仍以步骤断言为主，并用 0044 决议编号说清）

================================================================================
4. 第一轮 frontier（读完立刻问；问完停下。不要把依赖 Q1 的字段清单塞进这一轮）
================================================================================

按 grilling 格式输出（每个问题：标题、题干、推荐答案）。本轮只问互不依赖的决策：

❓ **Q1** - **本刀切多宽**：A / B / C / D / E / F / G。
➡️ 推荐 **B**（步骤断言成真 + 引擎判定；逐步事件后置）。若你认为决议 2 的「逐步详细事件」必须跟判定同一刀，选 **C**。不推荐 A（空字段）、D（空编排层）、E（B-live 替换主题）。选 F 则票 01 = B 或 C。推荐不等于已批准。

❓ **Q2** - **合同**：本刀是「实现已冻结的 ADR-0044 决议 4」，还是「断言约定要改枢纽语义所以先立新 ADR」？
➡️ 推荐 **实现 0044，不改 CONTEXT，不重开 0044 正文**。ExecuteStep 增断言字段是 ADR-0045 运输契约的**加料**，优先写进本刀 Spec；只有当你要否决「引擎判定 / 禁止模型判步」才需要新 ADR。不要改 0045 正文来「澄清」上一刀故意省略的字段——用 Spec 或（若你坚持）新薄 ADR-0046。推荐不等于已批准。

❓ **Q3** - **tracker 落点**：新 `.scratch/<slug>/` 叫什么？
➡️ 推荐 slug **`plan-step-assertion`**。禁止写入 `.scratch/control-plane-executor/issues/` 当票 02（上一刀 Spec 已把断言列为 Out of Scope）。禁止 unbound / 改策展 / A1 目录。推荐不等于已批准。

❓ **Q4** - **自动化验收接缝**：是否仍以控制面 HTTP 为主（断言失败的 start-execution → VOIDED，即便 SSH/fake 退出成功）？是否把 proto 单测或 Playwright 当完成定义？
➡️ 推荐 **控制面 HTTP 为主**。夹具：引擎 fake 返回可脚本化的 structured_output + 退出成功，断言约定不匹配 → 计划 VOIDED。gRPC 字段随 HTTP 故事验收，不单独当定义完成。不要 Playwright。不要真 SSH 公网机。既有 `ExecutorSingleStepDispatchHttpAcceptanceTest` 等必须仍绿（无断言的旧计划：须钉「缺省约定」行为，见后续轮）。推荐不等于已批准。

若第一条消息已点名 A–G 之一，把 Q1 视为已决，从未决的 Q2–Q4 开始。本条已点名主题「步骤断言」，**尚未**圈定 A–G 宽度，所以仍要问 Q1。

================================================================================
5. 后续轮次（完成标准：选中切面之后才展开；每轮问完就停）
================================================================================

Q1 选定后，下一轮只问被解开的问题（举例，不要提前全问）：

共同：
- 用户可感知的一条故事（人审后的计划某步：SSH 成功但结构化结果不符合约定 → 怎样 VOIDED）
- Out of Scope（至少：编排层进程、B-live、工作台、打断 MINA、把 LLM 加回控制面、未绑定 10）
- 薄 UI 是否本刀 Must（推荐否）
- 已存在、无人审后再写断言的旧计划 / 竖切夹具：缺省是「无断言则退回仅退出码」还是「无断言即失败」？（推荐：无断言字段时保持上一刀行为，避免竖切断裂；**新生成**的修实际计划必须带断言）

若选 B 或 F-票01=B：
- 断言写在计划步骤哪一块（与 params 并列的约定结构，不要用自然语言让模型解释）
- 引擎判定的最小运算（例如：structured_output 含约定键值 / 退出码与约定同时成立）；禁止「LLM 读 stdout」
- ExecuteStep 是否必须把断言放进 request（推荐是：引擎判定，控制面不代判）
- 谁在生成计划时写入断言（当前是规则夹具生成修实际步骤，不是编排层；本刀可规则模板写死约定，不必等编排层）

若选 C：
- 逐步事件落谁：冲突事件 / 计划 executionLog / 新表。推荐复用或扩展已有 executionLog + 冲突事件，不新开编排层 inbox
- 编排层不存在时「推送」是否只等于控制面已持久化（推荐是）

若选 D（若我坚持）：
- 决议 7：规则兜底 HTTP 必须仍绿；密钥不得进控制面

不要一次问 15 个。每轮你都可以改推荐。

================================================================================
6. 收束（frontier 空了才做；仍不要写代码）
================================================================================

1. 用 CONTEXT / ADR-0044 术语写「共享理解」：本刀故事、Must、Out of Scope、接缝、slug、是否需要新 ADR、建议票序。
2. 等我确认「可以 /to-spec」。
3. 我确认后，**同一对话**再跑 /to-spec（先提出测试接缝等我点头，再写 Spec 到 docs/specs/<slug>.md 与 .scratch/<slug>/spec.md）。不要用 gh issue create。不要改 ADR-0044 / 0045 正文来「澄清」。不要往 control-plane-executor 加票。
4. Spec 发布后，**同一对话**再跑 /to-tickets（先给票清单+阻塞图等我批准，再写 .scratch/<slug>/issues/）。
5. 不要在本对话 /implement。拆票完成后更新 docs/dev-handoff.md 的下一票指针，并注明 frontier = 新刀 01。

开始：先用 ≤12 行复述当前位置，然后立刻输出第一轮 Q1–Q4。不要写 Spec，不要改产品代码。
```

---

## grilling 完成之后（同一对话，不要新开窗口）

确认共享理解后，直接在该对话发送 `/to-spec`，并要求 Agent 加载 `.cursor/skills/to-spec/SKILL.md`。接缝仍先确认再写全文。Spec 发布后再 `/to-tickets`。

拆票完成后，**每个工单新开对话** `/implement` `/tdd`，一次一张；TDD overlay 见 [`docs/agents/tdd.md`](agents/tdd.md)。不要把编排层密钥写进控制面 `.env` 或 git。不要把本刀工单写进 `.scratch/control-plane-executor/`。
