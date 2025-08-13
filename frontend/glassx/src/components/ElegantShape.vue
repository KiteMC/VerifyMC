<template>
  <div :class="['absolute shape-container', className]" :style="{
    width: `${width}px`,
    height: `${height}px`,
    '--rotate': `${rotate}deg`,
    animationDelay: `${delay}s`
  }">
    <div :class="[
      'shape-element',
      'absolute inset-0 rounded-full',
      'bg-gradient-to-r to-transparent',
      gradient,
      'backdrop-blur-[2px] border-2 border-white/[0.15]',
      'shadow-[0_8px_32px_0_rgba(255,255,255,0.1)]'
    ]" />
  </div>
</template>

<script setup lang="ts">
interface Props {
  className?: string
  delay?: number
  width?: number
  height?: number
  rotate?: number
  gradient?: string
}

const props = withDefaults(defineProps<Props>(), {
  delay: 0,
  width: 400,
  height: 100,
  rotate: 0,
  gradient: 'from-white/[0.08]'
})
</script>

<style scoped>
/* Ultra-lightweight animations for low-performance devices */
.shape-container {
  /* Hardware acceleration */
  transform: translateZ(0);
  will-change: opacity;
  
  /* Simple fade-in entrance */
  opacity: 0;
  animation: simpleEntrance 1.5s ease-out forwards;
}

.shape-element {
  /* Hardware acceleration */
  transform: translateZ(0);
  will-change: opacity;
  
  /* Minimal floating - only opacity changes */
  animation: minimalFloat 12s ease-in-out infinite;
}

/* Minimal entrance animation */
@keyframes simpleEntrance {
  0% {
    opacity: 0;
  }
  100% {
    opacity: 1;
  }
}

/* Ultra-minimal floating animation - only opacity for maximum performance */
@keyframes minimalFloat {
  0%, 100% {
    opacity: 0.7;
  }
  50% {
    opacity: 1;
  }
}

/* Disable all animations on low-performance devices */
@media (max-width: 768px), (prefers-reduced-motion: reduce) {
  .shape-container {
    animation: none;
    opacity: 0.8;
  }
  
  .shape-element {
    animation: none;
    opacity: 0.8;
  }
}

/* Apply rotation transform */
.shape-container {
  transform: rotate(var(--rotate, 0deg)) translateZ(0);
}

/* Reduce animation for users who prefer reduced motion */
@media (prefers-reduced-motion: reduce) {
  .shape-container {
    animation: none;
    opacity: 1;
    transform: rotate(var(--rotate, 0deg)) translateZ(0);
  }

  .shape-element {
    animation: none;
  }
}
</style>