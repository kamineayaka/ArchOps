# ArchOps HTTP API 摘要

统一前缀 `/api/...`，响应包装为 `ApiResponse<T>`（`code` / `message` / `data`）。除登录外均需 `Authorization: Bearer <accessToken>`。

角色：`ROLE_ADMIN` / `ROLE_OPERATOR` / `ROLE_VIEWER`（见各端点 `@PreAuthorize`）。

> 本文档为集成方速查，非 OpenAPI 全量规范。路径以源码 Controller 为准。

## 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回 access / refresh token |
| POST | `/api/auth/refresh` | 刷新 token |
| POST | `/api/auth/logout` | 登出 |

## 资产

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET/POST | `/api/assets` | 视操作 | 资产列表 / 创建 |
| GET/PUT/DELETE | `/api/assets/{id}` | 视操作 | 单资产 |
| POST | `/api/assets/{id}/test-connection` | OPERATOR+ | 连通性测试 |
| GET/POST | `/api/asset-groups` | 视操作 | 资产分组 |
| GET | `/api/asset-types` | 登录用户 | 资产类型描述 |

## Architecture SSOT

> 注意：视图路径是 `/api/architecture/partitions/view`，**不是** `/api/architecture/view`。

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/api/architecture/partitions` | VIEWER+ | 分区摘要列表 |
| GET | `/api/architecture/partitions/view` | VIEWER+ | 聚合视图（可选 `assetIds` / `groupIds`） |
| GET | `/api/architecture/partitions/{key}` | VIEWER+ | 分区详情 |
| PUT | `/api/architecture/partitions/{key}` | ADMIN | 写入修订 |
| POST | `/api/architecture/partitions/{key}/rollback` | ADMIN | 回滚到指定版本 |
| GET | `/api/architecture/proposals` | VIEWER+ | Proposal 列表（`status` / `partitionKey`） |
| POST | `/api/architecture/proposals` | OPERATOR+ | 创建 Proposal |
| GET | `/api/architecture/proposals/{id}` | VIEWER+ | Proposal 详情 |
| POST | `/api/architecture/proposals/{id}/decide` | OPERATOR+ | 批准 / 拒绝 |

## AI

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/chat` | 发起对话（流式见 WebSocket） |
| GET | `/api/ai/conversations` | 会话列表 |
| GET/PUT | `/api/ai/providers` | Provider 管理（ADMIN） |
| GET/PUT | `/api/ai/settings` | 平台 AI 设置（ADMIN） |

WebSocket 流式：见前端 `AiView` / 后端 `AiStreamWebSocketHandler`（路径以源码为准）。

## 审批 / 知识库 / 审计

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/approvals` | 审批单列表 |
| POST | `/api/approvals/{id}/decide` | 批准 / 拒绝 |
| POST | `/api/knowledge/reindex` | RAG 重建索引（ADMIN） |
| GET | `/api/audit/logs` | 审计日志 |

## 健康检查

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/actuator/health` | 经前端 Nginx 反代亦可访问 |
