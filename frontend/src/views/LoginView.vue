<script setup lang="ts">
import { t } from '@/messages'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NForm, NFormItem, NInput, NSpace, useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const form = ref({
  username: '',
  password: '',
})

const rules = computed(() => ({
  username: { required: true, message: t('common.username'), trigger: 'blur' },
  password: { required: true, message: t('common.password'), trigger: 'blur' },
}))

async function handleSubmit() {
  try {
    await authStore.login(form.value.username, form.value.password)
    await router.push({ name: 'topology' })
  } catch (error) {
    message.error(t('common.loginFailed'))
    console.error(error)
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-backdrop ao-blueprint-grid" aria-hidden="true" />
    <div class="login-glow" aria-hidden="true" />
    <NCard class="login-card" :bordered="true">
      <div class="login-brand">
        <div class="login-brand__mark">AO</div>
        <div>
          <p class="login-brand__product">{{ t('common.appName') }}</p>
          <h1 class="login-brand__title">{{ t('auth.title') }}</h1>
          <p class="login-brand__subtitle">{{ t('auth.subtitle') }}</p>
        </div>
      </div>
      <NForm :model="form" :rules="rules" @submit.prevent="handleSubmit">
        <NFormItem path="username" :label="t('common.username')">
          <NInput
            v-model:value="form.username"
            autocomplete="username"
            spellcheck="false"
            :placeholder="t('common.username')"
          />
        </NFormItem>
        <NFormItem path="password" :label="t('common.password')">
          <NInput
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            autocomplete="current-password"
            :placeholder="t('common.password')"
            @keyup.enter="handleSubmit"
          />
        </NFormItem>
        <NSpace vertical :size="12">
          <NButton type="primary" block :loading="authStore.loading" attr-type="submit" @click="handleSubmit">
            {{ t('common.login') }}
          </NButton>
          <p class="hint">{{ t('auth.defaultAccount') }}</p>
        </NSpace>
      </NForm>
    </NCard>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--ao-space-6);
  background: var(--ao-ink);
  overflow: hidden;
}

.login-backdrop {
  position: absolute;
  inset: 0;
  opacity: 1;
}

.login-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 70% 50% at 15% 20%, rgba(61, 139, 255, 0.18), transparent 55%),
    radial-gradient(ellipse 50% 40% at 85% 80%, rgba(232, 163, 23, 0.08), transparent 50%);
  pointer-events: none;
}

.login-card {
  position: relative;
  width: 100%;
  max-width: 400px;
  z-index: 1;
  background: var(--ao-slate) !important;
  border-color: var(--ao-border) !important;
  color: var(--ao-text);
}

.login-card :deep(.n-card__content) {
  color: #e8eef7;
}

.login-card :deep(.n-form-item-label) {
  color: #8ba3c7 !important;
}

.login-brand {
  display: flex;
  gap: var(--ao-space-3);
  align-items: flex-start;
  margin-bottom: var(--ao-space-5);
}

.login-brand__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--ao-radius-sm);
  background: var(--ao-blueprint);
  color: #fff;
  font-size: 0.8125rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  font-family: var(--ao-font-mono);
  flex-shrink: 0;
}

.login-brand__product {
  margin: 0 0 4px;
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ao-blueprint);
}

.login-brand__title {
  margin: 0;
  font-size: 1.375rem;
  font-weight: 600;
  color: #e8eef7;
  line-height: 1.25;
  letter-spacing: -0.02em;
  text-wrap: balance;
}

.login-brand__subtitle {
  margin: var(--ao-space-2) 0 0;
  font-size: 0.875rem;
  color: #8ba3c7;
  line-height: 1.5;
}

.hint {
  margin: 0;
  font-size: 0.75rem;
  color: #6b7f99;
  text-align: center;
  line-height: 1.5;
}
</style>
