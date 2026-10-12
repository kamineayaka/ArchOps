# 新对话：未绑定 / 身份失联票 07（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `add-frontend-page` — `.cursor/skills/add-frontend-page/SKILL.md`（桌面：`.agents/skills/add-frontend-page/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）；本票只取其 overlay 的 **UI and helpers**，不要走能力票 witnessed red，也不要走 06 的 Suite / tracer
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。本票是 **薄 UI / 演示层**，不是新能力票，不是 HTTP suite。HTTP 主接缝已由票 06 钉绿（PR #97）。领域语义以 `CONTEXT.md` 与有效 ADR 为准。循环纪律以 [`docs/agents/tdd.md`](agents/tdd.md) 的「UI and helpers」+ `add-frontend-page` 为准。

Matt 位置：grilling / to-spec / to-tickets **已完成**。竖切 01–13 与改策展 01–06 已闭合。未绑定票 **01–06 + 08 TDD-done**（PR #97 = 票 06 已合入）。本对话只 `/implement` **未绑定刀 frontier = 07**。不要做票 09，不要给 `change-curated-draft` 加 07，不要重做 01–06 / 08 / 竖切 12 的产品行为。不要拆执行引擎 / AI 编排层。

本票带着代码 vs ADR-0044 只读审计（[`.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md`](../.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md)）：**A2 已由票 05 交付**；**A3 / C-1 是票 09**；**A1**（升级不作废活跃计划）与 **B1–B5**（0044 进程债）禁止写入本票。若 `AGENTS.md` 仍写 frontier=05/06，以 [`docs/dev-handoff.md`](dev-handoff.md) 与本文件为准：frontier = 07。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：实现未绑定 / 身份失联 frontier 工单 07（薄 UI）。质量优先于速度。本票不是新能力票，不是 TDD redo，不是 06 HTTP tracer。01–06 与 08 已闭合（PR #97 = 票 06 已合入）。禁止删除 01–06 / 08 / 竖切 / 改策展生产来装红灯。禁止重做它们的产品行为。票外行为一律不做。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准。若仍写 frontier=05/06，以 docs/dev-handoff.md 与本 prompt 为准：frontier = 07）
- implement skill、add-frontend-page skill、docs/agents/tdd.md 的「UI and helpers」
- tdd skill 只提醒：不要把 Playwright / 组件单测 / 新 HTTP 套件当作完成定义；不要为 UI 票伪造 witnessed red
- docs/agents/domain.md（合同冻结：禁止静默改 CONTEXT.md / 已有 ADR）
- 票结束再用 code-review skill（Standards + Spec）

不要问用户接缝、范围、路由名、循环切分或错误码。下面已钉死。不要把 Playwright、新 Gradle HTTP 测试、SSH fake 或 MINA 当作完成定义。不要默认开工票 09。不要拆执行引擎 / AI 编排层，不要把 WebClient/密钥加回控制面，不要加 B-live / 步骤断言。不要「顺便」抽 05 的 judgement smell。不要重做竖切票 12 冲突工作台。

================================================================================
0. 任务边界（完成标准：一句话说出本票交付物，且不把 09 / A1 / 0044 / 新产品 HTTP 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/unbound-identity-rebind/issues/07-thin-ui.md

Spec：docs/specs/unbound-identity-rebind.md — 只取 Testing seams（前端最小 UI 手工/冒烟，不进自动化主接缝）、User Story 56、Implementation Decisions 的 Frontend / 未绑定草案 HTTP 形状、Prior art（竖切票 12）、Out of Scope。
审计（只读，不要改）：
- .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md
- .scratch/unbound-identity-rebind/audit-01-03-opus.md

最近一次只读架构审计（本对话的边界，不是要你修 0044）：
- A2 已由票 05 交付。本票只读冲突 GET 的 identityLost 旗标做展示，不重做闸门。
- A3 / 审计 C-1 = 票 09。失联叠加心跳超时时问法仍须说出观测空洞。本票不要动 observedAskValue。
- A1 = 另开工单。upgradeOpen 在观测 B→C（健康、未失联）时不作废计划。不要顺便修。
- B1–B5 = ADR-0044 过渡债。MINA 仍在控制面、无编排层、无 B-live、无步骤断言。禁止扩大生产直连 SSH，禁止把 DiagnosisLlmClient / WebClient 加回来。

