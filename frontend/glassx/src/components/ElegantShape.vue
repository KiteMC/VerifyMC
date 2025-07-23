<template>
  <div
    :class="['absolute', className]"
    :style="{
      transform: `translateY(${translateY}px) rotate(${currentRotate}deg)`,
      opacity: opacity,
      transition: `all 2.4s ease-out ${delay}s`
    }"
  >
    <div
      :style="{
        width: `${width}px`,
        height: `${height}px`,
        transform: `translateY(${floatY}px)`,
        transition: 'transform 12s ease-in-out infinite'
      }"
      class="relative"
    >
      <div
        :class="[
          'absolute inset-0 rounded-full',
          'bg-gradient-to-r to-transparent',
          gradient,
          'backdrop-blur-[2px] border-2 border-white/[0.15]',
          'shadow-[0_8px_32px_0_rgba(255,255,255,0.1)]',
          'after:absolute after:inset-0 after:rounded-full',
          'after:bg-[radial-gradient(circle_at_50%_50%,rgba(255,255,255,0.2),transparent_70%)]'
        ]"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'

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

const opacity = ref(0)
const translateY = ref(-150)
const currentRotate = ref(props.rotate - 15)
const floatY = ref(0)

onMounted(() => {
  // 初始动画
  setTimeout(() => {
    opacity.value = 1
    translateY.value = 0
    currentRotate.value = props.rotate
  }, props.delay * 1000)

  // 浮动动画
  let direction = 1
  setInterval(() => {
    if (floatY.value >= 15) {
      direction = -1
    } else if (floatY.value <= 0) {
      direction = 1
    }
    floatY.value += direction * 0.5
  }, 50)
})
</script> 