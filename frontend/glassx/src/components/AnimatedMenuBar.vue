<template>
  <nav class="p-2 rounded-2xl bg-gradient-to-b from-white/10 to-white/5 backdrop-blur-lg border border-white/20 shadow-lg relative overflow-hidden">
    <ul :class="['flex items-center gap-2 relative z-10', $attrs.class]">
      <li v-for="item in menuItems" :key="item.label" class="relative">
        <div class="block rounded-xl overflow-visible group relative" style="perspective: 600px">
          <div 
            class="absolute inset-0 z-0 pointer-events-none rounded-xl opacity-0 group-hover:opacity-100 transition-all duration-500 scale-80 group-hover:scale-200"
            :style="{ background: item.gradient }"
          />
          <router-link
            :to="item.href"
            class="flex items-center gap-2 px-4 py-2 relative z-10 bg-transparent text-white/70 group-hover:text-white transition-colors rounded-xl"
            style="transform-style: preserve-3d; transform-origin: center bottom"
          >
            <span :class="`transition-colors duration-300 group-hover:${item.iconColor} text-white`">
              <component :is="item.icon" class="w-5 h-5" />
            </span>
            <span>{{ item.label }}</span>
          </router-link>
        </div>
      </li>
    </ul>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Home, UserPlus, LogIn, Settings } from 'lucide-vue-next'

const { t } = useI18n()

const menuItems = computed(() => [
  {
    icon: Home,
    label: t('nav.home'),
    href: '/',
    gradient: 'radial-gradient(circle, rgba(59,130,246,0.15) 0%, rgba(37,99,235,0.06) 50%, rgba(29,78,216,0) 100%)',
    iconColor: 'text-blue-500',
  },
  {
    icon: UserPlus,
    label: t('nav.register'),
    href: '/register',
    gradient: 'radial-gradient(circle, rgba(249,115,22,0.15) 0%, rgba(234,88,12,0.06) 50%, rgba(194,65,12,0) 100%)',
    iconColor: 'text-orange-500',
  },
  {
    icon: LogIn,
    label: t('nav.login'),
    href: '/login',
    gradient: 'radial-gradient(circle, rgba(34,197,94,0.15) 0%, rgba(22,163,74,0.06) 50%, rgba(21,128,61,0) 100%)',
    iconColor: 'text-green-500',
  },
  {
    icon: Settings,
    label: t('nav.admin'),
    href: '/admin',
    gradient: 'radial-gradient(circle, rgba(239,68,68,0.15) 0%, rgba(220,38,38,0.06) 50%, rgba(185,28,28,0) 100%)',
    iconColor: 'text-red-500',
  },
])
</script>

<style scoped>
.bg-gradient-radial {
  background: radial-gradient(circle, var(--tw-gradient-stops));
}

.rotate-x-0 {
  transform: rotateX(0deg);
}

.rotate-x-90 {
  transform: rotateX(90deg);
}
</style> 