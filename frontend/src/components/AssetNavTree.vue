<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NEmpty, NIcon, NSpin, NTree, useMessage, type TreeOption } from 'naive-ui'
import { DesktopOutline, FolderOutline, ServerOutline } from '@vicons/ionicons5'
import { getAssetGroup, listAssetGroups, type AssetGroup } from '@/api/assetGroups'
import { listAssets, type Asset } from '@/api/assets'
import { openAsset } from '@/assetTypes/openAsset'

const { t } = useI18n()
const router = useRouter()
const message = useMessage()

const loading = ref(false)
const treeData = ref<TreeOption[]>([])
const expandedKeys = ref<Array<string | number>>([])
const assetsById = ref<Map<number, Asset>>(new Map())

function groupKey(id: number) {
  return `group:${id}`
}

function assetKey(id: number) {
  return `asset:${id}`
}

function renderPrefix({ option }: { option: TreeOption }) {
  const key = String(option.key ?? '')
  if (key.startsWith('group:')) {
    return h(NIcon, null, { default: () => h(FolderOutline) })
  }
  if (key.startsWith('asset:')) {
    return h(NIcon, null, { default: () => h(ServerOutline) })
  }
  if (key === 'ungrouped') {
    return h(NIcon, null, { default: () => h(DesktopOutline) })
  }
  return null
}

async function loadTree() {
  loading.value = true
  try {
    const [groupRes, assetRes] = await Promise.all([listAssetGroups(), listAssets()])
    const groups: AssetGroup[] = groupRes.success && groupRes.data ? groupRes.data : []
    const assets: Asset[] = assetRes.success && assetRes.data ? assetRes.data : []
    assetsById.value = new Map(assets.map((a) => [a.id, a]))
    const memberIds = new Set<number>()

    const groupNodes: TreeOption[] = groups.map((group) => ({
      key: groupKey(group.id),
      label: `${group.name} (${group.memberCount})`,
      isLeaf: group.memberCount === 0,
      children: group.memberCount > 0 ? undefined : [],
    }))

    // Prefetch members for groups so the tree is navigable without extra clicks when small
    await Promise.all(
      groups.map(async (group) => {
        if (group.memberCount === 0) return
        const res = await getAssetGroup(group.id)
        if (!res.success || !res.data) return
        const node = groupNodes.find((n) => n.key === groupKey(group.id))
        if (!node) return
        node.children = (res.data.members ?? []).map((member) => {
          memberIds.add(member.id)
          return {
            key: assetKey(member.id),
            label: member.host ? `${member.name} (${member.host})` : member.name,
            isLeaf: true,
          }
        })
      }),
    )

    const ungrouped = assets.filter((a) => !memberIds.has(a.id))
    if (ungrouped.length) {
      groupNodes.push({
        key: 'ungrouped',
        label: t('workbench.ungrouped'),
        children: ungrouped.map((asset) => ({
          key: assetKey(asset.id),
          label: asset.host ? `${asset.name} (${asset.host})` : asset.name,
          isLeaf: true,
        })),
      })
    }

    treeData.value = groupNodes
    expandedKeys.value = groups.slice(0, 3).map((g) => groupKey(g.id))
  } finally {
    loading.value = false
  }
}

function handleSelect(keys: Array<string | number>) {
  const key = String(keys[0] ?? '')
  if (key.startsWith('group:')) {
    const id = Number(key.slice('group:'.length))
    if (Number.isFinite(id)) {
      void router.push({ name: 'asset-groups', query: { groupId: String(id) } })
    }
    return
  }
  if (key.startsWith('asset:')) {
    const id = Number(key.slice('asset:'.length))
    if (!Number.isFinite(id)) return
    const asset = assetsById.value.get(id)
    if (!asset) {
      void router.push({ name: 'assets' })
      return
    }
    openAsset(asset, { router, message, t })
  }
}

onMounted(() => {
  void loadTree()
})
</script>

<template>
  <div class="asset-nav-tree">
    <div class="asset-nav-tree__header">
      <span class="asset-nav-tree__title">{{ t('workbench.assetTree') }}</span>
    </div>
    <div class="asset-nav-tree__body">
      <NSpin v-if="loading" size="small" class="asset-nav-tree__spin" />
      <NEmpty
        v-else-if="!treeData.length"
        size="small"
        :description="t('workbench.assetTreeEmpty')"
      />
      <NTree
        v-else
        block-line
        selectable
        :data="treeData"
        :expanded-keys="expandedKeys"
        :render-prefix="renderPrefix"
        @update:expanded-keys="(keys) => (expandedKeys = keys)"
        @update:selected-keys="handleSelect"
      />
    </div>
  </div>
</template>

<style scoped>
.asset-nav-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.asset-nav-tree__header {
  padding: var(--co-space-3) var(--co-space-4);
  border-bottom: 1px solid var(--co-border);
  flex-shrink: 0;
}

.asset-nav-tree__title {
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--co-text-muted);
}

.asset-nav-tree__body {
  flex: 1;
  overflow: auto;
  padding: var(--co-space-2);
  min-height: 0;
}

.asset-nav-tree__spin {
  display: flex;
  justify-content: center;
  padding: var(--co-space-6);
}
</style>
