<template>
  <div class="w-full max-w-2xl mx-auto py-8 sm:py-12 px-2 sm:px-8">
    <h2 class="text-xl sm:text-2xl font-bold mb-6 text-center text-blue-600 cursor-default">{{ $t('register.title') }}</h2>
    <RegistrationForm />
  </div>
</template>

<script setup lang="ts">
import RegistrationForm from '../components/RegistrationForm.vue'
</script> 