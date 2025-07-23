<template>
  <div
    :class="[
      'rounded-lg border bg-card text-card-foreground shadow-sm',
      className
    ]"
    v-bind="$attrs"
  >
    <slot />
  </div>
</template>

<script setup lang="ts">
interface Props {
  className?: string
}

defineProps<Props>()
</script>

<style scoped>
.bg-card {
  background-color: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
}

.text-card-foreground {
  color: white;
}

.border {
  border: 1px solid rgba(255, 255, 255, 0.2);
}
</style> 