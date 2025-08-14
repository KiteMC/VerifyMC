<template>
  <div class="admin-panel">
    <div class="glass-card">
      <AdminPanelNew />
    </div>
  </div>
</template>

<script setup lang="ts">
import AdminPanelNew from '../components/AdminPanelNew.vue'
</script>

<style scoped>
.admin-panel {
  min-height: 100vh;
  padding: 1rem;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background: #030303;
}

@media (min-width: 768px) {
  .admin-panel {
    padding: 2rem;
  }
}

.glass-card {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 1rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  width: 100%;
  max-width: 1200px;
}

@media (min-width: 768px) {
  .glass-card {
    padding: 2rem;
  }
}
</style> 