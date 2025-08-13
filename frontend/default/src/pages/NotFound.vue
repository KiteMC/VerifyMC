<template>
  <!-- Minimalist 404 error page -->
  <div class="min-h-screen flex items-center justify-center px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full text-center">
      <div class="fade-in">
        <!-- Error illustration -->
        <div class="mx-auto h-24 w-24 flex items-center justify-center rounded-full bg-gray-100 mb-8">
          <svg class="h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.172 16.172a4 4 0 015.656 0M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
          </svg>
        </div>
        
        <!-- Error code -->
        <div class="text-6xl font-bold text-gray-300 mb-4">404</div>
        
        <!-- Error message -->
        <h1 class="text-2xl font-semibold text-gray-900 mb-4">{{ $t('error.404.title') }}</h1>
        <p class="text-gray-600 mb-8">{{ $t('error.404.desc') }}</p>
        
        <!-- Action button -->
        <router-link to="/" class="btn-primary inline-flex items-center">
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
          </svg>
          {{ $t('error.backHome') }}
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
</script> 