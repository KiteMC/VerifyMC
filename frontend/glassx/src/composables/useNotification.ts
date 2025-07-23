import { ref } from 'vue'

interface NotificationAction {
  label: string
  action: 'confirm' | 'cancel' | 'custom'
  variant?: 'primary' | 'secondary' | 'danger'
  callback?: () => void
}

interface NotificationOptions {
  type?: 'success' | 'error' | 'warning' | 'info'
  title: string
  message?: string
  duration?: number
  autoClose?: boolean
  actions?: NotificationAction[]
  persistent?: boolean
}

interface NotificationInstance extends NotificationOptions {
  id: string
  visible: boolean
  resolve?: (value: any) => void
  reject?: (reason: any) => void
}

const notifications = ref<NotificationInstance[]>([])
let notificationId = 0

export function useNotification() {
  // 根据堆积数量计算显示时间
  const calculateDuration = (baseDuration: number) => {
    const count = notifications.value.length
    if (count <= 1) return baseDuration
    if (count <= 3) return Math.max(baseDuration * 0.7, 2000) // 至少2秒
    if (count <= 5) return Math.max(baseDuration * 0.5, 1500) // 至少1.5秒
    return Math.max(baseDuration * 0.3, 1000) // 至少1秒
  }

  const showNotification = (options: NotificationOptions): Promise<any> => {
    return new Promise((resolve, reject) => {
      const id = `notification-${++notificationId}`
      
      // 计算实际显示时间
      const actualDuration = calculateDuration(options.duration || 5000)
      
      const notification: NotificationInstance = {
        id,
        visible: true,
        ...options,
        duration: actualDuration,
        resolve,
        reject
      }
      
      notifications.value.push(notification)
    })
  }

  const removeNotification = (id: string) => {
    const index = notifications.value.findIndex(n => n.id === id)
    if (index > -1) {
      notifications.value.splice(index, 1)
    }
  }

  const handleNotificationClose = (id: string) => {
    const notification = notifications.value.find(n => n.id === id)
    if (notification) {
      notification.visible = false
      setTimeout(() => {
        notification.reject?.(new Error('Notification closed'))
        removeNotification(id)
      }, 300)
    }
  }

  const handleNotificationConfirm = (id: string) => {
    const notification = notifications.value.find(n => n.id === id)
    if (notification) {
      notification.visible = false
      setTimeout(() => {
        notification.resolve?.(true)
        removeNotification(id)
      }, 300)
    }
  }

  const handleNotificationCancel = (id: string) => {
    const notification = notifications.value.find(n => n.id === id)
    if (notification) {
      notification.visible = false
      setTimeout(() => {
        notification.resolve?.(false)
        removeNotification(id)
      }, 300)
    }
  }

  const handleNotificationAction = (id: string, action: NotificationAction) => {
    const notification = notifications.value.find(n => n.id === id)
    if (notification) {
      notification.visible = false
      setTimeout(() => {
        notification.resolve?.(action)
        removeNotification(id)
      }, 300)
    }
  }

  // 替代浏览器默认的alert
  const alert = (message: string, title = '提示'): Promise<void> => {
    return showNotification({
      type: 'info',
      title,
      message,
      actions: [
        {
          label: '确定',
          action: 'confirm',
          variant: 'primary'
        }
      ],
      persistent: true,
      autoClose: false
    }).then(() => {})
  }

  // 替代浏览器默认的confirm
  const confirm = (message: string, title = '确认'): Promise<boolean> => {
    return showNotification({
      type: 'warning',
      title,
      message,
      actions: [
        {
          label: '取消',
          action: 'cancel',
          variant: 'secondary'
        },
        {
          label: '确定',
          action: 'confirm',
          variant: 'primary'
        }
      ],
      persistent: true,
      autoClose: false
    })
  }

  // 替代浏览器默认的prompt
  const prompt = (message: string, defaultValue = '', title = '输入'): Promise<string | null> => {
    return new Promise((resolve) => {
      showNotification({
        type: 'info',
        title,
        message,
        actions: [
          {
            label: '取消',
            action: 'cancel',
            variant: 'secondary'
          },
          {
            label: '确定',
            action: 'confirm',
            variant: 'primary',
            callback: () => {
              // 这里可以添加输入框逻辑
              resolve(defaultValue || null)
            }
          }
        ],
        persistent: true,
        autoClose: false
      }).then((result) => {
        if (result === true) {
          resolve(defaultValue || null)
        } else {
          resolve(null)
        }
      }).catch(() => {
        resolve(null)
      })
    })
  }

  // 显示成功通知
  const success = (title: string, message?: string, duration = 3000) => {
    return showNotification({
      type: 'success',
      title,
      message,
      duration,
      autoClose: true
    })
  }

  // 显示错误通知
  const error = (title: string, message?: string, duration = 5000) => {
    return showNotification({
      type: 'error',
      title,
      message,
      duration,
      autoClose: true
    })
  }

  // 显示警告通知
  const warning = (title: string, message?: string, duration = 4000) => {
    return showNotification({
      type: 'warning',
      title,
      message,
      duration,
      autoClose: true
    })
  }

  // 显示信息通知
  const info = (title: string, message?: string, duration = 3000) => {
    return showNotification({
      type: 'info',
      title,
      message,
      duration,
      autoClose: true
    })
  }

  return {
    notifications,
    showNotification,
    removeNotification,
    handleNotificationClose,
    handleNotificationConfirm,
    handleNotificationCancel,
    handleNotificationAction,
    alert,
    confirm,
    prompt,
    success,
    error,
    warning,
    info
  }
} 