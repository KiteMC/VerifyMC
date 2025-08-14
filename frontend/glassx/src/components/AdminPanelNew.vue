<template>
  <div class="space-y-6 w-full overflow-hidden">
    <div class="flex justify-between items-center">
      <h1 class="text-3xl font-bold text-white">{{ $t('admin.title') }}</h1>
    </div>

    <Tabs :tabs="tabs" default-tab="review" @tab-change="onTabChange">
      <template #default="{ activeTab }">
        <!-- 待审核用户 -->
        <div v-if="activeTab === 'review'" class="space-y-4">
          <div class="flex justify-between items-center">
            <h2 class="text-2xl font-bold text-white">{{ $t('admin.review.title') }}</h2>
            <button
              @click="loadPendingUsers"
              :disabled="loading"
              class="glass-button text-white hover:text-blue-300 transition-colors duration-300 p-2"
              :title="$t('common.refresh')"
            >
              <svg class="w-5 h-5" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
              </svg>
            </button>
          </div>

          <div class="overflow-x-auto w-full rounded-md border border-white/20">
            <Table class="min-w-[600px]">
              <TableHeader>
                <TableRow>
                  <TableHead>{{ $t('admin.review.table.username') }}</TableHead>
                  <TableHead>{{ $t('admin.review.table.email') }}</TableHead>
                  <TableHead>{{ $t('admin.review.table.register_time') }}</TableHead>
                  <TableHead class="w-[150px]">{{ $t('admin.review.table.actions') }}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="user in pendingUsers" :key="user.uuid">
                  <TableCell class="font-medium text-white">{{ user.username }}</TableCell>
                  <TableCell>{{ user.email }}</TableCell>
                  <TableCell>{{ formatDate(user.regTime) }}</TableCell>
                  <TableCell>
                    <div class="flex space-x-2">
                      <Button 
                        variant="outline" 
                        size="sm"
                        @click="approveUser(user.uuid)"
                        :disabled="loading"
                      >
                        {{ $t('admin.review.actions.approve') }}
                      </Button>
                      <Button 
                        variant="outline" 
                        size="sm"
                        @click="rejectUser(user.uuid)"
                        :disabled="loading"
                      >
                        {{ $t('admin.review.actions.reject') }}
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
                <TableRow v-if="pendingUsers.length === 0">
                  <TableCell colspan="4" class="text-center py-8 text-white/60">
                    {{ $t('admin.review.no_pending') }}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </div>

        <!-- 用户管理 -->
        <div v-if="activeTab === 'users'" class="space-y-4 w-full">
          <div class="flex justify-between items-center">
            <h2 class="text-2xl font-bold text-white">{{ $t('admin.users.title') }}</h2>
            <button
              @click="loadAllUsers"
              :disabled="loading"
              class="glass-button text-white hover:text-blue-300 transition-colors duration-300 p-2"
              :title="$t('common.refresh')"
            >
              <svg class="w-5 h-5" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
              </svg>
            </button>
          </div>
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div class="max-w-md flex-1">
              <SearchBar
                v-model="searchQuery"
                :placeholder="$t('admin.users.search_placeholder')"
              />
            </div>
          </div>
          <div class="overflow-x-auto w-full rounded-md border border-white/20">
            <Table class="min-w-[600px] text-xs sm:text-sm">
              <TableHeader>
                <TableRow>
                  <TableHead>{{ $t('admin.users.table.username') }}</TableHead>
                  <TableHead>{{ $t('admin.users.table.email') }}</TableHead>
                  <TableHead>{{ $t('admin.users.table.status') }}</TableHead>
                  <TableHead>{{ $t('admin.users.table.register_time') }}</TableHead>
                  <TableHead class="w-[200px]">{{ $t('admin.users.table.actions') }}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="user in filteredUsers" :key="user.uuid">
                  <TableCell class="font-medium text-white break-all">{{ user.username }}</TableCell>
                  <TableCell class="break-all">{{ user.email }}</TableCell>
                  <TableCell>
                    <span :class="getStatusClass(user.status) + ' inline-block px-2 py-1 text-xs font-medium rounded-full whitespace-nowrap'">
                      {{ $t(`admin.users.status.${(user.status || '').toLowerCase()}`) }}
                    </span>
                  </TableCell>
                  <TableCell>{{ formatDate(user.regTime) }}</TableCell>
                  <TableCell>
                    <div class="flex items-center gap-2">
                      <!-- Change Password -->
                      <button
                        @click="showPasswordDialog = true; selectedUser = user; newPassword = ''"
                        :disabled="loading"
                        class="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-blue-400/50 text-blue-400 hover:bg-blue-400/10 transition-colors"
                        :title="$t('admin.users.actions.change_password')"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v-2l-4.257-4.257A6 6 0 0117 9z"></path>
                        </svg>
                      </button>
                      <!-- Delete -->
                      <button
                        @click="showDeleteConfirm(user)"
                        :disabled="loading"
                        class="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-red-400/50 text-red-400 hover:bg-red-400/10 transition-colors"
                        :title="$t('admin.users.actions.delete')"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                        </svg>
                      </button>
                      <!-- Ban/Unban -->
                      <button
                        v-if="user.status !== 'banned'"
                        @click="showBanConfirm(user)"
                        :disabled="loading"
                        class="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-red-400/50 text-red-400 hover:bg-red-400/10 transition-colors"
                        :title="$t('admin.users.actions.ban')"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728L5.636 5.636"></path>
                        </svg>
                      </button>
                      <button
                        v-if="user.status === 'banned'"
                        @click="showUnbanConfirm(user)"
                        :disabled="loading"
                        class="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-green-400/50 text-green-400 hover:bg-green-400/10 transition-colors"
                        :title="$t('admin.users.actions.unban')"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                        </svg>
                      </button>
                    </div>
                  </TableCell>
                </TableRow>
                <TableRow v-if="filteredUsers.length === 0">
                  <TableCell colspan="5" class="text-center py-8 text-white/60">
                    {{ searchQuery ? $t('admin.users.no_search_results') : $t('admin.users.no_users') }}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
          
          <!-- Pagination component for user management -->
          <Pagination
            :current-page="currentPage"
            :total-pages="totalPages"
            :total-count="totalCount"
            :page-size="pageSize"
            :has-next="hasNext"
            :has-prev="hasPrev"
            @page-change="handlePageChange"
            @page-size-change="handlePageSizeChange"
          />
        </div>
      </template>
    </Tabs>
    
    <!-- 确认对话框 -->
    <ConfirmDialog
      :show="showDeleteDialog"
      :title="$t('admin.users.delete_modal.title')"
      :message="$t('admin.users.delete_modal.message').replace('{username}', selectedUser?.username || '')"
      :confirm-text="$t('admin.users.delete_modal.confirm')"
      :cancel-text="$t('admin.users.delete_modal.cancel')"
      type="danger"
      @confirm="confirmDelete"
      @cancel="showDeleteDialog = false"
    />
    
    <ConfirmDialog
      :show="showBanDialog"
      :title="$t('admin.users.ban_modal.title')"
      :message="$t('admin.users.ban_modal.message').replace('{username}', selectedUser?.username || '')"
      :confirm-text="$t('admin.users.ban_modal.confirm')"
      :cancel-text="$t('admin.users.ban_modal.cancel')"
      type="danger"
      @confirm="confirmBan"
      @cancel="showBanDialog = false"
    />
    
    <ConfirmDialog
      :show="showUnbanDialog"
      :title="$t('admin.users.unban_modal.title')"
      :message="$t('admin.users.unban_modal.message').replace('{username}', selectedUser?.username || '')"
      :confirm-text="$t('admin.users.unban_modal.confirm')"
      :cancel-text="$t('admin.users.unban_modal.cancel')"
      type="info"
      @confirm="confirmUnban"
      @cancel="showUnbanDialog = false"
    />

    <!-- Change password dialog - Fixed positioning for proper viewport centering -->
    <div v-if="showPasswordDialog" class="password-modal-overlay">
      <div class="password-modal-backdrop" @click="showPasswordDialog = false"></div>
      <div class="password-modal-dialog">
        <h3 class="text-xl font-semibold text-white mb-6 text-center">{{ $t('admin.users.change_password_modal.title') }}</h3>
        <div class="space-y-6">
          <div>
            <Label for="newPassword" class="text-white text-sm font-medium mb-2 block">{{ $t('admin.users.change_password_modal.new_password') }}</Label>
            <input
              id="newPassword"
              v-model="newPassword"
              type="password"
              class="w-full px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/50 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent backdrop-blur-sm"
              :placeholder="$t('admin.users.change_password_modal.password_placeholder')"
            />
          </div>
          <div class="flex justify-end space-x-3">
            <Button variant="outline" @click="showPasswordDialog = false" class="bg-white/10 border-white/20 text-white hover:bg-white/20">
              {{ $t('common.cancel') }}
            </Button>
            <Button @click="confirmChangePassword" :disabled="!newPassword" class="bg-blue-500 hover:bg-blue-600 text-white">
              {{ $t('common.save') }}
            </Button>
          </div>
        </div>
      </div>
    </div>
    
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
  z-index: 99999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  /* Ensure it's above everything */
  pointer-events: auto;
}

