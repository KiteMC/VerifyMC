<template>
  <!-- Pagination component with default theme styling -->
  <div v-if="totalPages > 1" class="flex items-center justify-between mt-6 p-4 bg-gray-50 rounded-lg">
    <!-- Page info -->
    <div class="text-sm text-gray-600">
      {{ $t('pagination.showing') }} {{ startItem }} - {{ endItem }} {{ $t('pagination.of') }} {{ totalCount }} {{ $t('pagination.items') }}
    </div>
    
    <!-- Pagination controls -->
    <div class="flex items-center space-x-2">
      <!-- Previous button -->
      <button
        @click="goToPage(currentPage - 1)"
        :disabled="!hasPrev"
        :class="[
          'px-3 py-2 rounded-md text-sm font-medium transition-colors',
          hasPrev 
            ? 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50' 
            : 'bg-gray-100 text-gray-400 border border-gray-200 cursor-not-allowed'
        ]"
      >
        {{ $t('pagination.previous') }}
      </button>
      
      <!-- Page numbers -->
      <div class="flex items-center space-x-1">
        <!-- First page -->
        <button
          v-if="showFirstPage"
          @click="goToPage(1)"
          :class="[
            'px-3 py-2 rounded-md text-sm font-medium transition-colors',
            currentPage === 1
              ? 'bg-blue-500 text-white border border-blue-500'
              : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'
          ]"
        >
          1
        </button>
        
        <!-- Left ellipsis -->
        <span v-if="showLeftEllipsis" class="px-2 text-gray-500">...</span>
        
        <!-- Visible page numbers -->
        <button
          v-for="page in visiblePages"
          :key="page"
          @click="goToPage(page)"
          :class="[
            'px-3 py-2 rounded-md text-sm font-medium transition-colors',
            currentPage === page
              ? 'bg-blue-500 text-white border border-blue-500'
              : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'
          ]"
        >
          {{ page }}
        </button>
        
        <!-- Right ellipsis -->
        <span v-if="showRightEllipsis" class="px-2 text-gray-500">...</span>
        
        <!-- Last page -->
        <button
          v-if="showLastPage"
          @click="goToPage(totalPages)"
          :class="[
            'px-3 py-2 rounded-md text-sm font-medium transition-colors',
            currentPage === totalPages
              ? 'bg-blue-500 text-white border border-blue-500'
              : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'
          ]"
        >
          {{ totalPages }}
        </button>
      </div>
      
      <!-- Next button -->
      <button
        @click="goToPage(currentPage + 1)"
        :disabled="!hasNext"
        :class="[
          'px-3 py-2 rounded-md text-sm font-medium transition-colors',
          hasNext 
            ? 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50' 
            : 'bg-gray-100 text-gray-400 border border-gray-200 cursor-not-allowed'
        ]"
      >
        {{ $t('pagination.next') }}
      </button>
    </div>
    
    <!-- Page size selector -->
    <div class="flex items-center space-x-2 text-sm">
      <span class="text-gray-600">{{ $t('pagination.per_page') }}:</span>
      <select
        :value="pageSize"
        @change="changePageSize($event.target.value)"
        class="bg-white border border-gray-300 rounded-md px-2 py-1 text-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
      >
        <option value="10">10</option>
        <option value="20">20</option>
        <option value="50">50</option>
        <option value="100">100</option>
      </select>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface Props {
  currentPage: number
  totalPages: number
  totalCount: number
  pageSize: number
  hasNext: boolean
  hasPrev: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'page-change': [page: number]
  'page-size-change': [pageSize: number]
}>()

/**
 * Calculate start item number for current page
 */
const startItem = computed(() => {
  return (props.currentPage - 1) * props.pageSize + 1
})

/**
 * Calculate end item number for current page
 */
const endItem = computed(() => {
  return Math.min(props.currentPage * props.pageSize, props.totalCount)
})

/**
 * Calculate visible page numbers around current page
 */
const visiblePages = computed(() => {
  const delta = 2 // Number of pages to show on each side of current page
  const range = []
  
  for (let i = Math.max(2, props.currentPage - delta); 
       i <= Math.min(props.totalPages - 1, props.currentPage + delta); 
       i++) {
    range.push(i)
  }
  
  return range
})

/**
 * Check if first page should be shown separately
 */
const showFirstPage = computed(() => {
  return props.totalPages > 1 && !visiblePages.value.includes(1)
})

/**
 * Check if last page should be shown separately
 */
const showLastPage = computed(() => {
  return props.totalPages > 1 && !visiblePages.value.includes(props.totalPages)
})

/**
 * Check if left ellipsis should be shown
 */
const showLeftEllipsis = computed(() => {
  return visiblePages.value.length > 0 && visiblePages.value[0] > 2
})

/**
 * Check if right ellipsis should be shown
 */
const showRightEllipsis = computed(() => {
  return visiblePages.value.length > 0 && visiblePages.value[visiblePages.value.length - 1] < props.totalPages - 1
})

/**
 * Navigate to specific page
 * @param page Target page number
 */
const goToPage = (page: number) => {
  if (page >= 1 && page <= props.totalPages && page !== props.currentPage) {
    emit('page-change', page)
  }
}

/**
 * Change page size
 * @param newPageSize New page size value
 */
const changePageSize = (newPageSize: string) => {
  const size = parseInt(newPageSize)
  if (size > 0 && size !== props.pageSize) {
    emit('page-size-change', size)
  }
}
</script>