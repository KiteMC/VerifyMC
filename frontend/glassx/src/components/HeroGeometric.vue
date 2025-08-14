<template>
  <div class="relative min-h-screen w-full flex items-center justify-center overflow-hidden bg-[#030303]">
    <!-- 简洁的背景渐变 -->
    <div class="absolute inset-0 bg-gradient-to-br from-gray-900 via-gray-800 to-black opacity-50" />

    <!-- 主要内容 -->
    <div class="relative z-10 container mx-auto px-4 md:px-6">
      <div class="max-w-3xl mx-auto text-center">
        <!-- Badge -->
        <div class="fade-up-item" style="--delay: 0s">
          <div
            class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.03] border border-white/[0.08] mb-8 md:mb-12">
            <a href="https://github.com/KiteMC/VerifyMC" target="_blank" rel="noopener noreferrer"
              class="inline-flex items-center gap-2 text-white/60 hover:text-white transition-colors duration-300">
              <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                <path
                  d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
              </svg>
              <span class="text-sm tracking-wide">GitHub</span>
            </a>
          </div>
        </div>

        <!-- 标题 -->
        <div class="fade-up-item" style="--delay: 0.2s">
          <h1 class="text-4xl sm:text-6xl md:text-8xl font-bold mb-6 md:mb-8 tracking-tight">
            <span class="bg-clip-text text-transparent bg-gradient-to-b from-white to-white/80">{{ displayTitle1
            }}</span>
            <br />
            <span
              class="bg-clip-text text-transparent bg-gradient-to-r from-indigo-300 via-white/90 to-rose-300 font-pacifico">
              {{ displayTitle2 }}
            </span>
          </h1>
        </div>

        <!-- 描述文字 -->
        <div class="fade-up-item" style="--delay: 0.4s">
          <p v-if="announcement"
            class="text-base sm:text-lg md:text-xl text-white/40 mb-8 leading-relaxed font-light tracking-wide max-w-xl mx-auto px-4">
            {{ announcement }}
          </p>
          <p v-else
            class="text-base sm:text-lg md:text-xl text-white/40 mb-8 leading-relaxed font-light tracking-wide max-w-xl mx-auto px-4">
            {{ $t('home.description') }}
          </p>
        </div>

        <!-- 操作按钮 -->
        <div class="fade-up-item" style="--delay: 0.6s">
          <div class="flex flex-col sm:flex-row gap-4 justify-center items-center">
            <router-link to="/register"
              class="register-button bg-gradient-to-r from-blue-500 to-purple-600 text-white font-semibold py-3 px-8 rounded-lg transition-all duration-300 shadow-lg hover:shadow-xl transform hover:-translate-y-0.5 flex items-center space-x-2 relative overflow-hidden">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"></path>
              </svg>
              <span>{{ $t('home.cta.register') }}</span>
            </router-link>

            <router-link to="/admin"
              class="glass-button text-white hover:text-blue-300 transition-colors duration-300 flex items-center space-x-2 py-3 px-8 rounded-lg">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z">
                </path>
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
              </svg>
              <span>{{ $t('nav.admin') }}</span>
            </router-link>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部渐变遮罩 -->
    <div class="absolute inset-0 bg-gradient-to-t from-[#030303] via-transparent to-[#030303]/80 pointer-events-none" />
  </div>
</template>

<script setup lang="ts">
import { inject, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const config = inject('config', { value: {} as any })

interface Props {
  badge?: string
  title1?: string
  title2?: string
}

const props = withDefaults(defineProps<Props>(), {
  badge: 'VerifyMC',
  title1: 'Welcome to join',
  title2: 'VerifyMC'
})

// 使用配置中的服务器名称
const serverName = computed(() => config.value?.frontend?.web_server_prefix)

const displayTitle1 = computed(() => {
  return props.title1 || t('home.welcome')
})

const displayTitle2 = computed(() => serverName.value)

// 获取announcement
const announcement = computed(() => {
  return config.value?.frontend?.announcement || ''
})

// 监听配置变化
watch(() => config.value?.frontend?.web_server_prefix, (newPrefix) => {
  if (newPrefix) {
    // Optimized: removed empty block
  }
}, { immediate: true })
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Pacifico&display=swap');

.font-pacifico {
  font-family: 'Pacifico', cursive;
}

/* 简洁背景样式 */

/* 内容动画 */
.fade-up-item {
  opacity: 0;
  transform: translateY(30px);
  animation: fadeUp 1s cubic-bezier(0.25, 0.4, 0.25, 1) forwards;
  animation-delay: calc(0.5s + var(--delay));
}

@keyframes fadeUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 注册按钮特效 */
.register-button {
  position: relative;
  overflow: hidden;
}

.register-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.6s ease;
}

.register-button:hover::before {
  left: 100%;
}

.register-button:hover {
  background: linear-gradient(to right, #3b82f6, #8b5cf6) !important;
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(59, 130, 246, 0.3);
}

/* Glass button 样式 */
.glass-button {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

/* 手机端适配 */
@media (max-width: 768px) {
  .fade-up-item h1 {
    font-size: 2.5rem;
  }

  .fade-up-item p {
    font-size: 1rem;
  }
}

/* 只在用户明确要求减少动画时才禁用 */
@media (prefers-reduced-motion: reduce) {
  .fade-up-item {
    animation: none;
    opacity: 1;
    transform: translateY(0);
  }
}

/* 确保在移动端也能正确显示 */
@supports (-webkit-touch-callout: none) {
  .relative.min-h-screen {
    min-height: -webkit-fill-available;
  }
}
</style>