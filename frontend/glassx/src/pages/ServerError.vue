<template>
  <div class="server-error">
    <div class="glass-card">
      <h1>500</h1>
      <h2>{{ $t('errors.500.title') }}</h2>
      <p>{{ $t('errors.500.message') }}</p>
      <router-link to="/" class="btn-primary">
        {{ $t('common.back_home') }}
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
// 500 Server Error page
</script>

<style scoped>
.server-error {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 2rem;
}

.glass-card {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 3rem;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  max-width: 500px;
}

h1 {
  font-size: 6rem;
  color: white;
  margin: 0;
  font-weight: 700;
}

h2 {
  font-size: 2rem;
  color: white;
  margin: 1rem 0;
  font-weight: 600;
}

p {
  color: rgba(255, 255, 255, 0.8);
  margin: 1.5rem 0;
  font-size: 1.1rem;
}

.btn-primary {
  display: inline-block;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  padding: 12px 24px;
  border-radius: 10px;
  text-decoration: none;
  font-weight: 500;
  transition: all 0.3s ease;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.btn-primary:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateY(-2px);
}
</style> 