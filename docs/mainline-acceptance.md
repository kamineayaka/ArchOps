# ArchOps 主线验收剧本与成功指标

> 固化 [`docs/product-vision.md`](product-vision.md) §11。  
> 各大阶段（ML-1…ML-8）结束后对照打勾。  
> 领域：[`mainline-domain-model.md`](mainline-domain-model.md) · API：[`mainline-api-contracts.md`](mainline-api-contracts.md)

---

## 1. 成功长什么样（愿景 §11）

- [ ] 新会话询问 Hadoop 问题时，Agent 能引用**已确认**的 NN/DN 等事实，而不是从头猜。  
- [ ] 错误架构写入可被发现、**回滚**，且审计可追责。  
- [ ] 只读排障（L0）**不会污染** Architecture；认知升级（L1）与环境变更（L2）留下清晰提案与日志。  
- [ ] 权限不足的用户既不能乱执行，也不能改写其不可见资产的架构知识。

---

## 2. 主线演示剧本：Hadoop 三机

### 前置

| # | 步骤 | 完成 |
|---|------|------|
| P1 | 部署平台；ADMIN 登录 | [ ] |
| P2 | 录入三台 Linux 资产（含 SSH 凭证）：node1 / node2 / node3 | [ ] |
| P3 | 创建资产组 `Hadoop`，成员为上述三机（**ML-1**） | [ ] |
| P4 | 新对话仅绑定目标组 `Hadoop`（可不单独勾选资产）（**ML-1**） | [ ] |

### 发现与提案

| # | 步骤 | 期望 | 完成 |
|---|------|------|------|
| A1 | 对话：「这是 Hadoop 集群，帮我确认各节点角色」 | Agent 仅对组内机器 `ssh_exec`；目标外 assetId 被拒 | [ ] |
| A2 | Agent 观察日志/进程，识别 NN/DN | 产生 L1 级认知；**不**静默写 SSOT | [ ] |
| A3 | 系统/模型创建 Architecture Proposal（含 provenance） | 状态 `PENDING_REVIEW`；含 evidence（command/输出摘要/assetId） | [ ] |
| A4 | OPERATOR/ADMIN（非提案者）批准 | 合并进 `group:{hadoopId}`（及/或成员 `asset:{id}`）分区 | [ ] |
| A5 | RAG / 架构视图可见 NN=node1、DN=node2/3 | 分区修订版本 +1；审计有合并记录 | [ ] |

### 反哺与防污染

| # | 步骤 | 期望 | 完成 |
|---|------|------|------|
| B1 | **新会话**再问「Hadoop NameNode 在哪」 | RAG/上下文命中已确认事实，减少盲目探测 | [ ] |
| B2 | 对话中执行 `df -h` 类只读 | L0：仅 Work Log；**无**新 Proposal / 无 SSOT 变更 | [ ] |
| B3 | 故意批准错误事实后回滚 | Architecture 回到上一版本；reindex；审计可追责 | [ ] |

### 越权与治理

| # | 步骤 | 期望 | 完成 |
|---|------|------|------|
| C1 | 对话目标为 Hadoop 时，工具传入无关 `assetId` | `TOOL_OUT_OF_SCOPE`（**ML-1**） | [ ] |
| C2 | VIEWER 尝试批准提案 / 执行写工具 | 拒绝 | [ ] |
| C3 | 提案者自批 | 拒绝（四眼） | [ ] |

---

## 3. 分阶段勾选（对照实施计划）

| 阶段 | 本剧本可验收点 | 阶段完成 |
|------|----------------|----------|
| **ML-0** | 愿景/领域/API/本文件齐全互链 | [x] |
| **ML-1** | P3、P4、A1、C1（分组、目标、工具范围） | [x] |
| **ML-2** | 分区键与修订可读；事实带 provenance 约束 | [ ] |
| **ML-3** | A3–A5、B3（提案→合并→回滚） | [ ] |
| **ML-4** | A2–A3（Classifier + propose 工具） | [ ] |
| **ML-5** | B2 Work Log 按会话可查；晋升仅经 Proposal | [ ] |
| **ML-6** | B1 范围化 RAG；非整本 summary 唯一策略 | [ ] |
| **ML-7** | C2、C3 与资产范围 ACL | [ ] |
| **ML-8** | 架构浏览器 + 提案台可走完 A3–A5 | [ ] |

---

## 4. 产品指标（ML-8-05 预留）

| 指标 | 含义 |
|------|------|
| `archops_architecture_proposals_pending` | 待审提案数 |
| `archops_architecture_merged_total` | 合并总数 |
| `archops_rag_hits_per_chat` | 每轮检索命中 |
| `archops_architecture_auto_merge_total` | 自动合并（应极少） |
| `archops_architecture_rollback_total` | 回滚次数 |

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-23 | ML-0-04：固化愿景 §11 为可测剧本 |