/* Background backdrop */
.password-modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  z-index: 99998;
}

/* Dialog container */
.password-modal-dialog {
  position: relative;
  background: rgba(15, 15, 15, 0.85);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-radius: 1rem;
  padding: 2rem;
  width: 100%;
  max-width: 28rem;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.1);
  /* Ensure dialog stays centered and above backdrop */
  margin: auto;
  z-index: 99999;
  /* Hardware acceleration */
  transform: translateZ(0);
}
</style>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useNotification } from '@/composables/useNotification'
import { apiService } from '@/services/api'
import Tabs from './ui/Tabs.vue'
import Table from './ui/Table.vue'
import TableHeader from './ui/TableHeader.vue'
import TableBody from './ui/TableBody.vue'
import TableRow from './ui/TableRow.vue'
import TableHead from './ui/TableHead.vue'
import TableCell from './ui/TableCell.vue'
import Button from './ui/Button.vue'
import Label from './ui/Label.vue'
import ConfirmDialog from './ui/ConfirmDialog.vue'
import SearchBar from './ui/SearchBar.vue'
import Pagination from './ui/Pagination.vue'
import VersionUpdateNotification from './ui/VersionUpdateNotification.vue'

const { t, locale } = useI18n()
const notification = useNotification()

// 认证检查
const token = localStorage.getItem('admin_token')
if (!token) {
  // 如果没有token，重定向到登录页面
  window.location.href = '/login'
}

