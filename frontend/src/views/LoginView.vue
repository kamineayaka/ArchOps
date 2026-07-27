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
    <div class="login-backdrop" aria-hidden="true" />
    <NCard class="login-card page-card" :bordered="false">
      <div class="login-brand">
        <div class="login-brand__mark">CO</div>
        <div>
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
        <NSpace vertical :size="16">
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
  padding: var(--co-space-6);
  background: #0f172a;
  overflow: hidden;
}

.login-backdrop {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 20% 20%, rgba(15, 118, 110, 0.35), transparent),
    radial-gradient(ellipse 60% 50% at 80% 80%, rgba(30, 41, 59, 0.8), transparent),
    linear-gradient(160deg, #0f172a 0%, #1e293b 45%, #0f766e 100%);
}

.login-card {
  position: relative;
  width: 100%;
  max-width: 420px;
  z-index: 1;
}

.login-brand {
  display: flex;
  gap: var(--co-space-4);
  align-items: flex-start;
  margin-bottom: var(--co-space-6);
}

.login-brand__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--co-radius);
  background: var(--co-primary);
  color: #fff;
  font-size: 0.8125rem;
  font-weight: 700;
  flex-shrink: 0;
}

.login-brand__title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--co-text);
  line-height: 1.3;
  text-wrap: balance;
}

.login-brand__subtitle {
  margin: var(--co-space-2) 0 0;
  font-size: 0.875rem;
  color: var(--co-text-secondary);
  line-height: 1.5;
}

.hint {
  margin: 0;
  font-size: 0.75rem;
  color: var(--co-text-muted);
  text-align: center;
  line-height: 1.5;
}
</style>
