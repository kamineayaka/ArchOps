<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = defineProps<{
  kind: 'risk' | 'status' | 'credential'
  value: string
}>()

const tagType = computed(() => {
  const v = props.value.toUpperCase()
  if (props.kind === 'risk') {
    if (v === 'HIGH') return 'error'
    if (v === 'MEDIUM') return 'warning'
    return 'info'
  }
  if (props.kind === 'status') {
    if (v === 'SUCCESS' || v === 'APPROVED') return 'success'
    if (v === 'FAILURE' || v === 'REJECTED' || v === 'ERROR') return 'error'
    if (v === 'PENDING') return 'warning'
    return 'default'
  }
  if (props.kind === 'credential') {
    return v === 'CONFIGURED' ? 'success' : 'warning'
  }
  return 'default'
})
</script>

<template>
  <NTag :type="tagType" size="small">{{ value }}</NTag>
</template>
