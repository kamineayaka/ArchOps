# 从 OpsKat 学什么：面向 ArchOps 主线的结论

> 对照仓库：[opskat/opskat](https://github.com/opskat/opskat)（Go + React + Wails 桌面工作台）。  
> ArchOps 北极星：[`product-vision.md`](product-vision.md)（共享活 Architecture + Work Log + RAG）。  
> 主线任务：[`mainline-implementation-plan.md`](mainline-implementation-plan.md)（`ML-*`）。  
> 本文回答：**哪些值得学、如何改编进 B/S 多用户控制面、哪些不要照搬。**

---

## 1. 产品定位差异（先定边界）

| 维度 | OpsKat | ArchOps |
|------|--------|---------|
| 形态 | 单机桌面工作台（IPC，无对外 HTTP） | B/S 控制面（多用户、JWT、审批、审计） |
| 执行面 | 操作者本机进程直连资产 | 服务端 SSH/工具代理 |
| 知识 | 资产 `Description` + 会话历史 | **组织级 Architecture SSOT + RAG** |
| 资产面 | SSH/RDP/DB/Redis/Kafka/K8s/OSS… 一站式 | 先 Linux/K8s 舰队 + 架构记忆；类型可扩展 |
| 治理 | 本地策略 / grant / 审批 socket | RBAC + 执行审批 + **知识提案审批** |

**结论：** 学它的 **可扩展资产类型、策略门、工具总线、连接池与 UI 信息架构**；不要学它把「笔记本当控制面」和「Description 当架构真相」。

---

## 2. 值得学习的核心（按优先级）

### P1 — 资产类型注册表（前后端双注册）

**他们怎么做：**

- 后端 `AssetTypeHandler`：`Type / DefaultPort / SafeView / ResolvePassword / DefaultPolicy / PolicyKind / Validate|ApplyCreate|Update`（`internal/assettype/registry.go`），各类型 `init()` 注册。
- 前端 `registerAssetType({ connectAction, ConfigSection, DetailInfoCard, policy })`。
- 文档：`docs/references/adding-an-asset-type.md`；原则：**扩展靠注册，禁止共享代码里 `switch type`**（`AGENTS.md`）。
- 资产一行 + `config` JSON；分组树 + 标签；策略可沿 group 链继承。

**ArchOps 建议：**

| 动作 | 落入主线 |
|------|----------|
| 引入 `AssetTypeHandler`（Java SPI / Spring `@Component` 注册）+ 前端类型模块 | **ML-1 扩展**：分组之外增加「类型可插拔」子任务 |
| `PolicyKind` 与 asset type 解耦（同一策略可服务多类型） | 对接现有 `RiskClassifier` / 审批 |
| 分组树 + 策略继承 | 强化 **ML-1**（已有 AssetGroup 方向） |
| 暂时不必一次做齐 DB/Kafka/RDP | 类型注册先支撑 `SERVER` / `K8S` / 未来 `DATABASE`；GUI 能力按类型渐进 |

### P1 — 工具调用治理闭环

**他们怎么做：**

- LLM 工具 → 权限检查 → **Allow / Deny / NeedConfirm** → 审计带 `decision_source`。
- Grant「本次记住」；SQL 语句级解析、shell AST；`cp` 与命令 grant 隔离。
- 同一套 tool handler 给 Agent 与 `opsctl` 复用。

**ArchOps 建议：**

| 动作 | 说明 |
|------|------|
| 保留并加强现有 ApprovalGate | 对齐 NeedConfirm；记录决策来源（auto / user / grant） |
| 增加 **持久 Grant**（会话级/TTL 白名单） | 减少重复点批；必须带 userId + asset 范围 |
| 命令风险：从关键词升级到更细规则 | 可借鉴「按协议 PolicyKind」；不必一上来 TiDB parser |
| CLI/API/Agent 共用工具总线 | 与 ARCH「tools 包」一致；未来 `archopsctl` 可走同一执行器 |

### P1 — 上下文组装模式（内容换成我们的 SSOT）

**他们怎么做：**

- `PromptBuilder`：语言、**当前打开的 tabs**、mention、资产 Description、多资产/batch、密钥警示、扩展 SKILL.md。
- 「先发现再行动」的工具指引写进 system 动态段。

**ArchOps 建议：**

- **保留槽位设计**：`openTargets` / 用户 mention / 范围化 RAG / 活跃 Architecture facts / 最近 Work Log。
- **替换内容源**：Description scrapbook → **Architecture 分区 + facts + RAG**（愿景 §3–§4、ML-2/ML-6）。
- 聊天侧展示「本轮引用了哪些架构切片」（ML-8-03），对标他们 context bar。

### P2 — SSH 池与跳板链

**他们怎么做：**

- `sshpool`：按 assetId、refcount、keepalive、空闲 5min 回收；`PoolDialer` 解耦鉴权/跳板。
- `proxy_chain`：SSH 跳板 / SOCKS5 / HTTP tunnel，多协议复用。
- **注意：** UI 交互终端、AI 执行缓存、sshpool **三套连通路径并存** —— 这是反面教材。

**ArchOps 建议：**

| 学 | 改 |
|----|----|
| refcount + idle + dialer 接口 | 池键改为 `(userId, assetId[, credentialVersion])`（我们已有用户维度） |
| 跳板链作为一等运输能力 | 资产配置支持 jump host 链（ML 可增「连接拓扑」任务，≠ 架构 SSOT） |
| 隧道复用池给非 SSH 协议 | 未来 DB/K8s API 经跳板时复用 |
| — | **统一** 终端 WebSSH 与 `ssh_exec` 走同一池（我们方向已对，坚持不要再分叉） |

### P2 — AI Provider 配置体验

**他们怎么做：** Setup Wizard、拉模型列表、reasoning 开关、密钥加密存储、表单清晰。

**ArchOps 建议：** 我们已有 Provider CRUD；补齐 **向导式首次配置、测连通、模型下拉刷新、失败可读错误**；密钥脱敏与轮换文案对齐部署文档。

### P2 — 前端信息架构

**他们怎么做：**

- 侧栏资产树 + 主区多 Tab（terminal / ai / query / page）+ **AI 侧栏常驻**（边开终端边问 AI）。
- 类型驱动 `connectAction`；分屏终端；SFTP。

**ArchOps 建议：**

| 优先 | 说明 |
|------|------|
| **AI 侧轨 / 可钉住助手** | 终端页与资产页旁挂 ArchOps Agent，不必只能进独立 AI 路由 |
| 资产树（组 → 成员） | 服务 ML-1 Hadoop 故事 |
| 连接动作注册表 | 点资产 → Terminal / 未来 Query |
| 暂缓 | RDP/VNC/全量 DB GUI（非主线，可列为 P3 工作台能力） |

### P3 — 工程纪律（可直接搬）

- 扩展靠注册、依赖接口、archtest 防分层穿透。
- 「Reuse first / 边界校验 / 不吞错误」写进 AGENTS 类文档。
- 贡献者文档：`adding-an-asset-type` 一站式。

**ArchOps：** 已有 `ARCH-A4-01` ArchUnit；可补 `docs/adding-an-asset-type.md` 与前端类型注册约定。

---

## 3. 明确不要照搬

1. **单用户桌面信任模型**（本机 keychain、Unix socket 审批）→ 多用户必须 HTTP/WS + RBAC。  
2. **用 Description 当架构记忆** → 污染与不可治理；我们走 Proposal + SSOT。  
3. **无拓扑/CMDB 却号称「懂集群」** → 我们主线就是补 Architecture。  
4. **三套 SSH 连接路径** → 控制面必须统一会话经纪。  
5. **控制面本机 `local_bash` 当一等 Agent 能力** → 多用户场景高危。  
6. **一次做完所有资产 GUI** → 会冲淡 Living Architecture 主线。

---

## 4. 映射到 ArchOps 主线：已正式立项

下列项已写入 [`mainline-implementation-plan.md`](mainline-implementation-plan.md)，状态以该清单勾选为准：

| ID | 内容 | OpsKat 来源 |
|----|------|-------------|
| **ML-1-06** | 资产类型 SPI（后端 Handler + 前端 register） | assettype 双注册 |
| **ML-1-07** | Jump / proxy chain | proxy_chain / Dialer |
| **ML-3-07** | 执行 Grant + decision_source | permission / grant |
| **ML-4-07** | Prompt 槽位组装 | PromptBuilder |
| **ML-8-06** | 资产树 + AI 侧轨 | App shell / SideAssistant |
| **ML-8-07** | AI Provider 首次向导 | AISetupWizard |

指挥 Agent：使用 [`mainline-implementation-prompt.md`](mainline-implementation-prompt.md) 中 **「一键 Prompt — OpsKat 第二波」** 或阶段 O1–O4。

**推荐顺序：** `ML-1-06` →（`ML-1-07` ∥ `ML-8-07`）→ `ML-4-07` → `ML-3-07` → `ML-8-06`。
---

## 5. 一句话总判

OpsKat 是优秀的 **「单人运维工作台 + 带策略的 AI 手」**；ArchOps 要做的是 **「多人控制面 + 可治理的组织架构记忆」**。  

**最该偷的三样（原）：** ① 资产/策略双注册；② Allow/Deny/Confirm/Grant/Audit；③ 上下文槽位。  

**2026-07-23 修正（用户三图反馈）：** 另须 **字段级对齐** SSH 创建表单、**布局级对齐** 终端 IDE+Agent 侧栏、**字段级对齐** AI Provider（含 reasoning）。工作台不足会阻断主线触达。详见 [`workbench-gap-audit.md`](workbench-gap-audit.md)。

**最该自己做、他们没有的：** 分区 Architecture SSOT、Proposal 写回、范围化 RAG、多用户 ACL。

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-23 | 初版：基于 opskat 源码阅读的 ArchOps 主线学习结论 |
| 2026-07-23 | ML-*-06/07 已正式写入 mainline-implementation-plan；指挥入口改为 OpsKat 第二波 Prompt |
