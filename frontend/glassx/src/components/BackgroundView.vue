<script lang="ts" setup>
import { ref, onMounted, onUnmounted, useTemplateRef } from 'vue'

interface Star {
  x: number
  y: number
  size: number
  opacity: number
  blinkSpeed: number
  blinkDirection: number
}

// 检测设备类型
const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)

const canvasRef = useTemplateRef<HTMLCanvasElement>('canvas')
const starsRef = ref<Star[]>([])
const animationRef = ref<number>()
const lastWidthRef = ref<number>(0)
const lastHeightRef = ref<number>(0)
const initialHeightRef = ref<number>(0)
const resizeTimeoutRef = ref<NodeJS.Timeout | null>(null)

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  // 生成星星
  const generateStars = () => {
    if (!canvas) return
    // 移动端减少星星密度
    const densityFactor = isMobile ? 1500 : 1000
    const starCount = Math.floor((canvas.width * canvas.height) / densityFactor)
    const stars: Star[] = []

    for (let i = 0; i < starCount; i++) {
      stars.push({
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        size: Math.random() * (isMobile ? 1.5 : 2) + 0.5,
        opacity: Math.random(),
        blinkSpeed: Math.random() * 0.02 + 0.005,
        blinkDirection: Math.random() > 0.5 ? 1 : -1,
      })
    }

    starsRef.value = stars
  }

  // 存储初始窗口高度
  if (initialHeightRef.value === 0) {
    initialHeightRef.value = window.innerHeight

    // 设置初始画布大小为固定高度
    canvas.height = initialHeightRef.value
    canvas.width = window.innerWidth

    lastWidthRef.value = window.innerWidth
    lastHeightRef.value = initialHeightRef.value

    generateStars()
  }

  // 绘制星星
  const drawStars = () => {
    if (!ctx || !canvas) return

    ctx.clearRect(0, 0, canvas.width, canvas.height)

    starsRef.value.forEach((star) => {
      // 更新星星闪烁
      star.opacity += star.blinkSpeed * star.blinkDirection

      // 当透明度达到极限时改变方向
      if (star.opacity >= 1 || star.opacity <= 0.1) {
        star.blinkDirection *= -1
      }

      // 绘制星星
      ctx.beginPath()
      ctx.arc(star.x, star.y, star.size, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(255, 255, 255, ${star.opacity})`
      ctx.fill()
    })

    animationRef.value = requestAnimationFrame(drawStars)
  }

  // 只处理宽度变化，完全忽略高度变化
  const handleResize = () => {
    if (resizeTimeoutRef.value) {
      clearTimeout(resizeTimeoutRef.value)
    }

    resizeTimeoutRef.value = setTimeout(() => {
      // 只有当宽度显著变化时才更新
      if (Math.abs(window.innerWidth - lastWidthRef.value) > 50) {
        canvas.width = window.innerWidth
        // 保持高度固定为初始高度
        canvas.height = initialHeightRef.value

        lastWidthRef.value = window.innerWidth
        // 高度保持不变

        generateStars()
      }
    }, isMobile ? 500 : 250)
  }

  // 初始化
  window.addEventListener('resize', handleResize)
  drawStars()

  console.log('Canvas blinking stars initialized:', {
    width: canvas.width,
    height: canvas.height,
    starCount: starsRef.value.length,
    isMobile
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeTimeoutRef.value) {
    clearTimeout(resizeTimeoutRef.value)
  }
  if (animationRef.value) {
    cancelAnimationFrame(animationRef.value)
  }
})

// 处理窗口大小改变
const handleResize = () => {
  if (resizeTimeoutRef.value) {
    clearTimeout(resizeTimeoutRef.value)
  }

  resizeTimeoutRef.value = setTimeout(() => {
    const canvas = canvasRef.value
    if (!canvas) return

    // 只有当宽度显著变化时才更新
    if (Math.abs(window.innerWidth - lastWidthRef.value) > 50) {
      canvas.width = window.innerWidth
      // 保持高度固定为初始高度
      canvas.height = initialHeightRef.value

      lastWidthRef.value = window.innerWidth

      // 重新生成星星
      const ctx = canvas.getContext('2d')
      if (ctx) {
        const densityFactor = isMobile ? 1500 : 1000
        const starCount = Math.floor((canvas.width * canvas.height) / densityFactor)
        const stars: Star[] = []

        for (let i = 0; i < starCount; i++) {
          stars.push({
            x: Math.random() * canvas.width,
            y: Math.random() * canvas.height,
            size: Math.random() * (isMobile ? 1.5 : 2) + 0.5,
            opacity: Math.random(),
            blinkSpeed: Math.random() * 0.02 + 0.005,
            blinkDirection: Math.random() > 0.5 ? 1 : -1,
          })
        }

        starsRef.value = stars
      }
    }
  }, isMobile ? 500 : 250)
}
</script>

<template>
  <div class="blinking-stars-background">
    <!-- Canvas星空背景 -->
    <canvas
      ref="canvas"
      class="stars-canvas"
      :style="{ height: initialHeightRef + 'px' }"
      aria-hidden="true"
    />
    
    <!-- 内容插槽 -->
    <slot></slot>
  </div>
</template>

<style scoped>
.blinking-stars-background {
  position: fixed;
  width: 100%;
  height: 100%;
  background: linear-gradient(-225deg, #030303 0%, #1a1a2e 30%, #16213e 60%, #0f3460 100%);
  z-index: -1;
  overflow: hidden;
}

.stars-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  pointer-events: none;
}

/* 移动端优化 */
@media (max-width: 768px) {
  .blinking-stars-background {
    /* 移动端使用更简单的背景 */
    background: linear-gradient(-225deg, #030303 0%, #1a1a2e 50%, #16213e 100%);
  }
}

/* 减少动画偏好的用户 */
@media (prefers-reduced-motion: reduce) {
  .stars-canvas {
    display: none;
  }
}

/* 低性能设备优化 */
@media (max-width: 480px), (max-height: 600px) {
  .blinking-stars-background {
    /* 超小屏幕使用纯色背景 */
    background: #1a1a2e;
  }
  
  .stars-canvas {
    display: none;
  }
}
</style>