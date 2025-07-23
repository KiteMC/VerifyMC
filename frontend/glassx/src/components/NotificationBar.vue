<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-300 ease-out"
      enter-from-class="opacity-0 translate-y-[-100%]"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition-all duration-200 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-[-100%]"
    >
      <div
        v-if="visible"
        class="fixed top-0 left-0 right-0 z-50"
      >
        <div
          class="w-full p-4 flex items-center justify-between"
          :class="notificationClasses"
        >
          <div class="flex items-center space-x-3 flex-1">
            <div class="flex-shrink-0">
              <CheckCircle v-if="type === 'success'" class="w-5 h-5 text-green-400" />
              <XCircle v-else-if="type === 'error'" class="w-5 h-5 text-red-400" />
              <AlertCircle v-else-if="type === 'warning'" class="w-5 h-5 text-yellow-400" />
              <Info v-else class="w-5 h-5 text-blue-400" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-white">{{ title }}</p>
              <p v-if="message" class="text-sm text-gray-300 mt-1">{{ message }}</p>
            </div>
          </div>
          
          <div class="flex items-center space-x-2">
            <!-- Action Buttons -->
            <button
              v-if="actions && actions.length > 0"
              v-for="action in actions"
              :key="action.label"
              @click="handleAction(action)"
              class="px-3 py-1 text-xs font-medium rounded-md transition-colors duration-200"
              :class="actionButtonClasses(action)"
            >
              {{ action.label }}
            </button>
            
            <!-- Close Button -->
            <button
              @click="close"
              class="flex-shrink-0 text-gray-400 hover:text-white transition-colors duration-200 ml-2"
            >
              <X class="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { CheckCircle, XCircle, AlertCircle, Info, X } from 'lucide-vue-next'

interface NotificationAction {
  label: string
  action: 'confirm' | 'cancel' | 'custom'
  variant?: 'primary' | 'secondary' | 'danger'
  callback?: () => void
}

interface Props {
  id?: string
  visible?: boolean
  type?: 'success' | 'error' | 'warning' | 'info'
  title: string
  message?: string
  duration?: number
  autoClose?: boolean
  actions?: NotificationAction[]
  persistent?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'info',
  duration: 5000,
  autoClose: true,
  persistent: false,
  visible: true
})

const emit = defineEmits<{
  close: []
  confirm: []
  cancel: []
  action: [action: NotificationAction]
}>()

const notificationClasses = computed(() => {
  const baseClasses = 'backdrop-blur-lg border-b'
  switch (props.type) {
    case 'success':
      return `${baseClasses} bg-green-900/90 border-green-400/30`
    case 'error':
      return `${baseClasses} bg-red-900/90 border-red-400/30`
    case 'warning':
      return `${baseClasses} bg-yellow-900/90 border-yellow-400/30`
    default:
      return `${baseClasses} bg-blue-900/90 border-blue-400/30`
  }
})

const actionButtonClasses = (action: NotificationAction) => {
  const baseClasses = 'hover:opacity-80'
  switch (action.variant) {
    case 'primary':
      return `${baseClasses} bg-blue-600 text-white`
    case 'danger':
      return `${baseClasses} bg-red-600 text-white`
    default:
      return `${baseClasses} bg-gray-600 text-white`
  }
}

const handleAction = (action: NotificationAction) => {
  if (action.callback) {
    action.callback()
  }
  
  switch (action.action) {
    case 'confirm':
      emit('confirm')
      break
    case 'cancel':
      emit('cancel')
      break
    default:
      emit('action', action)
  }
  
  if (!props.persistent) {
    close()
  }
}

const close = () => {
  emit('close')
}

onMounted(() => {
  if (props.autoClose && !props.persistent) {
    setTimeout(() => {
      close()
    }, props.duration)
  }
})
</script> 