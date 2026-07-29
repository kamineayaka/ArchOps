import { computed, ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'archops-theme'

const stored = (localStorage.getItem(STORAGE_KEY) as ThemeMode | null) ?? 'dark'
const mode = ref<ThemeMode>(stored)

function systemPrefersDark() {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

const resolvedDark = ref(mode.value === 'dark' || (mode.value === 'system' && systemPrefersDark()))

if (typeof window !== 'undefined') {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (mode.value === 'system') resolvedDark.value = systemPrefersDark()
  })
}

watch(mode, (value) => {
  localStorage.setItem(STORAGE_KEY, value)
  resolvedDark.value = value === 'dark' || (value === 'system' && systemPrefersDark())
})

export function useTheme() {
  const isDark = computed(() => resolvedDark.value)

  function toggle() {
    mode.value = isDark.value ? 'light' : 'dark'
  }

  function setTheme(value: ThemeMode) {
    mode.value = value
  }

  return { mode, isDark, toggle, setTheme }
}