const loading = ref(false)
const pendingUsers = ref<any[]>([])
const allUsers = ref<any[]>([])
const announcementContent = ref('')

// Pagination state
const currentPage = ref(1)
const pageSize = ref(10)
const totalCount = ref(0)
const totalPages = ref(0)
const hasNext = ref(false)
const hasPrev = ref(false)

// 搜索功能
const searchQuery = ref('')
const searchDebounceTimer = ref<NodeJS.Timeout | null>(null)

// Use paginated data instead of filtered data
const filteredUsers = computed(() => {
  return allUsers.value
})

// 确认对话框状态
const showDeleteDialog = ref(false)
const showBanDialog = ref(false)
const showUnbanDialog = ref(false)
const showPasswordDialog = ref(false)
const selectedUser = ref<any>(null)
const newPassword = ref('')

// 修复 tabs 多语言切换
const tabs = computed(() => [
  { value: 'review', label: t('admin.tabs.review') },
  { value: 'users', label: t('admin.tabs.users') }
])

const formatDate = (dateString: string) => {
  if (!dateString) return '—'
  return new Date(dateString).toLocaleDateString()
}

const getStatusClass = (status: string) => {
  const s = (status || '').toLowerCase();
  const baseClasses = 'px-2 py-1 rounded-full text-xs font-medium';
  switch (s) {
    case 'pending':
      return `${baseClasses} bg-yellow-500/20 text-yellow-300`;
    case 'approved':
      return `${baseClasses} bg-green-500/20 text-green-300`;
    case 'rejected':
      return `${baseClasses} bg-red-500/20 text-red-300`;
    case 'banned':
      return `${baseClasses} bg-red-500/20 text-red-300`;
    default:
      return `${baseClasses} bg-gray-500/20 text-gray-300`;
  }
}

