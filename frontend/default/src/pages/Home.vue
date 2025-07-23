<template>
  <div class="w-full max-w-2xl mx-auto text-center py-12 px-4 sm:px-6 bg-white shadow-xl rounded-2xl flex flex-col items-center mt-12 sm:mt-24 transition-transform duration-300 hover:scale-105">
    <div class="text-5xl mb-4">🎉</div>
    <h1 class="text-2xl sm:text-3xl font-bold mb-4 text-blue-600 cursor-default">{{ $t('home.welcome') }}</h1>
    <p v-if="serverName !== undefined" class="text-base sm:text-lg text-gray-700 mb-2">
      {{ serverName }}
    </p>
    <p v-if="config?.value?.frontend?.announcement" class="text-base sm:text-lg text-gray-500 mb-8">{{ config.value.frontend.announcement }}</p>
    <router-link to="/register" class="inline-block w-full sm:w-auto bg-blue-600 text-white px-4 sm:px-6 py-3 rounded-lg shadow hover:bg-blue-700 transition cursor-pointer text-base sm:text-lg font-semibold">{{ $t('home.registerBtn') }}</router-link>
  </div>
</template>

<script setup lang="ts">
import { inject, computed } from 'vue'

const config = inject('config', { value: {} as any })
const serverName = computed(() => config.value?.frontend?.web_server_prefix)
</script> 