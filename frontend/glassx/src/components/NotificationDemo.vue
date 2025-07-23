<template>
  <div class="p-6 max-w-2xl mx-auto">
    <h1 class="text-3xl font-bold text-white mb-8">通知栏演示</h1>
    
    <div class="space-y-6">
      <!-- 基础通知 -->
      <div class="glass-card p-6">
        <h2 class="text-xl font-semibold text-white mb-4">基础通知</h2>
        <div class="grid grid-cols-2 gap-4">
          <button @click="showSuccess" class="glass-button bg-green-600 hover:bg-green-700">
            成功通知
          </button>
          <button @click="showError" class="glass-button bg-red-600 hover:bg-red-700">
            错误通知
          </button>
          <button @click="showWarning" class="glass-button bg-yellow-600 hover:bg-yellow-700">
            警告通知
          </button>
          <button @click="showInfo" class="glass-button bg-blue-600 hover:bg-blue-700">
            信息通知
          </button>
        </div>
      </div>

      <!-- 替代浏览器默认提示 -->
      <div class="glass-card p-6">
        <h2 class="text-xl font-semibold text-white mb-4">替代浏览器默认提示</h2>
        <div class="grid grid-cols-2 gap-4">
          <button @click="showAlert" class="glass-button bg-purple-600 hover:bg-purple-700">
            替代 Alert
          </button>
          <button @click="showConfirm" class="glass-button bg-orange-600 hover:bg-orange-700">
            替代 Confirm
          </button>
          <button @click="showPrompt" class="glass-button bg-teal-600 hover:bg-teal-700">
            替代 Prompt
          </button>
          <button @click="showCustomAction" class="glass-button bg-indigo-600 hover:bg-indigo-700">
            自定义操作
          </button>
        </div>
      </div>

      <!-- 持久化通知 -->
      <div class="glass-card p-6">
        <h2 class="text-xl font-semibold text-white mb-4">持久化通知</h2>
        <div class="grid grid-cols-2 gap-4">
          <button @click="showPersistentInfo" class="glass-button bg-gray-600 hover:bg-gray-700">
            持久化信息
          </button>
          <button @click="showPersistentWarning" class="glass-button bg-yellow-600 hover:bg-yellow-700">
            持久化警告
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useNotification } from '@/composables/useNotification'

const notification = useNotification()

// 基础通知
const showSuccess = () => {
  notification.success('操作成功', '您的操作已成功完成！')
}

const showError = () => {
  notification.error('操作失败', '发生了一个错误，请重试。')
}

const showWarning = () => {
  notification.warning('警告', '请注意这个重要信息。')
}

const showInfo = () => {
  notification.info('信息', '这是一条普通的信息通知。')
}

// 替代浏览器默认提示
const showAlert = async () => {
  await notification.alert('这是一个替代浏览器alert的通知！', '提示')
  console.log('Alert 已确认')
}

const showConfirm = async () => {
  const result = await notification.confirm('您确定要执行这个操作吗？', '确认操作')
  if (result) {
    notification.success('已确认', '您选择了确认操作')
  } else {
    notification.info('已取消', '您取消了操作')
  }
}

const showPrompt = async () => {
  const result = await notification.prompt('请输入您的姓名：', '', '输入信息')
  if (result) {
    notification.success('输入成功', `您输入的姓名是：${result}`)
  } else {
    notification.info('已取消', '您取消了输入')
  }
}

const showCustomAction = () => {
  notification.showNotification({
    type: 'info',
    title: '自定义操作',
    message: '请选择一个操作：',
    actions: [
      {
        label: '保存',
        action: 'custom',
        variant: 'primary',
        callback: () => {
          notification.success('已保存', '数据已成功保存')
        }
      },
      {
        label: '删除',
        action: 'custom',
        variant: 'danger',
        callback: () => {
          notification.error('已删除', '数据已删除')
        }
      },
      {
        label: '取消',
        action: 'cancel',
        variant: 'secondary'
      }
    ],
    persistent: true,
    autoClose: false
  })
}

// 持久化通知
const showPersistentInfo = () => {
  notification.showNotification({
    type: 'info',
    title: '持久化信息',
    message: '这条通知不会自动关闭，需要手动关闭。',
    persistent: true,
    autoClose: false
  })
}

const showPersistentWarning = () => {
  notification.showNotification({
    type: 'warning',
    title: '重要警告',
    message: '这是一个重要的警告信息，请仔细阅读。',
    actions: [
      {
        label: '我知道了',
        action: 'confirm',
        variant: 'primary'
      }
    ],
    persistent: true,
    autoClose: false
  })
}
</script>

<style scoped>
.glass-card {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
}

.glass-button {
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s ease;
  color: white;
}
</style> 