<template>
  <div class="bg-white rounded-lg shadow p-6 overflow-x-auto">
    <div class="mb-4 flex justify-between items-center">
      <span class="text-lg font-semibold">{{ $t('admin.allUsers') }}</span>
      <button class="text-blue-600 hover:underline cursor-pointer text-xs" @click="fetchList">{{ $t('admin.refresh') }}</button>
    </div>
    <div v-if="loading" class="text-gray-400">{{ $t('admin.loading') }}</div>
    <div v-else-if="users.length === 0" class="text-gray-400">{{ $t('admin.empty') }}</div>
    <table v-else class="w-full text-left border-t overflow-x-auto text-xs sm:text-base min-w-0">
      <thead>
        <tr class="bg-gray-100">
          <th class="py-2 px-2 min-w-0">{{ $t('admin.username') }}</th>
          <th class="py-2 px-2 min-w-0">{{ $t('admin.email') }}</th>
          <th class="py-2 px-2 min-w-0">{{ $t('admin.status') }}</th>
          <th class="py-2 px-2 min-w-0">{{ $t('admin.time') }}</th>
          <th class="py-2 px-2 min-w-0">{{ $t('admin.action') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.uuid" class="border-b">
          <td class="py-2 px-2 min-w-0">{{ user.username }}</td>
          <td class="py-2 px-2 min-w-0">{{ user.email || user.qq }}</td>
          <td class="py-2 px-2 min-w-0">
            <span :class="getStatusClass(user.status)">{{ $t('admin.status_' + user.status) }}</span>
          </td>
          <td class="py-2 px-2 min-w-0">{{ formatTime(user.regTime) }}</td>
          <td class="py-2 px-2 flex gap-2 flex-wrap min-w-0">
            <button class="bg-red-500 text-white px-2 py-1 rounded hover:bg-red-600 cursor-pointer text-xs whitespace-nowrap" @click="showDeleteConfirm(user)" :disabled="processingUsers.has(user.uuid)">{{ $t('admin.delete') }}</button>
            <button class="bg-orange-500 text-white px-2 py-1 rounded hover:bg-orange-600 cursor-pointer text-xs whitespace-nowrap" @click="showBanConfirm(user)" :disabled="processingUsers.has(user.uuid)">{{ $t('admin.ban') }}</button>
            <button v-if="user.status === 'banned'" class="bg-green-500 text-white px-2 py-1 rounded hover:bg-green-600 cursor-pointer text-xs whitespace-nowrap" @click="showUnbanConfirm(user)" :disabled="processingUsers.has(user.uuid)">{{ $t('admin.unban') }}</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="msg" :class="msgType === 'error' ? 'text-red-500' : 'text-green-600'" class="mt-4">{{ msg }}</div>
    
    <!-- 确认对话框 -->
    <ConfirmDialog
      :show="showDeleteDialog"
      :title="$t('admin.delete')"
      :message="$t('admin.confirmDelete')"
      :confirm-text="$t('admin.delete')"
      :cancel-text="$t('common.cancel')"
      type="danger"
      @confirm="confirmDelete"
      @cancel="showDeleteDialog = false"
    />
    
    <ConfirmDialog
      :show="showBanDialog"
      :title="$t('admin.ban')"
      :message="$t('admin.confirmBan')"
      :confirm-text="$t('admin.ban')"
      :cancel-text="$t('common.cancel')"
      type="danger"
      @confirm="confirmBan"
      @cancel="showBanDialog = false"
    />
    
    <ConfirmDialog
      :show="showUnbanDialog"
      :title="$t('admin.unban')"
      :message="$t('admin.confirmUnban')"
      :confirm-text="$t('admin.unban')"
      :cancel-text="$t('common.cancel')"
      type="info"
      @confirm="confirmUnban"
      @cancel="showUnbanDialog = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import ConfirmDialog from './ConfirmDialog.vue'

const { t } = useI18n()
const users = ref<any[]>([])
const loading = ref(false)
const msg = ref('')
const msgType = ref<'success'|'error'>('success')
const processingUsers = ref(new Set<string>())
const token = inject('token') as any

// 确认对话框状态
const showDeleteDialog = ref(false)
const showBanDialog = ref(false)
const showUnbanDialog = ref(false)
const selectedUser = ref<any>(null)

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
    const res = await fetch('/api/all-users', {
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

function showDeleteConfirm(user: any) {
  selectedUser.value = user
  showDeleteDialog.value = true
}

function showBanConfirm(user: any) {
  selectedUser.value = user
  showBanDialog.value = true
}

function showUnbanConfirm(user: any) {
  selectedUser.value = user
  showUnbanDialog.value = true
}

async function confirmDelete() {
  if (!selectedUser.value) return
  
  processingUsers.value.add(selectedUser.value.uuid)
  msg.value = ''
  showDeleteDialog.value = false
  
  try {
    const res = await fetch('/api/delete-user', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ 
        uuid: selectedUser.value.uuid, 
        language: t('lang') 
      })
    })
    const data = await res.json()
    if (data.success) {
      msg.value = t('admin.deleteSuccess')
      msgType.value = 'success'
      fetchList()
    } else {
      msg.value = data.message || t('admin.deleteFailed')
      msgType.value = 'error'
    }
  } catch (e) {
    msg.value = t('admin.deleteFailed')
    msgType.value = 'error'
  } finally {
    processingUsers.value.delete(selectedUser.value.uuid)
    selectedUser.value = null
  }
}

async function confirmUnban() {
  if (!selectedUser.value) return
  
  processingUsers.value.add(selectedUser.value.uuid)
  msg.value = ''
  showUnbanDialog.value = false
  
  try {
    const res = await fetch('/api/unban-user', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ 
        uuid: selectedUser.value.uuid, 
        language: t('lang') 
      })
    })
    const data = await res.json()
    if (data.success) {
      msg.value = t('admin.unbanSuccess')
      msgType.value = 'success'
      fetchList()
    } else {
      msg.value = data.msg || data.message || t('admin.unbanFailed')
      msgType.value = 'error'
    }
  } catch (e) {
    msg.value = t('admin.unbanFailed')
    msgType.value = 'error'
  } finally {
    processingUsers.value.delete(selectedUser.value.uuid)
    selectedUser.value = null
  }
}

async function confirmBan() {
  if (!selectedUser.value) return
  
  processingUsers.value.add(selectedUser.value.uuid)
  msg.value = ''
  showBanDialog.value = false
  
  try {
    const res = await fetch('/api/ban-user', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ 
        uuid: selectedUser.value.uuid, 
        language: t('lang') 
      })
    })
    const data = await res.json()
    if (data.success) {
      msg.value = t('admin.banSuccess')
      msgType.value = 'success'
fetchList()
    } else {
      msg.value = data.message || t('admin.banFailed')
      msgType.value = 'error'
    }
  } catch (e) {
    msg.value = t('admin.banFailed')
    msgType.value = 'error'
  } finally {
    processingUsers.value.delete(selectedUser.value.uuid)
    selectedUser.value = null
  }
}

function formatTime(ts: number) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString()
}

function getStatusClass(status: string) {
  switch (status) {
    case 'pending': return 'text-yellow-600'
    case 'approved': return 'text-green-600'
    case 'rejected': return 'text-red-600'
    case 'banned': return 'text-red-800'
    default: return 'text-gray-600'
  }
}

onMounted(fetchList)
</script> 