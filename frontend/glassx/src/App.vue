<template>
  <div id="app">
    <!-- 小清新背景层 -->
    <div class="fixed inset-0 overflow-hidden pointer-events-none bg-layer">
      <!-- 主渐变背景 -->
      <div class="absolute inset-0 bg-gradient-to-br from-purple-500/[0.06] via-pink-500/[0.04] to-blue-500/[0.06]" />
      
      <!-- 装饰性光点 -->
      <div class="absolute top-1/4 left-1/4 w-2 h-2 bg-white/30 rounded-full animate-pulse" style="animation-delay: 0s;"></div>
      <div class="absolute top-3/4 right-1/3 w-1 h-1 bg-white/40 rounded-full animate-pulse" style="animation-delay: 2s;"></div>
      <div class="absolute bottom-1/4 left-2/3 w-1.5 h-1.5 bg-white/25 rounded-full animate-pulse" style="animation-delay: 4s;"></div>

      <!-- 3D斜角长方形背景 - 根据性能调整动画 -->
      <div class="absolute inset-0 overflow-hidden">
        <!-- 保持3D斜角效果的长方形 -->
        <div class="bg-shape bg-shape-1 left-[-10%] md:left-[-5%] top-[15%] md:top-[20%] w-[600px] h-[140px] bg-gradient-to-r from-indigo-500/[0.12] to-transparent blur-xl" 
             style="transform: rotate(12deg) translateZ(0); border-radius: 20px;" />
        <div class="bg-shape bg-shape-2 right-[-5%] md:right-[0%] top-[70%] md:top-[75%] w-[500px] h-[120px] bg-gradient-to-l from-rose-500/[0.12] to-transparent blur-xl" 
             style="transform: rotate(-15deg) translateZ(0); border-radius: 20px;" />
        <div class="bg-shape bg-shape-3 left-[5%] md:left-[10%] bottom-[5%] md:bottom-[10%] w-[300px] h-[80px] bg-gradient-to-r from-violet-500/[0.10] to-transparent blur-xl" 
             style="transform: rotate(-8deg) translateZ(0); border-radius: 20px;" />
      </div>
    </div>

    <!-- Top Navigation -->
    <TopNavigation />

    <!-- Main Content with top padding for navigation -->
    <div class="pt-16 relative z-10">
      <router-view />
    </div>

    <!-- Notification Container -->
    <div class="fixed top-20 right-4 z-[9999] space-y-2 pointer-events-none">
      <div class="pointer-events-auto">
        <Toast v-for="notification in notifications" :key="notification.id" :type="notification.type"
          :title="notification.title" :message="notification.message" :duration="notification.duration"
          :auto-close="notification.autoClose" @close="handleNotificationClose(notification.id)" />
      </div>
    </div>

    <!-- 小清新Footer -->
    <footer class="w-full text-center text-white/70 py-6 text-xs sm:text-sm border-t border-white/10 bg-white/5 backdrop-blur-xl mt-8">
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
import { inject, watch } from 'vue'
import { useNotification } from '@/composables/useNotification'
import Toast from '@/components/Toast.vue'
import TopNavigation from '@/components/TopNavigation.vue'

const config = inject('config', { value: {} as any })
const reloadConfig = inject('reloadConfig', () => { })
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
  /* Hardware acceleration for the entire app */
  transform: translateZ(0);
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

/* Performance optimized background layer */
.bg-layer {
  /* Hardware acceleration */
  transform: translateZ(0);
  will-change: opacity;
}

/* Aggressive performance optimizations for low-end devices */
@media (max-width: 1024px) {
  .bg-layer {
    opacity: 0.5; /* Reduce visual complexity on smaller screens */
  }
  
  .bg-shape {
    animation-duration: 20s; /* Slower animations */
  }
}

/* Ultra-low performance mode for very weak devices */
@media (max-width: 768px) {
  .bg-layer {
    display: none; /* Completely disable background animations */
  }
  
  #app {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  }
}

/* CPU-based performance detection */
@media (prefers-reduced-motion: reduce) {
  .bg-layer {
    display: none;
  }
  
  * {
    animation: none !important;
    transition: none !important;
  }
}

/* 3D斜角长方形 - 根据性能调整动画 */
.bg-shape {
  position: absolute;
  /* Hardware acceleration */
  transform: translateZ(0);
  will-change: transform, opacity;
  /* 保持斜角，根据性能决定是否晃动 */
}

