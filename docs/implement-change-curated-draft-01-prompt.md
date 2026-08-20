# 新对话：改策展票 01 TDD 重做（Prompt）

将下面 **「复制区」** 整段作为新对话的第一条用户消息。若客户端支持手动附带 skill，同时附上：

- `implement` — `.cursor/skills/implement/SKILL.md`（桌面：`.agents/skills/implement/SKILL.md`）
- `tdd` — `.cursor/skills/tdd/SKILL.md`（桌面：`.agents/skills/tdd/SKILL.md`）
- `code-review` — 票结束时用；不要用它替代每一圈的 refactor

本文件是 `/implement` 入口。循环规则以 [`docs/agents/tdd.md`](agents/tdd.md) 与 `/tdd` skill 为准；领域语义以 `CONTEXT.md` 与有效 ADR 为准。

---

## 复制区

```text
/implement /tdd

你是 ArchOps 的编码 Agent。本对话只做一件事：按严格 TDD（red → green → refactor）重做改策展 frontier 工单 01。质量优先于速度：没有 witnessed red 的绿灯不算完成；没有每圈 refactor 的实现不算完成；票外行为一律不做。

加载并遵守：
- AGENTS.md（执行纪律；与 skill 冲突时本文件 + AGENTS.md + docs/agents/tdd.md 为准）
- implement skill、tdd skill、docs/agents/tdd.md
- 票结束再用 code-review skill（Standards + Spec）

================================================================================
0. 任务边界（完成标准：你能用一句话说出本票交付物，且不把 02–06 算进范围）
================================================================================

工单（唯一验收清单）：
.scratch/change-curated-draft/issues/01-close-bootstrap-runs-on-overwrite.md

Matt 位置：主路径 idea → ship 已走完 grilling / to-spec / to-tickets。本对话是 /implement 的一张 frontier 票，内部驱动 /tdd。竖切 MVP 01–13 已闭合。改策展 01–03 曾同提交落地，现以 TDD 重做；本对话只重做 01。

本票交付（用户可感知、HTTP 可断言）：
- 尚无 `运行于` 的容器：POST /api/curated/facts/runs-on 仍插入第一条；随后 GET 「应该在哪」读到该宿主（竖切建底保留）。
- 已有 `运行于` 的容器：再 POST 同一或不同宿主，必须拒绝；策展事实与 「应该在哪」均不变。
- 拒绝走统一信封：HTTP 400，ApiResponse.success=false，code=CURATED_RUNS_ON_EXISTS，data=null。
- 关掉的是建底旁路。合法策展改写仍是「接受的草案条目」（04）或计划内显式对齐步（本刀不含）。

本票不做：改理想分叉、选支、草案表、逐条确认、立刻比对、冲突 UI、SSH、Y2 策展对齐、改 Flyway 历史、改 CONTEXT/ADR 语义、重做竖切 01–13、实现改策展 02–06。

冲突优先级：ADR 与 CONTEXT > Spec > 票 > 本 prompt。票过宽时缩到验收清单。

================================================================================
1. 先读（完成标准：按序读完下列文件；用票内术语写作，不发明同义新词）
================================================================================

按序阅读，读完再改代码：

1. AGENTS.md
2. docs/agents/tdd.md
3. .scratch/change-curated-draft/issues/01-close-bootstrap-runs-on-overwrite.md
4. docs/specs/change-curated-draft.md — 只取：Testing seams (confirmed)、Curated write bypass、Testing Decisions（本票对应 Negative 第 5 条）
5. CONTEXT.md — 只用：策展真相、运行于、应该在哪、草案、逐条确认
6. docs/adr/0039-domain-contract-frozen.md
7. docs/adr/0043-tech-stack.md
8. docs/adr/0006-curated-writes-via-itemized-proposals.md（为何必须关覆盖旁路）
9. docs/adr/0002-dual-track-relationship-truth.md（双轨；「应该在哪」只答策展）
10. docs/dev-handoff.md（确认 frontier = 01）
11. 现行样板（读，不重写竖切故事）：
    - backend/src/test/java/com/archops/curated/CuratedTruthHttpAcceptanceTest.java
    - backend/src/main/java/com/archops/curated/service/CuratedTruthService.java 的 confirmRunsOn
    - backend/src/main/java/com/archops/curated/controller/CuratedController.java 的 POST /facts/runs-on
    - backend/src/main/java/com/archops/common/api/ApiResponse.java
    - backend/src/main/java/com/archops/common/exception/BusinessException.java
    - backend/src/test/java/com/archops/support/HttpAcceptanceTest.java
    - .cursor/rules/backend-java.mdc

接缝已确认，不必再问用户：唯一自动化验收接缝 = 控制面公开 HTTP API（Gradle MockMvc 与 bootRun+curl 是同一条接缝）。本票无 UI、无 Agent ingest 新行为、无 SSH。

================================================================================
2. 思想与质量条（完成标准：后续每一步都能对照这一节说「满足」）
================================================================================

产品：运维关系真相。策展 = 理想；观测 = 实际；冲突 = 偏差。策展写入必须经草案逐条确认或计划内显式对齐步；AI 不能独自定稿策展。建底 POST 覆盖已有 `运行于` 会让冲突路径旁路草案，必须关掉。

栈（ADR-0043）：Java 21、Spring Boot 3、Gradle、MyBatis-Plus、Flyway、PostgreSQL SSOT。Redis 不作关系真相 SSOT。本票不引入 Maven、JPA 当地基、Vue、Neo4j、LangChain。

分层：Controller → Service → Mapper；DTO 用 record；DO 不当响应；构造器注入；业务错误 BusinessException；写操作事务在 service。

测试质量（/tdd）：
- 测公开 HTTP 行为：状态码、ApiResponse 信封、后续 GET 可读状态。
- 期望值来自独立真相：字面量 CURATED_RUNS_ON_EXISTS、「应该在哪」、宿主 A。禁止用实现再算一遍期望。
- 不测 Mapper SQL、Redis key、confirmRunsOn 私有分支、调用图。
- 一圈一条行为。禁止先铺完全部测试再实现，也禁止先写完实现再补测。
- 不 mock 本模块协作对象。本票用现有 @HttpAcceptanceTest（Zonky embedded Postgres）。

TDD 循环（每一圈三条全做）：
1. Red：一条失败测试；跑命令；失败原因是「缺本圈行为」（编译失败或断言失败都算）。把完整命令与失败输出追加到票 ## Comments。
2. Green：只写让这一条测试通过的最少生产代码。
3. Refactor：不改行为，整理命名与结构；再跑同一条测试，仍绿。然后提交这一圈。

Witnessed red 是硬门。已经绿的测试不能事后称作 TDD 完成。/code-review 是票结束第二道门，替代不了每圈 refactor。

质量优先的裁决：
- 循环纪律与赶工冲突 → 守循环。
- 全量测试红了 → 先修到绿，再开下一圈。
- 想「顺便」做 02–06 或加抽象 → 不做。
- 只删 throw、让第二下 POST 撞 UNIQUE(subject_id, relation_type) 变 500 → 不是诚实红灯。必须先恢复「覆盖更新」，让失败表现为 200 且「应该在哪」变成 B。

Git：从最新 main 拉分支 cursor/tdd-redo-change-curated-01-24fb（或同等 cursor/…-24fb）。每圈绿灯后提交，信息写 why。不提交 .env、密钥、node_modules、build/。不 force push、不 amend。

Cloud：Compose 的 Postgres/Redis 由 start 拉起。本票 HTTP 测试用 embedded Postgres，不依赖 Redis。单测：
cd backend && ./gradlew test --tests com.archops.curated.CuratedTruthHttpAcceptanceTest.<method>
票结束：
cd backend && ./gradlew test

================================================================================
3. 现状（完成标准：你能指出「哪段生产代码是 01 的旁路关闭」，以及为何它现在不是 TDD）
================================================================================

现行 confirmRunsOn：已有 RUNS_ON 则 throw BusinessException("CURATED_RUNS_ON_EXISTS", …)；无则 insert。
现行测试：CuratedTruthHttpAcceptanceTest.bootstrapRunsOnPostRejectsOverwriteOfExistingFact 在同一方法里覆盖了「先插入 A + 覆盖到 B 被拒 + 再 POST 同一宿主 A 被拒 + GET 不变」。这是同提交补测，不是 witnessed red。

竖切已交付、必须保持绿：
- createHostsContainerConfirmRunsOnAndAskShouldWhere（第一次 POST 插入 + GET 「应该在哪」）
- 其他 *HttpAcceptanceTest / VerticalSliceHttpE2eAcceptanceTest 里对 confirmRunsOn 只 POST 一次的夹具

表 curated_fact 已有 UNIQUE (subject_id, relation_type)（V3）。本票不改 V*.sql，不新增空迁移。

01 落地前的生产行为（须在 TDD 开始时恢复，作为红灯夹具）：已有事实则 update targetId（覆盖），无则 insert。见提交 e009d30 对 CuratedTruthService.confirmRunsOn 的反向。

================================================================================
4. 步骤（按序；每步有完成标准。未完成不准跳到下一步）
================================================================================

### 步骤 A — 恢复覆盖，制造诚实红灯

只改 confirmRunsOn：有已存在的 `运行于` 时恢复为更新 target（旧旁路）；第一次仍 insert。
不要在这一步写拒绝逻辑。不要改测试（可稍后在红灯下拆方法）。

完成标准：下列命令失败，且失败是「第二下 POST 得到 200 / 「应该在哪」变成 B」，不是 UNIQUE 约束 500：
cd backend && ./gradlew test --tests com.archops.curated.CuratedTruthHttpAcceptanceTest.bootstrapRunsOnPostRejectsOverwriteOfExistingFact

createHostsContainerConfirmRunsOnAndAskShouldWhere 仍绿。
把该红灯命令与输出贴到票 Comments。可提交：「Restore bootstrap 运行于 overwrite so ticket 01 TDD starts red」。这不是产品完成。

### 步骤 B — 拆成一行为一测试（仍在红灯下）

把覆盖拒绝拆成至多两个测试方法，每个方法只证明一件事：
1. 已有 A 时，POST 到不同宿主 B：400 + CURATED_RUNS_ON_EXISTS + data=null；随后 GET /api/curated/asks/should-where 与 GET /api/curated/facts/runs-on/{containerId} 仍为 A。
2. 已有 A 时，POST 到同一宿主 A：同样拒绝；「应该在哪」仍为 A。

第一次插入成功继续由 createHostsContainerConfirmRunsOnAndAskShouldWhere 守住；不要把竖切建底故事抄进 01 的拒绝测试里当第二条实现路径。
风格对齐现有 *HttpAcceptanceTest：MockMvc、统一信封、X-ArchOps-User-Id（TempAuthHeaders.USER_ID，一般角色 user-general-demo）、Hamcrest。
测试名描述能力（例如 bootstrapPostRejectsOverwriteToDifferentHost），不描述 Service 方法名。

完成标准：每个新方法单独跑都红；红的原因仍是覆盖成功而不是 500。Comments 追加这两条红灯。

### 步骤 C — 第 1 圈：拒绝覆盖到不同宿主

只针对「POST 到 B 被拒且策展仍为 A」。
Green：confirmRunsOn 在 existing != null 时抛 BusinessException("CURATED_RUNS_ON_EXISTS", …)，经 GlobalExceptionHandler 变成 400 信封。最少代码。不要加草案、事件、比对触发。
Refactor：命名与结构；再跑这一条测试仍绿。
提交该切片（why：关建底旁路，使已有运行于不能改到另一宿主）。

完成标准：该测试绿；Comments 有本圈 red 与 green 命令；createHostsContainerConfirmRunsOnAndAskShouldWhere 仍绿。

### 步骤 D — 第 2 圈：拒绝覆盖到同一宿主

跑「POST 同一宿主 A 被拒」测试。
- 若仍红：只补让它绿的最少代码，再 refactor、提交。
- 若步骤 C 的规则（凡已存在即拒）已使它绿：在 Comments 写明「与 C 同一规则，本方法作为回归留下；未另写生产代码」。不要为了「看起来像一圈」去改实现。不要删这个测试。

完成标准：两个拒绝测试都绿；「应该在哪」字面量为 A；信封 code 字面量为 CURATED_RUNS_ON_EXISTS。

### 步骤 E — 票级回归与收尾

cd backend && ./gradlew test
失败则修到全绿（仍不扩范围）。

对照工单清单逐条用 HTTP 证据勾选。
/code-review：Standards + Spec。固定点用本分支相对 main 的 merge-base（或步骤 A 的恢复提交）。Spec 源：本票 + Spec 的 Curated write bypass。审查发现的行为错误要修并回归；气味按 judgement 处理，不借审查塞进 02–06。

更新文档指针（01 完成后 frontier = 02；不要实现 02）：
- 本票：Status: done；验收项全勾；Comments 含每圈 red
- docs/dev-handoff.md
- AGENTS.md 当前工单 / §6
- CLAUDE.md 工单行
- docs/agents/issue-tracker.md 表
- .cursor/rules/project-map.mdc、domain-contract.mdc
- docs/specs/vertical-slice-mvp.md 的 Next Matt step（01 TDD-done，frontier 02）

完成标准：全量测试绿；票 done；handoff 下一对话指向 02 的 TDD 重做提示词 docs/implement-change-curated-draft-02-prompt.md；工作区无票外文件。

================================================================================
5. HTTP 契约（本票断言用；完成标准：测试只断言这些可观察值）
================================================================================

认证：Header X-ArchOps-User-Id。
插入：POST /api/curated/facts/runs-on  body {"containerId":"…","hostId":"…"}  → 200，success=true，data.target.id = 该宿主。
规范问法：GET /api/curated/asks/should-where?containerId=…  → data.question 为「应该在哪」，data.track 为 CURATED，data.curatedValue.hostId 为策展宿主。
事实读取：GET /api/curated/facts/runs-on/{containerId}  → data.target.id 同上。
拒绝：同一容器再次 POST → 400，success=false，code=CURATED_RUNS_ON_EXISTS，data=null；随后两个 GET 仍为原宿主。

关系文案用「运行于」/ RUNS_ON，与现网竖切测试一致。

================================================================================
6. 停工检查（全部为真才许把票标 done）
================================================================================

- [ ] 每圈 Comments 里有独立的 red 命令与失败输出
- [ ] 没有「先实现后补测」或「测试已绿再宣称 TDD」
- [ ] 第一次插入仍绿（竖切测试未改语义）
- [ ] 覆盖到不同宿主、覆盖到同一宿主均拒；「应该在哪」仍为原宿主
- [ ] 未改任何已有 V*.sql；未新增无表迁移
- [ ] 未新增草案/诊断/选支/UI/SSH
- [ ] ./gradlew test 全绿
- [ ] /code-review 已跑；行为问题已修
- [ ] 文档 frontier 指向 02；02 的代码未改
```

---

完成后下一对话：**TDD 重做 02**。提示词 [`docs/implement-change-curated-draft-02-prompt.md`](implement-change-curated-draft-02-prompt.md)。
