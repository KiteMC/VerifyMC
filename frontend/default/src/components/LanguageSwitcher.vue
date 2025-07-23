<template>
  <div class="inline-flex items-center gap-1">
    <button
      v-for="([lang, label], i) in orderedLangs"
      :key="lang"
      :class="['px-2 py-1 rounded', locale === lang ? 'bg-blue-100 text-blue-700 font-bold' : 'hover:bg-gray-100']"
      @click="locale = lang"
    >
      {{ label }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
const { locale } = useI18n()
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
</script> 