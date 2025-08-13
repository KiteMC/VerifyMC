<template>
  <!-- Version update notification component with glassx theme styling -->
  <div v-if="showNotification" class="version-notification">
    <div class="version-notification-backdrop" @click="dismissNotification"></div>
    <div class="version-notification-dialog">
      <!-- Header -->
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center space-x-3">
          <div class="version-icon">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10"></path>
            </svg>
          </div>
          <h3 class="text-xl font-semibold text-white">{{ $t('version.update_available') }}</h3>
        </div>
        <button 
          @click="dismissNotification"
          class="text-white/60 hover:text-white transition-colors"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>
      </div>
      
      <!-- Version info -->
      <div class="version-info mb-6">
        <div class="flex justify-between items-center mb-2">
          <span class="text-white/80">{{ $t('version.current_version') }}:</span>
          <span class="text-white font-mono">{{ versionInfo.currentVersion }}</span>
        </div>
        <div class="flex justify-between items-center mb-4">
          <span class="text-white/80">{{ $t('version.latest_version') }}:</span>
          <span class="text-green-400 font-mono font-semibold">{{ versionInfo.latestVersion }}</span>
        </div>
        <div class="text-white/70 text-sm">
          {{ $t('version.update_description') }}
        </div>
      </div>
      
      <!-- Actions -->
      <div class="flex gap-3 justify-end">
        <button 
          @click="remindLater"
          class="px-4 py-2 text-white/80 hover:text-white border border-white/20 hover:border-white/40 rounded-lg transition-colors"
        >
          {{ $t('version.remind_later') }}
        </button>
        <button 
          @click="openDownloadPage"
          class="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg transition-colors flex items-center space-x-2"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path>
          </svg>
          <span>{{ $t('version.download_now') }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { apiService } from '@/services/api'

const { t } = useI18n()

interface VersionInfo {
  currentVersion: string
  latestVersion: string
  updateAvailable: boolean
  releasesUrl: string
}

const showNotification = ref(false)
const versionInfo = ref<VersionInfo>({
  currentVersion: '',
  latestVersion: '',
  updateAvailable: false,
  releasesUrl: ''
})

let checkInterval: NodeJS.Timeout | null = null

/**
 * Check for version updates
 */
const checkForUpdates = async () => {
  try {
    const response = await apiService.checkVersion()
    
    if (response.success && response.updateAvailable) {
      versionInfo.value = {
        currentVersion: response.currentVersion || '',
        latestVersion: response.latestVersion || '',
        updateAvailable: response.updateAvailable || false,
        releasesUrl: response.releasesUrl || ''
      }
      
      // Check if user has dismissed this version
      const dismissedVersion = localStorage.getItem('dismissed_version')
      if (dismissedVersion !== response.latestVersion) {
        showNotification.value = true
      }
    }
  } catch (error) {
    console.warn('Version check failed:', error)
  }
}

/**
 * Dismiss the notification
 */
const dismissNotification = () => {
  showNotification.value = false
  // Remember that user dismissed this version
  localStorage.setItem('dismissed_version', versionInfo.value.latestVersion)
}

/**
 * Remind later (dismiss for this session only)
 */
const remindLater = () => {
  showNotification.value = false
  // Don't save to localStorage, so it will show again on next session
}

/**
 * Open download page in new tab
 */
const openDownloadPage = () => {
  if (versionInfo.value.releasesUrl) {
    window.open(versionInfo.value.releasesUrl, '_blank')
  }
  dismissNotification()
}

onMounted(() => {
  // Check immediately
  checkForUpdates()
  
  // Check every 30 minutes
  checkInterval = setInterval(checkForUpdates, 30 * 60 * 1000)
})

onUnmounted(() => {
  if (checkInterval) {
    clearInterval(checkInterval)
  }
})
</script>

<style scoped>
/* Version notification overlay */
.version-notification {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

/* Background backdrop */
.version-notification-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
}

/* Dialog container */
.version-notification-dialog {
  position: relative;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 1rem;
  padding: 2rem;
  width: 100%;
  max-width: 32rem;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  margin: auto;
  animation: slideIn 0.3s ease-out;
}

/* Version icon */
.version-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  background: rgba(59, 130, 246, 0.2);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 0.75rem;
  color: rgb(59, 130, 246);
}

/* Version info section */
.version-info {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 0.75rem;
  padding: 1rem;
}

/* Animation */
@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>