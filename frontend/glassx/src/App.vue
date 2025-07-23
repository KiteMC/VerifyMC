<template>
  <div id="app">
    <!-- Dynamic Background -->
    <div class="fixed inset-0 overflow-hidden pointer-events-none">
      <div class="absolute inset-0 bg-gradient-to-br from-indigo-500/[0.05] via-transparent to-rose-500/[0.05] blur-3xl" />
      
      <!-- Animated Shapes -->
      <div class="absolute inset-0 overflow-hidden">
        <div class="absolute left-[-10%] md:left-[-5%] top-[15%] md:top-[20%] w-[600px] h-[140px] bg-gradient-to-r from-indigo-500/[0.15] to-transparent rounded-full blur-xl animate-pulse" style="transform: rotate(12deg);" />
        <div class="absolute right-[-5%] md:right-[0%] top-[70%] md:top-[75%] w-[500px] h-[120px] bg-gradient-to-r from-rose-500/[0.15] to-transparent rounded-full blur-xl animate-pulse" style="transform: rotate(-15deg);" />
        <div class="absolute left-[5%] md:left-[10%] bottom-[5%] md:bottom-[10%] w-[300px] h-[80px] bg-gradient-to-r from-violet-500/[0.15] to-transparent rounded-full blur-xl animate-pulse" style="transform: rotate(-8deg);" />
        <div class="absolute right-[15%] md:right-[20%] top-[10%] md:top-[15%] w-[200px] h-[60px] bg-gradient-to-r from-amber-500/[0.15] to-transparent rounded-full blur-xl animate-pulse" style="transform: rotate(20deg);" />
        <div class="absolute left-[20%] md:left-[25%] top-[5%] md:top-[10%] w-[150px] h-[40px] bg-gradient-to-r from-cyan-500/[0.15] to-transparent rounded-full blur-xl animate-pulse" style="transform: rotate(-25deg);" />
      </div>
    </div>

    <!-- Top Navigation -->
    <TopNavigation />
    
    <!-- Main Content with top padding for navigation -->
    <div class="pt-16 relative z-10">
      <router-view />
    </div>
    
    <!-- Notification Container -->
    <div class="fixed top-20 right-4 z-50 space-y-2">
      <Toast
        v-for="notification in notifications"
        :key="notification.id"
        :type="notification.type"
        :title="notification.title"
        :message="notification.message"
        :duration="notification.duration"
        :auto-close="notification.autoClose"
        @close="handleNotificationClose(notification.id)"
      />
    </div>

    <!-- Footer -->
    <footer class="w-full text-center text-gray-400 py-2 text-xs sm:text-sm border-t bg-white/10 backdrop-blur-lg mt-8">
      VerifyMC by KiteMC Team © 2025
    </footer>
  </div>
</template>

<script setup lang="ts">
import { inject, watch } from 'vue'
import { useNotification } from '@/composables/useNotification'
import Toast from '@/components/Toast.vue'
import TopNavigation from '@/components/TopNavigation.vue'

const config = inject('config', { value: {} as any })
const reloadConfig = inject('reloadConfig', () => {})
const { notifications, handleNotificationClose } = useNotification()

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

/* 添加动画效果 */
@keyframes pulse {
  0%, 100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}

.animate-pulse {
  animation: pulse 4s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
</style> 