一句话交付：给已认证运维一块最小 React + Ant Design 界面——列出待并入未绑定与身份失联、从不挂冲突的候选发起草案并按条接受/拒绝、绑定后「实际在哪」仍不把弱线索当可靠实际、标签命中后刷新可读状态——HTTP 只走 frontend/src/api/。不实现新产品 HTTP；不以 Playwright 或 SSH fake 为完成定义。

本票对应 Spec User Story 56。能力已由 01–06 + 08 交付。HTTP tracer 是 06。审计 C-1 是票 09。

本票交付（用户可感知、可在 Vite 点）：
- 新页 frontend/src/pages/UnboundCandidatesPage.tsx（函数组件 + hooks + Ant Design）。Header 增加 Link；不要替换竖切 `/` 冲突列表。
- 路由：/unbound （列表 + 就地或同页草案审条）。可选 sibling /unbound/drafts/:draftId；不要做成完整工作台。
- 新 API 模块 frontend/src/api/observed.ts（listUnboundCandidates / getIdentityLost / getActualWhere / createUnboundDraft）。解包 ApiResponse.data；只用 api/client.ts。
- 扩展 frontend/src/api/drafts.ts：未绑定草案走 GET /api/curated-drafts/{draftId} 与 POST /api/curated-drafts/{draftId}/items/{itemId}/accept|reject（不挂 conflictId）。不要把未绑定审条打到 /api/conflicts/{id}/curated-drafts/...。
- types.ts：CuratedDraft.conflictId 允许 null；补 origin / candidateId / sourceHostId / runtimeId。ObservedValue.availability 字面量可含 IDENTITY_LOST。
- 待并入表：reason、sourceHostId、runtimeId、name、labels（至少 archops.object_id）、upgradeChainPromised=false。reason 字面量 MISSING_LABEL / UNKNOWN_OBJECT_ID。
- 身份失联：不要新集合路由。对已知策展对象（BIND 条目 subjectId、问法输入框、冲突列表 identityLost=true 的旁路）GET /api/observed/identity-lost/{id} 与 GET /api/observed/asks/actual-where?containerId= 以及 GET /api/curated/asks/should-where?containerId=。400 IDENTITY_LOST_NOT_FOUND = 未失联。禁止发明 GET /api/observed/identity-lost。
- 绑定接受后：actual-where 的 observedValue.availability 不得展示为 PRESENT；不得把旧宿主写成可用实际。文案禁止「以现场为准」。
- 错误用 antd Alert / message 展示信封 code：AUTH_REQUIRED、UNBOUND_DRAFT_ALREADY_OPEN、UNBOUND_CANDIDATE_CONSUMED、UNBOUND_BIND_TARGET_ALREADY_BOUND、UNBOUND_BIND_TARGET_HEALTHY、UNBOUND_CREATE_IMMUTABLE_ID_MISSING、DRAFT_VOIDED。
- 演示身份：保留 user-general-demo / user-senior-demo；增加「未认证」（setApiUserId(null)）以便写草案时可见 AUTH_REQUIRED。不要做 JWT。
- 不展示完整 xterm；不接选支修实际 / 批准计划 / start-execution；不把本页做成冲突处理人工作台替代。

本票不做（Out of ticket；发现自己在做就停）：
- 新产品 HTTP、新错误码、新 Flyway、新 ConflictStatus
- Playwright / 组件单测 / 新 Unbound*HttpAcceptanceTest 当作完成定义
- 为装红灯删除或改坏 01–06 / 08 生产或 06 套件
- 票 09：改 observedAskValue，让失联∧超时改答 HOLLOW
- A1：upgradeOpen 无条件作废计划
- 改策展 07、重做竖切 01–13 / 票 12 冲突工作台
- 抽 05 的 duplicated mark probe / unique-site forkId 对
- 执行引擎进程、AI 编排层、B-live、步骤断言、控制面 WebClient/LLM、新的生产直连 SSH API
- Y2 策展对齐步骤、网络可达、K8s/数据库对象、自我迭代、LangChain
- 改已有 V*.sql；改 CONTEXT.md / 已有 ADR 正文
- Maven、JPA 当地基、Vue、Neo4j v1 必选、Redis 当关系真相 SSOT

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完；用票内/合同术语写作，不发明同义新词）
================================================================================

