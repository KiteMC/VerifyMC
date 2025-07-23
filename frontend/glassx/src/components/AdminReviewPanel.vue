<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold gradient-text">
        {{ $t('admin.review.title') }}
      </h2>
      <button
        @click="loadPendingUsers"
        :disabled="loading"
        class="glass-button text-white hover:text-blue-300 transition-colors duration-300 flex items-center space-x-2"
      >
        <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        <span>{{ $t('common.refresh') }}</span>
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="text-center py-12">
      <div class="spinner mx-auto mb-4"></div>
      <p class="text-gray-300">{{ $t('common.loading') }}</p>
    </div>

    <!-- Empty State -->
    <div v-else-if="!pendingUsers.length" class="text-center py-12">
      <div class="w-16 h-16 bg-gray-500 rounded-2xl flex items-center justify-center mx-auto mb-4">
        <Users class="w-8 h-8 text-white" />
      </div>
      <h3 class="text-xl font-bold text-white mb-2">
        {{ $t('admin.review.no_pending') }}
      </h3>
      <p class="text-gray-300">
        {{ $t('admin.review.no_pending') }}
      </p>
    </div>

    <!-- Users Table -->
    <div v-else class="glass-card overflow-hidden">
      <div class="rounded-md border border-white/20 overflow-x-auto w-full">
        <table class="min-w-[600px] text-xs sm:text-sm w-full">
          <thead>
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">{{ $t('admin.review.table.username') }}</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">{{ $t('admin.review.table.email') }}</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">{{ $t('admin.review.table.register_time') }}</th>
              <th class="px-6 py-3 text-right text-xs font-medium text-gray-400 uppercase tracking-wider">{{ $t('admin.review.table.actions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-glass-100">
            <tr v-for="user in pendingUsers" :key="user.uuid" class="hover:bg-glass-50 transition-colors duration-200">
              <td class="px-6 py-4 whitespace-nowrap">
                <div class="flex items-center">
                  <div class="w-8 h-8 bg-gradient-to-r from-blue-500 to-purple-600 rounded-lg flex items-center justify-center mr-3">
                    <User class="w-4 h-4 text-white" />
                  </div>
                  <div class="text-sm font-medium text-white">
                    {{ user.username }}
                  </div>
                </div>
              </td>
              <td class="px-6 py-4 whitespace-nowrap">
                <div class="text-sm text-gray-300">{{ user.email }}</div>
              </td>
              <td class="px-6 py-4 whitespace-nowrap">
                <div class="text-sm text-gray-300">
                  {{ formatDate(user.registerTime) }}
                </div>
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                <div class="flex justify-end space-x-2">
                  <button
                    @click="approveUser(user)"
                    :disabled="processingUsers.has(user.uuid)"
                    class="glass-button bg-green-500/20 hover:bg-green-500/30 text-green-400 hover:text-green-300 border-green-500/30 px-3 py-1 text-xs transition-all duration-300 disabled:opacity-50"
                  >
                    <Check class="w-3 h-3 mr-1" />
                    {{ $t('admin.review.actions.approve') }}
                  </button>
                  <button
                    @click="openRejectModal(user)"
                    :disabled="processingUsers.has(user.uuid)"
                    class="glass-button bg-red-500/20 hover:bg-red-500/30 text-red-400 hover:text-red-300 border-red-500/30 px-3 py-1 text-xs transition-all duration-300 disabled:opacity-50"
                  >
                    <X class="w-3 h-3 mr-1" />
                    {{ $t('admin.review.actions.reject') }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Reject Modal -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-all duration-300 ease-out"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-all duration-200 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="rejectModal.show"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50"
          @click.self="closeRejectModal"
        >
          <div class="glass-card p-6 w-full max-w-md animate-scale-in">
            <h3 class="text-xl font-bold text-white mb-4">
              {{ $t('admin.review.reject_modal.title') }}
            </h3>
            <div class="mb-4">
              <label class="block text-sm font-medium text-gray-300 mb-2">
                {{ $t('admin.review.reject_modal.reason_label') }}
              </label>
              <textarea
                v-model="rejectModal.reason"
                :placeholder="$t('admin.review.reject_modal.reason_placeholder')"
                class="glass-input w-full text-white placeholder-gray-300 h-24 resize-none"
              ></textarea>
            </div>
            <div class="flex justify-end space-x-3">
              <button
                @click="closeRejectModal"
                class="glass-button text-gray-300 hover:text-white transition-colors duration-300"
              >
                {{ $t('admin.review.reject_modal.cancel') }}
              </button>
              <button
                @click="confirmReject"
                :disabled="rejectModal.processing"
                class="glass-button bg-red-500/20 hover:bg-red-500/30 text-red-400 hover:text-red-300 border-red-500/30 transition-all duration-300 disabled:opacity-50 flex items-center space-x-2"
              >
                <div v-if="rejectModal.processing" class="spinner"></div>
                <span>{{ $t('admin.review.reject_modal.confirm') }}</span>
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { RefreshCw, Users, User, Check, X } from 'lucide-vue-next'
import { apiService } from '@/services/api'

const { t } = useI18n()

const emit = defineEmits<{
  success: [message: string]
  error: [message: string]
}>()

const loading = ref(true)
const pendingUsers = ref<any[]>([]) // Changed from UserType to any[]
const processingUsers = ref(new Set<string>())

const rejectModal = ref({
  show: false,
  user: null as any | null, // Changed from UserType to any
  reason: '',
  processing: false
})

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString()
}

const loadPendingUsers = async () => {
  loading.value = true
  try {
    const response = await apiService.getPendingUsers()
    if (response.success) {
      pendingUsers.value = response.data
    } else if (response.message && response.message.includes('Authentication')) {
      emit('error', response.message)
    }
  } catch (error: any) {
    console.error('Failed to load pending users:', error)
    emit('error', error.response?.data?.message || t('admin.review.messages.error'))
  } finally {
    loading.value = false
  }
}

const approveUser = async (user: any) => { // Changed from UserType to any
  processingUsers.value.add(user.uuid)
  
  try {
    const response = await apiService.reviewUser({
      uuid: user.uuid,
      action: 'approve',
      language: t('lang')
    })
    
    if (response.success) {
      emit('success', response.msg || t('admin.review.messages.approve_success'))
      await loadPendingUsers()
    } else {
      emit('error', response.msg || t('admin.review.messages.error'))
    }
  } catch (error: any) {
    console.error('Failed to approve user:', error)
    emit('error', error.response?.data?.msg || t('admin.review.messages.error'))
  } finally {
    processingUsers.value.delete(user.uuid)
  }
}

const openRejectModal = (user: any) => { // Changed from UserType to any
  rejectModal.value = {
    show: true,
    user,
    reason: '',
    processing: false
  }
}

const closeRejectModal = () => {
  rejectModal.value = {
    show: false,
    user: null,
    reason: '',
    processing: false
  }
}

const confirmReject = async () => {
  if (!rejectModal.value.user) return
  
  rejectModal.value.processing = true
  
  try {
    const response = await apiService.reviewUser({
      uuid: rejectModal.value.user.uuid,
      action: 'reject',
      reason: rejectModal.value.reason || undefined,
      language: t('lang')
    })
    
    if (response.success) {
      emit('success', response.msg || t('admin.review.messages.reject_success'))
      await loadPendingUsers()
      closeRejectModal()
    } else {
      emit('error', response.msg || t('admin.review.messages.error'))
    }
  } catch (error: any) {
    console.error('Failed to reject user:', error)
    emit('error', error.response?.data?.msg || t('admin.review.messages.error'))
  } finally {
    rejectModal.value.processing = false
  }
}

onMounted(() => {
  loadPendingUsers()

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
          }
        } catch {}
      };
    } catch {}
  }
})
</script>
