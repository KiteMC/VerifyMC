<template>
  <nav class="fixed top-0 left-0 right-0 z-40 w-full bg-[#151b22] border-b border-[#2b3540]">
    
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="relative flex justify-between items-center h-16">
        <!-- Logo -->
        <div class="flex items-center">
          <Button
            v-if="showTrigger"
            variant="ghost"
            size="icon"
            class="mr-2 text-white/70 hover:text-white hover:bg-white/10"
            @click="toggleSidebar"
          >
            <Menu class="w-5 h-5" />
          </Button>
          <router-link to="/" class="logo-link group">
            <span v-if="serverName !== undefined" class="logo-text">{{ serverName }}</span>
          </router-link>
        </div>

        <!-- Desktop Navigation Links -->
        <div class="hidden md:flex items-center absolute left-1/2 top-1/2 transform -translate-x-1/2 -translate-y-1/2">
          <AnimatedMenuBar />
        </div>

        <!-- Language Switcher -->
        <div class="flex items-center gap-2">
          <LanguageSwitcher class="text-white" />
          
          <!-- Mobile Menu Button -->
          <Button
            variant="ghost"
            size="icon"
            @click="toggleMobileMenu"
            class="md:hidden text-white"
            aria-label="Toggle mobile menu"
            title="Toggle mobile menu"
          >
            <svg
              class="w-6 h-6 transition-transform duration-300"
              :class="{ 'rotate-90': mobileMenuOpen }"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                v-if="!mobileMenuOpen"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M4 6h16M4 12h16M4 18h16"
              />
              <path
                v-else
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </Button>
        </div>
      </div>

      <!-- Mobile Menu with animation -->
      <transition name="slide-fade">
        <div
          v-show="mobileMenuOpen"
          class="md:hidden border-t border-[#2b3540] bg-[#171d24]"
        >
          <div class="px-3 pt-3 pb-4 space-y-2">
            <AnimatedMenuBar :vertical="true" />
          </div>
        </div>
      </transition>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { inject, computed, ref, type Ref, onMounted, onUnmounted } from 'vue'
import LanguageSwitcher from './LanguageSwitcher.vue'
import AnimatedMenuBar from './AnimatedMenuBar.vue'
import Button from '@/components/ui/Button.vue'
import { Menu } from 'lucide-vue-next'
import { useSidebar } from '@/composables/useSidebar'
import type { AppConfig } from '@/types'

const config = inject<Ref<AppConfig>>('config', ref({}))
const { toggleOpen, toggleCollapse, showTrigger, setCollapse } = useSidebar()

const mobileMenuOpen = ref(false)
const isMobile = ref(false)

const serverName = computed(() => config.value?.webServerPrefix)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 1024
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

const toggleSidebar = () => {
  if (isMobile.value) {
    setCollapse(false)
    toggleOpen()
  } else {
    toggleCollapse()
  }
}

const toggleMobileMenu = () => {
  mobileMenuOpen.value = !mobileMenuOpen.value
}
</script>

<style scoped>
/* Navigation container - replaced by Tailwind classes */

/* Logo styling */
.logo-link {
  display: flex;
  align-items: center;
  text-decoration: none;
  transition: all 0.3s ease;
}

.logo-text {
  font-size: 1.25rem;
  font-weight: 700;
  color: #fff;
  color: #f3f4f6;
  transition: all 0.3s ease;
}

.logo-link:hover .logo-text {
  color: #93c5fd;
}

/* Slide animation for mobile menu */
.slide-fade-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .slide-fade-enter-active,
  .slide-fade-leave-active {
    transition: none;
  }
}
</style>
