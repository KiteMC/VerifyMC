import { createI18n } from "vue-i18n"
import zh from "./locales/zh.json"
import en from "./locales/en.json"

const messages = {
  zh,
  en,
}

const savedLanguage = localStorage.getItem("language") || navigator.language.startsWith("zh") ? "zh" : "en"

const i18n = createI18n({
  legacy: false,
  locale: savedLanguage,
  fallbackLocale: "en",
  messages,
  globalInjection: true,
})

// 添加全局属性
// i18n.global.setLocaleMessage('zh', { ...i18n.global.getLocaleMessage('zh'), lang: 'zh' })
// i18n.global.setLocaleMessage('en', { ...i18n.global.getLocaleMessage('en'), lang: 'en' })

export function setLanguage(lang: string) {
  i18n.global.locale.value = lang
  localStorage.setItem('language', lang)
  document.documentElement.lang = lang
}

export default i18n
