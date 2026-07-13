# CloudOps 优化任务 — Agent 执行 Prompt

> 将本文 **「一键 Prompt」** 整段复制到 Cursor Cloud Agent / 本地 Agent，即可按清单执行优化。  
> 任务详情：`docs/agent-optimization-todo.md`

---

## 一键 Prompt（复制以下全部内容）

```text
你是 CloudOps AI Platform 的专职开发 Agent。请严格按任务清单执行优化，不要偏离范围。

## 仓库与分支

- 仓库：kamineayaka/CloudOps（工作区路径 /workspace）
- 基线分支：main
- 任务清单：docs/agent-optimization-todo.md（必读，所有任务 ID 以 OPT-P* 开头）
- 架构参考：docs/architecture.md、docs/deployment.md、docs/ssh-connection-pool-design.md

## 你的目标

1. 打开 docs/agent-optimization-todo.md，从 **P0 未完成项** 开始，按 OPT-P0-01 → OPT-P0-06 → P1 → P2 → P3 顺序执行。
2. 若用户指定了任务 ID（如「只做 OPT-P0-02」），仅执行该项及其直接依赖。
3. 每完成一项任务：
   - 在 docs/agent-optimization-todo.md 将对应 `[ ]` 改为 `[x]`
   - 单独 git commit，message 格式：`fix(scope): OPT-P0-XX 简短说明`
   - 跑通下方验证命令
4. 全部完成后创建 Draft PR，base=main，body 中列出已完成的 OPT-* 任务 ID。

## 执行约束（必须遵守）

### 代码风格
- 最小 diff：只改任务相关文件，不顺手重构无关代码
- 匹配现有约定：Spring Boot 分层（controller/service/repository）、Vue 3 `<script setup lang="ts">`、Flyway 迁移命名 `V{n}__description.sql`
- 配置外部化：新配置项写入 application.yml + .env.example，不写死密钥
- 安全敏感操作必须写审计日志（参考 AuditService）
- 不修改 .cursor/plans/ 下文件
- 不删除或弱化 ApprovalGate / RiskClassifier 逻辑

### Git 工作流
- 创建分支：`cursor/opt-<task-id-小写>-71f3`（例：`cursor/opt-p0-02-ci-tests-71f3`）
- 推送：`git push -u origin <branch-name>`
- 每项任务至少一个 commit；不要 squash 不同任务
- PR 默认创建为 Draft

### 验证命令（每项任务完成后必须执行）

```bash
# 后端
cd backend && ./mvnw verify

# 前端
cd frontend && npm ci && npm run build

# 若已添加前端测试（OPT-P1-06 之后）
cd frontend && npm run test
```

若修改了 Flyway 迁移，额外确认：
```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# 或 Testcontainers 集成测试通过
```

若修改了 Compose / Helm：
```bash
docker compose -f deploy/compose/compose.yaml config
# Helm 任务：cd deploy/helm && helm lint .
```

### 测试要求
- P0/P1 任务必须附带测试（单元或集成），不允许只改实现不补测试
- 使用现有 Testcontainers 模式（参考 backend/src/test/java/com/cloudops/ai/provider/AiProviderCrudIntegrationTest.java）
- 不要 mock 掉被测核心逻辑来让测试通过

### 文档要求
- 行为变更同步更新 docs/deployment.md 或 README.md
- 新增 API 或配置项在 PR body 中说明

## 任务领取策略

### 首次运行（无人指定任务）
按顺序执行第一个未完成项：OPT-P0-01（RAG 向量索引重建）

### 并行分工（多 Agent 时）
| Agent | 建议任务 |
|-------|----------|
| Agent A | P0 后端安全：OPT-P0-01, P0-03, P0-04, P0-05, P0-06 |
| Agent B | P0 CI：OPT-P0-02 → P1-04, P1-06, P3-05 |
| Agent C | P1 前端：OPT-P1-05, P3-01, P3-02, P3-03, P3-04 |
| Agent D | P2 运维：OPT-P2-05, P2-06, P2-07 |

避免两个 Agent 同时修改同一文件；有依赖的任务按依赖图顺序执行。

## 关键文件速查

| 模块 | 路径 |
|------|------|
| 安全配置 | backend/src/main/java/com/cloudops/common/config/SecurityConfig.java |
| JWT | backend/src/main/java/com/cloudops/common/security/ |
| 密钥引导 | backend/src/main/java/com/cloudops/common/bootstrap/PlatformSecretStore.java |
| SSH 池 | backend/src/main/java/com/cloudops/terminal/pool/ |
| AI Agent | backend/src/main/java/com/cloudops/ai/service/AiAgentService.java |
| 审批 | backend/src/main/java/com/cloudops/approval/ |
| 知识库/RAG | backend/src/main/java/com/cloudops/knowledge/ |
| Flyway | backend/src/main/resources/db/migration/ |
| 前端 API | frontend/src/api/client.ts |
| 前端路由守卫 | frontend/src/router/index.ts |
| CI | .github/workflows/ci.yml |
| Compose | deploy/compose/compose.yaml |
| Helm | deploy/helm/ |

## 完成标准（Definition of Done）

对每个 OPT-* 任务：
- [ ] 任务清单中对应项已勾选 [x]
- [ ] 验证命令全部通过
- [ ] 有测试覆盖（P0/P1 必须）
- [ ] commit message 含任务 ID
- [ ] 无新增 linter 错误
- [ ] PR 已创建/更新，描述含任务 ID 与验证结果

## 遇到问题时

1. **测试失败**：先读失败日志，修复实现而非跳过测试；禁止在 CI 中加 `-DskipTests`（除非任务明确要求回滚 OPT-P0-02）
2. **Flyway 冲突**：新迁移版本号 = 当前最大 V{n}+1，不要修改已发布的 V1–V8
3. **Breaking change**：WebSocket token 方式变更需前后端同 PR 或标注依赖顺序
4. **无法完成**：在 PR 中说明阻塞原因，不要留半成品未文档化

## 禁止事项

- 不要 force push main
- 不要提交真实 API Key、JWT secret、生产密码
- 不要为实现任务而删除现有安全机制
- 不要一次性巨型 PR 包含多个无关联 OPT 任务（每项任务可独立合并为佳）

开始执行：读取 docs/agent-optimization-todo.md，找到第一个 [ ] 项，创建分支并实现。
```

