# Graph SSOT 设计（固化）

> 状态：已定案，按波次落地。本文档是实现契约；细节以代码与 Flyway 为准。

## 1. 产品定案

| 项 | 决策 |
|---|---|
| 主界面 | 图工作台为资产管理**唯一主入口**（表/树降级或废除） |
| 图存储 | **Neo4j**（拓扑 SSOT） |
| PG 职责 | 用户/RBAC、凭证密文、Proposal/审计、资产锚点行、布局偏好 |
| 分组 | `asset_group` → 图标签；**强制** `assets.kind=TAG` 占一行 |
| 逻辑节点 | Tag / Environment 均占 `assets` 行 |
| 写路径 | 一律 **计划模式 → Proposal → Merge**（含 ADMIN、Cypher 写、画布） |
| 删资产/凭证 | **软删**；`audit_log` 只追加 |
| Cypher 写 | 编译为 ChangeSet，**不直接执行**写语句 |
| Agent 读范围 | 阶段 1 全图；执行类工具与可见范围分离（Grant） |
| 操作台选机 | **会话坞**（Pin/最近/已开 Tab），不恢复资产树作库存管理 |
| Scope 存储 | `architecture_proposal.partition_key` **重释义为 scope key**（不另加 scope_key 列） |

## 2. Scope Key（原 partition_key）

```text
graph:global
cluster:{elementId}
tag:{slug}
view:{savedViewId}
asset:{elementId}          # 新写用 UUID；迁移期兼容 asset:{numericId}
```

旧值迁移：`global` → `graph:global`；`group:{id}` → `tag:{slug}`；`asset:{id}` → `asset:{elementId}`。

过渡期校验仍接受 legacy `global` / `group:{id}` / `asset:{numericId}`。

## 3. 边类型（首期）

`MEMBER_OF` · `RUNS_ON` · `DEPENDS_ON` · `CONNECTS_VIA` · `HAS_TAG`

- 跳板语义（迁移默认）：目标资产扇出有序 `CONNECTS_VIA{order}`（语义 B）
- 旧 `jump_asset_ids` / `parent_id` / 分组：迁移后停写

## 4. ChangeSet / Proposal

见讨论定稿：`change_set` JSONB（GraphOp[]）、可选 `plan_json`、`base_graph_version`、`source`。

状态扩展：`CONFLICT` · `SUPERSEDED` · `MERGE_FAILED`（另保留原有枚举）。

密文经 `credential_staging`，proposal 只存 staging id。

## 5. Merge 双写

1. PG 锁 `graph_meta` + proposal；校验 `base_graph_version`
2. `NODE_CREATE` 先占位 `assets`（含 TAG/ENVIRONMENT）
3. Neo4j 事务应用 GraphOp
4. PG：staging→凭证、软删、projection、`graph_version++`、revision、proposal MERGED
5. 失败：两侧回滚或 Neo4j 已提交时用逆操作补偿

详细伪代码见会话设计；实现类：`com.archops.graph.service.GraphMergeEngine`。

## 6. Neo4j

- Constraint：`Asset.elementId` / `Asset.pgAssetId` / `Tag.slug` / 边 `elementId` 唯一
- 节点均为 `:Asset` + 特化 label；`pgAssetId` 必填
- 默认读过滤 `deleted=false`

## 7. Flyway 波次

| 版本 | 内容 |
|---|---|
| V14 | 资产 `element_id`/软删、`graph_meta`、布局表 |
| V15 | Proposal ChangeSet 列、revision 图字段、scope 重写、migration_map |
| V16 | 凭证软删、`credential_staging` |
| V17 | `terminal_session_dock` |
| V18 | 删除 `asset_group` / `asset_group_member`（分组迁图标签） |
| P2+ 清理 | Dialer 仅 `CONNECTS_VIA`；移除资产树/分组 UI 与直写资产 API；凭证 staging |
| P3 工作台 | 选中详情侧栏；编辑 / 软删 / 删边 / 更新凭证入草稿；开终端与测连；草稿列表与 plan warnings |

### 前端入口（P0–P3）

- 侧栏「拓扑图」→ `/topology`；「图编辑」→ `/graph`（旧 `/assets`、`/asset-groups` → topology）
- 拓扑图双击 / 右键「连接」→ 操作台；图编辑用底部悬浮条，无侧栏
- 操作台会话坞：`/api/terminal/dock` + PG 同步
- 变更路径：画布草稿 → `POST /api/graph/plan` → `POST /api/architecture/proposals` → 审批合并
- 建 SERVER/DATABASE 时可暂存凭证；提案仅带 `CREDENTIAL_UPSERT_REF`，合并时消费 staging

### Neo4j schema

- 脚本：`backend/src/main/resources/neo4j/init-schema.cypher`
- `Neo4jSchemaInitializer` 在启动时**异步重试**执行，失败不阻塞 Spring 启动
- Merge：`GraphOpApplier` 应用全部 GraphOp；`GraphPgAnchorService` 负责 PG 锚点/凭证侧效应
- **Hybrid RAG**：`HybridRetrievalService` 以 Neo4j 邻域 + `architecture_fact` 为主，pgvector 文本记忆为辅；Agent 工具 `graph_neighborhood` / `graph_path` 只读深挖

### Compose

Neo4j 与 Postgres/Redis 同级，始终随主 `compose.yaml` 启动（无 profile）：

```bash
docker compose -f compose.yaml --env-file .env up -d
```

- `backend` 对 `neo4j`：`depends_on: condition: service_healthy`（必选）
- lowmem overlay 可收紧 Neo4j heap（≥256m）与容器 limit；主机仍需 ≥4 GiB
- 后端镜像构建用基础镜像自带 `mvn`（不用 `./mvnw`，避免绕过 `MAVEN_MIRROR` 拉 Maven 本体）

## 8. 非目标（后续）

- 布局持久化 / 保存视图
- Agent 子图 scope 收紧（阶段 2）
- 清理历史列：`assets.parent_id`、`ssh_credentials.jump_asset_ids`、会话 `target_group_ids`（已停写，列可后续 DROP）