const loadPendingUsers = async () => {
  try {
    console.log('Loading pending users...')
    const response = await apiService.getPendingList(locale.value)
    console.log('Pending users response:', response)
    
    if (response.success) {
      pendingUsers.value = response.users || []
      console.log('Loaded pending users:', pendingUsers.value)
    } else {
      console.error('Pending users error:', response.message)
      if (response.message && response.message.includes('Authentication')) {
        notification.error(t('common.error'), response.message)
      } else {
        notification.error(t('common.error'), response.message || t('admin.review.messages.error'))
      }
    }
  } catch (error) {
    console.error('Exception loading pending users:', error)
    notification.error(t('common.error'), t('admin.review.messages.error'))
  }
}

/**
 * Load users with pagination - with fallback to all-users endpoint
 */
const loadAllUsers = async () => {
  try {
    loading.value = true
    console.log('Loading users with params:', {
      page: currentPage.value,
      pageSize: pageSize.value,
      search: searchQuery.value
    })
    
    // Try paginated endpoint first
    let response = await apiService.getUsersPaginated(
      currentPage.value, 
      pageSize.value, 
      searchQuery.value
    )
    
    console.log('Paginated API Response:', response)
    
    // If paginated endpoint returns empty users, try to get pending users and combine
    if (response.success && (!response.users || response.users.length === 0)) {
      console.log('No approved/rejected users found, trying to get pending users...')
      
      try {
        const pendingResponse = await apiService.getPendingList(locale.value)
        console.log('Pending users response:', pendingResponse)
        
        if (pendingResponse.success && pendingResponse.users && pendingResponse.users.length > 0) {
          // Use pending users as the data source
          const pendingUsers = pendingResponse.users
          const filteredData = searchQuery.value 
            ? pendingUsers.filter(user => 
                user.username?.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
                user.email?.toLowerCase().includes(searchQuery.value.toLowerCase())
              )
            : pendingUsers
          
          const startIndex = (currentPage.value - 1) * pageSize.value
          const endIndex = startIndex + pageSize.value
          const paginatedData = filteredData.slice(startIndex, endIndex)
          
          allUsers.value = paginatedData
          totalCount.value = filteredData.length
          totalPages.value = Math.ceil(filteredData.length / pageSize.value)
          hasNext.value = currentPage.value < totalPages.value
          hasPrev.value = currentPage.value > 1
          
          console.log('Pending users loaded as fallback:', {
            total: filteredData.length,
            currentPage: paginatedData,
            pagination: {
              currentPage: currentPage.value,
              totalPages: totalPages.value,
              hasNext: hasNext.value,
              hasPrev: hasPrev.value
            }
          })
          return
        }
      } catch (error) {
        console.error('Error loading pending users:', error)
      }
      
      // If still no data, try all-users endpoint
      console.log('Trying fallback all-users endpoint...')
      try {
        const fallbackResponse = await apiService.getAllUsers()
        console.log('All-users API Response:', fallbackResponse)
        
        if (fallbackResponse.success && fallbackResponse.users) {
          // Manually implement pagination for fallback data
          const allUsersData = fallbackResponse.users
          const filteredData = searchQuery.value 
            ? allUsersData.filter(user => 
                user.username?.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
                user.email?.toLowerCase().includes(searchQuery.value.toLowerCase())
              )
            : allUsersData
          
          const startIndex = (currentPage.value - 1) * pageSize.value
          const endIndex = startIndex + pageSize.value
          const paginatedData = filteredData.slice(startIndex, endIndex)
          
          allUsers.value = paginatedData
          totalCount.value = filteredData.length
          totalPages.value = Math.ceil(filteredData.length / pageSize.value)
          hasNext.value = currentPage.value < totalPages.value
          hasPrev.value = currentPage.value > 1
          
          console.log('All-users fallback data loaded:', {
            total: filteredData.length,
            currentPage: paginatedData,
            pagination: {
              currentPage: currentPage.value,
              totalPages: totalPages.value,
              hasNext: hasNext.value,
              hasPrev: hasPrev.value
            }
          })
          return
        }
      } catch (error) {
        console.error('Error loading all users:', error)
      }
    }
    
    if (response.success) {
      allUsers.value = response.users || []
      console.log('Loaded users:', allUsers.value)
      
      // Update pagination info
      if (response.pagination) {
        currentPage.value = response.pagination.currentPage
        totalCount.value = response.pagination.totalCount
        totalPages.value = response.pagination.totalPages
        hasNext.value = response.pagination.hasNext
        hasPrev.value = response.pagination.hasPrev
      }
    } else {
      console.error('API Error:', response.message)
      allUsers.value = [] // 确保清空数据
      if (response.message && response.message.includes('Authentication')) {
        notification.error(t('common.error'), response.message)
      } else {
        notification.error(t('common.error'), response.message || t('admin.users.messages.load_error'))
      }
    }
  } catch (error) {
    console.error('Exception loading users:', error)
    allUsers.value = [] // 确保清空数据
    notification.error(t('common.error'), t('errors.network'))
  } finally {
    loading.value = false
  }
}

