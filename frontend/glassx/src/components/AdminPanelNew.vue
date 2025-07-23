<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h1 class="text-3xl font-bold text-white">{{ $t('admin.title') }}</h1>
    </div>

    <Tabs :tabs="tabs" default-tab="review" @tab-change="onTabChange">
      <template #default="{ activeTab }">
        <!-- 待审核用户 -->
        <div v-if="activeTab === 'review'" class="space-y-4">
          <div class="flex justify-between items-center">
            <h2 class="text-2xl font-bold text-white">{{ $t('admin.review.title') }}</h2>
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
          </div>
          <div class="max-w-md">
            <SearchBar
              v-model="searchQuery"
              :placeholder="$t('admin.users.search_placeholder')"
            />
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
                    <div class="flex flex-col sm:flex-row gap-2 sm:space-x-2">
                    <Button 
                      variant="outline" 
                      size="sm"
                        @click="showDeleteConfirm(user)"
                      :disabled="loading"
                        class="text-red-400 border-red-400 hover:bg-red-400/10"
                    >
                      {{ $t('admin.users.actions.delete') }}
                    </Button>
                      <Button 
                        variant="outline" 
                        size="sm"
                        @click="showBanConfirm(user)"
                        :disabled="loading"
                        class="text-orange-400 border-orange-400 hover:bg-orange-400/10"
                      >
                        {{ $t('admin.users.actions.ban') }}
                      </Button>
                      <Button 
                        v-if="user.status === 'banned'"
                        variant="outline" 
                        size="sm"
                        @click="showUnbanConfirm(user)"
                        :disabled="loading"
                        class="text-green-400 border-green-400 hover:bg-green-400/10"
                      >
                        {{ $t('admin.users.actions.unban') }}
                      </Button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
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

// 搜索功能
const searchQuery = ref('')
const filteredUsers = computed(() => {
  if (!searchQuery.value.trim()) {
    return allUsers.value
  }
  const query = searchQuery.value.toLowerCase()
  return allUsers.value.filter(user => 
    user.username?.toLowerCase().includes(query) ||
    user.email?.toLowerCase().includes(query)
  )
})

// 确认对话框状态
const showDeleteDialog = ref(false)
const showBanDialog = ref(false)
const showUnbanDialog = ref(false)
const selectedUser = ref<any>(null)

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
    const response = await apiService.getPendingList(locale.value)
    if (response.success) {
      pendingUsers.value = response.users
    } else if (response.message && response.message.includes('Authentication')) {
      notification.error(t('common.error'), response.message)
    }
  } catch (error) {
    notification.error(t('common.error'), t('admin.review.messages.error'))
  }
}

const loadAllUsers = async () => {
  try {
    const response = await apiService.getAllUsers()
    if (response.success) {
      allUsers.value = response.users
    } else if (response.message && response.message.includes('Authentication')) {
      notification.error(t('common.error'), response.message)
    }
  } catch (error) {
    notification.error(t('common.error'), t('admin.users.messages.error'))
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

const onTabChange = (tab: string) => {
  if (tab === 'review') {
    loadPendingUsers();
  } else if (tab === 'users') {
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