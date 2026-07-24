# ArchOps UX 澄清任务（AI 配置流程 + 双窗口隐喻）

> 触发：用户纠正 (1) AI Provider 配置交互流程；(2) **操作台** vs **Agent 窗口** 产品隐喻（此前文档把 Agent Window 误等同于侧轨）。  
> 相关：[`workbench-gap-audit.md`](workbench-gap-audit.md)、[`opskat-learning.md`](opskat-learning.md)、[`mainline-implementation-prompt.md`](mainline-implementation-prompt.md)。  
> 对照 OpsKat：`AIProviderForm.tsx`（拉模型、选模型填默认参数）。

---

## 0. 结论先说

1. **W3 已交付字段与 reasoning 枚举**，但交互仍偏「随便填、随时点获取模型」；用户要求的是严格门控流程，且选模型后**自动填充默认参数**。  
2. 此前隐喻错误：**不要把「Agent Window」说成终端页右侧 AI 轨**。正确二分：
   - **操作台（原称 IDE Window）** = Web 终端 + **右侧 AI 对话**（≈ Cursor / VS Code 的编辑器+侧栏 Agent）。
   - **Agent 窗口** = **左侧资产树（≈ Cursor Workspaces）** + **右侧与 Agent 对话**；**没有 Web 终端**。
3. 本文件任务编号：`UX-AI-*`（配置流）、`UX-LAYOUT-*`（双窗口）。可与 W4 并行，但勿与正在改 Provider 表单的 PR 抢同一文件。

---

## 1. 用户期望的 AI 配置流程（权威）

```
填写名称 ──► 填写 API 地址 ──► 填写 API Key
                                    │
                                    ▼
                         「获取模型」按钮变为可用
                                    │
                                    ▼
                         点击「获取模型」→ 模型列表
                                    │
                                    ▼
                         在「模型」框中选择一项
                                    │
                                    ▼
              下方参数自动填充为该模型默认值
              （maxOutputTokens / contextWindow 等）
                                    │
                                    ▼
              思考深度仍用 ArchOps 分级
              （NONE / LOW / MEDIUM / HIGH / XHIGH / MAX）
              用户可再改；不因选模型强制覆盖 reasoning
```

| 步骤 | 规则 |
|------|------|
| 门控 | **名称 + API 地址 + API Key** 均非空后，「获取模型」才 `enabled`（编辑已存 Key 时：有脱敏 Key 且未清空可视为满足 Key） |
| 获取模型 | 成功后模型控件变为可搜索下拉/弹出列表；失败 toast，不静默 |
| 选模型 | 用返回的 `maxOutputTokens` / `contextWindow`（>0）覆盖表单对应字段；toast「已应用模型默认参数」 |
| 手输模型 | blur 时若有 `GetModelDefaults`（或等价 API）则同样回填；未知模型忽略 |
| 思考深度 | **保留**现有分级与 Anthropic `MAX` 规则；不改成别的产品枚举 |
| 字段顺序 | 表单/向导顺序：类型 → 名称 → API 地址 → API Key → **获取模型** → 模型 → 参数区（tokens/context/reasoning…） |

**缺口（相对当前 main）：**

| 缺口 | 现状 |
|------|------|
| 获取模型门控 | 按钮几乎始终可点，未要求名称+地址+Key |
| 选模型回填 | 仅拉 id 列表；无模型默认参数写入表单 |
| 模型默认值 API | 后端无 `GetModelDefaults` / fetch 响应无 tokens·context |
| 表单顺序 | 模型字段在 Key 之前；获取按钮在底部与保存并列 |

---

## 2. 产品结构：操作台 ↔ Agent 窗口（权威）

### 2.1 纠正旧说法

