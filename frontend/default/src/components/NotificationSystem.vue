<template>
  <Teleport to="body">
    <div class="fixed right-4 top-4 z-50 space-y-3 max-w-sm">
      <TransitionGroup
        enter-active-class="transition-all duration-300 ease-out"
        enter-from-class="opacity-0 translate-x-full scale-95"
        enter-to-class="opacity-100 translate-x-0 scale-100"
        leave-active-class="transition-all duration-200 ease-in"
        leave-from-class="opacity-100 translate-x-0 scale-100"
        leave-to-class="opacity-0 translate-x-full scale-95"
        move-class="transition-all duration-300 ease-out"
      >
        <div
          v-for="notification in notifications"
          :key="notification.id"
          class="notification-card"
          :class="getNotificationClasses(notification.type)"
        >
          <div class="flex items-start space-x-3">
            <div class="flex-shrink-0 mt-0.5">
              <svg v-if="notification.type === 'success'" class="w-5 h-5 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              <svg v-else-if="notification.type === 'error'" class="w-5 h-5 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              <svg v-else-if="notification.type === 'warning'" class="w-5 h-5 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
              </svg>
              <svg v-else class="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-gray-900">{{ notification.title }}</p>
              <p v-if="notification.message" class="text-sm text-gray-600 mt-1">{{ notification.message }}</p>
            </div>
            <button
              @click="removeNotification(notification.id)"
              class="flex-shrink-0 text-gray-400 hover:text-gray-600 transition-colors duration-200 mt-0.5"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'

export interface Notification {
  id: string
  type: 'success' | 'error' | 'warning' | 'info'
  title: string
  message?: string
  duration?: number
}

const notifications = ref<Notification[]>([])

const getNotificationClasses = (type: string) => {
  switch (type) {
    case 'success':
      return 'bg-green-50 border-green-200'
    case 'error':
      return 'bg-red-50 border-red-200'
    case 'warning':
      return 'bg-yellow-50 border-yellow-200'
    default:
      return 'bg-blue-50 border-blue-200'
  }
}

const addNotification = (notification: Omit<Notification, 'id'>) => {
  const id = Date.now().toString()
  const newNotification = { ...notification, id }
  notifications.value.push(newNotification)
  
  // Auto remove after duration
  const duration = notification.duration || 4000
  setTimeout(() => {
    removeNotification(id)
  }, duration)
}

const removeNotification = (id: string) => {
  const index = notifications.value.findIndex(n => n.id === id)
  if (index > -1) {
    notifications.value.splice(index, 1)
  }
}

// Expose methods for external use
defineExpose({
  addNotification,
  removeNotification
})
</script>

<style scoped>
.notification-card {
  @apply bg-white border rounded-lg p-4 shadow-lg backdrop-blur-sm;
  /* Simple rounded corners, not too large */
  border-radius: 8px;
  /* Ensure smooth transitions */
  transition: all 0.3s ease-out;
  transform: translateZ(0);
  will-change: transform, opacity;
}

/* Ensure TransitionGroup animations work properly */
.v-enter-active,
.v-leave-active {
  transition: all 0.3s ease-out;
}

.v-enter-from {
  opacity: 0;
  transform: translateX(100%) scale(0.95);
}

.v-enter-to {
  opacity: 1;
  transform: translateX(0) scale(1);
}

.v-leave-from {
  opacity: 1;
  transform: translateX(0) scale(1);
}

.v-leave-to {
  opacity: 0;
  transform: translateX(100%) scale(0.95);
}

.v-move {
  transition: all 0.3s ease-out;
}
</style>