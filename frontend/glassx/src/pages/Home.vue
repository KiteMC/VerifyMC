<template>
  <div class="home-page">
    <!-- Hero Section -->
    <HeroGeometric 
      v-if="serverName !== undefined"
      :title1="$t('home.welcome')"
      :title2="serverName"
    />
  </div>
</template>

<script setup lang="ts">
import { inject, computed } from 'vue'
import HeroGeometric from '@/components/HeroGeometric.vue'

const config = inject('config', { value: {} as any })

const serverName = computed(() => config.value?.frontend?.web_server_prefix)
</script>

<style scoped>
.home-page {
  position: relative;
  min-height: 100vh;
  width: 100%;
  overflow: hidden;
  /* 移除任何可能覆盖背景的样式 */
  background: transparent;
}

.gradient-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.glass-button {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 8px 16px;
  font-weight: 500;
}

/* 确保在移动端也能正确显示 */
@supports (-webkit-touch-callout: none) {
  .home-page {
    min-height: -webkit-fill-available;
  }
}
</style>