按序阅读，读完再写第一圈页面：

1. AGENTS.md（一次一张；HTTP 已由 06 钉绿；本票是演示层）
2. docs/agents/tdd.md — 「UI and helpers」；不要走 Suite / tracer，不要 TDD redo 删生产
3. .scratch/unbound-identity-rebind/issues/07-thin-ui.md
4. docs/specs/unbound-identity-rebind.md — Testing seams、Story 56、未绑定草案 HTTP 形状、Out of Scope
5. CONTEXT.md — 只用：身份失联、未绑定观测候选、观测空洞、观测消失、规范问法、冲突、待确认关闭、操作计划、冲突处理人、草案、逐条确认。Avoid：以现场为准、未绑定处理人、把失联当成空洞/消失
6. docs/adr/0039、0043、0044（0044 只为禁止本票拆进程 / 加回控制面 LLM）、0011、0012
7. .scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md — A1 / A3 / B 全部停；A2 已由 05 交付
8. docs/dev-handoff.md（确认 frontier = 未绑定 07）
9. 现行样板（读，当 reuse 来源，不重写范围外行为）：
    - frontend/src/pages/ConflictListPage.tsx、ConflictDetailPage.tsx（竖切 12：Table / Alert / DemoUser / api 模块）
    - frontend/src/App.tsx、auth/DemoUserContext.tsx、api/client.ts、api/drafts.ts、api/curated.ts、api/types.ts
    - .cursor/skills/add-frontend-page/SKILL.md、.cursor/rules/frontend-react.mdc
    - UnboundIdentityRebindTracerHttpAcceptanceTest（06；夹具与可读状态，不是本票要改的测试）
    - ObservedController / CuratedDraftController（既有路由；不要加新 Mapping）

接缝已确认：自动化主接缝仍是控制面公开 HTTP（06 已绿）。本票定义完成 = 票验收清单 + `cd frontend && npm run build` + Vite :5173 手工/冒烟。Cloud 可用 computerUse 走演示路径当证据，但不要把录屏或 Playwright 写成 CI 门槛。不要跑 MINA，不要 start-execution。

用合同术语。未绑定 ≠ 冲突 ≠ 身份失联 ≠ 观测空洞 ≠ 观测消失。

================================================================================
2. 思想与质量条
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 两侧可用且不等。本票 motto：匹配失败不升冲突；失联不是空洞；旧实际不可当作唯一落点。本票只把已交付 HTTP 接到薄 UI。

栈：React + TypeScript + Vite + Ant Design（ADR-0043）。HTTP 只走 frontend/src/api/。禁止 Vue / Naive / 第二套 fetch 客户端。后端 Java 21 / Gradle / MyBatis-Plus / Flyway 只增不改历史——本票不要动。ADR-0044：本票不拆进程、不加控制面 LLM、不扩大 MINA。

认证：Header X-ArchOps-User-Id。一般 = user-general-demo，高级 = user-senior-demo。未认证 = 不带头。Agent ingest 不在本页调用。

UI 循环（每一圈仍三条：写一个可点切片 → 构建/冒烟该切片 → 记录 + refactor + 提交）：
1. 写 **一个** 用户可感知切片（一页或一页上的一块）。不要先铺完全部页面再验证。
2. `cd frontend && npm run build`。把命令与输出追加到票 ## Comments。Cloud 对该切片做一次 Vite 冒烟（列表能出来 / 按钮能点 / 错误码能看见）。
3. 缺后端字段就停：只展示既有 JSON；禁止为 UI 加新路由或新码。
4. Refactor 仅限本票前端 helper / 命名。再 build 仍过。提交这一圈（why）。下一切片。

