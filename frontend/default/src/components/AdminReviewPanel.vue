<template>
  <!-- Minimalist pending review panel -->
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center space-x-3">
        <h2 class="section-title">{{ $t('admin.pendingList') }}</h2>
        <span v-if="users.length > 0" class="status-badge status-pending">
          {{ users.length }} {{ $t('admin.pendingCount') }}
        </span>
      </div>

      <button @click="fetchList" class="inline-flex items-center justify-center w-10 h-10 rounded-lg border border-gray-300 bg-white hover:bg-gray-50 transition-colors" :disabled="loading">
        <svg class="h-5 w-5" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15">
          </path>
        </svg>
      </button>
    </div>

    <!-- Content -->
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
            d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
        </svg>
        <p class="text-gray-500">{{ $t('admin.noPending') }}</p>
        <p class="text-sm text-gray-400 mt-2">{{ $t('admin.noPendingDesc') }}</p>
      </div>

      <div v-else class="overflow-x-auto">
        <table class="table-minimal">
          <thead class="table-header">
            <tr>
              <th class="table-header-cell">{{ $t('admin.username') }}</th>
              <th class="table-header-cell">{{ $t('admin.email') }}</th>
              <th class="table-header-cell">{{ $t('admin.time') }}</th>
              <th class="table-header-cell">{{ $t('admin.action') }}</th>
            </tr>
          </thead>
          <tbody class="table-body">
            <tr v-for="user in users" :key="user.uuid" class="hover:bg-gray-50">
              <td class="table-cell">
                <div class="flex items-center">
                  <div class="h-8 w-8 rounded-full bg-yellow-100 flex items-center justify-center mr-3">
                    <span class="text-xs font-medium text-yellow-600">{{ user.username?.charAt(0)?.toUpperCase()
                    }}</span>
                  </div>
                  <span class="font-medium">{{ user.username }}</span>
                </div>
              </td>
              <td class="table-cell-secondary">{{ user.email || user.qq }}</td>
              <td class="table-cell-secondary">{{ formatTime(user.regTime) }}</td>
              <td class="table-cell">
                <div class="flex items-center space-x-2">
                  <button @click="review(user.uuid, true)"
                    class="bg-green-600 text-white text-xs px-3 py-1 rounded-lg hover:bg-green-700 transition-colors">
                    <svg class="w-3 h-3 mr-1 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                    </svg>
                    {{ $t('admin.approve') }}
                  </button>
                  <button @click="review(user.uuid, false)" class="btn-danger text-xs px-3 py-1">
                    <svg class="w-3 h-3 mr-1 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12">
                      </path>
                    </svg>
                    {{ $t('admin.reject') }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Message display -->
    <div v-if="msg" class="p-4 rounded-lg"
      :class="msgType === 'error' ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'">
      <div class="flex items-center">
        <svg v-if="msgType === 'success'" class="h-5 w-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd"
            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
            clip-rule="evenodd"></path>
        </svg>
        <svg v-else class="h-5 w-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd"
            d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
            clip-rule="evenodd"></path>
        </svg>
        <span class="text-sm font-medium">{{ msg }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, inject, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { User, ApiResponse } from '@/types'

const { t } = useI18n()
const users = ref<User[]>([])
const loading = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')
const token = inject<Ref<string>>('token')

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

async function fetchList(): Promise<void> {
  loading.value = true
  msg.value = ''
  try {
    const res = await fetch('/api/pending-list?language=' + t('lang'), {
      headers: getAuthHeaders()
    })
    const data: ApiResponse = await res.json()
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

async function review(uuid: string, approve: boolean): Promise<void> {
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
    const data: ApiResponse = await res.json()
    if (data.success) {
      msg.value = data.msg || t('admin.reviewSuccess')
      msgType.value = 'success'
      await fetchList()
    } else {
      msg.value = data.msg || data.message || t('admin.reviewFailed')
      msgType.value = 'error'
    }
  } catch (e) {
    msg.value = t('admin.reviewFailed')
    msgType.value = 'error'
  }
}

function formatTime(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString()
}

onMounted(fetchList)
</script>