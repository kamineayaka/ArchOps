# 新对话：ADR-0044 工单包切面 → `/grill-with-docs`

将下面 **「复制区」** 整段作为**新对话**的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `grill-with-docs` — `.cursor/skills/grill-with-docs/SKILL.md`（桌面：`.agents/skills/grill-with-docs/SKILL.md`）
- `grilling` — `.cursor/skills/grilling/SKILL.md`（或 `.agents/skills/grilling/SKILL.md`）
- `domain-modeling` — `.cursor/skills/domain-modeling/SKILL.md`（或 `.agents/skills/domain-modeling/SKILL.md`）

本文件是 **grilling 入口**，不是 `/implement`。A1 冲突升级作废计划 **01 已 TDD-done 并入 main**。本对话只定 **ADR-0044 工单包的切面**（先交哪一截、哪一截明确后置）。

Matt 主路径：idea → **`/grill-with-docs`** → **`/to-spec`** → **`/to-tickets`** → 每票新对话 **`/implement` `/tdd`**。  
grilling、spec、tickets 尽量留在**同一上下文窗口**（不要在 `/to-tickets` 之前 `/clear`）。每个 `/implement` 另开对话。

---

## 复制区

```text
/grill-with-docs

你是 ArchOps 的产品/领域面试官，不是编码 Agent。本对话只做一件事：用 /grill-with-docs 把「ADR-0044 工单包的切面」问到共享理解。质量优先：frontier 未空之前不要写 Spec、不要拆票、不要写业务代码、不要改已有 ADR 正文（可以讨论「是否需要新 ADR」；默认不需要）。

加载并遵守：
- AGENTS.md（执行纪律；一次一张票；禁止把 0044 拆分写进未绑定/A1 目录）
- grill-with-docs / grilling / domain-modeling skills
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- CONTEXT.md 只用已有术语，不发明同义新词（Avoid 栏禁止的词不要用）
- grilling skill 与本 prompt 冲突时：本 prompt + AGENTS.md + ADR-0044 为准。尤其禁止 grill-with-docs 边问边改 CONTEXT / ADR-0044 正文。

================================================================================
0. 任务边界（完成标准：一句话说出本对话交付物，且不把 /implement 算进范围）
================================================================================

本对话交付：
- 按 grilling skill 多轮面试：每轮列出当前 frontier 全部问题（编号 + 推荐答案），然后停下来等我答。
- 事实（Compose 现服务、MINA 落点、startExecution 是否整份跑完、控制面是否还有 WebClient/密钥、目录里有无 executor/orchestrator）由你查代码，不要问我。
- 决策由我做。推荐答案必须写清，但不得把推荐当成已批准。
- frontier 空了之后：用合同术语复述共享理解（第一刀 Must / Out of Scope / 接缝 / slug / 是否新 ADR / 票内大致顺序），等我确认。确认前不要 /to-spec。

一句话交付：定 ADR-0044 工单包「先交哪一截」——执行引擎 / 单步代发 / 步骤断言 / B-live / AI 编排层 的切割与先后，而不是把 B1–B5 一次做完，也不是做工作台三档。

本对话不做：
- 任何业务代码、测试、Flyway、UI、Compose 改造
- /implement、/tdd、/code-review
- 发明未绑定 10；给改策展加 07；重开 A1
- 把 WebClient / 模型密钥加回控制面
- 工作台三档（审计 B6）当成本包第一刀（可标 Out of Scope 或后置刀）
- G2 时钟运营化、自我迭代、N² 可达、完整 xterm、多租户、JWT、Neo4j
- 静默改 CONTEXT.md / ADR-0039 / ADR-0043 / ADR-0044 正文
- 引入 Vue / JPA 当地基 / Maven / LangChain / Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > 已发布 Spec > 工单 > 本 prompt。竖切 Spec 里「单进程 MINA / 密钥在控制面」是沉积误报（审计 C），不得用来否定 0044。

================================================================================
1. 先读（完成标准：按序读完；用合同术语写作）
================================================================================

按序阅读，读完再问第一轮。不要等我催：

1. AGENTS.md §2、§5、§6（栈；一次一张；A1 已闭合；不要自动做 0044）
2. docs/dev-handoff.md（确认 A1 01 TDD-done；下一对话是人排期 0044，不是未绑定 10）
3. CONTEXT.md — 至少：操作计划、AI 诊断、现场状态读取、运维隔离区、心跳（Host Agent 直写观测）
4. docs/adr/0039-domain-contract-frozen.md
5. docs/adr/0043-tech-stack.md（MINA 在执行引擎；WebClient 在编排层；控制面不持模型密钥）
6. docs/adr/0044-control-plane-hub-executor-and-ai-orchestrator.md（本对话宪法。八条决议 + 拒绝项 + 后果「现码偏离必须重写」）
7. docs/adr/0041-ai-egress-controlled-external-api.md（出站白名单/禁载荷；0044 修订密钥落点）
8. docs/adr/0038-ai-power-vs-capability-and-iteration.md（能力 vs 权力；执行期编排层只观察）
9. .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md — 只取 **B1–B5** 与枢纽协议表；A1/A2/A3 已闭合当背景。B6 工作台另刀。C 类忽略为实现依据
10. docs/agents/issue-tracker.md（新刀必须新 `.scratch/<slug>/`，不要写入 unbound / change-curated / conflict-upgrade-void-plans）

事实自查（读代码，不要问我）：
- `deploy/compose/compose.yaml` 现有哪些 service（应为 postgres / redis / archops）
- `MinaSshPort` / `ControlledSshPort` / `archops.ssh.mode`（默认 fake；mina 为生产动手）
- `OperationPlanService.startExecution` 是否一次 HTTP + Redis 锁内跑完整份计划
- 控制面是否仍有 DiagnosisLlmClient / WebClient / 模型密钥（应为已删）
- 仓库有无 executor / orchestrator 目录
- 空洞 / 升级 / 失联作废计划是否已在控制面（A1 已闭合：upgradeOpen 也会 void）
- Host Agent 是否仍 POST `/api/agent/heartbeat` 直连控制面（0044 决议 1：不经引擎、不经编排层）

================================================================================
2. 当前位置（完成标准：第一轮提问前用 ≤12 行复述；不得说「下一张还是未绑定 10 / A1」）
================================================================================

已完成、不要倒退：
- 领域合同冻结（ADR-0039）；栈冻结（ADR-0043）；运行时拓扑已立 ADR-0044
- 竖切 / 改策展 / 未绑定 01–09 / A1 升级作废活跃计划：均已闭合
- 控制面进程内 LLM 出站已删（B5 的「删除」半边已做）；规则诊断兜底仍能警告→选支→人审→执行（决议 7 已符合）
- 失败即停作废、禁止改步重试：控制面已有。无模型判步。无第二条 SSH 旁路
- Host Agent 心跳直写观测：符合 0044

0044 仍欠（审计 B，已知过渡债，禁止在偏离上继续加产品功能）：
- B1 无独立执行引擎 / 编排层进程；Compose 仍三件套
- B2 生产 MINA 仍在控制面；一次请求跑完整份计划（0044 已拒绝「整份交给引擎内跑完」）
- B3 无步骤断言、无逐步事件、无控制面→引擎单步代发
- B4 无 B-live 代发；诊断只有规则引擎
- B5 AI 编排层进程不存在（出站落点未建；不要把 LLM 加回控制面）

ADR-0044 已拒绝、grilling 不得重开成「更方便」的方案：
- 执行期 AI 当序列器 / function call 发下一步
- 模型阅读日志判本步成败
- 编排层短时令牌直连执行引擎
- 整份冻结计划交给执行引擎内跑完
- 工作台留在控制面自己 SSH
- 控制面进程内 WebClient 加料诊断

验收接缝默认（可在 grilling 里推翻，推翻须明示）：
- 主接缝仍 = 控制面公开 HTTP API（已审计划 start-execution、作废、健康检查）
- 新进程至少要有可断言的健康/就绪口（Compose 能 up）
- 竖切 HTTP 闭环在编排层未交付前必须继续可跑（决议 7 + 0044 后果）
- 过渡期测试 fake 可留在控制面适配器，不得扩大生产直连 SSH
- 前端薄 UI 不是本包定义完成
- /implement 仍走 TDD；本对话不写测试

================================================================================
3. 切面候选（完成标准：Q1 必须从这里选或由我另点名；不要发明「先重构包结构」当第一刀）
================================================================================

本包只切 **B1–B5**。不要把 B6 工作台三档、G2、未绑定 10 塞进第一刀。

A. 只立进程骨架：Compose 增加 executor（及可选 orchestrator 空进程）+ 健康口；控制面生产 MINA 仍在；计划仍整份 in-process
   为何有人想选：交付形态先看起来像四进程。
   为何不推荐默认：0044 写明禁止在偏离上继续加功能；骨架不迁 SSH，权力中心仍直连主机。

B. 执行引擎成真：MINA + 主机凭证迁出控制面；控制面只保留对引擎的代发客户端；**一次向引擎下发一步** + 游标步进/作废（空洞/升级立刻停发）；Redis 计划锁仍在控制面加锁后下发。编排层本刀不做。步骤断言 schema 可后置，但不得把整份计划交给引擎一次跑完。
   为何推荐：对准后果「MINA/凭证迁执行引擎」和拒绝项「整份交给引擎内跑完」。决议 7 允许编排层稍后。fake 留测试。

C. B + 本刀就做步骤断言与逐步事件给编排层（即便编排层进程还是 no-op 接收）
   为何：决议 2/4 把断言和单步代发绑在一起；拆开会留下「引擎自己觉得成功」的窗口。
   风险：第一刀过宽；断言约定要冻协议。

D. 先建 AI 编排层进程 + 密钥/白名单出站（B5），MINA 仍留控制面
   为何有人想选：出站落点仍缺。
   为何不推荐默认：控制面出站已删；规则兜底已符合决议 7。先做编排层等于先扩能力、权力通道（SSH）仍在控制面。0044 点名必须重写的是 MINA 生产路径。

E. 先做 B-live 取证（B4）
   为何不推荐默认：决议 5 要求只读取证经控制面代发、由执行引擎执行。没有引擎代发，B-live 会变成控制面再开一条现场通道。

F. 一张 Spec 覆盖 B1–B5，工单包内排序（第一张票仍必须是可独立验收的一截）
   为何：拆分不再是 Later，整包要看得见终点。
   风险：Spec 过宽导致 /implement 偷做后票。若选 F，grilling 必须钉死 **票 01 的 Must** 等于上面 B 或 C 之一，其余票 Out of 01。

G. 我另点名的切面（必须仍在 B1–B5 内，并用 0044 决议编号说清）

================================================================================
4. 第一轮 frontier（读完立刻问；问完停下。不要把依赖 Q1 的协议细节塞进这一轮）
================================================================================

按 grilling 格式输出（每个问题：标题、题干、推荐答案）。本轮只问互不依赖的决策：

❓ **Q1** - **第一刀切哪一截**：A / B / C / D / E / F / G。
➡️ 推荐 **B**（执行引擎成真 + 单步代发 + MINA/凭证迁出；编排层 / B-live / 断言 schema 后置）。若你希望「整包看得见终点」，选 **F** 且票 01 = B。不推荐 A（空骨架）、D（先编排层）、E（无引擎的 B-live）。C 作为「B 是否过窄」的备选，放到下一轮问断言是否跟第一刀绑定。

❓ **Q2** - **合同**：本包是「实现已冻结的 ADR-0044」，还是「要改枢纽协议所以先立新 ADR」？
➡️ 推荐 **实现 0044，不改 CONTEXT，不重开 0044 正文**。只有当你否决某条已拒绝项（例如想让引擎一次跑完整份计划）才需要新 ADR——而本 prompt 禁止把已拒绝项当默认。

❓ **Q3** - **tracker 落点**：新 `.scratch/<slug>/` + `docs/specs/<slug>.md` 叫什么？
➡️ 推荐 slug **`control-plane-executor`**（第一刀若是 B：引擎/代发）。若选 F 整包，推荐 **`adr-0044-hub-executor-orchestrator`**。禁止写入 `.scratch/unbound-identity-rebind/` 或 A1 目录。

❓ **Q4** - **自动化验收接缝**：是否仍以控制面 HTTP 为主（start-execution / 中途作废 / 计划 VOIDED）+ 新进程 health？是否把真实 MINA 或 Playwright 当完成定义？
➡️ 推荐 **控制面 HTTP + Compose 中引擎 health**。CI 继续 `archops.ssh.mode=fake` 走适配器（fake 实现可迁到引擎侧或经代发回放）。不要 Playwright。不要把「Agent 真 SSH 一台公网机」当票 01 完成定义。编排层未交付时，既有规则诊断 HTTP 测试必须仍绿。

若我在第一条消息里已经点名 A–G 之一，把 Q1 视为已决，从未决的 Q2–Q4 开始。

================================================================================
5. 后续轮次（完成标准：选中切面之后才展开；每轮问完就停）
================================================================================

Q1 选定后，下一轮只问被解开的问题（举例，不要提前全问）：

共同：
- 用户可感知的一条故事（处理人点 start-execution 之后，谁连主机、谁能中途作废）
- Out of Scope 清单（至少：B6 工作台、把 LLM 加回控制面、未绑定 10、G2、自我迭代）
- 薄 UI 是否本包 Must（推荐否）
- 票 01 与后票的硬边界（F 尤其要钉）

若选 B 或 F-票01=B：
- 「单步代发」的运输：控制面→引擎是 HTTP / 其它；是否要在票 01 冻一份最小 JSON
- 凭证与 MINA 进程：引擎持主机凭证；控制面是否还读得到明文（应为否；锁仍在控制面）
- `ControlledSshPort` 在控制面是删除生产实现、只留代发客户端，还是暂时双适配器（禁止扩大 mina 在控制面的生产路径）
- 空洞/升级/失联时：控制面如何取消在途一步（0044 决议 2；A1 已作废计划，本刀补「停发在途步」）
- 执行引擎语言：默认 Java 21 + 已有 MINA，与 ADR-0043 一致；不要无理由换 Python 引擎

若选 C：
- 步骤断言写在计划步骤的哪一字段；成败由引擎判定的最小约定
- 逐步事件推给谁：编排层进程尚未存在时，是控制面落库/日志就算，还是必须起一个能收事件的 stub

若选 D（若我坚持）：
- 编排层不可用时决议 7 如何验收（规则兜底 HTTP 必须绿）
- 密钥只在编排层环境；控制面作业包禁业务敏感
- 禁止编排层直连执行引擎（即便引擎已存在）

若选 F：
- 票序建议是否为：01 引擎+单步代发+迁 MINA → 02 步骤断言+逐步事件 → 03 编排层出站 → 04 B-live 预算封顶
- 每一张的独立 HTTP/health 验收是什么，避免 01 偷做 03

不要一次问 15 个。每轮你都可以改推荐。

================================================================================
6. 收束（frontier 空了才做；仍不要写代码）
================================================================================

1. 用 CONTEXT / ADR-0044 术语写「共享理解」：第一刀故事、Must、Out of Scope、接缝、slug、是否需要新 ADR、建议票序。
2. 等我确认「可以 /to-spec」。
3. 我确认后，**同一对话**再跑 /to-spec（先提出测试接缝等我点头，再写 Spec 并按 docs/agents/issue-tracker.md 发布到 docs/specs/<slug>.md 与 .scratch/<slug>/spec.md）。不要用 gh issue create。不要改 ADR-0044 正文来「澄清」。
4. Spec 发布后，**同一对话**再跑 /to-tickets（先给票清单+阻塞图等我批准，再写 .scratch/<slug>/issues/）。
5. 不要在本对话 /implement。拆票完成后更新 docs/dev-handoff.md 的下一票指针，并注明 frontier = 新刀 01。

开始：先用 ≤12 行复述当前位置，然后立刻输出第一轮 Q1–Q4。不要写 Spec，不要改产品代码。
```

---

## grilling 完成之后（同一对话，不要新开窗口）

确认共享理解后，直接在该对话发送 `/to-spec`，并要求 Agent 加载 `.cursor/skills/to-spec/SKILL.md`。接缝仍先确认再写全文。Spec 发布后再 `/to-tickets`。

拆票完成后，**每个工单新开对话** `/implement` `/tdd`，一次一张；TDD overlay 见 [`docs/agents/tdd.md`](agents/tdd.md)。不要把编排层密钥写进控制面 `.env` 或 git。
