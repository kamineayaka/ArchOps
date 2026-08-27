# 新对话：未绑定 / 身份失联票 06（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。本票走 overlay 的 **Suite / tracer tickets**，不是能力票的 TDD redo。

Matt 位置：grilling / to-spec / to-tickets **已完成**。竖切 01–13 与改策展 01–06 已闭合。未绑定票 **01–05 + 08 TDD-done**（PR #95 已合入）。本对话只 `/implement` **未绑定刀 frontier = 06**。不要做 07，不要做票 09，不要给 `change-curated-draft` 加 07，不要把 01–05 / 08 当 TDD redo，不要删除它们的生产来装红灯。不要拆执行引擎 / AI 编排层。

本票带着代码 vs ADR-0044 只读审计（[`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md)）：**A2 已由票 05 交付**，本票只把闸门编进 tracer 负面 8；**A3 / C-1 是票 09**；**A1**（升级不作废活跃计划）与 **B1–B5**（0044 进程债）禁止写入本票。01–03 合同审计 C-1 仍待票 09，本票不要改 `observedAskValue` 的 mark 优先顺序。若 `AGENTS.md` 仍写 frontier=05，以 [`docs/dev-handoff.md`](dev-handoff.md) 与本文件为准。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按 docs/agents/tdd.md 的 **Suite / tracer** 循环实现未绑定 / 身份失联 frontier 工单 06。质量优先于速度。本票不是新能力票，不是 TDD redo，不是 05。01–05 与 08 已闭合（PR #95 = 票 05 已合入）。禁止删除 01–05 / 08 / 竖切 / 改策展生产来装红灯。禁止重做它们的产品行为。票外行为一律不做。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准。若仍写 frontier=05，以 docs/dev-handoff.md 与本 prompt 为准：frontier = 06）
- implement skill、tdd skill、docs/agents/tdd.md 的「Suite / tracer tickets」
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围、类名、循环切分或错误码。下面已钉死。不要用 Playwright、SSH fake、computerUse 或薄 UI 当作完成定义。不要默认开工 07 或票 09。不要拆执行引擎 / AI 编排层，不要把 WebClient/密钥加回控制面，不要加 B-live / 步骤断言。不要「顺便」抽 05 的 judgement smell（identity_lost_mark 探查重复、unique-site forkId 对）。

================================================================================
0. 任务边界（完成标准：一句话说出本票交付物，且不把 07 / 09 / A1 / 0044 / 新产品算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/unbound-identity-rebind/issues/06-http-tracer-acceptance.md

Spec：docs/specs/unbound-identity-rebind.md — 只取 Testing seams、HTTP tracer（happy path 1–8，可选 9）、Negative 1–12、Prior art、Out of Scope。
审计（只读，不要改）：
- .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md
- .scratch/unbound-identity-rebind/audit-01-03-opus.md

最近一次只读架构审计（本对话的边界，不是要你修 0044）：
- A2 已由票 05 交付。本票只把 05 闸门编进 tracer 负面 8，不重做闸门产品。
- A3 / 审计 C-1 = 票 09。失联叠加心跳超时时问法仍须说出观测空洞。本票不要动 observedAskValue 的 mark 优先于 staleness。
- A1 = 另开工单。upgradeOpen 在观测 B→C（健康、未失联）时不作废计划。不要顺便修。
- B1–B5 = ADR-0044 过渡债。MINA 仍在控制面、无编排层、无 B-live、无步骤断言。禁止扩大生产直连 SSH，禁止把 DiagnosisLlmClient / WebClient 加回来。

一句话交付：为本刀钉一条控制面公开 HTTP（含 Agent ingest）有序验收套件——先策展后缺标 → 未绑定 + 身份失联 → 草案逐条（新建 / 绑定互斥）→ 绑定不写可靠实际 → 补标命中收尾 → 失联闸门负面——外加 Spec Negative 1–12 各一条。不实现新产品；不以浏览器或 SSH fake 为完成定义。

本票对应 Spec「HTTP tracer」与 Negative 1–12。能力已由 01–05 + 08 交付。薄 UI 是 07。审计 C-1 是票 09。

本票交付（用户可感知、HTTP 可断言）：
- 新测试类：com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest（对标 ChangeCuratedDraftTracerHttpAcceptanceTest 的 @HttpAcceptanceTest + @Order；不要改 01–05 聚焦测试类，不要把它们删进本套件）。
- Happy path 1–8 是 **一条** 有序方法（方法内 step 注释），不是 8 个 cycle。可选 tracer 9（补标到非策展宿主 B → OPEN 冲突且诊断可再出修实际/改理想）可放同一方法末尾，或第二条 sibling 方法；不要拆成 8 张能力票。
- 每个 Negative 1–12 自己的方法、自己的夹具（@HttpAcceptanceTest 是 AFTER_CLASS 才清库：每方法 host/container/agent/runtime 必须唯一）。
- 断言只落 HTTP 状态码、统一 ApiResponse 信封、后续 GET 可读状态。字面量用：MISSING_LABEL、UNKNOWN_OBJECT_ID、IDENTITY_LOST、PRESENT、ABSENT、OPEN、PENDING_CLOSE、VOIDED、FIX_ACTUAL_TO_CURATED、CHANGE_CURATED_TO_OBSERVED、RESTORE_HEARTBEAT_CHANNEL、IDENTITY_LOST_BLOCKS_BRANCH、PLAN_REQUIRES_ACCEPTED_HANDLER、PLAN_VOIDED、DRAFT_VOIDED、UNBOUND_BIND_TARGET_ALREADY_BOUND、CURATED_RUNS_ON_EXISTS、CONFLICT_NOT_FOUND、UNBOUND_CANDIDATE、CHANGE_CURATED。文案禁止「以现场为准」。
- 未绑定草案不挂 conflictId。绑定不写可靠观测 运行于、不承诺升级链。未打标同名 by-merge-key 仍 400 CONFLICT_NOT_FOUND。

本票不做（Out of ticket；发现自己在做就停）：
- 新产品、新路由、新错误码、新 ConflictStatus、新 Flyway（除非 tracer 暴露了 01–05 已承诺但缺失的组合缝——只补既有语义，不发明产品）
- 为装红灯删除或改坏 01–05 / 08 生产；不要合并/删除聚焦测试
- 票 09：改 observedAskValue，让失联∧超时改答 HOLLOW
- A1：upgradeOpen 无条件作废计划
- 薄 UI（07）、改策展 07、重做竖切 01–13
- 抽 05 的 duplicated mark probe / unique-site forkId 对（那是 judgement smell，不是本票）
- 执行引擎进程、AI 编排层、B-live、步骤断言、控制面 WebClient/LLM、新的生产直连 SSH API
- Y2 策展对齐步骤、网络可达、K8s/数据库对象、自我迭代、LangChain
- 改已有 V*.sql；改 CONTEXT.md / 已有 ADR 正文
- Maven、JPA 当地基、Vue、Neo4j v1 必选、Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内/合同术语写作，不发明同义新词）
================================================================================

按序阅读，读完再写第一个套件方法：

1. AGENTS.md（一次一张；HTTP 主接缝；suite 票禁止 TDD redo 删生产）
2. docs/agents/tdd.md — 整节「Suite / tracer tickets」（first-run green = reuse/regression；夹具错才修测试；组合缝缺失才以既有语义补最小生产）
3. .scratch/unbound-identity-rebind/issues/06-http-tracer-acceptance.md
4. docs/specs/unbound-identity-rebind.md — Testing seams、HTTP tracer 1–9、Negative 1–12、Prior art
5. CONTEXT.md — 只用：身份失联、未绑定观测候选、观测空洞、观测消失、规范问法、冲突、待确认关闭、操作计划、冲突处理人、草案、逐条确认。Avoid：以现场为准、未绑定处理人、把失联当成空洞/消失
6. docs/adr/0039、0043、0044（0044 只为禁止本票拆进程 / 加回控制面 LLM）、0011、0012
7. .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md — A1 / A3 / B 全部停；A2 已由 05 交付
8. docs/dev-handoff.md（确认 frontier = 未绑定 06）
9. 现行样板（读，当 reuse 来源，不重写范围外行为）：
    - ChangeCuratedDraftTracerHttpAcceptanceTest（suite 形态：@Order、一条 happy path、每负面一方法、唯一 id 前缀）
    - UnboundIdentityLostIngestHttpAcceptanceTest（01）
    - UnboundDraftCreateHttpAcceptanceTest（02）
    - UnboundDraftItemReviewHttpAcceptanceTest（03）
    - UnboundBindGateHttpAcceptanceTest（08）
    - UnboundLabelMatchConsumeHttpAcceptanceTest（04）
    - IdentityLostPipelineGateHttpAcceptanceTest（05）
    - VerticalSliceHttpE2eAcceptanceTest.negative_unlabeledSnapshotDoesNotPromiseUpgradeChain
    - CuratedTruthHttpAcceptanceTest / 改策展 01（CURATED_RUNS_ON_EXISTS）
    - HeartbeatTimeoutHollowHttpAcceptanceTest（本票不要改空洞扫描；不要用超时冒充失联）
    - .cursor/rules/backend-java.mdc

接缝已确认：唯一自动化验收接缝 = 控制面公开 HTTP API（含 Agent ingest）。Gradle/MockMvc 与 bootRun+curl 是同一条接缝。本票定义完成不要求 SSH fake、不要求 start-execution、不要跑 MINA。

用合同术语。未绑定 ≠ 冲突 ≠ 身份失联 ≠ 观测空洞 ≠ 观测消失。

================================================================================
2. 思想与质量条
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 两侧可用且不等。本票 motto：匹配失败不升冲突；失联不是空洞；旧实际不可当作唯一落点。本票只把已交付能力按 Spec 顺序串成套件。

栈：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway 只增不改历史、PostgreSQL SSOT。Redis 不作这些行的真相。规则驱动。ADR-0044：本票不拆进程、不加控制面 LLM、不扩大 MINA。

认证：Header X-ArchOps-User-Id。一般 = user-general-demo，高级 = user-senior-demo。Agent POST /api/agent/heartbeat 不带头。未认证负面不要带头。

Suite 循环（每一圈仍三条：写方法 → 跑这一方法 → 记录 + 必要修复 + refactor + 提交）：
1. 写 **一个** 套件方法。Happy path 1–8（+可选 9）只写一次，方法内用步骤注释对齐 Spec tracer 编号。
2. 只跑这一方法。把完整命令与输出追加到票 ## Comments。
3. First-run **green**：Comments 记 reuse/regression，并点名已覆盖它的 01–05/08/竖切聚焦测试。保留新方法。不要删生产或聚焦测试来装红灯。
4. First-run **red** 因为夹具/id 碰撞/路径笔误：修测试，再跑；变绿则仍记 reuse。
5. First-run **red** 因为 01–05 组合缝真缺：只补最小生产到 **既有** 语义（同一错误码、同一状态机）。禁止发明产品、路由、条目类型。然后按 capability 最小改动变绿。
6. Refactor 仅限本套件类的 helper / 命名。再跑同一方法仍绿。提交这一圈（why）。下一方法。

建议循环顺序（可按教学调整，但每圈仍一条方法）：
A. happyPath 有序 tracer（Spec 1–8；9 可同方法或 sibling）
B. 他机快照不给 X 打失联（Neg 1）
C. 未打标同名不承诺升级链（Neg 2）
D. 绑到仍健康命中对象失败（Neg 3）
E. 同一候选第二份 OPEN 草案失败（Neg 4）
F. 未认证不可写草案（Neg 5）
G. MISSING_LABEL 新建不是成功路径（Neg 6）
H. 双接受绑定+新建失败（Neg 7）
I. 失联闸门：选支失败；诊断无旧实际落点；计划 / 改理想草案作废；PENDING_CLOSE→OPEN（Neg 8；复用 05 语义，不要重写闸门）
J. absentObjectIds 走观测消失并解除待补标绑定记忆（Neg 9）
K. 同一 runtimeId 刷新不作废开放未绑定草案（Neg 10）
L. 建底 POST 覆盖已有 运行于 仍 CURATED_RUNS_ON_EXISTS（Neg 11）
M. 标签命中作废开放未绑定草案（Neg 12）

Happy path 必须串起来的可读状态（字面量）：
1. 主机 A、C；策展 Docker 容器 X（不可变 id 如 ctr-x）运行于 A；现场未打标。
2. Agent 在 A 上报缺标 runtimeId=r1、名近似 X、无 absentObjectIds → 待并入 MISSING_LABEL；X 身份失联；GET 实际在哪不得把 A 报成可用实际（availability 不得为 PRESENT）；by-merge-key 不承诺升级链（CONFLICT_NOT_FOUND 或等价「无升级链」；不要新开冲突）。
3. Agent 在 C、快照不含 X → X 仍失联标来自 A 侧，C 不得单独给 X 打失联（主机范围，01 已有）。
4. A 上第二个 runtime、标签 never-curated → UNKNOWN_OBJECT_ID；POST 未绑定草案 ≥2 条（新建 + 运行于 A）；接受前 GET 策展无该对象。
5. 拒 运行于、接受新建 → 策展对象存在且带该标签、无策展 运行于。再心跳标签命中 → 观测 运行于 A；不得仅因两侧同意就新开冲突或进入待确认关闭。
6. 从 r1 发草案：绑定 X vs 新建。双接受 → 第二次失败。只接受绑定 → 不写可靠观测 运行于；r1 离开待并入；X 仍身份失联。
7. 再缺标心跳 r1 → 仍失联、不复活为可新建候选、仍无升级链。
8. A 上正确标签 archops.object_id=ctr-x 命中 → 清失联、消费绑定记忆、写观测 运行于；若落在策展 A 则既有比对（无冲突或不人造 pending-close）；升级链可恢复。
9.（可选）命中后放到另一策展宿主 B → OPEN 冲突；诊断可再出 FIX_ACTUAL_TO_CURATED / CHANGE_CURATED_TO_OBSERVED。

Reuse 对照（Comments 里要点名，不要复制粘贴聚焦测试当生产）：
- Neg 1 / tracer 3 → UnboundIdentityLostIngestHttpAcceptanceTest
- Neg 2 → VerticalSliceHttpE2eAcceptanceTest + 01/05 unlabeled
- Neg 3 → UnboundBindGateHttpAcceptanceTest
- Neg 4–5 → UnboundDraftCreateHttpAcceptanceTest
- Neg 6–7 → UnboundDraftItemReviewHttpAcceptanceTest
- Neg 8 → IdentityLostPipelineGateHttpAcceptanceTest（05；夹具：先开 OPEN 冲突再失联；PENDING_CLOSE 先对齐再失联；不要用超时扫描模拟失联）
- Neg 9 → 01 absentObjectIds
- Neg 10 / 12 → UnboundLabelMatchConsumeHttpAcceptanceTest
- Neg 11 → CuratedTruthHttpAcceptanceTest / 改策展 01

测试质量：
- 只测公开 HTTP。不测 Mapper、Redis key、私有方法。
- 一圈一条方法。禁止先铺完全部测试再跑。
- 测试名描述能力。@Order 只记录 Spec 顺序。
- 不要把 06 塞进 01/05/竖切测试类。

必须保持绿（跑单测时不要弄红它们）：
- UnboundIdentityLostIngestHttpAcceptanceTest、UnboundDraftCreateHttpAcceptanceTest、UnboundDraftItemReviewHttpAcceptanceTest、UnboundBindGateHttpAcceptanceTest、UnboundLabelMatchConsumeHttpAcceptanceTest、IdentityLostPipelineGateHttpAcceptanceTest
- HeartbeatTimeoutHollowHttpAcceptanceTest、ConflictDiagnosisHttpAcceptanceTest、OperationPlanReviewHttpAcceptanceTest、ChangeCuratedDraft*HttpAcceptanceTest、VerticalSliceHttpE2eAcceptanceTest、ControlledSshExecHttpAcceptanceTest

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 首跑已绿却去删 01–05 生产装红 → 停。
- 想改 observedAskValue 让超时压过失联 → 停，那是票 09。
- 想在 upgradeOpen 里无条件 voidActivePlans → 停，那是 A1。
- 想做 07 UI 或票 09 → 停。
- 想加回控制面 LLM / 拆 MINA / B-live → 停。
- 想抽 05 smell → 停。
- 组合缝红灯就发明新码/新状态 → 停；只用既有码。

Git：从已含票 05 的最新 origin/main（PR #95）开分支。Cloud 分支名须匹配 cursor/<slug>-<本 run 指定后缀>。建议 slug：tdd-implement-unbound-06。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres。单测：
cd backend && ./gradlew test --tests com.archops.observed.UnboundIdentityRebindTracerHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew cleanTest test

Comments 模板（每一圈追加）：
### Cycle <字母> — <行为一句>
Command:
<gradle 命令>
Output:
<green reuse/regression + 聚焦测试名> 或 <red 原因：夹具 vs 组合缝>
Production: 无 / <仅组合缝时的最小既有语义补丁>
Refactor: <套件 helper / 无>
Commit: <hash 或 message>

票结束：
- 验收清单全部勾掉；Status: done
- 更新 docs/dev-handoff.md：frontier 下一张是 07（薄 UI）；09 仍待人排期，不要自动做
- /code-review（Standards + Spec），对照本票清单与 Spec tracer / Negative 1–12
- 不要改 CONTEXT / ADR；不要开 07 除非用户下一条明确说 /implement 07
```
