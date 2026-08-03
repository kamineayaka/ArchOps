---
name: add-frontend-page
description: >-
  Adds an ArchOps frontend page (Vue 3 view, router, AppLayout menu, messages.ts
  copy, optional api module). Use when the user asks for a new page, view,
  menu item, settings screen, or admin UI under frontend/.
---

# 加前端页面（ArchOps）

遵守 `.cursor/rules/frontend-vue.mdc`。**无 vue-i18n**；文案只用 `@/messages` 的 `t()`。

## 流程清单

```
- [ ] 1. frontend/src/views/<Name>View.vue（script setup + Naive UI）
- [ ] 2. （可选）frontend/src/api/<name>.ts — 只用 client，返回 ApiResponse data
- [ ] 3. messages.ts 增加 nav / 页面标题 / 文案键
- [ ] 4. router/index.ts 子路由：path、name、component、meta.titleKey/descKey
- [ ] 5. layouts/AppLayout.vue 的 menuOptions 增加一项（含 icon）
- [ ] 6. 需管理员：meta.requiresAdmin + 菜单按角色过滤（对齐现有 ai-settings）
- [ ] 7. 错误：apiErrorMessage + useMessage()
```

## 样板

- 列表页：`views/AssetsView.vue`
- API：`api/assets.ts`、`api/client.ts`
- 文案：`messages.ts`（`import { t } from '@/messages'`）
- 路由：`router/index.ts`
- 侧栏：`layouts/AppLayout.vue`
- 页头：`components/PageHeader.vue`、`EmptyState.vue`

## 页面骨架

```vue
<script setup lang="ts">
import { t } from '@/messages'
import { onMounted, ref } from 'vue'
import { NCard, useMessage } from 'naive-ui'
import PageHeader from '@/components/PageHeader.vue'
import { apiErrorMessage } from '@/utils/apiError'
// import { listX } from '@/api/x'

const message = useMessage()
const loading = ref(false)

onMounted(() => { void load() })

async function load() {
  loading.value = true
  try {
    // await listX()
  } catch (e) {
    message.error(apiErrorMessage(e, t('common.loadFailed')))
  } finally {
    loading.value = false
  }
}
</script>
```

## 路由 meta

```ts
{
  path: 'foo',
  name: 'foo',
  component: () => import('@/views/FooView.vue'),
  meta: { titleKey: 'nav.foo', descKey: 'foo.subtitle' },
  // 管理页：requiresAdmin: true
}
```

`titleKey` / `descKey` 必须在 `messages.ts` 有对应字符串。

## 禁止

- 引入 `vue-i18n` / `locales/` / 新建 axios 实例
- 在 View 里写死资产 `kind` 大分支（走 `assetTypes`）
- 为纯展示再包一层无交互的卡片堆砌（跟现有 View 密度对齐）

## 验证

- `cd frontend && npm run build`（或至少打开路由无控制台报错）
- 上机整包验证：aliserver + `remote-deploy`（见 `remote-aliserver`）
