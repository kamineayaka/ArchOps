---
name: add-rest-api
description: >-
  Adds a new ArchOps REST API end-to-end (Controller, Service, DTO, optional
  Flyway/MyBatis-Plus Mapper, optional frontend api module). Use when the user
  asks to add an API, endpoint, CRUD resource, or backend+frontend data surface
  under /api/.
---

# 加 REST API（ArchOps）

先看 `.cursor/rules/backend-java.mdc`（涉及前端时再看 `frontend-react.mdc`）和 `AGENTS.md`。下面是加接口时要对齐的分层，不是规定先改哪个文件。

## 流程清单

```
- [ ] 1. 定模块包：com.archops.<module>（curated / observed / conflict / plan / user / agent / common）。`user` 不是冲突链的身份门禁（ADR-0046）
- [ ] 2. DTO：request/response 用 record；勿把 DO 当响应
- [ ] 3. Domain + MyBatis-Plus Mapper（若新表）
- [ ] 4. Flyway：只新增 V{N+1}__*.sql，禁止改历史脚本
- [ ] 5. Service：构造器注入、事务边界、BusinessException
- [ ] 6. Controller：/api/...、ApiResponse、@Valid。冲突链不按身份鉴权，不发送用户身份（ADR-0046）。不要给每个 Controller 都加身份鉴权
- [ ] 7. （可选）frontend/src/api/<name>.ts 只用 api/client.ts
- [ ] 8. （可选）薄页接线：pages + App.tsx 路由；Ant Design
```

## 必须对齐的样板

- Controller：`conflict/controller/ConflictController.java`
- 响应包装：`common/api/ApiResponse.java`
- 前端：`frontend/src/api/conflicts.ts`、`frontend/src/api/client.ts`

## 硬性检查

- 不返回 JPA Entity / DO；不在 Controller 写业务
- 凭证字段不进 Response；SSH 不新开旁路直连
- 新表：看 `backend/src/main/resources/db/migration/` 最大版本号再 +1
- 禁止复活已删除的 `ai/` `asset/` `graph/` 包

## 前端（若需要）

```ts
import { apiRequest } from './client'

export function listX(): Promise<X[]> {
  return apiRequest<X[]>('/api/x')
}
```

错误用 `ApiError` + Ant Design `message`/`Alert`。

## 验证

- 用测试和类型检查核对 `/api/...`；也可以 `curl` 这条路径
- Cloud VM：Compose Postgres/Redis 已由 `scripts/cloud-start.sh` 拉起
