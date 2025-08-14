<template>
  <div id="app">
    <!-- Top Navigation -->
    <TopNavigation />

    <!-- Main Content with top padding for navigation -->
    <div class="pt-16 pb-safe relative z-10">
      <router-view />
    </div>

    <!-- Notification System -->
    <NotificationSystem ref="notificationSystemRef" />

    <!-- Footer -->
    <footer class="w-full text-center text-white/70 py-6 text-xs sm:text-sm border-t border-white/10 bg-white/5 backdrop-blur-xl mt-8 relative z-10">
      <div class="flex items-center justify-center space-x-3">
        <span class="font-medium">VerifyMC by KiteMC Team © 2025</span>
        <span class="text-white/40">•</span>
        <span class="text-white/50 flex items-center space-x-1">
          <span>Made with</span>
          <span class="text-pink-300 animate-pulse">💖</span>
        </span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { inject, watch, ref, onMounted } from 'vue'
import { useNotification } from '@/composables/useNotification'
import NotificationSystem from '@/components/NotificationSystem.vue'
import TopNavigation from '@/components/TopNavigation.vue'

const config = inject('config', { value: {} as any })
const reloadConfig = inject('reloadConfig', () => { })
const { setNotificationSystem } = useNotification()

const notificationSystemRef = ref()

onMounted(() => {
  if (notificationSystemRef.value) {
    setNotificationSystem(notificationSystemRef.value)
  }
})

// 监听配置变化，动态设置页面标题
watch(() => config.value?.frontend?.web_server_prefix, (newPrefix: string | undefined) => {
  if (newPrefix) {
    document.title = newPrefix
    console.log('Page title updated:', newPrefix)
  } else {
    document.title = 'VerifyMC - GlassX Theme'
  }
}, { immediate: true })

// 暴露重载配置方法给全局
if (typeof window !== 'undefined') {
  (window as any).reloadVerifyMCConfig = reloadConfig
}
</script>

<style>
#app {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  min-height: 100vh;
  background: #030303;
  /* Hardware acceleration for the entire app */
  transform: translateZ(0);
}

/* Mobile safe area support */
.pb-safe {
  padding-bottom: env(safe-area-inset-bottom);
}

/* Ensure proper viewport handling on mobile */
@supports (-webkit-touch-callout: none) {
  .min-h-screen {
    min-height: -webkit-fill-available;
  }
}

/* 确保所有页面都使用相同的背景 */
body {
  background: #030303;
  margin: 0;
  padding: 0;
}

/* 移除其他页面可能的重置背景 */
* {
  box-sizing: border-box;
}

/* Reduce animations for users who prefer reduced motion */
@media (prefers-reduced-motion: reduce) {
  .animate-pulse {
    animation: none;
  }
}

/* Legacy pulse animation for compatibility */
@keyframes pulse {
  0%, 100% {
    opacity: 0.3;
    transform: translateZ(0);
  }
  50% {
    opacity: 0.7;
    transform: translateZ(0);
  }
}

.animate-pulse {
  animation: pulse 8s cubic-bezier(0.4, 0, 0.6, 1) infinite;
  transform: translateZ(0);
  will-change: opacity;
}

/* Low-performance mode styles */
.low-performance-mode {
  /* Disable all expensive effects */
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
  filter: none !important;
  box-shadow: none !important;
  text-shadow: none !important;
}

.low-performance-mode * {
  animation: none !important;
  transition: none !important;
  transform: none !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
}

/* Simplified glass effects for low-performance */
.low-performance-mode .glass-card {
  background: rgba(255, 255, 255, 0.1) !important;
  backdrop-filter: none !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
}

.low-performance-mode .glass-button {
  background: rgba(255, 255, 255, 0.1) !important;
  backdrop-filter: none !important;
}

/* 只在用户明确要求减少动画时才禁用 */
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>