建议循环顺序（可按教学调整，但每圈仍一块）：
A. api/observed.ts + UnboundCandidatesPage 列出待并入（reason / 宿主 / runtime / 标签线索）；Header Link
B. 身份失联 + 应该在哪 / 实际在哪（compose 既有 GET；不新开集合路由）
C. 对一个候选发起草案；展示 ≥2 条；按条接受/拒绝
D. 互斥失败与未认证失败有可见提示（第二份 OPEN 草案、无头 AUTH_REQUIRED、双接受第二次失败、MISSING_LABEL 新建 UNBOUND_CREATE_IMMUTABLE_ID_MISSING）
E. 绑定接受后刷新 actual-where：availability 不得为 PRESENT；失联仍在（IDENTITY_LOST / identityLost=true）
F. 文案与空态：upgradeChainPromised 不承诺升级链；无「以现场为准」；空列表可演示

既有 HTTP（只封装，不发明）：
- GET  /api/observed/unbound-candidates
- POST /api/observed/unbound-candidates/{candidateId}/drafts
- GET  /api/observed/identity-lost/{curatedObjectId}
- GET  /api/observed/asks/actual-where?containerId=
- GET  /api/curated/asks/should-where?containerId=
- GET  /api/curated-drafts/{draftId}
- POST /api/curated-drafts/{draftId}/items/{itemId}/accept
- POST /api/curated-drafts/{draftId}/items/{itemId}/reject
- GET  /api/conflicts（可选旁路：identityLost=true 的行；不要在本页做认领/选支）

标签命中收尾仍是现场补标 + Agent 心跳（04/06 已有）。UI 只提供刷新按钮读回清失联 / PRESENT。不要从本页 POST /api/agent/heartbeat。

测试质量：
- 不要新增 Playwright。不要新增 Gradle HTTP 测试类。
- 不要把 07 塞进 01–06 / 竖切测试类。
- npm run build 必须过。不要弄红既有 frontend。

必须保持绿（不要改它们来迁就 UI）：
- UnboundIdentityRebindTracerHttpAcceptanceTest（06）
- UnboundIdentityLostIngestHttpAcceptanceTest、UnboundDraftCreateHttpAcceptanceTest、UnboundDraftItemReviewHttpAcceptanceTest、UnboundBindGateHttpAcceptanceTest、UnboundLabelMatchConsumeHttpAcceptanceTest、IdentityLostPipelineGateHttpAcceptanceTest
- HeartbeatTimeoutHollowHttpAcceptanceTest、ConflictDiagnosisHttpAcceptanceTest、OperationPlanReviewHttpAcceptanceTest、ChangeCuratedDraft*HttpAcceptanceTest、VerticalSliceHttpE2eAcceptanceTest、ControlledSshExecHttpAcceptanceTest

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环（一圈一块 UI）。
- 想加 GET 失联集合或新错误码 → 停。
- 想改 observedAskValue 让超时压过失联 → 停，那是票 09。
- 想在 upgradeOpen 里无条件 voidActivePlans → 停，那是 A1。
- 想做票 09 或重做 06 tracer → 停。
- 想加回控制面 LLM / 拆 MINA / B-live → 停。
- 想抽 05 smell → 停。
- 想把未绑定审条接到冲突处理人路由 → 停。
- 想用 Playwright 当 CI 门槛 → 停。

Git：从已含票 06 的最新 origin/main（PR #97）开分支。Cloud 分支名须匹配 cursor/<slug>-<本 run 指定后缀>。建议 slug：tdd-implement-unbound-07。每圈可点后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。backend `./gradlew bootRun`，frontend `npm run dev -- --host 0.0.0.0 --port 5173`（/api 代理 :8080）。本票不要求跑 Gradle 全量；票结束：
cd frontend && npm run build
若改了 frontend 之外的文件（不应发生）再 cd backend && ./gradlew test。

Comments 模板（每一圈追加）：
### Cycle <字母> — <行为一句>
Command:
cd frontend && npm run build
Output:
<green build / 冒烟看见了什么>
Production: <本圈前端文件> / 无后端
Refactor: <helper / 无>
Commit: <hash 或 message>

票结束：
- 验收清单全部勾掉；Status: done
- 更新 docs/dev-handoff.md：本刀演示层闭合；票 09 仍待人排期，不要自动做；不要发明未绑定 10
- /code-review（Standards + Spec），对照本票清单与 Spec Story 56
- 不要改 CONTEXT / ADR；不要开票 09 除非用户下一条明确说 /implement 09
```
