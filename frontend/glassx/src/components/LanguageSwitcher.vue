<template>
  <div class="fluid-language-switcher" ref="dropdownRef">
    <button
      @click="toggleDropdown"
      :class="[
        'fluid-trigger',
        { 'fluid-trigger-open': isOpen }
      ]"
      :aria-expanded="isOpen"
      aria-haspopup="true"
    >
      <span class="fluid-trigger-content">
        <div class="fluid-icon-wrapper">
          <Globe class="fluid-icon" />
          <div v-if="hoveredLanguage" class="fluid-icon-highlight" :style="{ color: currentLanguage.color }">
            <Globe class="fluid-icon" />
          </div>
        </div>
        <span class="fluid-trigger-text">{{ currentLanguage.label }}</span>
      </span>
      <div class="fluid-chevron" :class="{ 'fluid-chevron-open': isOpen }">
        <ChevronDown class="w-4 h-4" />
      </div>
    </button>
    
    <Transition
      name="fluid-dropdown"
      @enter="onEnter"
      @leave="onLeave"
    >
      <div v-if="isOpen" class="fluid-dropdown">
        <div class="fluid-dropdown-content">
          <div class="fluid-dropdown-inner">
            <!-- 背景高亮指示器 -->
            <div 
              class="fluid-highlight"
              :style="highlightStyle"
            ></div>
            
            <!-- 语言选项 -->
            <button
              v-for="(lang, index) in languages"
              :key="lang.code"
              @click="switchLanguage(lang.code)"
              @mouseenter="hoveredLanguage = lang.code"
              @mouseleave="hoveredLanguage = null"
              :class="[
                'fluid-option',
                {
                  'fluid-option-active': currentLocale === lang.code,
                  'fluid-option-hovered': hoveredLanguage === lang.code
                }
              ]"
              :style="{ '--option-index': index }"
            >
              <div class="fluid-option-icon">
                <span class="fluid-flag">{{ lang.flag }}</span>
                <div v-if="hoveredLanguage === lang.code" class="fluid-flag-highlight">
                  <span class="fluid-flag">{{ lang.flag }}</span>
                </div>
              </div>
              <span class="fluid-option-text">{{ lang.label }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Globe, ChevronDown } from 'lucide-vue-next'

const { locale, t } = useI18n()

const isOpen = ref(false)
const hoveredLanguage = ref<string | null>(null)
const dropdownRef = ref<HTMLElement>()

const languages = [
  { code: 'zh', label: '中文', flag: '🇨🇳', color: '#FF6B6B' },
  { code: 'en', label: 'English', flag: '🇺🇸', color: '#4ECDC4' }
]

const currentLocale = computed(() => locale.value)

const currentLanguage = computed(() => {
  const current = languages.find(lang => lang.code === currentLocale.value)
  return current || languages[0]
})

// 计算高亮指示器的位置
const highlightStyle = computed(() => {
  const targetLang = hoveredLanguage.value || currentLocale.value
  const index = languages.findIndex(lang => lang.code === targetLang)
  
  return {
    transform: `translateY(${index * 40}px)`,
    height: '40px'
  }
})

const toggleDropdown = () => {
  isOpen.value = !isOpen.value
}

const switchLanguage = (langCode: string) => {
  locale.value = langCode
  localStorage.setItem('language', langCode)
  isOpen.value = false
  hoveredLanguage.value = null
  
  // Update document language
  document.documentElement.lang = langCode
}

const closeDropdown = (event: Event) => {
  const target = event.target as HTMLElement
  if (!dropdownRef.value?.contains(target)) {
    isOpen.value = false
    hoveredLanguage.value = null
  }
}

// 动画钩子
const onEnter = (el: Element) => {
  const element = el as HTMLElement
  element.style.height = '0'
  element.style.opacity = '0'
  
  nextTick(() => {
    element.style.height = 'auto'
    element.style.opacity = '1'
  })
}

const onLeave = (el: Element) => {
  const element = el as HTMLElement
  element.style.height = '0'
  element.style.opacity = '0'
}

// 键盘导航
const handleKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    isOpen.value = false
    hoveredLanguage.value = null
  }
}

// 初始化优先读取 localStorage
const savedLang = localStorage.getItem('language') || localStorage.getItem('lang')
if (savedLang && savedLang !== locale.value) {
  locale.value = savedLang
}

watch(locale, (val) => {
  localStorage.setItem('language', val)
  localStorage.setItem('lang', val)
})

onMounted(() => {
  document.addEventListener('click', closeDropdown)
  document.addEventListener('keydown', handleKeyDown)
  document.documentElement.lang = currentLocale.value
})

