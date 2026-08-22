<template>
  <div class="max-w-[900px] mx-auto">
    <div class="flex items-center justify-end mb-4">
      <Button
        @click="refreshStatus"
        :disabled="loading"
        variant="outline"
        size="sm"
        class="gap-2"
      >
        <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        {{ $t('common.refresh') }}
      </Button>
    </div>

    <!-- Loading State -->
    <div v-if="loading && !status" class="flex flex-col items-center justify-center py-16 text-white/60">
      <RefreshCw class="w-10 h-10 animate-spin text-purple-500 mb-4" />
      <p>{{ $t('common.loading') }}</p>
    </div>

    <!-- Status Content -->
    <div v-else class="space-y-4">
      <!-- Server Status Card -->
      <Card
        class="p-6"
        :class="status?.online
          ? 'bg-[#192720] border border-green-500/30'
          : 'bg-[#2b1d22] border border-red-500/30'"
      >
        <div class="flex items-center gap-3 mb-3">
          <div
            class="w-3 h-3 rounded-full animate-pulse"
            :class="status?.online
              ? 'bg-green-500 shadow-[0_0_12px_rgba(34,197,94,0.5)]'
              : 'bg-red-500 shadow-[0_0_12px_rgba(239,68,68,0.5)]'"
          ></div>
          <span class="text-lg font-semibold text-white">
            {{ status?.online ? $t('dashboard.server_status.online') : $t('dashboard.server_status.offline') }}
          </span>
        </div>
        <div class="text-sm text-white/70" v-if="status?.motd">
          {{ status.motd }}
        </div>
      </Card>

      <!-- Stats Grid -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <!-- Players -->
        <Card class="p-5 flex items-start gap-4 glass-card-hover">
          <div class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 bg-blue-500/20 text-blue-500">
            <Users class="w-6 h-6" />
          </div>
          <div class="flex flex-col">
            <span class="text-xl font-bold text-white">{{ status?.players?.online ?? 0 }} / {{ status?.players?.max ?? 0 }}</span>
            <span class="text-xs text-white/50 uppercase tracking-wider">{{ $t('dashboard.server_status.players') }}</span>
          </div>
        </Card>

        <!-- TPS -->
        <Card class="p-5 flex items-start gap-4 glass-card-hover">
          <div
            class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0"
            :class="{
              'bg-green-500/20 text-green-500': !tpsStatus || tpsStatus === 'good',
              'bg-yellow-500/20 text-yellow-500': tpsStatus === 'warning',
              'bg-red-500/20 text-red-500': tpsStatus === 'bad'
            }"
          >
            <Activity class="w-6 h-6" />
          </div>
          <div class="flex flex-col">
            <span class="text-xl font-bold text-white">{{ status?.tps?.toFixed(1) ?? '—' }}</span>
            <span class="text-xs text-white/50 uppercase tracking-wider">{{ $t('dashboard.server_status.tps') }}</span>
          </div>
        </Card>

        <!-- Memory -->
        <Card class="p-5 flex flex-col gap-4 relative overflow-hidden">
          <div class="flex items-start gap-4 z-10">
            <div class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 bg-purple-500/20 text-purple-500">
              <Database class="w-6 h-6" />
            </div>
            <div class="flex flex-col">
              <span class="text-xl font-bold text-white">{{ formatMemory(status?.memory?.used) }} / {{ formatMemory(status?.memory?.max) }}</span>
              <span class="text-xs text-white/50 uppercase tracking-wider">{{ $t('dashboard.server_status.memory') }}</span>
            </div>
          </div>
          <div class="w-full h-1 bg-white/10 rounded-full overflow-hidden z-10" v-if="status?.memory">
            <div class="h-full bg-blue-500 transition-all duration-300" :style="{ width: memoryPercent + '%' }"></div>
          </div>
        </Card>

        <!-- Version -->
        <Card class="p-5 flex items-start gap-4 glass-card-hover">
          <div class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 bg-pink-500/20 text-pink-500">
            <Tag class="w-6 h-6" />
          </div>
          <div class="flex flex-col">
            <span class="text-xl font-bold text-white">{{ status?.version ?? '—' }}</span>
            <span class="text-xs text-white/50 uppercase tracking-wider">{{ $t('dashboard.server_status.version') }}</span>
          </div>
        </Card>
      </div>

      <!-- Online Players -->
      <Card class="p-6" v-if="status?.players?.list?.length">
        <h3 class="flex items-center gap-2 text-base font-semibold text-white mb-4">
          <Users class="w-5 h-5" />
          {{ $t('dashboard.server_status.online_players') }}
        </h3>
        <div class="flex flex-wrap gap-3">
          <div
            v-for="player in status.players.list"
            :key="player.uuid || player.name"
            class="flex items-center gap-2 py-2 px-3 bg-white/5 border border-white/10 rounded-lg"
          >
            <img
              :src="`https://mc-heads.net/avatar/${player.uuid || player.name}/32`"
              :alt="player.name"
              class="w-6 h-6 rounded pixelated"
            />
            <span class="text-sm font-medium text-white">{{ player.name }}</span>
          </div>
        </div>
      </Card>

      <!-- No Players Online -->
      <Card v-else-if="status?.online" class="p-8 flex flex-col items-center justify-center text-center text-white/40">
        <UserX class="w-8 h-8 mb-2" />
        <p>{{ $t('dashboard.server_status.no_players') }}</p>
      </Card>

      <!-- Error Notice -->
      <div v-if="errorMessage" class="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-sm">
        <AlertCircle class="w-4 h-4" />
        <span>{{ errorMessage }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Users,
  Activity,
  Database,
  Tag,
  RefreshCw,
  UserX,
  AlertCircle,
} from 'lucide-vue-next'
import { apiService } from '@/services/api'
import Card from '../ui/Card.vue'
import Button from '../ui/Button.vue'

const { t } = useI18n()
const loading = ref(false)
const errorMessage = ref('')

interface ServerStatus {
  online: boolean
  players?: {
    online: number
    max: number
    list?: Array<{ name: string; uuid?: string }>
  }
  version?: string
  tps?: number
  memory?: {
    used: number
    max: number
  }
  motd?: string
}

const status = ref<ServerStatus | null>(null)

const tpsStatus = computed(() => {
  const tps = status.value?.tps
  if (!tps) return ''
  if (tps >= 18) return 'good'
  if (tps >= 15) return 'warning'
  return 'bad'
})

const memoryPercent = computed(() => {
  if (!status.value?.memory) return 0
  return Math.round((status.value.memory.used / status.value.memory.max) * 100)
})

const formatMemory = (mb?: number): string => {
  if (!mb) return '—'
  if (mb >= 1024) {
    return `${(mb / 1024).toFixed(1)} GB`
  }
  return `${mb} MB`
}

const loadStatus = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await apiService.getServerStatus()
    if (response.success && response.data) {
      status.value = response.data
    } else {
      status.value = null
      errorMessage.value = response.message || t('dashboard.server_status.load_failed')
    }
  } catch (error) {
    console.error('Failed to load server status:', error)
    status.value = null
    errorMessage.value = t('dashboard.server_status.load_failed')
  } finally {
    loading.value = false
  }
}

const refreshStatus = () => {
  loadStatus()
}

onMounted(() => {
  loadStatus()
})
</script>
