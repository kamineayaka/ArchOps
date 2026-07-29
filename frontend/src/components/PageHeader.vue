<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header__text">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.extra" class="page-header__extra">
      <slot name="extra" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ao-space-3);
  margin-bottom: var(--ao-space-4);
}

.page-header__title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: -0.02em;
  color: var(--ao-text);
  text-wrap: balance;
}

.page-header__desc {
  margin: var(--ao-space-1) 0 0;
  font-size: 0.8125rem;
  line-height: 1.5;
  color: var(--ao-text-secondary);
  max-width: 52rem;
}

.page-header__extra {
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