| 旧（错误/不完整） | 新（正确） |
|-------------------|------------|
| 终端页 = IDE；Agent = 侧轨 / Agent Window | **操作台** = 终端中心区 + **右侧 AI 对话**；侧轨是操作台的一部分，不是「Agent 窗口」 |
| Agent Window ≈ Cursor 的 Agent 侧栏 alone | **Agent 窗口**是独立工作面：左资产（Workspaces），右对话；**无终端** |
| 「打开 Agent」= 进入 Agent Window | 操作台上「打开 AI」= 展开右侧对话轨；进 **Agent 窗口**应走独立路由/入口 |

### 2.2 操作台（Ops Console / 原 IDE Window）

```
┌──────────────┬────────────────────────────────┬─────────────────┐
│ 资产树（可选） │   Web 终端 Tabs（操作主区）         │  AI 对话（右侧栏） │
│ 分组 / 搜索   │   xterm · 状态条 · 重连            │  上下文来自当前 Tab │
│              │   人亲手操作资产                    │  工具 / 提案卡片    │
└──────────────┴────────────────────────────────┴─────────────────┘
```

- 类比：**Cursor / VS Code 的 IDE 界面** = 编辑器（≈终端）+ 右侧 Agent。  
- 现有 `/terminal` + AI 侧轨应对齐此隐喻（命名、文案、默认展开策略可改，壳已有基础）。

### 2.3 Agent 窗口（无终端）

```
┌────────────────────┬────────────────────────────────────────────┐
│ 资产 / Workspaces   │           Agent 对话（主区）                  │
│ 树 · 搜索 · 选目标  │           无 Web 终端                         │
│ 可多选上下文资产    │           工具调用 · 提案 · 审批入口            │
└────────────────────┴────────────────────────────────────────────┘
```

- 类比：Cursor 里「以工作区为左栏、以 Agent 对话为右栏」的专注面，**不是**带终端的 IDE。  
- 现有全页 `/ai` 偏聊天窗，**缺左侧资产 Workspaces**；需升级为 Agent 窗口，或新路由（如 `/agent`）并收口导航文案。

### 2.4 二者关系

- **并列入口**：导航上同时有「操作台」与「Agent」。  
- **共用**：资产 ACL、SSH/连接池（Agent 经工具用）、Architecture SSOT + RAG、同一 Provider。  
- **不共用 UI**：Agent 窗口**禁止**嵌 Web 终端；操作台**可以**没有左侧树（窄屏），但必须有终端主区 + 可开右侧 AI。

---

## 3. 任务表

### Wave UX-AI — Provider 配置交互（在 W3 之上精修）

| ID | 任务 | 实现要点 | 完成标准 | 状态 |
|----|------|----------|----------|------|
| **UX-AI-01** | 表单字段顺序与门控 | `AiProvidersView` + `AiProviderSetupWizard`：顺序改为 名称→地址→Key→获取模型→模型→参数；`canFetchModels = name && baseUrl && apiKeySatisfied`；编辑态已存 Key 规则写清 | 未填齐三件套时「获取模型」disabled；填齐后可点 | [x] |
| **UX-AI-02** | 获取模型 → 列表弹出 | 成功后模型控件打开选项列表（Select/Popover）；可搜索；仍允许 tag 手输；失败 toast | 与用户流程第 3 步一致 | [x] |
| **UX-AI-03** | 模型默认参数 API | 后端：`POST .../models` 返回项含可选 `maxOutputTokens`/`contextWindow`；和/或 `GET/POST .../model-defaults?model=`（对照 OpsKat `GetModelDefaults`）；内置常见模型表 + 未知返回空 | 单测：已知模型有默认值；未知不 500 | [x] |
| **UX-AI-04** | 选模型自动填充 | `onUpdate:value` / select：写入 tokens/context；toast；reasoning **不**被默认覆盖（保留用户/分级） | 选完模型后下方参数已变；可再手改 | [x] |
| **UX-AI-05** | 手输模型 blur 回填 | 调用 model-defaults；无数据则静默 | 手输 `gpt-4o` 类已知 id 可回填 | [x] |
| **UX-AI-06** | 文档与验收 | 更新本文件勾选；契约补 model-defaults；`workbench-gap-audit` 指向本波 | 手工剧本：三件套→获取→选模型→参数变→改 reasoning→保存→发消息 | [x] |

