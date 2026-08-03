---
name: add-rest-api
description: >-
  Adds a new ArchOps REST API end-to-end (Controller, Service, DTO, optional
  Flyway/Repository, optional frontend api module). Use when the user asks to
  add an API, endpoint, CRUD resource, or backend+frontend data surface under
  /api/.
---

# 加 REST API（ArchOps）

先读并遵守 `.cursor/rules/backend-java.mdc`（以及涉及前端时的 `frontend-vue.mdc`）。

## 流程清单

复制并勾选：

```
- [ ] 1. 定模块包：com.archops.<module>（user/asset/ai/approval/knowledge/audit/terminal/tools/common）
- [ ] 2. DTO：request/response 用 record + jakarta.validation
- [ ] 3. Domain + Repository（若新表）
- [ ] 4. Flyway：只新增 V{N+1}__*.sql，禁止改历史脚本
- [ ] 5. Service：构造器注入、@Transactional、BusinessException、必要审计
- [ ] 6. Controller：/api/...、ApiResponse、@PreAuthorize、@Valid、AuthUserPrincipal
- [ ] 7. （可选）frontend/src/api/<name>.ts 用 client + ApiResponse
- [ ] 8. （可选）View 接线；文案键进 messages.ts
```

## 必须对齐的样板

- Controller：`asset/controller/AssetController.java`
- Service：`asset/service/AssetService.java`
- DTO：`asset/dto/AssetRequest.java`（record）
- 响应包装：`common/dto/ApiResponse.java`
- 前端：`frontend/src/api/assets.ts`

## 硬性检查

- 每个 mapping 有 `@PreAuthorize`（`ROLE_ADMIN` / `OPERATOR` / `VIEWER`）
- 不返回 JPA Entity；不在 Controller 写业务
- 凭证字段不进 Response；SSH 不新开直连
- 新表：看 `backend/src/main/resources/db/migration/` 最大版本号再 +1

## 前端（若需要）

```ts
import client from './client'
import type { ApiResponse } from './types'

export async function listX() {
  const { data } = await client.get<ApiResponse<X[]>>('/api/x')
  return data
}
```

错误用 `apiErrorMessage`；文案 `import { t } from '@/messages'`。

## 验证

- 本机有环境：调对应 `/api/...` 或跑相关测试
- 用户要求上机验证：按 `remote-aliserver` 规则部署冒烟，不在 2G 机做热更新开发
