<template>
  <!-- Minimalist home page design -->
  <div class="min-h-screen flex items-center justify-center px-4 sm:px-6 lg:px-8">
    <div class="max-w-2xl w-full space-y-10 text-center">
      <!-- Logo/Icon section -->
      <div class="fade-in">
        <div class="mx-auto h-20 w-20 flex items-center justify-center rounded-full bg-gray-100 mb-8">
          <svg class="h-10 w-10 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z">
            </path>
          </svg>
        </div>

        <!-- Server name -->
        <h1 class="page-header text-center">
          {{ serverName || $t('home.welcome') }}
        </h1>

        <!-- Announcement -->
        <p v-if="config?.value?.frontend?.announcement" class="text-gray-600 mb-10 slide-up text-lg">
          {{ config.value.frontend.announcement }}
        </p>
        <p v-else class="text-gray-600 mb-10 slide-up text-lg">
          {{ $t('home.desc') }}
        </p>
      </div>

      <!-- Action section -->
      <div class="space-y-6 scale-in">
        <div class="flex justify-center">
          <router-link to="/register"
            class="btn-primary inline-flex justify-center items-center text-lg w-64 px-8 py-4">
            <svg class="w-6 h-6 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"></path>
            </svg>
            {{ $t('home.registerBtn') }}
          </router-link>
        </div>

        <!-- Secondary actions -->
        <div class="flex justify-center">
          <router-link to="/admin" class="btn-outline inline-flex justify-center items-center text-lg w-64 px-8 py-4">
            <svg class="w-5 h-5 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z">
              </path>
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
            </svg>
            {{ $t('nav.admin') }}
          </router-link>
        </div>
      </div>

      <!-- Footer info -->
      <div class="text-xs text-gray-400 mt-8 fade-in">
        Powered by VerifyMC
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { inject, computed } from 'vue'

const config = inject('config', { value: {} as any })
const serverName = computed(() => config.value?.frontend?.web_server_prefix)
</script>