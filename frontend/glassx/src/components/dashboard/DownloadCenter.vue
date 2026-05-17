<template>
  <div class="max-w-4xl mx-auto">
    <div class="mb-6" v-if="resources.length > 0">
      <p class="text-white/60 m-0">{{ $t('dashboard.downloads.description') }}</p>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex flex-col items-center justify-center py-16 text-white/60">
      <div class="w-10 h-10 border-4 border-white/10 border-t-purple-500 rounded-full animate-spin mb-4"></div>
      <p>{{ $t('common.loading') }}</p>
    </div>

    <!-- Resources Grid -->
    <div v-else-if="resources.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
      <Card
        v-for="resource in resources"
        :key="resource.id"
        class="p-6 flex flex-col h-full hover:border-blue-500/30 transition-all"
      >
        <div class="w-14 h-14 rounded-2xl bg-gradient-to-br from-blue-500/20 to-purple-500/20 flex items-center justify-center text-purple-500 mb-4">
          <component :is="getResourceIcon(resource.icon)" class="w-8 h-8" />
        </div>
        <div class="flex-1 mb-4">
          <h3 class="text-lg font-semibold text-white mb-2">{{ resource.name }}</h3>
          <p class="text-sm text-white/60 mb-3 leading-relaxed">{{ resource.description }}</p>
          <div class="flex gap-4">
            <span v-if="resource.version" class="flex items-center gap-1.5 text-xs text-white/50">
              <Tag class="w-3 h-3" />
              {{ resource.version }}
            </span>
            <span v-if="resource.size" class="flex items-center gap-1.5 text-xs text-white/50">
              <HardDrive class="w-3 h-3" />
              {{ resource.size }}
            </span>
          </div>
        </div>
        <Button 
          variant="default" 
          class="w-full mt-auto"
          @click="openUrl(resource.url)"
        >
          <Download class="w-4 h-4 mr-2" />
          {{ $t('dashboard.downloads.download') }}
        </Button>
      </Card>
    </div>

    <!-- Empty State -->
    <div v-else class="flex flex-col items-center justify-center py-16 text-center">
      <div class="w-20 h-20 rounded-3xl bg-white/5 flex items-center justify-center text-white/30 mb-6">
        <Package class="w-16 h-16" />
      </div>
      <h3 class="text-xl font-semibold text-white mb-2">{{ $t('dashboard.downloads.no_resources') }}</h3>
      <p class="text-white/50 m-0">{{ $t('dashboard.downloads.no_resources_hint') }}</p>
    </div>

    <!-- Instructions -->
    <Card class="p-6">
      <h3 class="flex items-center gap-2 text-lg font-semibold text-white mb-5">
        <HelpCircle class="w-5 h-5" />
        {{ $t('dashboard.downloads.instructions_title') }}
      </h3>
      <div class="flex flex-col gap-4">
        <div class="flex items-start gap-3">
          <span class="w-6 h-6 rounded-lg bg-gradient-to-br from-blue-500 to-purple-500 flex items-center justify-center text-xs font-bold text-white shrink-0">1</span>
          <p class="text-white/70 text-sm leading-relaxed m-0">{{ $t('dashboard.downloads.instruction_1') }}</p>
        </div>
        <div class="flex items-start gap-3">
          <span class="w-6 h-6 rounded-lg bg-gradient-to-br from-blue-500 to-purple-500 flex items-center justify-center text-xs font-bold text-white shrink-0">2</span>
          <p class="text-white/70 text-sm leading-relaxed m-0">{{ $t('dashboard.downloads.instruction_2') }}</p>
        </div>
        <div class="flex items-start gap-3">
          <span class="w-6 h-6 rounded-lg bg-gradient-to-br from-blue-500 to-purple-500 flex items-center justify-center text-xs font-bold text-white shrink-0">3</span>
          <p class="text-white/70 text-sm leading-relaxed m-0">{{ $t('dashboard.downloads.instruction_3') }}</p>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue'
import {
  Download,
  Package,
  Tag,
  HardDrive,
  HelpCircle,
  FileArchive,
  Gamepad2,
  Monitor,
} from 'lucide-vue-next'
import { apiService, type DownloadResource } from '@/services/api'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'

const isSafeUrl = (url: string): boolean => {
  try {
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      return false
    }
    const parsedUrl = new URL(url)
    return ['http:', 'https:'].includes(parsedUrl.protocol)
  } catch {
    return false
  }
}

const getSafeUrl = (url: string | undefined): string => {
  if (!url) return '#'
  return isSafeUrl(url) ? url : '#'
}

const openUrl = (url: string | undefined) => {
  const safeUrl = getSafeUrl(url)
  if (safeUrl !== '#') {
    window.open(safeUrl, '_blank')
  }
}

const loading = ref(true)
const resources = ref<DownloadResource[]>([])

const getResourceIcon = (icon?: string) => {
  switch (icon) {
    case 'gamepad':
      return markRaw(Gamepad2)
    case 'monitor':
      return markRaw(Monitor)
    case 'package':
    default:
      return markRaw(FileArchive)
  }
}

const loadResources = async () => {
  try {
    const response = await apiService.getDownloadResources()
    if (response.success && response.resources) {
      resources.value = response.resources
    } else {
      resources.value = []
    }
  } catch (error) {
    console.error('Failed to load resources:', error)
    resources.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadResources()
})
</script>
