<template>
  <div class="relative min-h-screen w-full flex items-center justify-center overflow-hidden">
    <!-- Main content -->
    <div class="relative z-10 container mx-auto px-4 md:px-6">
      <div class="max-w-3xl mx-auto text-center">
        <!-- Badge -->
        <div class="fade-up-item" style="--delay: 0s">
          <div
            class="github-badge inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/5 border border-white/10 mb-8 md:mb-12 backdrop-blur-sm">
            <a href="https://github.com/KiteMC/VerifyMC" target="_blank" rel="noopener noreferrer"
              class="inline-flex items-center gap-2 text-white/70 hover:text-white transition-all duration-300">
              <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                <path
                  d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
              </svg>
              <span class="text-sm font-medium tracking-wide">GitHub</span>
            </a>
            <div class="version-badge inline-flex items-center gap-2">
              <span>v{{ currentVersion }}</span>
              <a
                v-if="updateAvailable"
                :href="releasesUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="update-indicator"
                :title="$t('version.upgrade_available')"
                :aria-label="$t('version.upgrade_available')"
              >
                <ArrowUpCircle class="w-4 h-4" />
              </a>
            </div>
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

        <!-- Action buttons -->
        <div class="fade-up-item" style="--delay: 0.6s">
          <div class="flex flex-col sm:flex-row gap-4 justify-center items-center">
            <router-link v-if="!isAdminLoggedIn" to="/register" class="register-button group">
              <span class="button-glow"></span>
              <span class="button-content">
                <svg class="w-5 h-5 transition-transform duration-300 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"></path>
                </svg>
                <span>{{ $t('home.cta.register') }}</span>
              </span>
              <span class="button-shine"></span>
            </router-link>

            <router-link :to="secondaryAction.href" class="glass-button group">
              <component
                :is="isAdminLoggedIn ? Settings : LogIn"
                class="w-5 h-5 transition-transform duration-300"
                :class="{ 'group-hover:rotate-90': isAdminLoggedIn, 'group-hover:translate-x-1': !isAdminLoggedIn }"
              />
              <span>{{ secondaryAction.label }}</span>
            </router-link>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部渐变遮罩 (softened to let fixed background show) -->
    <div class="absolute inset-0 bg-gradient-to-t from-[#030303]/60 via-transparent to-transparent pointer-events-none" />
  </div>
</template>

<script setup lang="ts">
import { inject, computed, watch, ref, onMounted, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { sessionService } from '@/services/session'
import { ArrowUpCircle, LogIn, Settings } from 'lucide-vue-next'
import { apiService } from '@/services/api'

const { t } = useI18n()

interface AppConfig {
  webServerPrefix?: string
  announcement?: string
}

const config = inject<Ref<AppConfig>>('config', ref({}))

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
const serverName = computed(() => config.value?.webServerPrefix)

const displayTitle1 = computed(() => {
  return props.title1 || t('home.welcome')
})

const displayTitle2 = computed(() => serverName.value)

// 获取announcement
const announcement = computed(() => {
  return config.value?.announcement || ''
})

const currentVersion = ref('1.8.0')
const updateAvailable = ref(false)
const releasesUrl = ref('https://github.com/KiteMC/VerifyMC/releases')

onMounted(async () => {
  try {
    const response = await apiService.checkVersion()
    if (response.success) {
      currentVersion.value = response.currentVersion || currentVersion.value
      updateAvailable.value = response.updateAvailable === true
      releasesUrl.value = response.releasesUrl || releasesUrl.value
    }
  } catch {
    // The version badge keeps the bundled version when the server is offline.
  }
})

const isAdminLoggedIn = computed(() => {
  return sessionService.isAuthenticated()
})

const secondaryAction = computed(() => {
  if (isAdminLoggedIn.value) {
    return {
      href: '/admin',
      label: t('nav.admin')
    }
  }

  return {
    href: '/login',
    label: t('nav.login')
  }
})

// 监听配置变化
watch(() => config.value?.webServerPrefix, (newPrefix) => {
  if (newPrefix) {
    // Config loaded
  }
}, { immediate: true })
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Pacifico&display=swap');

.font-pacifico {
  font-family: 'Pacifico', cursive;
}

/* Floating orbs for ambient effect */
.floating-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
  animation: float 20s ease-in-out infinite;
}

.orb-1 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  top: -10%;
  left: -5%;
  animation-delay: 0s;
}

.orb-2 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  bottom: 10%;
  right: -5%;
  animation-delay: -7s;
}

.orb-3 {
  width: 250px;
  height: 250px;
  background: linear-gradient(135deg, #06b6d4, #3b82f6);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -14s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  25% {
    transform: translate(30px, -30px) scale(1.05);
  }
  50% {
    transform: translate(-20px, 20px) scale(0.95);
  }
  75% {
    transform: translate(20px, 30px) scale(1.02);
  }
}

/* GitHub badge styling */
.github-badge {
  transition: all 0.3s ease;
}

.github-badge:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

.version-badge {
  padding: 0.35rem 0.65rem;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.48);
  color: rgba(255, 255, 255, 0.82);
  font-size: 0.75rem;
  font-weight: 600;
}

.update-indicator {
  display: inline-flex;
  color: #fbbf24;
  transition: color 0.2s ease, transform 0.2s ease;
}

.update-indicator:hover {
  color: #fde68a;
  transform: translateY(-1px);
}

/* Content animation */
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

/* Register button with glow effect */
.register-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.875rem 2rem;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  border-radius: 12px;
  color: #fff;
  font-weight: 600;
  text-decoration: none;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow:
    0 4px 15px rgba(59, 130, 246, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.register-button .button-glow {
  position: absolute;
  inset: -2px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6, #ec4899, #3b82f6);
  background-size: 300% 300%;
  border-radius: 14px;
  z-index: -1;
  opacity: 0;
  transition: opacity 0.3s ease;
  animation: gradient-rotate 3s ease infinite;
}

@keyframes gradient-rotate {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

.register-button:hover .button-glow {
  opacity: 1;
}

.register-button .button-content {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  position: relative;
  z-index: 1;
}

.register-button .button-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.3),
    transparent
  );
  z-index: 2;
}

.register-button:hover .button-shine {
  animation: shine 0.6s ease forwards;
}

@keyframes shine {
  to {
    left: 100%;
  }
}

.register-button:hover {
  transform: translateY(-3px);
  box-shadow:
    0 8px 30px rgba(59, 130, 246, 0.5),
    0 0 40px rgba(139, 92, 246, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

/* Glass button styling */
.glass-button {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.875rem 2rem;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  color: #fff;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.glass-button:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.25);
  color: #93c5fd;
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.2);
}

/* Mobile adaptation */
@media (max-width: 768px) {
  .fade-up-item h1 {
    font-size: 2.5rem;
  }

  .fade-up-item p {
    font-size: 1rem;
  }

  .floating-orb {
    display: none;
  }

  .register-button,
  .glass-button {
    width: 100%;
    justify-content: center;
  }
}

/* Reduce motion for accessibility */
@media (prefers-reduced-motion: reduce) {
  .fade-up-item {
    animation: none;
    opacity: 1;
    transform: translateY(0);
  }

  .floating-orb {
    animation: none;
  }

  .register-button .button-shine {
    display: none;
  }

  .register-button .button-glow {
    animation: none;
  }
}

/* iOS Safari support */
@supports (-webkit-touch-callout: none) {
  .relative.min-h-screen {
    min-height: -webkit-fill-available;
  }
}
</style>
