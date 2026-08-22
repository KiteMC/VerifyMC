import { createI18n } from "vue-i18n"
import zh from "./locales/zh.json"
import en from "./locales/en.json"

const messages = {
  zh,
  en,
}

// SSR 兼容性：检查是否在浏览器环境中
const canUseBrowser = () => typeof window !== "undefined"

// 获取存储的语言，未保存时使用中文默认值
const getSavedLanguage = (): "zh" | "en" => {
  if (!canUseBrowser()) {
    return "zh" // SSR 环境默认使用中文
  }

  const stored = localStorage.getItem("language")
  if (stored === "zh" || stored === "en") {
    return stored
  }

  // 没有保存的用户偏好时，统一使用中文默认文本
  return "zh"
}

const savedLanguage = getSavedLanguage()

const i18n = createI18n({
  legacy: false,
  locale: savedLanguage,
  fallbackLocale: "zh",
  messages,
  globalInjection: true,
})

// 添加全局属性
// i18n.global.setLocaleMessage('zh', { ...i18n.global.getLocaleMessage('zh'), lang: 'zh' })
// i18n.global.setLocaleMessage('en', { ...i18n.global.getLocaleMessage('en'), lang: 'en' })

export function setLanguage(lang: "zh" | "en") {
  i18n.global.locale.value = lang

  // SSR 兼容性：仅在浏览器环境中访问浏览器 API
  if (canUseBrowser()) {
    localStorage.setItem("language", lang)
    document.documentElement.lang = lang
  }
}

export default i18n
