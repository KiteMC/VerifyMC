<template>
  <div class="relative min-h-screen w-full flex items-center justify-center overflow-hidden bg-[#030303]">
    <div class="absolute inset-0 bg-gradient-to-br from-indigo-500/[0.05] via-transparent to-rose-500/[0.05] blur-3xl" />

    <div class="absolute inset-0 overflow-hidden">
      <ElegantShape
        :delay="0.3"
        :width="600"
        :height="140"
        :rotate="12"
        gradient="from-indigo-500/[0.15]"
        class="left-[-10%] md:left-[-5%] top-[15%] md:top-[20%]"
      />

      <ElegantShape
        :delay="0.5"
        :width="500"
        :height="120"
        :rotate="-15"
        gradient="from-rose-500/[0.15]"
        class="right-[-5%] md:right-[0%] top-[70%] md:top-[75%]"
      />

      <ElegantShape
        :delay="0.4"
        :width="300"
        :height="80"
        :rotate="-8"
        gradient="from-violet-500/[0.15]"
        class="left-[5%] md:left-[10%] bottom-[5%] md:bottom-[10%]"
      />

      <ElegantShape
        :delay="0.6"
        :width="200"
        :height="60"
        :rotate="20"
        gradient="from-amber-500/[0.15]"
        class="right-[15%] md:right-[20%] top-[10%] md:top-[15%]"
      />

      <ElegantShape
        :delay="0.7"
        :width="150"
        :height="40"
        :rotate="-25"
        gradient="from-cyan-500/[0.15]"
        class="left-[20%] md:left-[25%] top-[5%] md:top-[10%]"
      />
    </div>

    <div class="relative z-10 container mx-auto px-4 md:px-6">
              <div class="max-w-3xl mx-auto text-center">
          <transition
            appear
            enter-active-class="transition-all duration-1000 ease-out"
            enter-from-class="opacity-0 translate-y-8"
            enter-to-class="opacity-100 translate-y-0"
            style="transition-delay: 0.5s"
          >
            <div>
              <!-- Github图标跳转保留 -->
            <div class="mb-8 md:mb-12">
              <a 
                href="https://github.com/KiteMC/VerifyMC" 
                target="_blank" 
                rel="noopener noreferrer"
                class="inline-flex items-center gap-2 text-white/60 hover:text-white transition-colors duration-300"
              >
                <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
                <span class="text-sm tracking-wide">GitHub</span>
              </a>
            </div>
              <!-- 主内容 -->
            <h1 class="text-4xl sm:text-6xl md:text-8xl font-bold mb-6 md:mb-8 tracking-tight">
              <span class="bg-clip-text text-transparent bg-gradient-to-b from-white to-white/80">{{ displayTitle1 }}</span>
              <br />
                <span class="bg-clip-text text-transparent bg-gradient-to-r from-indigo-300 via-white/90 to-rose-300 font-pacifico">
                {{ displayTitle2 }}
                </span>
            </h1>
              <!-- Announcement -->
              <div v-if="announcement" class="mb-8 md:mb-12">
                <p class="text-white/80 text-lg md:text-xl font-medium tracking-wide">
                  {{ announcement }}
                </p>
              </div>
          </div>
        </transition>


      </div>
    </div>

    <div class="absolute inset-0 bg-gradient-to-t from-[#030303] via-transparent to-[#030303]/80 pointer-events-none" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ElegantShape from './ElegantShape.vue'

const { t } = useI18n()
const config = inject('config', { value: {} as any })

interface Props {
  badge?: string
  title1?: string
  title2?: string
}

const props = withDefaults(defineProps<Props>(), {
  badge: 'VerifyMC',
  title1: '欢迎加入',
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
  }
}, { immediate: true })
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Pacifico&display=swap');

.font-pacifico {
  font-family: 'Pacifico', cursive;
}
</style> 