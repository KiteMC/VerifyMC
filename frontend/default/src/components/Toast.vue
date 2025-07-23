<template>
  <div class="fixed z-50 left-1/2 top-4 transform -translate-x-1/2 flex flex-col gap-2 items-center w-full max-w-xs pointer-events-none">
    <transition-group name="toast-fade" tag="div">
      <div v-for="(toast, i) in toasts" :key="toast.id" :class="toastClass(toast.type)" class="w-full px-4 py-2 rounded shadow flex items-center gap-2 animate-fade-in-out">
        <span v-if="toast.type==='success'" class="text-green-500"><svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 00-1.414 0L9 11.586 6.707 9.293a1 1 0 00-1.414 1.414l3 3a1 1 0 001.414 0l7-7a1 1 0 000-1.414z" clip-rule="evenodd" /></svg></span>
        <span v-else-if="toast.type==='error'" class="text-red-500"><svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm-1-4a1 1 0 112 0 1 1 0 01-2 0zm.293-7.707a1 1 0 011.414 0l.293.293.293-.293a1 1 0 111.414 1.414l-.293.293.293.293a1 1 0 01-1.414 1.414l-.293-.293-.293.293a1 1 0 01-1.414-1.414l.293-.293-.293-.293a1 1 0 010-1.414z" clip-rule="evenodd" /></svg></span>
        <span v-else-if="toast.type==='warning'" class="text-yellow-500"><svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5"><path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.72-1.36 3.485 0l6.518 11.591c.75 1.334-.213 2.985-1.742 2.985H3.48c-1.53 0-2.492-1.651-1.742-2.985L8.257 3.1zM11 13a1 1 0 10-2 0 1 1 0 002 0zm-1-2a1 1 0 01-1-1V8a1 1 0 112 0v2a1 1 0 01-1 1z" clip-rule="evenodd" /></svg></span>
        <span v-else class="text-blue-500"><svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5"><path fill-rule="evenodd" d="M18 10A8 8 0 11.001 10 8 8 0 0118 10zm-8 4a1 1 0 100-2 1 1 0 000 2zm1-7V7a1 1 0 10-2 0v2a1 1 0 002 0zm0 4a1 1 0 10-2 0 1 1 0 002 0z" clip-rule="evenodd" /></svg></span>
        <span class="flex-1 text-sm">{{ toast.message }}</span>
      </div>
    </transition-group>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
export type ToastType = 'success' | 'info' | 'warning' | 'error'
interface Toast { id: number, message: string, type: ToastType }
const toasts = ref<Toast[]>([])
let id = 0
function showToast(message: string, type: ToastType = 'info') {
  const count = toasts.value.length
  const duration = count > 2 ? 1200 : count > 0 ? 2000 : 3000
  const toast = { id: ++id, message, type }
  toasts.value.push(toast)
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== toast.id)
  }, duration)
}
defineExpose({ showToast })
function toastClass(type: ToastType) {
  if (type === 'success') return 'bg-green-50 border border-green-200'
  if (type === 'error') return 'bg-red-50 border border-red-200'
  if (type === 'warning') return 'bg-yellow-50 border border-yellow-200'
  return 'bg-blue-50 border border-blue-200'
}
</script>
<style scoped>
.toast-fade-enter-active, .toast-fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}
.toast-fade-enter-from, .toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
.toast-fade-leave-active {
  transition-duration: 0.2s;
}
</style> 