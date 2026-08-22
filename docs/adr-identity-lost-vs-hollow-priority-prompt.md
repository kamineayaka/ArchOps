# 新对话：身份失联与观测空洞并存时的优先级（小任务 / Claude Opus 5）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。模型选 **Claude Opus 5**。

这是一件**小任务**：一个领域判断 + 至多一条新 ADR（本仓 ADR 体例 = 标题 + 一段话）+ 两处一行同步。不跑 Gradle，不写生产代码，不实现票 09 / 05。若客户端支持附带 skill，附上 `domain-modeling`。

为什么值得用 Opus：审计 **C-1** 暴露的是合同**空白**——`CONTEXT.md` 分别定义了身份失联与观测空洞，但没说两者并存时规范问法与诊断分叉该听谁。今天票 09 已经单方面选了「通道超时优先」，票 05 又要在同一空白上决定分叉集。空白由实现票各自填 = `AGENTS.md` §3 禁止的「实现时顺手改合同」。

---

## 复制区

```text
你是 ArchOps 的领域决策官，不是编码 Agent。本对话只做一件事：裁定「同一对象既被打上身份失联标、其观测通道又确实心跳超时」时，规范问法与 AI 诊断分叉集听谁，并把结论落成合同（至多一条新 ADR）。质量优先于篇幅：结论必须可证伪、必须同时约束问法与诊断、必须说清票 09 与票 05 各读它的哪一句。

加载并遵守：
- AGENTS.md（执行纪律；本对话不 /implement 任何票）
- docs/agents/domain.md（合同冻结：不得静默改写 CONTEXT.md 或已有 ADR；缺口要新 ADR，不是内联改写）
- domain-modeling skill（术语用 CONTEXT 原词；ADR 只在「难回退 + 无上下文会困惑 + 真实取舍」三条都成立时才立）
- 权威顺序：CONTEXT + 有效 ADR > docs/specs/unbound-identity-rebind.md > 票 > 审计报告 > 本 prompt

这是小任务。不要全仓探索，不要重审 01–04，不要跑测试（本判断不需要运行时证据），不要顺手改进代码或文档。

================================================================================
0. 交付物（完成标准：一句话说得出交付物，且不含任何生产代码）
================================================================================

三件，缺一不可：

1. **真值表 + 裁定**：身份失联（有/无）× 心跳超时（有/无）四格，每格写出规范问法「实际在哪」应答什么（availability + identityLost 旗标 + hostId 是否可出现），以及 AI 诊断该给哪一组分叉。
2. **合同落点**，二选一，必须明确选一个并说明理由：
   (甲落点) 新增 `docs/adr/0044-<slug>.md`，本仓体例：一行标题 + **一段**决定（对齐 `docs/adr/0011-object-identity-rules.md`、`0012-container-label-bootstrap-and-identity-loss.md` 的长度与写法，不要写成 Spec，不要 Context/Options/Consequences 小标题）。
   (乙落点) 判定**不值得立 ADR**（照 domain-modeling 三条门槛逐条驳回），改为把裁定写进票 09 的 `## Comments`，并说明为何这不算「实现票私自填合同空白」。
3. **两处一行同步**：`.scratch/unbound-identity-rebind/issues/09-ask-hollow-when-channel-timed-out.md` 与 `.scratch/unbound-identity-rebind/issues/05-identity-lost-gates-conflict-pipeline.md` 各加一行指针，写明该票从这条裁定读什么。不要改两票的验收清单条目本身，不要改 `Status:`。

本对话不做：
- 实现票 09（问法读模型）或票 05（失联闸门）；不写测试、Flyway、UI、前端
- 改 `CONTEXT.md`、改已有 `V*.sql`、改已有 ADR 正文、改 ADR-0039 / 0043
- 重开技术栈；引入 Maven / JPA 当地基 / Vue / Neo4j / LangChain / Redis 当真相 SSOT
- 造合成新词（例如「失联空洞」「半可用观测」）当合同术语
- 顺手做票 05、票 06、票 07，或给未绑定刀加第 10 张票
- 跑 `./gradlew test`（本任务不以运行时为证据；引用代码即可）

================================================================================
1. 先读（完成标准：读完这 7 处即可下判断，不要扩大阅读面）
================================================================================