/* 高性能设备：完整的上下晃动动画 */
@media (min-width: 1025px) and (prefers-reduced-motion: no-preference) {
  .bg-shape-1 {
    animation-delay: 0s;
  }
  
  .bg-shape-2 {
    animation-delay: 2.5s;
  }
  
  .bg-shape-3 {
    animation-delay: 5s;
  }
  
  @keyframes floatUpDown {
    0%, 100% {
      opacity: 0.4;
      transform: translateY(0px) translateZ(0);
    }
    25% {
      opacity: 0.6;
      transform: translateY(-15px) translateZ(0);
    }
    50% {
      opacity: 0.8;
      transform: translateY(-25px) translateZ(0);
    }
    75% {
      opacity: 0.6;
      transform: translateY(-15px) translateZ(0);
    }
  }
  
  /* 为每个形状单独定义带旋转的动画 */
  .bg-shape-1 {
    animation: floatUpDown1 8s ease-in-out infinite;
  }
  
  .bg-shape-2 {
    animation: floatUpDown2 8s ease-in-out infinite;
  }
  
  .bg-shape-3 {
    animation: floatUpDown3 8s ease-in-out infinite;
  }
  
  @keyframes floatUpDown1 {
    0%, 100% {
      opacity: 0.4;
      transform: rotate(12deg) translateY(0px) translateZ(0);
    }
    25% {
      opacity: 0.6;
      transform: rotate(12deg) translateY(-15px) translateZ(0);
    }
    50% {
      opacity: 0.8;
      transform: rotate(12deg) translateY(-25px) translateZ(0);
    }
    75% {
      opacity: 0.6;
      transform: rotate(12deg) translateY(-15px) translateZ(0);
    }
  }
  
  @keyframes floatUpDown2 {
    0%, 100% {
      opacity: 0.4;
      transform: rotate(-15deg) translateY(0px) translateZ(0);
    }
    25% {
      opacity: 0.6;
      transform: rotate(-15deg) translateY(-15px) translateZ(0);
    }
    50% {
      opacity: 0.8;
      transform: rotate(-15deg) translateY(-25px) translateZ(0);
    }
    75% {
      opacity: 0.6;
      transform: rotate(-15deg) translateY(-15px) translateZ(0);
    }
  }
  
  @keyframes floatUpDown3 {
    0%, 100% {
      opacity: 0.4;
      transform: rotate(-8deg) translateY(0px) translateZ(0);
    }
    25% {
      opacity: 0.6;
      transform: rotate(-8deg) translateY(-15px) translateZ(0);
    }
    50% {
      opacity: 0.8;
      transform: rotate(-8deg) translateY(-25px) translateZ(0);
    }
    75% {
      opacity: 0.6;
      transform: rotate(-8deg) translateY(-15px) translateZ(0);
    }
  }
}

/* 中等性能设备：仅透明度动画，保持旋转 */
@media (max-width: 1024px) and (min-width: 769px) {
  .bg-shape-1 { 
    animation: simpleOpacity1 12s ease-in-out infinite;
    animation-delay: 0s; 
  }
  .bg-shape-2 { 
    animation: simpleOpacity2 12s ease-in-out infinite;
    animation-delay: 4s; 
  }
  .bg-shape-3 { 
    animation: simpleOpacity3 12s ease-in-out infinite;
    animation-delay: 8s; 
  }
  
  @keyframes simpleOpacity1 {
    0%, 100% { 
      opacity: 0.3; 
      transform: rotate(12deg) translateZ(0);
    }
    50% { 
      opacity: 0.7; 
      transform: rotate(12deg) translateZ(0);
    }
  }
  
  @keyframes simpleOpacity2 {
    0%, 100% { 
      opacity: 0.3; 
      transform: rotate(-15deg) translateZ(0);
    }
    50% { 
      opacity: 0.7; 
      transform: rotate(-15deg) translateZ(0);
    }
  }
  
  @keyframes simpleOpacity3 {
    0%, 100% { 
      opacity: 0.3; 
      transform: rotate(-8deg) translateZ(0);
    }
    50% { 
      opacity: 0.7; 
      transform: rotate(-8deg) translateZ(0);
    }
  }
}

/* 低性能设备：静态显示，保持斜角 */
@media (max-width: 768px), (prefers-reduced-motion: reduce) {
  .bg-shape {
    animation: none;
    opacity: 0.4;
  }
  
  .bg-shape-1 {
    transform: rotate(12deg) translateZ(0) !important;
  }
  
  .bg-shape-2 {
    transform: rotate(-15deg) translateZ(0) !important;
  }
  
  .bg-shape-3 {
    transform: rotate(-8deg) translateZ(0) !important;
  }
}

/* Low-performance device detection and optimization */
@media (max-width: 768px), (prefers-reduced-motion: reduce) {
  .bg-shape {
    animation: none;
    opacity: 0.4;
  }
  
  .bg-layer {
    display: none; /* Completely disable background on mobile/low-performance */
  }
}

/* Reduce animations for users who prefer reduced motion */
@media (prefers-reduced-motion: reduce) {
  .bg-shape {
    animation: none;
    opacity: 0.5;
  }
  
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

/* Auto-enable low performance mode based on device capabilities */
@media (max-width: 768px), 
       (max-height: 600px),
       (prefers-reduced-motion: reduce) {
  #app {
    /* Apply low-performance mode automatically */
    backdrop-filter: none;
    filter: none;
  }
  
  .bg-layer {
    display: none;
  }
  
  * {
    animation-duration: 0s !important;
    transition-duration: 0s !important;
  }
}
</style>