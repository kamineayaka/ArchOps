# Wave W4 验收剧本（Workbench 多资产类型）

对照 `docs/workbench-gap-audit.md` Wave W4 / W4a–d 与 `docs/opskat-learning.md` 资产类型矩阵。

**产品隐喻**：SSH = IDE；DATABASE / K8S / REDIS = query/page；Agent 仅只读探活。写操作走审批。扩展靠 `register`，禁止共享 `switch(kind)`。

**合并顺序建议**：W4a → W4b → W4c → W4d。

---

## 前置

- [ ] 已合入或本地串起 W4a–d 相关提交
- [ ] `./mvnw -Dtest='!*IntegrationTest' test` 通过（完整 `mvn verify` 需 Docker）
- [ ] `cd frontend && npm run build` 通过
- [ ] 至少一名测试员 + 一台可连的跳板/数据库（或本机 Redis）

---

## A. W4-01 / W4-02 — SPI 与 connectAction

| # | 步骤 | 期望 | ✓ |
|---|------|------|---|
| A1 | 打开 `docs/adding-an-asset-type.md` | 含 connectAction、testConnection、checklist | |
| A2 | 后端新增类型仅实现 `AssetTypeHandler` + 前端 `register` | 无共享 `switch(kind)` 业务分发 | |
| A3 | SERVER Connect | 打开终端 IDE（ssh） | |
| A4 | DATABASE / REDIS / K8S Connect | 打开 Query 页或占位 page（非终端） | |

---

## B. W4-03 — DATABASE

| # | 步骤 | 期望 | ✓ |
|---|------|------|---|
| B1 | 资产列表 → 新建 DATABASE | 表单有 host/port/库名/用户/密码；默认端口 3306 | |
| B2 | 「测试连接」对可达库 | toast 成功；后端日志无明文密码 | |
| B3 | 对错误密码/端口 | toast 失败摘要 | |
| B4 | Connect | 进入 Query 页 | |
| B5 | Query：`SELECT 1` | 只读结果返回 | |
| B6 | Query：`DELETE FROM …` | 被拒绝（只读门禁） | |

---

## C. W4-04 — K8S

| # | 步骤 | 期望 | ✓ |
|---|------|------|---|
| C1 | 新建 K8S，模式 API Server | 填 apiServerUrl + Bearer token；测连通成功（/version） | |
| C2 | 模式 JUMP_KUBECTL | 跳板 SSH + 远端 `kubectl get ns` 成功 | |
| C3 | Connect | 进 Query/page，**不是**完整控制台 | |

---

## D. W4-05 / W4-06 — REDIS + Query 壳

| # | 步骤 | 期望 | ✓ |
|---|------|------|---|
| D1 | 新建 REDIS | 默认端口 6379；测连通 PING | |
| D2 | Query：`PING` | PONG | |
| D3 | Query：`GET key` / `INFO` | 只读结果 | |
| D4 | Query：`SET …` | 拒绝 | |

---

## E. W4-07 — Agent 只读工具

| # | 步骤 | 期望 | ✓ |
|---|------|------|---|
| E1 | Agent 调用 `list_assets`（可选 kind） | 返回资产列表 | |
| E2 | `db_ping` 对 DATABASE/REDIS | 探活摘要；不写库 | |
| E3 | `k8s_get` 对 K8S | 连通/version 类只读摘要 | |
| E4 | Risk | 上述工具为 LOW，不强制走写审批 | |

---

## F. W4-08 — 文档与回归

| # | 步骤 | 期望 | ✓ |
|---|------|------|---|
| F1 | 本文件勾选完成 | 缺口已关闭或记入 Known gaps | |
| F2 | `workbench-gap-audit` W4-01…08 已勾 | | |
| F3 | 密钥/密码不进明文日志 | | |

---

## Known gaps（可后置）

- Kafka 类型（W4c 选 Redis）
- K8s 完整控制台 / 多资源浏览器
- DATABASE 写 SQL 审批流
- 专用 Query 面板美化（表单+测试已先可用）

---

## 签字

| 角色 | 日期 | 结果 |
|------|------|------|
| 开发 | | PASS / FAIL |
| 验收 | | PASS / FAIL |