1. `CONTEXT.md` 三个条目全文 + 各自 _Avoid_：**心跳**、**观测空洞**、**身份失联**（另可扫**观测消失**、**规范问法**、**冲突**）
2. `docs/adr/0039-domain-contract-frozen.md`（合同冻结 → 空白须新 ADR）
3. `docs/adr/0012-container-label-bootstrap-and-identity-loss.md`（失联的来源语义）
4. `.scratch/unbound-identity-rebind/audit-01-03-opus.md` 的 **C-1** 与 **P11 (b)**（本裁定的起点；C-1 的推荐是「甲」但未立合同）
5. `.scratch/unbound-identity-rebind/issues/09-ask-hollow-when-channel-timed-out.md`（已采用「甲」的实现票）
6. `.scratch/unbound-identity-rebind/issues/05-identity-lost-gates-conflict-pipeline.md`（验收里有「心跳通道仍新鲜时，不得改走纯空洞的恢复观测通道分叉集」——反面未定）
7. `docs/specs/unbound-identity-rebind.md` 的 `GET 「实际在哪」 for 身份失联` 段（Must not … **solely** because of 失联）

代码只看两处，够了：
- `backend/src/main/java/com/archops/observed/service/ObservedTruthService.java` 的 `observedAskValue`（现状：先判失联标，再判 `isObservedFactStale` → 超时被失联吞掉）
- `backend/src/main/java/com/archops/conflict/diagnosis/DiagnosisRuleEngine.java`（现有三组分叉：空洞 → `RESTORE_HEARTBEAT_CHANNEL`；观测消失 → `RESTORE_OBSERVATION_OR_RECREATE`；两侧不等 → `FIX_ACTUAL_TO_CURATED` / `CHANGE_CURATED_TO_OBSERVED`）

================================================================================
2. 备选语义（完成标准：选一个，并驳回其余两个，不得含糊其辞）
================================================================================

- **甲｜通道超时优先**：并存时问法答 `HOLLOW`（无当前可用观测值），`identityLost=true` 仅作旗标；诊断给恢复观测通道那一组。审计推荐。理由是通道死了先救通道，「去现场补标」是误导。
- **乙｜失联优先**：并存时问法答 `IDENTITY_LOST`，空洞只在诊断文案里体现。理由是失联要人去现场处理，空洞会掩盖标签已丢这件事。
- **丙｜并列**：问法同时输出两态（不排序），由调用方决定。代价：`availability` 是单值字段，并列意味着改 DTO 形状或加第二字段，且诊断仍得排序。

必须回答的判据（每条一句话，不要展开成小作文）：
- 哪一种会让运维被引向错误动作？（补标 vs 修通道）
- 哪一种会让 `CONTEXT.md` 观测空洞的 _Avoid_（旧值不得当可靠实际）被绕过？
- 哪一种在通道恢复之后能自然回到既有比对 / 升级链，不需要额外状态？
- 选定项是否要求票 09 改验收？（若要求，只指出，不代改条目）

同时钉死两条边界（合同已有，不要在裁定里推翻）：
- 失联**不写入** `observed_fact.availability`，也不做 `ConflictStatus`；`IDENTITY_LOST` 只活在问法读模型
- 不得**单因**失联报 `HOLLOW` / `ABSENT`（票 01 已立；本裁定只管「确实超时」那一格）

================================================================================
3. 步骤（完成标准：每步有 bound；写完就停）
================================================================================

A. 填四格真值表（≤12 行）。
B. 选甲/乙/丙并驳回其余（≤10 行）。
C. 走 domain-modeling 的 ADR 三门槛：难回退？无上下文会困惑？真实取舍？三条都成立 → 走甲落点写 `docs/adr/0044-<slug>.md`（编号已确认：现有最大为 0043）；任一不成立 → 走乙落点，把裁定写进票 09 Comments。
D. 给票 09 与票 05 各加一行指针（写明各读哪一句；不改验收条目、不改 Status）。
E. 停。向我汇报：裁定一句话 + 落点 + 两票各读到什么 + 是否需要票 09 改验收。等我确认。

================================================================================
4. 质量条（交卷前自检）
================================================================================

- [ ] 裁定同时约束**问法**与**诊断分叉**，不是只答问法
- [ ] 明确「不得单因失联报空洞」仍成立，且说清本裁定只覆盖真超时那一格
- [ ] 若写了 ADR：一段话、仓库体例、编号 0044、未改任何已有 ADR 或 CONTEXT
- [ ] 若拒绝写 ADR：逐条驳回三门槛，并说明为何票 09 记录不构成实现票私填合同
- [ ] 未写生产代码 / 测试 / Flyway；未跑 Gradle；未动票 Status 与验收条目
- [ ] 未造合成新词；术语与 CONTEXT 一致
- [ ] 输出总量控制在一屏可读；本任务不需要长报告

现在从步骤 A 开始。
```
