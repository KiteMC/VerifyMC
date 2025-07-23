<template>
  <div
    :class="[
      'text-2xl font-semibold leading-none tracking-tight text-white',
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