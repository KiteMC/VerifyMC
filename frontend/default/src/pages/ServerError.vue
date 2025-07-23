<template>
  <div class="flex flex-col items-center justify-center min-h-[60vh] text-center">
    <div class="bg-white rounded-2xl shadow-xl p-10 transition-transform duration-300 hover:scale-105">
      <h1 class="text-7xl font-extrabold text-red-600 mb-4">500</h1>
      <p class="text-2xl font-semibold mb-2">{{ $t('error.500.title') }}</p>
      <p class="text-gray-500 mb-6">{{ $t('error.500.desc') }}</p>
      <router-link to="/" class="text-blue-600 hover:underline">{{ $t('error.backHome') }}</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
</script> 