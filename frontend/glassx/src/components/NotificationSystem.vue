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
        <div v-for="notification in notifications" :key="notification.id" class="notification-card"
          :class="getNotificationClasses(notification.type)">
          <div class="flex items-start space-x-3">
            <div class="flex-shrink-0 mt-0.5">
              <CheckCircle v-if="notification.type === 'success'" class="w-5 h-5 text-green-400" />
              <XCircle v-else-if="notification.type === 'error'" class="w-5 h-5 text-red-400" />
              <AlertCircle v-else-if="notification.type === 'warning'" class="w-5 h-5 text-yellow-400" />
              <Info v-else class="w-5 h-5 text-blue-400" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium">{{ notification.title }}</p>
              <p v-if="notification.message" class="text-sm opacity-90 mt-1">{{ notification.message }}</p>
            </div>
            <button @click="removeNotification(notification.id)"
              class="flex-shrink-0 text-gray-400 hover:text-white transition-colors duration-200 mt-0.5">
              <X class="w-4 h-4" />
            </button>
          </div>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CheckCircle, XCircle, AlertCircle, Info, X } from 'lucide-vue-next'

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
      return 'border-l-4 border-green-400 bg-green-500/20 text-green-100'
    case 'error':
      return 'border-l-4 border-red-400 bg-red-500/20 text-red-100'
    case 'warning':
      return 'border-l-4 border-yellow-400 bg-yellow-500/20 text-yellow-100'
    default:
      return 'border-l-4 border-blue-400 bg-blue-500/20 text-blue-100'
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
  @apply backdrop-blur-xl border border-white/20 rounded-lg p-4 shadow-lg;
  /* Simple rounded corners, not too large */
  border-radius: 8px;
  /* Glass effect */
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
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