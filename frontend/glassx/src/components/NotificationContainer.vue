<template>
  <div class="notification-container">
    <NotificationBar
      v-for="notification in notifications"
      :key="notification.id"
      v-bind="notification"
      @close="handleClose(notification.id)"
      @confirm="handleConfirm(notification.id)"
      @cancel="handleCancel(notification.id)"
      @action="handleAction(notification.id, $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { useNotification } from '@/composables/useNotification'
import NotificationBar from '@/components/NotificationBar.vue'

const { 
  notifications, 
  handleNotificationClose, 
  handleNotificationConfirm, 
  handleNotificationCancel, 
  handleNotificationAction 
} = useNotification()

const handleClose = (id: string) => {
  handleNotificationClose(id)
}

const handleConfirm = (id: string) => {
  handleNotificationConfirm(id)
}

const handleCancel = (id: string) => {
  handleNotificationCancel(id)
}

const handleAction = (id: string, action: any) => {
  handleNotificationAction(id, action)
}
</script> 