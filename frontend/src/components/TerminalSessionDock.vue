<script setup lang="ts">
import { t } from '@/messages'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NEmpty, NSpace, NTag, useMessage } from 'naive-ui'
import {
  listTerminalDock,
  pinTerminalDock,
  removeTerminalDock,
  type TerminalDockItem,
} from '@/api/graph'

const emit = defineEmits<{ reload: [] }>()

const router = useRouter()
const message = useMessage()
const items = ref<TerminalDockItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listTerminalDock()
    items.value = res.success && res.data ? res.data : []
  } finally {
    loading.value = false
  }
}

async function openItem(item: TerminalDockItem) {
  void router.push({ name: 'terminal', params: { assetId: String(item.assetId) } })
}

async function togglePin(item: TerminalDockItem) {
  const res = await pinTerminalDock(item.elementId, !item.pinned)
  if (!res.success) {
    message.error(res.message || t('common.failed'))
    return
  }
  await load()
}

async function remove(item: TerminalDockItem) {
  const res = await removeTerminalDock(item.elementId)
  if (!res.success) {
    message.error(res.message || t('common.failed'))
    return
  }
  await load()
  emit('reload')
}

onMounted(() => {
  void load()
})

defineExpose({ reload: load })
</script>

<template>
  <div class="session-dock">
    <div class="session-dock__title">{{ t('terminal.sessionDock') }}</div>
    <NEmpty v-if="!loading && !items.length" size="small" :description="t('terminal.sessionDockEmpty')" />
    <div v-for="item in items" :key="item.elementId" class="session-dock__item">
      <button type="button" class="session-dock__open" @click="openItem(item)">
        <span class="name">{{ item.name }}</span>
        <span class="meta">{{ item.host || item.kind }}</span>
      </button>
      <NSpace :size="4">
        <NTag v-if="item.pinned" size="tiny" type="primary">{{ t('terminal.pinned') }}</NTag>
        <NButton size="tiny" quaternary @click="togglePin(item)">
          {{ item.pinned ? t('terminal.unpin') : t('terminal.pin') }}
        </NButton>
        <NButton size="tiny" quaternary type="error" @click="remove(item)">{{ t('common.delete') }}</NButton>
      </NSpace>
    </div>
  </div>
</template>

<style scoped>
.session-dock {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--co-border);
  border-radius: 8px;
  background: var(--co-bg-elevated, rgba(255, 255, 255, 0.03));
  max-height: 240px;
  overflow: auto;
}

.session-dock__title {
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--co-text-muted);
}

.session-dock__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-dock__open {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  min-width: 0;
}

.session-dock__open .name {
  font-size: 0.875rem;
  font-weight: 600;
}

.session-dock__open .meta {
  font-size: 0.75rem;
  color: var(--co-text-muted);
}
</style>
