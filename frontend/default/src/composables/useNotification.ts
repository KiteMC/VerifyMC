import { ref } from 'vue'
import type { Notification } from '@/components/NotificationSystem.vue'

// Global notification system
const notificationSystem = ref<any>(null)

export const useNotification = () => {
  const setNotificationSystem = (system: any) => {
    notificationSystem.value = system
  }

  const showNotification = (notification: Omit<Notification, 'id'>) => {
    if (notificationSystem.value) {
      notificationSystem.value.addNotification(notification)
    }
  }

  const success = (title: string, message?: string, duration?: number) => {
    showNotification({ type: 'success', title, message, duration })
  }

  const error = (title: string, message?: string, duration?: number) => {
    showNotification({ type: 'error', title, message, duration })
  }

  const warning = (title: string, message?: string, duration?: number) => {
    showNotification({ type: 'warning', title, message, duration })
  }

  const info = (title: string, message?: string, duration?: number) => {
    showNotification({ type: 'info', title, message, duration })
  }

  return {
    setNotificationSystem,
    showNotification,
    success,
    error,
    warning,
    info
  }
}