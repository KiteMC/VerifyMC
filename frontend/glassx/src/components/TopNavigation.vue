<template>
  <nav class="fixed top-0 left-0 right-0 z-40 bg-white/10 backdrop-blur-lg border-b border-white/20">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex justify-between items-center h-16">
        <!-- Logo -->
        <div class="flex items-center">
          <router-link to="/" class="text-xl font-bold text-white">
            <span v-if="serverName !== undefined">{{ serverName }}</span>
          </router-link>
        </div>

        <!-- Desktop Navigation Links -->
        <div class="hidden md:flex items-center">
          <AnimatedMenuBar />
        </div>

        <!-- Language Switcher -->
        <div class="flex items-center">
          <LanguageSwitcher class="text-white" />
          
          <!-- Mobile Menu Button -->
          <button
            @click="toggleMobileMenu"
            class="md:hidden ml-4 p-2 rounded-md text-white hover:bg-white/10 transition-colors duration-200"
            aria-label="Toggle mobile menu"
          >
            <svg
              class="w-6 h-6"
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
          </button>
        </div>
      </div>

      <!-- Mobile Menu -->
      <div
        v-show="mobileMenuOpen"
        class="md:hidden border-t border-white/20 bg-white/5 backdrop-blur-lg"
      >
        <div class="px-2 pt-2 pb-3 space-y-1">
          <AnimatedMenuBar class="flex-col space-y-2" />
        </div>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { inject, computed, ref } from 'vue'
import LanguageSwitcher from './LanguageSwitcher.vue'
import AnimatedMenuBar from './AnimatedMenuBar.vue'

const config = inject('config', { value: {} as any })
const mobileMenuOpen = ref(false)

const serverName = computed(() => config.value?.frontend?.web_server_prefix)

const toggleMobileMenu = () => {
  mobileMenuOpen.value = !mobileMenuOpen.value
}
</script> 