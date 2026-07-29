<script setup lang="ts">
defineProps<{
  message: string
  hint?: string
}>()
</script>

<template>
  <div class="empty-state" role="status">
    <p class="empty-state__message">{{ message }}</p>
    <p v-if="hint" class="empty-state__hint">{{ hint }}</p>
    <div v-if="$slots.action" class="empty-state__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  padding: var(--ao-space-8) var(--ao-space-4);
  text-align: center;
  border: 1px dashed var(--ao-border);
  border-radius: var(--ao-radius);
  background: var(--ao-bg-elevated);
}

.empty-state__message {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--ao-text-secondary);
}

.empty-state__hint {
  margin: var(--ao-space-2) 0 0;
  font-size: 0.8125rem;
  color: var(--ao-text-muted);
}

.empty-state__action {
  margin-top: var(--ao-space-4);
}
</style>
