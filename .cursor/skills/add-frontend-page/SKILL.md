---
name: add-frontend-page
description: >-
  Adds an ArchOps frontend page (React + Ant Design page, App.tsx route,
  optional api module). Use when the user asks for a new page, view, menu item,
  settings screen, or admin UI under frontend/.
---

# 加前端页面（ArchOps）

遵守 `.cursor/rules/frontend-react.mdc` 与 ADR-0043。禁止把 Vue / Naive UI 当作现行前端重新引入。票内薄 UI 排在该票 HTTP 循环变绿之后（[`docs/agents/tdd.md`](../../../docs/agents/tdd.md)）；UI 不是自动化主接缝。

## 流程清单

```
- [ ] 1. frontend/src/pages/<Name>Page.tsx（函数组件 + hooks + Ant Design）
- [ ] 2. （可选）frontend/src/api/<name>.ts — 只用 api/client.ts，返回解包后的 data
- [ ] 3. App.tsx 增加 Route（react-router-dom）
- [ ] 4. 需要导航时在 Header 加 Link；竖切未要求不做完整工作台
- [ ] 5. 错误：ApiError + antd message / Alert
```

## 样板

- 列表页：`pages/ConflictListPage.tsx`
- 详情页：`pages/ConflictDetailPage.tsx`
- API：`api/conflicts.ts`、`api/client.ts`、`api/types.ts`
- 根路由：`App.tsx`
- 演示身份：`auth/DemoUserContext.tsx`（`X-ArchOps-User-Id`）

## 页面骨架

```tsx
import { useCallback, useEffect, useState } from 'react'
import { Alert, Typography, message } from 'antd'
import { apiRequest } from '../api/client'
import { ApiError } from '../api/types'

export default function FooPage() {
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      await apiRequest('/api/x')
    } catch (err) {
      const msg = err instanceof ApiError ? `${err.code}: ${err.message}` : String(err)
      setError(msg)
      message.error(msg)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <>
      <Typography.Title level={3}>标题</Typography.Title>
      {error ? <Alert type="error" showIcon message={error} /> : null}
    </>
  )
}
```

## 禁止

- 引入 Vue、Naive UI、`vue-i18n`、新建第二套 HTTP 客户端
- 票外页面 / 完整 xterm 工作台 / 过重状态管理

## 验证

- `cd frontend && npm run build`
- Cloud：Vite `:5173` 代理 `/api` → `:8080`
