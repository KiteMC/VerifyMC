<template>
  <nav 
    class="menu-nav"
    @mouseenter="isNavHovered = true"
    @mouseleave="isNavHovered = false"
  >
    <!-- 导航发光背景 -->
    <div 
      class="nav-glow-bg"
      :class="{ 'nav-glow-active': isNavHovered }"
    />
    
    <ul class="menu-items-list">
      <li 
        v-for="(item, index) in menuItems" 
        :key="item.label" 
        class="menu-item-wrapper"
      >
        <div 
          class="menu-item-3d"
          @mouseenter="hoveredIndex = index"
          @mouseleave="hoveredIndex = null"
        >
          <!-- 项目发光效果 -->
          <div 
            class="item-glow-effect"
            :class="{ 'glow-active': hoveredIndex === index }"
            :style="{ background: item.gradient }"
          />
          
          <!-- 正面 -->
          <router-link
            :to="item.href"
            class="menu-link menu-front"
            :class="{ 
              'front-flipped': hoveredIndex === index,
              'link-active': isActive(item.href)
            }"
          >
            <span 
              class="menu-icon-wrapper"
              :class="{ [item.iconColor]: hoveredIndex === index }"
            >
              <component :is="item.icon" class="menu-icon" />
            </span>
            <span class="menu-label">{{ item.label }}</span>
          </router-link>
          
          <!-- 背面 -->
          <router-link
            :to="item.href"
            class="menu-link menu-back"
            :class="{ 
              'back-visible': hoveredIndex === index,
              'link-active': isActive(item.href)
            }"
          >
            <span 
              class="menu-icon-wrapper"
              :class="{ [item.iconColor]: hoveredIndex === index }"
            >
              <component :is="item.icon" class="menu-icon" />
            </span>
            <span class="menu-label">{{ item.label }}</span>
          </router-link>
        </div>
      </li>
    </ul>
  </nav>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { Home, UserPlus, LogIn, Settings } from 'lucide-vue-next'

const { t } = useI18n()
const route = useRoute()

const hoveredIndex = ref<number | null>(null)
const isNavHovered = ref(false)

const menuItems = computed(() => [
  {
    icon: Home,
    label: t('nav.home'),
    href: '/',
    gradient: 'radial-gradient(circle, rgba(59,130,246,0.15) 0%, rgba(37,99,235,0.06) 50%, rgba(29,78,216,0) 100%)',
    iconColor: 'text-blue-500',
  },
  {
    icon: UserPlus,
    label: t('nav.register'),
    href: '/register',
    gradient: 'radial-gradient(circle, rgba(249,115,22,0.15) 0%, rgba(234,88,12,0.06) 50%, rgba(194,65,12,0) 100%)',
    iconColor: 'text-orange-500',
  },
  {
    icon: LogIn,
    label: t('nav.login'),
    href: '/login',
    gradient: 'radial-gradient(circle, rgba(34,197,94,0.15) 0%, rgba(22,163,74,0.06) 50%, rgba(21,128,61,0) 100%)',
    iconColor: 'text-green-500',
  },
  {
    icon: Settings,
    label: t('nav.admin'),
    href: '/admin',
    gradient: 'radial-gradient(circle, rgba(239,68,68,0.15) 0%, rgba(220,38,38,0.06) 50%, rgba(185,28,28,0) 100%)',
    iconColor: 'text-red-500',
  },
])

const isActive = (href: string) => {
  if (href === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(href)
}
</script>

<style scoped>
.menu-nav {
  padding: 0.5rem;
  border-radius: 1rem;
  background: linear-gradient(to bottom, rgba(255, 255, 255, 0.15), rgba(255, 255, 255, 0.08));
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3);
  position: relative;
  overflow: hidden;
}

.nav-glow-bg {
  position: absolute;
  inset: -0.5rem;
  background: radial-gradient(
    circle,
    transparent 0%,
    rgba(255, 255, 255, 0.1) 30%,
    rgba(255, 255, 255, 0.05) 60%,
    transparent 100%
  );
  border-radius: 1.5rem;
  z-index: 0;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.nav-glow-active {
  opacity: 1;
}

.menu-items-list {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  position: relative;
  z-index: 10;
  margin: 0;
  padding: 0;
  list-style: none;
}

.menu-item-wrapper {
  position: relative;
}

.menu-item-3d {
  display: block;
  border-radius: 0.75rem;
  overflow: visible;
  position: relative;
  perspective: 600px;
}

.item-glow-effect {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  border-radius: 1rem;
  opacity: 0;
  transform: scale(0.8);
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.glow-active {
  opacity: 1;
  transform: scale(2);
}

.menu-link {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  position: relative;
  z-index: 10;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  border-radius: 0.75rem;
  transition: all 0.5s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  transform-style: preserve-3d;
}

.menu-link:hover {
  color: rgba(255, 255, 255, 1);
}

.link-active {
  color: rgba(255, 255, 255, 1);
  background: rgba(255, 255, 255, 0.1);
}

.menu-front {
  transform-origin: center bottom;
  transform: rotateX(0deg);
  opacity: 1;
}

.front-flipped {
  transform: rotateX(-90deg);
  opacity: 0;
}

.menu-back {
  position: absolute;
  inset: 0;
  transform-origin: center top;
  transform: rotateX(90deg);
  opacity: 0;
}

.back-visible {
  transform: rotateX(0deg);
  opacity: 1;
}

.menu-icon-wrapper {
  transition: color 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  color: rgba(255, 255, 255, 0.7);
}

.menu-icon {
  width: 1.25rem;
  height: 1.25rem;
}

.text-blue-500 {
  color: #3b82f6 !important;
}

.text-orange-500 {
  color: #f97316 !important;
}

.text-green-500 {
  color: #22c55e !important;
}

.text-red-500 {
  color: #ef4444 !important;
}

.menu-label {
  font-weight: 500;
  font-size: 0.875rem;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .menu-nav {
    padding: 0.25rem;
  }
  
  .menu-items-list {
    flex-direction: column;
    gap: 0.25rem;
    width: 100%;
  }
  
  .menu-item-wrapper {
    width: 100%;
  }
  
  .menu-link {
    width: 100%;
    justify-content: flex-start;
    padding: 0.75rem 1rem;
  }
  
  .glow-active {
    transform: scale(1.1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .nav-glow-bg,
  .item-glow-effect,
  .menu-link,
  .menu-icon-wrapper {
    transition: none !important;
  }
  
  .menu-front,
  .menu-back {
    transform: none !important;
  }
  
  .front-flipped {
    opacity: 1 !important;
    transform: none !important;
  }
  
  .back-visible {
    display: none !important;
  }
}
</style>