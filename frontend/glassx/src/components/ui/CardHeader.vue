<template>
  <div
    :class="[
      'flex flex-col space-y-1.5 p-6',
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