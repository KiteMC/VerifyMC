<template>
  <!-- Fixed positioning modal overlay - ensures proper centering relative to viewport -->
  <div v-if="show" class="modal-overlay">
    <!-- Background mask -->
    <div class="modal-backdrop" @click="handleCancel"></div>
    
    <!-- Dialog container -->
    <div class="modal-dialog">
      <!-- 标题 -->
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold text-white">{{ title }}</h3>
        <button 
          @click="handleCancel"
          class="text-white/60 hover:text-white transition-colors"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>
      </div>
      
      <!-- 内容 -->
      <div class="mb-6">
        <p class="text-white/80">{{ message }}</p>
      </div>
      
      <!-- 操作按钮 -->
      <div class="flex gap-3 justify-end">
        <button 
          @click="handleCancel"
          class="px-4 py-2 text-white/80 hover:text-white border border-white/20 hover:border-white/40 rounded-md transition-colors"
        >
          {{ cancelText }}
        </button>
        <button 
          @click="handleConfirm"
          :class="[
            'px-4 py-2 rounded-md transition-colors',
            type === 'danger' 
              ? 'bg-red-500 hover:bg-red-600 text-white' 
              : 'bg-blue-500 hover:bg-blue-600 text-white'
          ]"
        >
          {{ confirmText }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Modal overlay - ensures proper viewport-relative positioning */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

/* Background backdrop */
.modal-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

/* Dialog container */
.modal-dialog {
  position: relative;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 0.5rem;
  padding: 1.5rem;
  max-width: 28rem;
  width: 100%;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  /* Ensure dialog stays centered */
  margin: auto;
}
</style>

<script setup lang="ts">
import { defineProps, defineEmits } from 'vue'

interface Props {
  show: boolean
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: 'danger' | 'warning' | 'info'
}

const props = withDefaults(defineProps<Props>(), {
  confirmText: '确认',
  cancelText: '取消',
  type: 'danger'
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const handleConfirm = () => {
  emit('confirm')
}

const handleCancel = () => {
  emit('cancel')
}
</script> 