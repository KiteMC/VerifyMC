<template>
  <div class="discord-link">
    <!-- Already linked state -->
    <div v-if="linked" class="linked-state">
      <div class="discord-badge">
        <svg class="discord-icon" viewBox="0 0 127.14 96.36" fill="#5865F2">
          <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.7,77.7,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.25,105.25,0,0,0,126.6,80.22h0C129.24,52.84,122.09,29.11,107.7,8.07ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z"/>
        </svg>
        <span class="discord-username">{{ discordUser?.global_name || discordUser?.username || 'Discord' }}</span>
        <span class="linked-check">✓</span>
      </div>
      <p class="linked-text">{{ $t('discord.linked') }}</p>
    </div>

    <!-- Not linked state -->
    <div v-else class="unlinked-state">
      <button 
        type="button"
        @click="startDiscordLink"
        :disabled="loading || !username"
        class="discord-btn"
      >
        <svg v-if="!loading" class="discord-icon" viewBox="0 0 127.14 96.36" fill="currentColor">
          <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.7,77.7,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.25,105.25,0,0,0,126.6,80.22h0C129.24,52.84,122.09,29.11,107.7,8.07ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z"/>
        </svg>
        <div v-else class="spinner"></div>
        <span>{{ $t('discord.link_button') }}</span>
      </button>
      <p v-if="required" class="required-text">{{ $t('discord.required_hint') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { apiService } from '@/services/api'
import { useNotification } from '@/composables/useNotification'

const props = defineProps<{
  username: string
  required?: boolean
}>()

const emit = defineEmits<{
  (e: 'linked', user: any): void
  (e: 'unlinked'): void
}>()

const { t } = useI18n()
const { success, error } = useNotification()

const loading = ref(false)
const linked = ref(false)
const discordUser = ref<any>(null)

// Poll for link status after opening Discord auth window
let pollInterval: number | null = null

const startDiscordLink = async () => {
  if (!props.username || loading.value) return
  
  loading.value = true
  
  try {
    const response = await apiService.getDiscordAuthUrl(props.username)
    
    if (response.success && response.auth_url) {
      // Open Discord auth in a new window
      const authWindow = window.open(response.auth_url, 'discord_auth', 'width=500,height=800')
      
      // Start polling for link status
      startPolling()
      
      // Check if window was closed
      const checkClosed = setInterval(() => {
        if (authWindow && authWindow.closed) {
          clearInterval(checkClosed)
          // Final check after window closes
          setTimeout(() => checkLinkStatus(), 1000)
        }
      }, 500)
    } else {
      error(response.msg || t('discord.link_failed'))
    }
  } catch (e) {
    console.error('Discord auth error:', e)
    error(t('discord.link_failed'))
  } finally {
    loading.value = false
  }
}

const checkLinkStatus = async () => {
  if (!props.username) {
    linked.value = false
    discordUser.value = null
    emit('unlinked')
    return
  }
  
  try {
    const response = await apiService.getDiscordStatus(props.username)

    if (response.success && response.linked) {
      const wasLinked = linked.value
      linked.value = true
      discordUser.value = response.user
      stopPolling()
      emit('linked', response.user)
      if (!wasLinked) {
        success(t('discord.link_success'))
      }
      return
    }

    if (linked.value) {
      emit('unlinked')
    }
    linked.value = false
    discordUser.value = null
  } catch (e) {
    console.error('Check Discord status error:', e)
  }
}

const startPolling = () => {
  stopPolling()
  // Poll every 2 seconds for up to 5 minutes
  let attempts = 0
  const maxAttempts = 150
  
  pollInterval = window.setInterval(() => {
    attempts++
    if (attempts >= maxAttempts) {
      stopPolling()
      return
    }
    checkLinkStatus()
  }, 2000)
}

const stopPolling = () => {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

// Watch for username changes to reset link status
watch(() => props.username, async (newUsername) => {
  if (newUsername) {
    await checkLinkStatus()
  } else {
    linked.value = false
    discordUser.value = null
  }
})

onMounted(() => {
  if (props.username) {
    checkLinkStatus()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.discord-link {
  margin: 0.5rem 0;
}

.linked-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.discord-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(88, 101, 242, 0.2);
  border: 1px solid rgba(88, 101, 242, 0.4);
  border-radius: 8px;
  padding: 0.5rem 1rem;
}

.discord-icon {
  width: 20px;
  height: 20px;
}

.discord-username {
  color: #fff;
  font-weight: 500;
}

.linked-check {
  color: #4ade80;
  font-weight: bold;
}

.linked-text {
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.875rem;
}

.unlinked-state {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.discord-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  background: #5865F2;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.75rem 1.5rem;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.discord-btn:hover:not(:disabled) {
  background: #4752c4;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(88, 101, 242, 0.4);
}

.discord-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.required-text {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.75rem;
  text-align: center;
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>