const approveUser = async (uuid: string) => {
  loading.value = true
  try {
    const response = await apiService.reviewUser({
      uuid,
      action: 'approve',
      language: locale.value
    })
    
    if (response.success) {
      notification.success(t('admin.review.messages.approve_success'), response.msg && response.msg !== t('admin.review.messages.approve_success') ? response.msg : '')
      await loadPendingUsers()
    } else {
      notification.error(t('admin.review.messages.error'), response.msg && response.msg !== t('admin.review.messages.error') ? response.msg : '')
    }
  } catch (error) {
    notification.error(t('admin.review.messages.error'), t('admin.review.messages.error'))
  } finally {
    loading.value = false
  }
}

const rejectUser = async (uuid: string) => {
  loading.value = true
  try {
    const response = await apiService.reviewUser({
      uuid,
      action: 'reject',
      language: locale.value
    })
    
    if (response.success) {
      notification.success(t('admin.review.messages.reject_success'), response.msg && response.msg !== t('admin.review.messages.reject_success') ? response.msg : '')
      await loadPendingUsers()
    } else {
      notification.error(t('admin.review.messages.error'), response.msg && response.msg !== t('admin.review.messages.error') ? response.msg : '')
    }
  } catch (error) {
    notification.error(t('admin.review.messages.error'), t('admin.review.messages.error'))
  } finally {
    loading.value = false
  }
}

const showDeleteConfirm = (user: any) => {
  selectedUser.value = user
  showDeleteDialog.value = true
}

const showBanConfirm = (user: any) => {
  selectedUser.value = user
  showBanDialog.value = true
}

const showUnbanConfirm = (user: any) => {
  selectedUser.value = user
  showUnbanDialog.value = true
}

const confirmDelete = async () => {
  if (!selectedUser.value) return
  
  loading.value = true
  showDeleteDialog.value = false
  
  try {
    const response = await apiService.deleteUser(selectedUser.value.uuid, locale.value)
    
    if (response.success) {
      notification.success(t('admin.users.messages.delete_success'), response.msg && response.msg !== t('admin.users.messages.delete_success') ? response.msg : '')
      await loadAllUsers()
    } else {
      notification.error(t('admin.users.messages.error'), response.msg && response.msg !== t('admin.users.messages.error') ? response.msg : '')
    }
  } catch (error) {
    notification.error(t('admin.users.messages.error'), t('admin.users.messages.error'))
  } finally {
    loading.value = false
    selectedUser.value = null
  }
}

const confirmBan = async () => {
  if (!selectedUser.value) return
  
  loading.value = true
  showBanDialog.value = false
  
  try {
    const response = await apiService.banUser(selectedUser.value.uuid, locale.value)
    
    if (response.success) {
      notification.success(t('admin.users.messages.ban_success'), response.msg && response.msg !== t('admin.users.messages.ban_success') ? response.msg : '')
      await loadAllUsers()
    } else {
      notification.error(t('admin.users.messages.error'), response.msg && response.msg !== t('admin.users.messages.error') ? response.msg : '')
    }
  } catch (error) {
    notification.error(t('admin.users.messages.error'), t('admin.users.messages.error'))
  } finally {
    loading.value = false
    selectedUser.value = null
  }
}

