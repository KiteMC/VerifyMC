<template>
  <div class="bg-white rounded-lg shadow p-6 overflow-x-auto">
    <div class="mb-4 flex justify-between items-center">
      <span class="text-lg font-semibold">{{ $t('admin.pendingList') }}</span>
      <button class="text-blue-600 hover:underline cursor-pointer text-xs" @click="fetchList">{{ $t('admin.refresh') }}</button>
    </div>
    <div v-if="loading" class="text-gray-400">{{ $t('admin.loading') }}</div>
    <div v-else-if="users.length === 0" class="text-gray-400">{{ $t('admin.empty') }}</div>
    <table v-else class="w-full text-left border-t overflow-x-auto text-xs sm:text-base min-w-0">
      <thead>
        <tr class="bg-gray-100">
          <th class="py-2 px-2 min-w-0">{{ $t('admin.username') }}</th>
          <!-- <th class="py-2 px-2">{{ $t('admin.email') }}</th> -->
          <th class="py-2 px-2 min-w-0">{{ $t('admin.time') }}</th>
          <th class="py-2 px-2 min-w-0">{{ $t('admin.action') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.uuid" class="border-b">
          <td class="py-2 px-2 min-w-0">{{ user.username }}</td>
          <!-- <td class="py-2 px-2">{{ user.email || user.qq }}</td> -->
          <td class="py-2 px-2 min-w-0">{{ formatTime(user.regTime) }}</td>
          <td class="py-2 px-2 flex gap-2 flex-wrap min-w-0">
            <button class="bg-green-500 text-white px-2 py-1 rounded hover:bg-green-600 cursor-pointer text-xs whitespace-nowrap" @click="review(user.uuid, true)">{{ $t('admin.approve') }}</button>
            <button class="bg-red-500 text-white px-2 py-1 rounded hover:bg-red-600 cursor-pointer text-xs whitespace-nowrap" @click="review(user.uuid, false)">{{ $t('admin.reject') }}</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="msg" :class="msgType === 'error' ? 'text-red-500' : 'text-green-600'" class="mt-4">{{ msg }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const users = ref<any[]>([])
const loading = ref(false)
const msg = ref('')
const msgType = ref<'success'|'error'>('success')
const token = inject('token') as any

// 获取认证头
function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  if (token?.value) {
    headers['Authorization'] = `Bearer ${token.value}`
  }
  return headers
}

async function fetchList() {
  loading.value = true
  msg.value = ''
  try {
    const res = await fetch('/api/pending-list?language=' + t('lang'), {
      headers: getAuthHeaders()
    })
    const data = await res.json()
    if (data.success) {
      users.value = data.users || []
    } else {
      msg.value = data.message || t('admin.loadFailed')
      msgType.value = 'error'
    }
  } catch (e) {
    msg.value = t('admin.loadFailed')
    msgType.value = 'error'
  } finally {
    loading.value = false
  }
}

async function review(uuid: string, approve: boolean) {
  msg.value = ''
  try {
    const res = await fetch('/api/review', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ 
        uuid, 
        action: approve ? 'approve' : 'reject', 
        language: t('lang') 
      })
    })
    const data = await res.json()
    if (data.success) {
      msg.value = data.msg || t('admin.reviewSuccess')
      msgType.value = 'success'
      fetchList()
    } else {
      msg.value = data.msg || data.message || t('admin.reviewFailed')
      msgType.value = 'error'
    }
  } catch (e) {
    msg.value = t('admin.reviewFailed')
    msgType.value = 'error'
  }
}

function formatTime(ts: number) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString()
}

onMounted(fetchList)
</script> 