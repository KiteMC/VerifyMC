<template>
  <!-- Minimalist 500 error page -->
  <div class="min-h-screen flex items-center justify-center px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full text-center">
      <div class="fade-in">
        <!-- Error illustration -->
        <div class="mx-auto h-24 w-24 flex items-center justify-center rounded-full bg-red-100 mb-8">
          <svg class="h-12 w-12 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
          </svg>
        </div>
        
        <!-- Error code -->
        <div class="text-6xl font-bold text-gray-300 mb-4">500</div>
        
        <!-- Error message -->
        <h1 class="text-2xl font-semibold text-gray-900 mb-4">{{ $t('error.500.title') }}</h1>
        <p class="text-gray-600 mb-8">{{ $t('error.500.desc') }}</p>
        
        <!-- Action buttons -->
        <div class="space-y-3">
          <button @click="$router.go(-1)" class="btn-outline w-full inline-flex items-center justify-center">
            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
            </svg>
            {{ $t('common.back') }}
          </button>
          <router-link to="/" class="btn-primary w-full inline-flex items-center justify-center">
            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"></path>
            </svg>
            {{ $t('error.backHome') }}
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
</script> 