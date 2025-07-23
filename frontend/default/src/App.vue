<template>
  <div class="min-h-screen flex flex-col">
    <!-- Top Navigation -->
    <nav class="fixed top-0 left-0 right-0 z-40 bg-white/80 backdrop-blur-lg border-b border-gray-200 shadow-sm">
      <div class="max-w-4xl mx-auto px-2 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-14">
          <!-- Logo & Server Name -->
          <div class="flex items-center">
            <img v-if="config?.frontend?.logo_url" :src="config.frontend.logo_url" alt="logo" class="h-8 w-8 rounded mr-2" />
            <router-link to="/" class="text-xl sm:text-2xl font-bold text-blue-600 cursor-pointer">{{ config?.frontend?.web_server_prefix || 'VerifyMC' }}</router-link>
          </div>
          <!-- Desktop Menu -->
          <div class="hidden md:flex items-center gap-2">
            <router-link to="/register" class="hover:text-blue-500 cursor-pointer px-3 py-1 text-base font-semibold" active-class="text-blue-700 font-bold">{{ $t('nav.register') }}</router-link>
            <router-link to="/admin" class="hover:text-blue-500 cursor-pointer px-3 py-1 text-base font-semibold" active-class="text-blue-700 font-bold">{{ $t('nav.admin') }}</router-link>
            <LanguageSwitcher />
            <a href="https://github.com/KiteMC/VerifyMC/" target="_blank" rel="noopener" class="ml-2 flex items-center justify-center w-8 h-8 rounded hover:bg-gray-100 transition" title="GitHub">
              <svg viewBox="0 0 24 24" fill="currentColor" class="w-6 h-6 text-gray-700"><path d="M12 2C6.477 2 2 6.484 2 12.021c0 4.428 2.865 8.184 6.839 9.504.5.092.682-.217.682-.482 0-.237-.009-.868-.014-1.703-2.782.605-3.369-1.342-3.369-1.342-.454-1.157-1.11-1.465-1.11-1.465-.908-.62.069-.608.069-.608 1.004.07 1.532 1.032 1.532 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.339-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.025A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.295 2.748-1.025 2.748-1.025.546 1.378.202 2.397.1 2.65.64.7 1.028 1.595 1.028 2.688 0 3.847-2.338 4.695-4.566 4.944.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.749 0 .267.18.579.688.481C19.138 20.2 22 16.448 22 12.021 22 6.484 17.523 2 12 2z"/></svg>
            </a>
          </div>
          <!-- Mobile Menu Button -->
          <div class="md:hidden flex items-center">
            <button @click="mobileMenuOpen = !mobileMenuOpen" class="p-2 rounded-md text-gray-700 hover:bg-gray-200 transition-colors duration-200" aria-label="Toggle mobile menu">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path v-if="!mobileMenuOpen" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
                <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
        <!-- Mobile Menu -->
        <div v-show="mobileMenuOpen" class="md:hidden border-t border-gray-200 bg-white/95 backdrop-blur-lg">
          <div class="px-2 pt-2 pb-3 space-y-1 flex flex-col">
            <router-link to="/register" class="hover:text-blue-500 cursor-pointer px-3 py-2 text-base font-semibold" active-class="text-blue-700 font-bold">{{ $t('nav.register') }}</router-link>
            <router-link to="/admin" class="hover:text-blue-500 cursor-pointer px-3 py-2 text-base font-semibold" active-class="text-blue-700 font-bold">{{ $t('nav.admin') }}</router-link>
            <LanguageSwitcher />
            <a href="https://github.com/KiteMC/VerifyMC/" target="_blank" rel="noopener" class="flex items-center justify-center w-8 h-8 rounded hover:bg-gray-100 transition mt-2" title="GitHub">
              <svg viewBox="0 0 24 24" fill="currentColor" class="w-6 h-6 text-gray-700"><path d="M12 2C6.477 2 2 6.484 2 12.021c0 4.428 2.865 8.184 6.839 9.504.5.092.682-.217.682-.482 0-.237-.009-.868-.014-1.703-2.782.605-3.369-1.342-3.369-1.342-.454-1.157-1.11-1.465-1.11-1.465-.908-.62.069-.608.069-.608 1.004.07 1.532 1.032 1.532 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.339-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.025A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.295 2.748-1.025 2.748-1.025.546 1.378.202 2.397.1 2.65.64.7 1.028 1.595 1.028 2.688 0 3.847-2.338 4.695-4.566 4.944.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.749 0 .267.18.579.688.481C19.138 20.2 22 16.448 22 12.021 22 6.484 17.523 2 12 2z"/></svg>
            </a>
          </div>
        </div>
      </div>
    </nav>
    <main class="flex-1 flex flex-col items-center justify-center p-2 sm:p-4 pt-16">
      <router-view />
    </main>
    <footer class="bg-white text-center text-gray-400 py-2 text-xs sm:text-sm border-t">VerifyMC © 2025</footer>
    <Toast ref="toastRef" />
  </div>
</template>

<script setup lang="ts">
import { inject, provide, ref, h, getCurrentInstance, watch } from 'vue'
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import Toast from './components/Toast.vue'
const config = inject('config', { value: {} })
const toastRef = ref()
const mobileMenuOpen = ref(false)

// 监听配置变化，动态设置页面标题
watch(() => config.value?.frontend?.web_server_prefix, (newPrefix: string | undefined) => {
  if (newPrefix) {
    document.title = newPrefix
  } else {
    document.title = 'VerifyMC'
  }
}, { immediate: true })

provide('showToast', (msg: string, type = 'info') => toastRef.value?.showToast(msg, type))
</script>

<style scoped>
</style> 