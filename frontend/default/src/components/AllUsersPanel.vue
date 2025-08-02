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
            <button class="bg-blue-500 text-white px-2 py-1 rounded hover:bg-blue-600 cursor-pointer text-xs whitespace-nowrap" @click="showPasswordDialog = true; selectedUser = user; newPassword = ''" :disabled="processingUsers.has(user.uuid)">{{ $t('admin.changePassword') }}</button>
            <button class="bg-red-500 text-white px-2 py-1 rounded hover:bg-red-600 cursor-pointer text-xs whitespace-nowrap" @click="showDeleteConfirm(user)" :disabled="processingUsers.has(user.uuid)">{{ $t('admin.delete') }}</button>
            <button v-if="user.status !== 'banned'" class="bg-orange-500 text-white px-2 py-1 rounded hover:bg-orange-600 cursor-pointer text-xs whitespace-nowrap" @click="showBanConfirm(user)" :disabled="processingUsers.has(user.uuid)">{{ $t('admin.ban') }}</button>
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

    <!-- 修改密码对话框 -->
    <div v-if="showPasswordDialog" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg p-6 w-full max-w-md mx-4">
        <h3 class="text-lg font-semibold mb-4">{{ $t('admin.changePassword') }}</h3>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">{{ $t('admin.newPassword') }}</label>
            <input
              v-model="newPassword"
              type="password"
              class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              :placeholder="$t('admin.passwordPlaceholder')"
            />
          </div>
          <div class="flex justify-end space-x-2">
            <button class="px-4 py-2 text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50" @click="showPasswordDialog = false">
              {{ $t('common.cancel') }}
            </button>
            <button class="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700" @click="confirmChangePassword" :disabled="!newPassword">
              {{ $t('common.save') }}
            </button>
          </div>
        </div>
      </div>
    </div>
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
const showPasswordDialog = ref(false)
const selectedUser = ref<any>(null)
const newPassword = ref('')

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

async function confirmChangePassword() {
  if (!selectedUser.value || !newPassword.value) return
  
  processingUsers.value.add(selectedUser.value.uuid)
  msg.value = ''
  
  try {
    const res = await fetch('/api/change-password', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ 
        uuid: selectedUser.value.uuid,
        newPassword: newPassword.value,
        language: t('lang') 
      })
    })
    const data = await res.json()
    if (data.success) {
      msg.value = t('admin.passwordChangeSuccess')
      msgType.value = 'success'
      showPasswordDialog.value = false
      newPassword.value = ''
      selectedUser.value = null
      fetchList()
    } else {
      msg.value = data.msg || data.message || t('admin.passwordChangeFailed')
      msgType.value = 'error'
    }
  } catch (e) {
    msg.value = t('admin.passwordChangeFailed')
    msgType.value = 'error'
  } finally {
    processingUsers.value.delete(selectedUser.value.uuid)
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