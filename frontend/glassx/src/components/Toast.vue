<template>
  <Transition
    enter-active-class="transition-all duration-300 ease-out"
    enter-from-class="opacity-0 translate-y-2 scale-95"
    enter-to-class="opacity-100 translate-y-0 scale-100"
    leave-active-class="transition-all duration-200 ease-in"
    leave-from-class="opacity-100 translate-y-0 scale-100"
    leave-to-class="opacity-0 translate-y-2 scale-95"
  >
    <div
      v-if="visible"
      class="max-w-sm w-full"
    >
        <div
          class="glass-card p-4 flex items-center space-x-3"
          :class="toastClasses"
        >
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
          <button
            @click="close"
            class="flex-shrink-0 text-gray-400 hover:text-white transition-colors duration-200"
          >
            <X class="w-4 h-4" />
          </button>
        </div>
      </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { CheckCircle, XCircle, AlertCircle, Info, X } from 'lucide-vue-next'

interface Props {
  type?: 'success' | 'error' | 'warning' | 'info'
  title: string
  message?: string
  duration?: number
  autoClose?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'info',
  duration: 3000,
  autoClose: true
})

const emit = defineEmits<{
  close: []
}>()

const visible = ref(false)

const toastClasses = computed(() => {
  const baseClasses = 'border-l-4'
  switch (props.type) {
    case 'success':
      return `${baseClasses} border-green-400 bg-green-900/20`
    case 'error':
      return `${baseClasses} border-red-400 bg-red-900/20`
    case 'warning':
      return `${baseClasses} border-yellow-400 bg-yellow-900/20`
    default:
      return `${baseClasses} border-blue-400 bg-blue-900/20`
  }
})

const close = () => {
  visible.value = false
  setTimeout(() => {
    emit('close')
  }, 300)
}

onMounted(() => {
  visible.value = true
  
  if (props.autoClose) {
    setTimeout(() => {
      close()
    }, props.duration)
  }
})
</script>
