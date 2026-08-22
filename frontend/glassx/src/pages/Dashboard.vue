<template>
  <div class="min-h-screen w-full relative">
    <!-- Mobile Overlay -->
    <div v-if="isOpen" class="fixed inset-0 bg-black/60 z-40 lg:hidden" @click="setOpen(false)"></div>

    <!-- Sidebar -->
    <DashboardSidebar
      :active-section="activeSection"
      :user-info="userInfo"
      :is-admin="isAdmin"
      @set-active-section="setActiveSection"
      @logout="handleLogout"
    />

    <!-- Main Content -->
    <main 
      class="min-h-screen transition-all duration-300 flex flex-col"
      :class="[
        isCollapsed ? 'lg:pl-20' : 'lg:pl-64'
      ]"
    >
      <div class="pt-20 lg:pt-8 px-4 lg:px-8 pb-8 flex-1">
        <div class="mb-6 flex items-center justify-between">
          <div>
            <p class="text-xs uppercase tracking-wider text-slate-400 mb-1">{{ isAdmin ? $t('dashboard.sections.admin') : $t('dashboard.sections.player') }}</p>
            <h1 class="text-2xl font-semibold text-slate-100">{{ currentSectionTitle }}</h1>
          </div>
          <div class="hidden">
            <LanguageSwitcher />
          </div>
        </div>

        <div class="relative">
          <!-- Player Sections -->
          <ProfileSection v-if="activeSection === 'profile'" />
          <DownloadCenter v-if="activeSection === 'downloads'" />

          <!-- Admin Sections -->
          <ServerStatus v-if="activeSection === 'server-status' && isAdmin" />
          <UserManagement v-if="activeSection === 'user-management' && isAdmin" />
          <AuditLog v-if="activeSection === 'audit-log' && isAdmin" />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { sessionService } from '@/services/session'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import DashboardSidebar from '@/components/dashboard/DashboardSidebar.vue'
import type { UserInfo } from '@/types'
import { getPlayerMenuItems, getAdminMenuItems } from '@/config/menu'
import { useSidebar } from '@/composables/useSidebar'

// Section components - lazy loaded for better performance
const ProfileSection = defineAsyncComponent(() => import('@/components/dashboard/ProfileSection.vue'))
const DownloadCenter = defineAsyncComponent(() => import('@/components/dashboard/DownloadCenter.vue'))
const ServerStatus = defineAsyncComponent(() => import('@/components/dashboard/ServerStatus.vue'))
const UserManagement = defineAsyncComponent(() => import('@/components/dashboard/UserManagement.vue'))
const AuditLog = defineAsyncComponent(() => import('@/components/dashboard/AuditLog.vue'))

const { t } = useI18n()
const router = useRouter()
const { isCollapsed, isOpen, setTrigger, setOpen, setCollapse } = useSidebar()

const activeSection = ref('profile')
const userInfo = ref<UserInfo | null>(null)

const isAdmin = computed(() => sessionService.isAdmin())

const currentSectionTitle = computed(() => {
  const allItems = [...getPlayerMenuItems(t), ...getAdminMenuItems(t)]
  const item = allItems.find(i => i.id === activeSection.value)
  return item?.label || ''
})

const setActiveSection = (section: string) => {
  activeSection.value = section
  setOpen(false)
}

const handleLogout = () => {
  sessionService.clearToken()
  router.push('/')
}

onMounted(() => {
  setTrigger(true)
  // Check authentication
  if (!sessionService.isAuthenticated()) {
    sessionService.redirectToLogin()
    return
  }

  // Load user info
  userInfo.value = sessionService.getUserInfo()

  // Set default section based on user role
  if (isAdmin.value) {
    activeSection.value = 'user-management'
  } else {
    activeSection.value = 'profile'
  }
})

onUnmounted(() => {
  setTrigger(false)
  setOpen(false)
  setCollapse(false)
})
</script>
