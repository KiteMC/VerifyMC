<template>
  <div class="w-full">
    <div class="inline-flex h-10 items-center justify-center rounded-md bg-white/10 backdrop-blur-sm p-1 text-white/70 border border-white/20">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        @click="activeTab = tab.value"
        :class="[
          'inline-flex items-center justify-center whitespace-nowrap rounded-sm px-3 py-1.5 text-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/50 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50',
          activeTab === tab.value 
            ? 'bg-white/20 text-white shadow-sm' 
            : 'text-white/70 hover:text-white'
        ]"
      >
        {{ tab.label }}
      </button>
    </div>
    
    <div class="mt-2">
      <slot :active-tab="activeTab" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, provide } from 'vue'

interface Tab {
  value: string
  label: string
}

interface Props {
  tabs: Tab[]
  defaultTab?: string
}

const props = withDefaults(defineProps<Props>(), {
  defaultTab: ''
})

const activeTab = ref(props.defaultTab || props.tabs[0]?.value || '')

// Provide active tab to child components
provide('activeTab', activeTab)
</script> 