---

## 单任务 Prompt 模板

当只需执行某一个任务时，复制并替换 `{TASK_ID}` 和 `{TASK_TITLE}`：

```text
仓库 kamineayaka/CloudOps，分支 main。

请仅执行 docs/agent-optimization-todo.md 中的任务 {TASK_ID}（{TASK_TITLE}）。

要求：
1. 阅读该任务在清单中的「涉及文件」「实现要点」「完成标准」
2. 创建分支 cursor/opt-{task-id-小写}-71f3
3. 实现 + 测试 + 更新清单勾选
4. commit: fix(scope): {TASK_ID} 简短说明
5. 跑 cd backend && ./mvnw verify && cd frontend && npm run build
6. 创建 Draft PR

约束见 docs/agent-optimization-prompt.md「执行约束」章节。
```

### 示例：仅执行 CI 测试

```text
仓库 kamineayaka/CloudOps，分支 main。

请仅执行 docs/agent-optimization-todo.md 中的任务 OPT-P0-02（CI 启用后端测试）。

要求：
1. 修改 .github/workflows/ci.yml 移除 -DskipTests
2. 确保 ./mvnw verify 本地全绿
3. 同步修正 docs/TODO.md 中 P0-4 与 CI 实际行为不一致的描述
4. 创建分支 cursor/opt-p0-02-ci-tests-71f3，单独 commit，开 Draft PR

约束见 docs/agent-optimization-prompt.md。
```

---

## 批量阶段 Prompt 模板

### 阶段一：P0 安全（6 项）

```text
仓库 kamineayaka/CloudOps。按 docs/agent-optimization-todo.md 完成全部 P0 任务（OPT-P0-01 至 OPT-P0-06）。

顺序：P0-01 → P0-02 → P0-03 → P0-04 → P0-05 → P0-06（P0-05 与 P0-06 可拆分为两个 PR）。

每项单独 commit，全部完成后一个 Draft PR 或按任务拆多个 PR。

验证：./mvnw verify && cd frontend && npm run build

约束见 docs/agent-optimization-prompt.md。
```

### 阶段二：P1 质量

```text
仓库 kamineayaka/CloudOps。P0 已全部完成。请执行 docs/agent-optimization-todo.md 中全部 P1 任务（OPT-P1-01 至 OPT-P1-06）。

注意依赖：P1-04、P1-06 需要 CI 已启用测试（P0-02）。

约束见 docs/agent-optimization-prompt.md。
```

---

## Agent 输出格式要求

每个任务完成后，在 PR 或会话中输出：

```markdown
## OPT-P0-XX — 任务标题

### 变更摘要
- 文件1：做了什么
- 文件2：做了什么

### 验证结果
- [ ] ./mvnw verify — PASS/FAIL
- [ ] npm run build — PASS/FAIL
- [ ] 新增/更新测试：列出测试类名

### 清单更新
- docs/agent-optimization-todo.md 已勾选 [x]
```

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-12 | 初版：一键 Prompt + 单任务/分阶段模板 + DoD |
