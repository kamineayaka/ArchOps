import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN'

/** 单语（中文）。文案仍走 vue-i18n 键值，便于集中维护，不再提供语言切换。 */
const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
  },
})

export default i18n