onUnmounted(() => {
  document.removeEventListener('click', closeDropdown)
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<style scoped>
/* 主容器 */
.fluid-language-switcher {
  position: relative;
  width: fit-content;
  min-width: 120px;
}

/* 触发按钮 */
.fluid-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 40px;
  padding: 0 12px;
  background: rgba(23, 23, 23, 0.9);
  border: 1px solid transparent;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.fluid-trigger:hover {
  background: rgba(38, 38, 38, 0.9);
  color: rgba(255, 255, 255, 0.9);
  border-color: rgba(115, 115, 115, 0.3);
}

.fluid-trigger:focus {
  outline: none;
  ring: 2px;
  ring-color: rgba(115, 115, 115, 0.5);
  ring-offset: 2px;
  ring-offset-color: transparent;
  border-color: rgba(115, 115, 115, 0.5);
}

.fluid-trigger-open {
  background: rgba(38, 38, 38, 0.9);
  color: rgba(255, 255, 255, 0.9);
  border-color: rgba(115, 115, 115, 0.3);
}

/* 触发按钮内容 */
.fluid-trigger-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 图标包装器 */
.fluid-icon-wrapper {
  position: relative;
  width: 16px;
  height: 16px;
}

.fluid-icon {
  width: 16px;
  height: 16px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fluid-icon-highlight {
  position: absolute;
  inset: 0;
  opacity: 0;
  animation: iconPulse 0.5s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

@keyframes iconPulse {
  0% {
    opacity: 0;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

.fluid-trigger-text {
  white-space: nowrap;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 箭头图标 */
.fluid-chevron {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.fluid-chevron-open {
  transform: rotate(180deg);
}

/* 下拉菜单容器 */
.fluid-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  left: 0;
  z-index: 50;
}

.fluid-dropdown-content {
  background: rgba(23, 23, 23, 0.95);
  border: 1px solid rgba(38, 38, 38, 1);
  border-radius: 12px;
  padding: 4px;
  box-shadow: 
    0 10px 25px -5px rgba(0, 0, 0, 0.3),
    0 4px 6px -2px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
}

.fluid-dropdown-inner {
  position: relative;
  padding: 8px 0;
}

/* 背景高亮指示器 */
.fluid-highlight {
  position: absolute;
  left: 4px;
  right: 4px;
  background: rgba(38, 38, 38, 1);
  border-radius: 6px;
  transition: all 0.5s cubic-bezier(0.25, 0.1, 0.25, 1);
  pointer-events: none;
  z-index: 1;
}

/* 选项按钮 */
.fluid-option {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  padding: 10px 16px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.875rem;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 2;
  opacity: 0;
  transform: translateY(-10px);
  animation: optionSlideIn 0.3s cubic-bezier(0.25, 0.1, 0.25, 1) forwards;
  animation-delay: calc(var(--option-index) * 0.1s);
}

@keyframes optionSlideIn {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fluid-option:focus {
  outline: none;
}

.fluid-option-active,
.fluid-option-hovered {
  color: rgba(255, 255, 255, 0.9);
}

.fluid-option:active {
  transform: scale(0.98);
}

/* 选项图标 */
.fluid-option-icon {
  position: relative;
  width: 20px;
  height: 20px;
  margin-right: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fluid-flag {
  font-size: 16px;
  line-height: 1;
}

.fluid-flag-highlight {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transform: scale(0.8);
  animation: flagHighlight 0.4s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

@keyframes flagHighlight {
  0% {
    opacity: 0;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

.fluid-option-text {
  white-space: nowrap;
}

/* 下拉菜单动画 */
.fluid-dropdown-enter-active {
  transition: all 0.3s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.fluid-dropdown-leave-active {
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.fluid-dropdown-enter-from {
  opacity: 0;
  transform: translateY(-4px) scale(0.95);
}

.fluid-dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.95);
}

.fluid-dropdown-enter-to,
.fluid-dropdown-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
}

/* 响应式设计 */
@media (max-width: 480px) {
  .fluid-language-switcher {
    min-width: 100px;
  }
  
  .fluid-trigger {
    height: 36px;
    padding: 0 10px;
    font-size: 0.8rem;
  }
  
  .fluid-option {
    padding: 8px 12px;
    font-size: 0.8rem;
  }
}

/* 减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
  .fluid-trigger,
  .fluid-chevron,
  .fluid-highlight,
  .fluid-option,
  .fluid-icon,
  .fluid-icon-highlight,
  .fluid-flag-highlight {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
  }
}

/* 高对比度模式 */
@media (prefers-contrast: high) {
  .fluid-trigger {
    border-color: rgba(255, 255, 255, 0.5);
  }
  
  .fluid-dropdown-content {
    border-color: rgba(255, 255, 255, 0.3);
  }
  
  .fluid-highlight {
    background: rgba(255, 255, 255, 0.1);
  }
}
</style>