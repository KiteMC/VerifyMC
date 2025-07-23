<template>
  <div class="relative">
    <button
      @click="toggleDropdown"
      class="glass-button flex items-center space-x-2 text-white hover:text-blue-300 transition-colors duration-300"
    >
      <Globe class="w-4 h-4" />
      <span class="text-sm font-medium">{{ currentLanguageLabel }}</span>
      <ChevronDown 
        class="w-4 h-4 transition-transform duration-300"
        :class="{ 'rotate-180': isOpen }"
      />
    </button>
    
    <Transition
      enter-active-class="transition-all duration-200 ease-out"
      enter-from-class="opacity-0 scale-95 translate-y-1"
      enter-to-class="opacity-100 scale-100 translate-y-0"
      leave-active-class="transition-all duration-150 ease-in"
      leave-from-class="opacity-100 scale-100 translate-y-0"
      leave-to-class="opacity-0 scale-95 translate-y-1"
    >
      <div
        v-if="isOpen"
        class="absolute right-0 top-full mt-2 w-32 glass-card border border-glass-200 shadow-xl z-50"
      >
        <div class="py-2">
          <button
            v-for="lang in languages"
            :key="lang.code"
            @click="switchLanguage(lang.code)"
            class="w-full px-4 py-2 text-left text-sm text-white hover:bg-glass-100 transition-colors duration-200 flex items-center space-x-2"
            :class="{ 'bg-glass-100': currentLocale === lang.code }"
          >
            <span>{{ lang.flag }}</span>
            <span>{{ lang.label }}</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Globe, ChevronDown } from 'lucide-vue-next'

const { locale, t } = useI18n()

const isOpen = ref(false)

const languages = [
  { code: 'zh', label: '中文', flag: '🇨🇳' },
  { code: 'en', label: 'English', flag: '🇺🇸' }
]

const currentLocale = computed(() => locale.value)

const currentLanguageLabel = computed(() => {
  const current = languages.find(lang => lang.code === currentLocale.value)
  return current ? current.label : 'Language'
})

const toggleDropdown = () => {
  isOpen.value = !isOpen.value
}

const switchLanguage = (langCode: string) => {
  locale.value = langCode
  localStorage.setItem('language', langCode)
  isOpen.value = false
  
  // Update document language
  document.documentElement.lang = langCode
}

const closeDropdown = (event: Event) => {
  const target = event.target as HTMLElement
  if (!target.closest('.relative')) {
    isOpen.value = false
  }
}

const langs = { zh: '中文', en: 'EN' }
const orderedLangs = computed(() => {
  const arr = Object.entries(langs)
  return arr.sort(([a], [b]) => (a === locale.value ? -1 : b === locale.value ? 1 : 0))
})

// 初始化优先读取 localStorage
const savedLang = localStorage.getItem('lang')
if (savedLang && savedLang !== locale.value) locale.value = savedLang

watch(locale, (val) => {
  localStorage.setItem('lang', val)
})

onMounted(() => {
  document.addEventListener('click', closeDropdown)
  document.documentElement.lang = currentLocale.value
})

onUnmounted(() => {
  document.removeEventListener('click', closeDropdown)
})
</script>
