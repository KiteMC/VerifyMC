<template>
  <div class="space-y-6">
    <!-- Header with search and refresh -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
      <div class="flex items-center space-x-3">
        <h2 class="section-title">{{ $t('admin.allUsers') }}</h2>
        <span v-if="totalCount > 0" class="status-badge bg-gray-100 text-gray-700">
          {{ totalCount }} {{ $t('pagination.items') }}
        </span>
      </div>

      <div class="flex items-center space-x-3 w-full sm:w-auto">
        <!-- Search bar -->
        <div class="relative flex-1 sm:flex-none">
          <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
            <svg class="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
          </div>
          <input v-model="searchQuery" type="text" :placeholder="$t('admin.searchPlaceholder')"
            class="input-field pl-10 w-full sm:w-80" />
        </div>

        <!-- Refresh button -->
        <button @click="fetchList"
          class="inline-flex items-center justify-center w-10 h-10 rounded-lg border border-gray-300 bg-white hover:bg-gray-50 transition-colors flex-shrink-0"
          :disabled="loading">
          <svg class="h-5 w-5" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor"
            viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15">
            </path>
          </svg>
        </button>
      </div>
    </div>
    <!-- Users table -->
    <div class="minimal-card overflow-hidden">
      <div v-if="loading" class="p-8 text-center">
        <div class="inline-flex items-center">
          <svg class="animate-spin -ml-1 mr-3 h-5 w-5 text-gray-500" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
            </path>
          </svg>
          <span class="text-gray-500">{{ $t('admin.loading') }}</span>
        </div>
      </div>

      <div v-else-if="users.length === 0" class="p-8 text-center">
        <svg class="mx-auto h-12 w-12 text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197m13.5-9a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z">
          </path>
        </svg>
        <p class="text-gray-500">{{ searchQuery ? $t('admin.noSearchResults') : $t('admin.empty') }}</p>
      </div>

      <div v-else class="overflow-x-auto w-full">
        <table class="table-minimal w-full">
          <thead class="table-header">
            <tr>
              <th class="table-header-cell">{{ $t('admin.username') }}</th>
              <th class="table-header-cell">{{ $t('admin.email') }}</th>
              <th class="table-header-cell">{{ $t('admin.status') }}</th>
              <th class="table-header-cell">{{ $t('admin.time') }}</th>
              <th class="table-header-cell">{{ $t('admin.action') }}</th>
            </tr>
          </thead>
          <tbody class="table-body">
            <tr v-for="user in users" :key="user.uuid" class="hover:bg-gray-50">
              <td class="table-cell">
                <div class="flex items-center">
                  <div class="h-8 w-8 rounded-full bg-gray-200 flex items-center justify-center mr-3">
                    <span class="text-xs font-medium text-gray-600">{{ user.username?.charAt(0)?.toUpperCase() }}</span>
                  </div>
                  <span class="font-medium">{{ user.username }}</span>
                </div>
              </td>
              <td class="table-cell-secondary">{{ user.email || user.qq }}</td>
              <td class="table-cell">
                <span class="status-badge" :class="getStatusBadgeClass(user.status)">
                  {{ $t('admin.status_' + user.status) }}
                </span>
              </td>
              <td class="table-cell-secondary">{{ formatTime(user.regTime) }}</td>
              <td class="table-cell-wide">
                <div class="flex items-center space-x-2 min-w-max">
                  <!-- Change Password -->
                  <button @click="showPasswordDialog = true; selectedUser = user; newPassword = ''"
                    :disabled="processingUsers.has(user.uuid)"
                    class="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-blue-300 text-blue-600 hover:bg-blue-50 transition-colors"
                    :title="$t('admin.changePassword')">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v-2l-4.257-4.257A6 6 0 0117 9z"></path>
                    </svg>
                  </button>
                  <!-- Delete -->
                  <button @click="showDeleteConfirm(user)" :disabled="processingUsers.has(user.uuid)"
                    class="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 transition-colors"
                    :title="$t('admin.delete')">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16">
                      </path>
                    </svg>
                  </button>
                  <!-- Ban/Unban -->
                  <button v-if="user.status !== 'banned'" @click="showBanConfirm(user)"
                    :disabled="processingUsers.has(user.uuid)"
                    class="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 transition-colors"
                    :title="$t('admin.ban')">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728L5.636 5.636"></path>
                    </svg>
                  </button>
                  <button v-if="user.status === 'banned'" @click="showUnbanConfirm(user)"
                    :disabled="processingUsers.has(user.uuid)"
                    class="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-green-300 text-green-600 hover:bg-green-50 transition-colors"
                    :title="$t('admin.unban')">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="msg" :class="msgType === 'error' ? 'text-red-500' : 'text-green-600'" class="mt-4">{{ msg }}</div>

    <!-- 确认对话框 -->
    <ConfirmDialog :show="showDeleteDialog" :title="$t('admin.delete')" :message="$t('admin.confirmDelete')"
      :confirm-text="$t('admin.delete')" :cancel-text="$t('common.cancel')" type="danger" @confirm="confirmDelete"
      @cancel="showDeleteDialog = false" />

    <ConfirmDialog :show="showBanDialog" :title="$t('admin.ban')" :message="$t('admin.confirmBan')"
      :confirm-text="$t('admin.ban')" :cancel-text="$t('common.cancel')" type="danger" @confirm="confirmBan"
      @cancel="showBanDialog = false" />

    <ConfirmDialog :show="showUnbanDialog" :title="$t('admin.unban')" :message="$t('admin.confirmUnban')"
      :confirm-text="$t('admin.unban')" :cancel-text="$t('common.cancel')" type="info" @confirm="confirmUnban"
      @cancel="showUnbanDialog = false" />

    <!-- Change password dialog - Enhanced UI -->
    <div v-if="showPasswordDialog" class="password-modal-overlay">
      <div class="password-modal-backdrop" @click="showPasswordDialog = false"></div>
      <div class="password-modal-dialog">
        <!-- Header with icon -->
        <div class="flex items-center mb-6">
          <div class="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center mr-3">
            <svg class="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v-2l-4.257-4.257A6 6 0 0117 9z"></path>
            </svg>
          </div>
          <h3 class="text-xl font-semibold text-gray-900">{{ $t('admin.changePassword') }}</h3>
        </div>
        
        <div class="space-y-6">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">{{ $t('admin.newPassword') }}</label>
            <input v-model="newPassword" type="password"
              class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              :placeholder="$t('admin.newPasswordPlaceholder')" />
          </div>
          
          <div class="flex justify-end space-x-3 pt-4 border-t border-gray-200">
            <button class="px-6 py-2.5 text-gray-700 bg-gray-100 border border-gray-300 rounded-lg hover:bg-gray-200 transition-colors font-medium"
              @click="showPasswordDialog = false">
              {{ $t('common.cancel') }}
            </button>
            <button class="px-6 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium disabled:opacity-50 disabled:cursor-not-allowed" 
              @click="confirmChangePassword"
              :disabled="!newPassword">
              {{ $t('common.save') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Pagination component -->
    <Pagination :current-page="currentPage" :total-pages="totalPages" :total-count="totalCount" :page-size="pageSize"
      :has-next="hasNext" :has-prev="hasPrev" @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange" />

    <div v-if="msg" :class="msgType === 'error' ? 'text-red-500' : 'text-green-600'" class="mt-4">{{ msg }}</div>

    <!-- Version update notification -->
    <VersionUpdateNotification />
  </div>
</template>

<style scoped>
/* Password modal overlay - ensures proper viewport-relative positioning */
.password-modal-overlay {
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
.password-modal-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
}

/* Dialog container */
.password-modal-dialog {
  position: relative;
  background: white;
  border-radius: 1rem;
  padding: 2rem;
  width: 100%;
  max-width: 32rem;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(0, 0, 0, 0.05);
  /* Ensure dialog stays centered */
  margin: auto;
  /* Add subtle animation */
  transform: scale(1);
  transition: transform 0.2s ease-out;
}

.password-modal-dialog:hover {
  transform: scale(1.01);
}
</style>

<script setup lang="ts">
import { ref, onMounted, inject, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ConfirmDialog from './ConfirmDialog.vue'
import Pagination from './Pagination.vue'
import VersionUpdateNotification from './VersionUpdateNotification.vue'

const { t } = useI18n()
const users = ref<any[]>([])
const loading = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')
const processingUsers = ref(new Set<string>())
const token = inject('token') as any

// Pagination state
const currentPage = ref(1)
const pageSize = ref(10)
const totalCount = ref(0)
const totalPages = ref(0)
const hasNext = ref(false)
const hasPrev = ref(false)
const searchQuery = ref('')
const searchDebounceTimer = ref<NodeJS.Timeout | null>(null)

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

/**
 * Fetch users with pagination - Enhanced with debugging and fallback
 */
async function fetchList() {
  loading.value = true
  msg.value = ''
  try {
    console.log('Default theme - Loading users with params:', {
      page: currentPage.value,
      pageSize: pageSize.value,
      search: searchQuery.value
    })

    // Build query parameters for pagination
    const params = new URLSearchParams({
      page: currentPage.value.toString(),
      pageSize: pageSize.value.toString(),
    });

    if (searchQuery.value.trim()) {
      params.append('search', searchQuery.value.trim());
    }

    const res = await fetch(`/api/users-paginated?${params.toString()}`, {
      headers: getAuthHeaders()
    })
    const data = await res.json()

    console.log('Default theme - API Response:', data)

    if (data.success) {
      users.value = data.users || []
      console.log('Default theme - Loaded users:', users.value)

      // Update pagination info
      if (data.pagination) {
        currentPage.value = data.pagination.currentPage
        totalCount.value = data.pagination.totalCount
        totalPages.value = data.pagination.totalPages
        hasNext.value = data.pagination.hasNext
        hasPrev.value = data.pagination.hasPrev
      }

      // If no users found, try pending users first, then all-users endpoint
      if (users.value.length === 0) {
        console.log('Default theme - No approved/rejected users found, trying pending users...')

        try {
          const pendingRes = await fetch('/api/pending-list', {
            headers: getAuthHeaders()
          })
          const pendingData = await pendingRes.json()
          console.log('Default theme - Pending users response:', pendingData)

          if (pendingData.success && pendingData.users && pendingData.users.length > 0) {
            // Use pending users as data source
            let pendingUsers = pendingData.users

            // Apply search filter if needed
            if (searchQuery.value.trim()) {
              pendingUsers = pendingUsers.filter(user =>
                user.username?.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
                user.email?.toLowerCase().includes(searchQuery.value.toLowerCase())
              )
            }

            // Apply pagination
            const startIndex = (currentPage.value - 1) * pageSize.value
            const endIndex = startIndex + pageSize.value
            users.value = pendingUsers.slice(startIndex, endIndex)
            totalCount.value = pendingUsers.length
            totalPages.value = Math.ceil(pendingUsers.length / pageSize.value)
            hasNext.value = currentPage.value < totalPages.value
            hasPrev.value = currentPage.value > 1

            console.log('Default theme - Pending users loaded:', users.value)
            return
          }
        } catch (error) {
          console.error('Default theme - Error loading pending users:', error)
        }

        // If still no pending users, try all-users fallback
        console.log('Default theme - No pending users, trying all-users fallback...')
        try {
          const fallbackRes = await fetch('/api/all-users', {
            headers: getAuthHeaders()
          })
          const fallbackData = await fallbackRes.json()
          console.log('Default theme - All-users fallback response:', fallbackData)

          if (fallbackData.success && fallbackData.users) {
            users.value = fallbackData.users
            totalCount.value = fallbackData.users.length
            totalPages.value = Math.ceil(fallbackData.users.length / pageSize.value)
            hasNext.value = false
            hasPrev.value = false
            console.log('Default theme - All-users fallback data loaded:', users.value)
          }
        } catch (error) {
          console.error('Default theme - Error loading all users:', error)
        }
      }
    } else {
      console.error('Default theme - API Error:', data.message)
      users.value = [] // 确保清空数据
      msg.value = data.message || t('admin.users.messages.load_error')
      msgType.value = 'error'
    }
  } catch (e) {
    console.error('Default theme - Exception:', e)
    users.value = [] // 确保清空数据
    msg.value = t('errors.network')
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

function getStatusBadgeClass(status: string) {
  switch (status) {
    case 'pending': return 'status-pending'
    case 'approved': return 'status-approved'
    case 'rejected': return 'status-rejected'
    case 'banned': return 'status-banned'
    default: return 'bg-gray-100 text-gray-800'
  }
}

/**
 * Handle pagination page change
 * @param page New page number
 */
function handlePageChange(page: number) {
  currentPage.value = page
  fetchList()
}

/**
 * Handle pagination page size change
 * @param newPageSize New page size
 */
function handlePageSizeChange(newPageSize: number) {
  pageSize.value = newPageSize
  currentPage.value = 1 // Reset to first page when changing page size
  fetchList()
}

/**
 * Handle search with debounce
 */
function handleSearch() {
  if (searchDebounceTimer.value) {
    clearTimeout(searchDebounceTimer.value)
  }

  searchDebounceTimer.value = setTimeout(() => {
    currentPage.value = 1 // Reset to first page when searching
    fetchList()
  }, 500) // 500ms debounce
}

// Watch for search query changes
watch(searchQuery, () => {
  handleSearch()
})

onMounted(fetchList)
</script>