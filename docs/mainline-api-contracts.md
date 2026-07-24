# ArchOps 主线 API / WS 契约草案

> 领域模型：[`docs/mainline-domain-model.md`](mainline-domain-model.md)  
> 路径风格与现有 `/api/*`、`ApiResponse<T>` 一致。  
> 本文供前端 mock 与后端实现对照；未实现的端点标注阶段。

通用响应：

```json
{ "success": true, "message": "...", "data": { } }
```

错误：`BusinessException` → HTTP 状态 + `code`（如 `ASSET_GROUP_NOT_FOUND`、`TOOL_OUT_OF_SCOPE`）。

---

## 1. 资产分组（ML-1）— `/api/asset-groups`

| Method | Path | 角色 | 说明 |
|--------|------|------|------|
| GET | `/api/asset-groups` | ADMIN/OPERATOR/VIEWER | 列表（含 `memberCount`、成员摘要可选） |
| POST | `/api/asset-groups` | ADMIN/OPERATOR | 创建；`name` 唯一 |
| GET | `/api/asset-groups/{id}` | ADMIN/OPERATOR/VIEWER | 详情 + 成员列表 |
| PUT | `/api/asset-groups/{id}` | ADMIN/OPERATOR | 更新 name/description/enabled |
| DELETE | `/api/asset-groups/{id}` | ADMIN | 删除组；**不**删资产 |
| PUT | `/api/asset-groups/{id}/members` | ADMIN/OPERATOR | 全量替换成员 `assetIds: number[]` |
| POST | `/api/asset-groups/{id}/members` | ADMIN/OPERATOR | 增量添加 `{ assetIds }` |
| DELETE | `/api/asset-groups/{id}/members/{assetId}` | ADMIN/OPERATOR | 移除单成员 |

### DTO 示例

```json
// AssetGroupResponse
{
  "id": 1,
  "name": "Hadoop",
  "description": "prod hadoop cluster",
  "enabled": true,
  "memberCount": 3,
  "members": [
    { "id": 11, "name": "nn1", "kind": "SERVER", "host": "10.0.0.1" }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

```json
// AssetGroupRequest
{ "name": "Hadoop", "description": "...", "enabled": true }
```

---

## 2. 对话目标（ML-1）— `/api/ai/conversations/{id}/targets`

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/ai/conversations/{id}/targets` | 返回资产 + 分组 + **解析后的有效资产并集** |
| PUT | `/api/ai/conversations/{id}/targets` | 更新目标；校验资产/组存在；warm SSH 覆盖并集 |

### Request（PUT）

```json
{
  "targetAssetIds": [11],
  "targetGroupIds": [1]
}
```

字段均可省略或 `[]`。服务端规范化去重。

### Response

```json
{
  "id": 42,
  "title": "...",
  "targetAssetIds": [11],
  "targetGroupIds": [1],
  "resolvedAssetIds": [11, 12, 13],
  "createdAt": "...",
  "updatedAt": "..."
}
```

`ConversationResponse` 同步扩展 `targetGroupIds`；列表/创建接口同样返回该字段。

兼容：旧客户端只传 `targetAssetIds` 仍可用。

---

## 3. Architecture 分区（ML-2，草案）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/architecture/partitions` | 分区列表 |
| GET | `/api/architecture/partitions/{key}` | 最新 revision + 活跃 facts |
| PUT | `/api/architecture/partitions/{key}` | ADMIN 直接编辑（乐观锁 `baseVersion`） |
| POST | `/api/architecture/partitions/{key}/rollback` | 回滚到指定 version |

旧 `/api/knowledge/architecture`：标记 deprecated 或转发到 `global`。

分区键：`global` / `group:{id}` / `asset:{id}`（见领域模型）。

---

## 4. Architecture Proposal（ML-3，草案）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/architecture/proposals` | 过滤 status / partitionKey |
| POST | `/api/architecture/proposals` | 创建（通常 PENDING_REVIEW） |
| GET | `/api/architecture/proposals/{id}` | 详情含 diff / facts / evidence |
| POST | `/api/architecture/proposals/{id}/decide` | `{ decision: "APPROVE"\|"REJECT", comment? }` |

规则（愿景 §4.1 / ML-7）：批准者 ≠ 提案者；高影响分区可要求 ADMIN。

---

## 5. Work Log（ML-5，草案）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/knowledge/work-logs` | Query：`conversationId` / `assetId` / `groupId` |

---

## 6. 范围化 RAG（ML-6，草案）

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/knowledge/search` | body 增加可选 `scope: { assetIds?, groupIds?, partitionKeys? }` |

禁止把整本 Architecture 无差别塞进 prompt 作为唯一策略。

---

## 7. WebSocket Agent 事件扩展

现有 AI 流式 WS 事件基础上增加（ML-4 / ML-5）：

| event | payload 要点 |
|-------|----------------|
| `architecture_proposal_created` | `proposalId`, `partitionKey`, `summary`, `status` |
| `work_log_appended` | `workLogId`, `conversationId`, `level` |

前端仅展示；合并/分类在服务端。

---

## 8. 工具行为契约（ML-1-04）

| 工具 | 无显式 assetId | 显式 assetId 不在目标并集 | 目标并集为空 |
|------|----------------|---------------------------|--------------|
| `list_assets` | 仅列出并集 | — | 列出全部（现有行为，待 ACL 收紧） |
| `ssh_exec` | 对并集逐台执行 | `TOOL_OUT_OF_SCOPE` | 必须提供 assetId 或先设目标 |

---

## 9. AI Provider（W3 / OpsKat 对齐）— `/api/ai/providers`

| Method | Path | 角色 | 说明 |
|--------|------|------|------|
| GET | `/api/ai/providers` | 已登录 | 启用且支持 Chat 的 Provider 列表 |
| GET | `/api/ai/providers/all` | ADMIN | 全部 Provider（含禁用） |
| POST | `/api/ai/providers` | ADMIN | 创建 |
| PUT | `/api/ai/providers/{id}` | ADMIN | 更新（`apiKey` 空=不改） |
| DELETE | `/api/ai/providers/{id}` | ADMIN | 删除（不可删默认） |
| POST | `/api/ai/providers/{id}/test` | ADMIN | 最小 chat 探活；`data.status`=`ok` 或 `failed: …`（脱敏） |
| GET | `/api/ai/providers/{id}/models` | ADMIN | 拉取模型列表；失败 `FETCH_MODELS_FAILED` |

### DTO 字段（相对既有 CRUD 新增 / 对齐图 3）

| 字段 | 类型 | 说明 |
|------|------|------|
| `maxOutputTokens` | int | `0`=运行时默认；写入请求体时可选 |
| `contextWindow` | int | `0`=平台默认上下文预算；>0 时按 ≈3 chars/token 截断 Agent 系统上下文 |
| `reasoningEnabled` | bool | 与 `reasoningEffort` 联动；`NONE` 时为 false |
| `reasoningEffort` | enum | `NONE\|LOW\|MEDIUM\|HIGH\|XHIGH\|MAX`；**MAX 仅 Anthropic**，OpenAI 兼容归一为 `HIGH` |

运行时：`LlmGenerationConfig` 注入 `OpenAiCompatRuntime` / `AnthropicRuntime`（`max_tokens`、`reasoning_effort` / Anthropic `thinking.budget_tokens`）。

**不做：** API Key 明文日志；改 Architecture SSOT 语义。

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-23 | ML-0-03：初版；含 ML-1 已定契约与后续阶段草案 |
| 2026-07-24 | W3：补充 AI Provider 高级字段与 test/models 契约 |