const confirmUnban = async () => {
  if (!selectedUser.value) return
  
  loading.value = true
  showUnbanDialog.value = false
  
  try {
    const response = await apiService.unbanUser(selectedUser.value.uuid, locale.value)
    
    if (response.success) {
      notification.success(t('admin.users.messages.unban_success'), response.msg && response.msg !== t('admin.users.messages.unban_success') ? response.msg : '')
      await loadAllUsers()
    } else {
      notification.error(t('admin.users.messages.error'), response.msg && response.msg !== t('admin.users.messages.error') ? response.msg : '')
    }
  } catch (error) {
    notification.error(t('admin.users.messages.error'), t('admin.users.messages.error'))
  } finally {
    loading.value = false
    selectedUser.value = null
  }
}

const confirmChangePassword = async () => {
  if (!selectedUser.value || !newPassword.value) return
  
  loading.value = true
  
  try {
    const response = await apiService.changePassword({
      uuid: selectedUser.value.uuid,
      newPassword: newPassword.value,
      language: locale.value
    })
    
    if (response.success) {
      notification.success(t('admin.users.messages.password_change_success'), response.msg && response.msg !== t('admin.users.messages.password_change_success') ? response.msg : '')
      showPasswordDialog.value = false
      newPassword.value = ''
      selectedUser.value = null
    } else {
      notification.error(t('admin.users.messages.error'), response.msg && response.msg !== t('admin.users.messages.error') ? response.msg : '')
    }
  } catch (error) {
    notification.error(t('admin.users.messages.error'), t('admin.users.messages.error'))
  } finally {
    loading.value = false
  }
}

const updateAnnouncement = async () => {
  loading.value = true
  try {
    const response = await apiService.updateAnnouncement(announcementContent.value, locale.value)
    
    if (response.success) {
      notification.success(t('admin.announcement.messages.update_success'), response.msg && response.msg !== t('admin.announcement.messages.update_success') ? response.msg : '')
    } else {
      notification.error(t('admin.announcement.messages.error'), response.msg && response.msg !== t('admin.announcement.messages.error') ? response.msg : '')
    }
  } catch (error) {
    notification.error(t('admin.announcement.messages.error'), t('admin.announcement.messages.error'))
  } finally {
    loading.value = false
  }
}

/**
 * Handle pagination page change
 * @param page New page number
 */
const handlePageChange = (page: number) => {
  currentPage.value = page
  loadAllUsers()
}

/**
 * Handle pagination page size change
 * @param newPageSize New page size
 */
const handlePageSizeChange = (newPageSize: number) => {
  pageSize.value = newPageSize
  currentPage.value = 1 // Reset to first page when changing page size
  loadAllUsers()
}

/**
 * Handle search with debounce
 */
const handleSearch = () => {
  if (searchDebounceTimer.value) {
    clearTimeout(searchDebounceTimer.value)
  }
  
  searchDebounceTimer.value = setTimeout(() => {
    currentPage.value = 1 // Reset to first page when searching
    loadAllUsers()
  }, 500) // 500ms debounce
}

// Watch for search query changes
watch(searchQuery, () => {
  handleSearch()
})

const onTabChange = (tab: string) => {
  if (tab === 'review') {
    loadPendingUsers();
  } else if (tab === 'users') {
    // Reset pagination when switching to users tab
    currentPage.value = 1
    loadAllUsers();
  }
};



onMounted(() => {
  loadPendingUsers()
  loadAllUsers()

  // WebSocket实时刷新
  if (window.WebSocket) {
    const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const wsHost = window.location.hostname;
    const wsPort = window.location.port ? (parseInt(window.location.port) + 1) : 8081;
    const wsUrl = `${wsProtocol}://${wsHost}:${wsPort}`;
    try {
      const ws = new WebSocket(wsUrl);
      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'user_update') {
            // 只刷新数据，不弹通知
            loadPendingUsers();
            loadAllUsers();
          }
        } catch {}
      };
    } catch {}
  }
})
</script> 