**UX-AI 不做：** 改掉 reasoning 分级；明文日志 Key；多租户密钥；Architecture SSOT。

**推荐 PR：** UX-AI-01+02 → UX-AI-03+04+05 → UX-AI-06。

---

### Wave UX-LAYOUT — 操作台 / Agent 窗口

| ID | 任务 | 实现要点 | 完成标准 | 状态 |
|----|------|----------|----------|------|
| **UX-LAYOUT-01** | 文档与文案纠偏 | 改 `workbench-gap-audit` §4、本文件、prompt、导航 i18n：操作台 / Agent 窗口；删除「侧轨=Agent Window」表述 | 文档无矛盾表述 | |
| **UX-LAYOUT-02** | 操作台命名与引导 | `/terminal` 导航称「操作台」或副标题标明；右侧 AI 轨文案为「AI 助手 / 对话」而非「Agent 窗口」；默认策略可保留「进入终端展开轨」 | 用户能分辨「这是操作台」 | |
| **UX-LAYOUT-03** | Agent 窗口布局壳 | 新页或改造 `/ai`：`左 AssetNavTree（Workspaces）+ 右 Chat`；**禁止**挂载 xterm；选中资产写入 Agent 上下文（已有 uiContext/target 则接上） | 无终端 DOM；左选资产右对话可用 | |
| **UX-LAYOUT-04** | 导航与入口 | 顶栏/侧栏：「操作台」「Agent」并列；操作台内「打开 AI 轨」≠ 跳转 Agent 窗口；可选「在 Agent 中打开」带上当前资产 | 两入口可达；深链 `?assetId=` | |
| **UX-LAYOUT-05** | 响应式与空态 | 窄屏：Agent 窗口左树可折叠；无资产/无 Provider 引导与操作台一致（向导链到设置） | 移动端可对话；空态不白屏 | |
| **UX-LAYOUT-06** | 验收 | 剧本 + 勾选；截图或文字对照 §2 ASCII | 两人能说清两个窗口区别 | |

**UX-LAYOUT 不做：** 在 Agent 窗口加终端；重做整站 IA；SFTP/分屏。

**推荐 PR：** UX-LAYOUT-01（可先合）→ 02+03+04 → 05+06。

---

## 4. 与 W0–W4 / ML 关系

| 已有项 | 关系 |
|--------|------|
| W3-01…07 | **字段与 reasoning 保留**；交互由 UX-AI 精修，不重开 W3 |
| W2 终端多 Tab + 侧轨 | 归入**操作台**；侧轨不是 Agent 窗口 |
| ML-8-06 AI 侧轨 | 操作台能力；Agent 窗口是另一面 |
| `/ai` 全页 Chat | UX-LAYOUT-03 升级目标 |
| W4 多类型 | 操作台按 connectAction；Agent 窗口树展示多类型，点非 terminal 类型不强制开终端 |

---

## 5. 手工验收剧本（摘要）

### AI 配置

1. 新建 Provider：只填名称 →「获取模型」灰。  
2. 补地址+Key → 按钮亮 → 点击列出模型。  
3. 选一模型 → max tokens / context 变为默认（若 API 有值）→ toast。  
4. 改思考深度为 HIGH → 保存 → Chat 新消息带上配置。  

### 双窗口

1. 进操作台：有终端；开右侧 AI；**不要**在此页找不到终端。  
2. 进 Agent 窗口：左资产右对话；**页面内无终端**。  
3. 操作台选中资产 A →「在 Agent 中打开」→ Agent 窗口上下文含 A。

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-24 | 初版：用户澄清 AI 配置流 + 操作台/Agent 窗口隐喻；UX-AI / UX-LAYOUT 任务表 |
| 2026-07-24 | UX-AI-01…06 交付：门控获取模型、AiModelInfo/model-defaults、选模型/blur 回填 |
