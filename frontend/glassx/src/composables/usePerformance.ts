import { ref, onMounted, onUnmounted } from 'vue'

/**
 * Performance optimization composable for managing animations and resource usage
 */
export function usePerformance() {
  const isReducedMotion = ref(false)
  const isLowPerformance = ref(false)
  const frameRate = ref(60)
  
  // Check for reduced motion preference
  const checkReducedMotion = () => {
    if (typeof window !== 'undefined') {
      const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
      isReducedMotion.value = mediaQuery.matches
      
      // Listen for changes
      mediaQuery.addEventListener('change', (e) => {
        isReducedMotion.value = e.matches
      })
    }
  }
  
  // Aggressive low performance device detection
  const detectPerformance = () => {
    if (typeof navigator !== 'undefined') {
      // Check for low-end devices
      const connection = (navigator as any).connection || (navigator as any).mozConnection || (navigator as any).webkitConnection
      
      if (connection) {
        // More aggressive connection-based detection
        isLowPerformance.value = connection.effectiveType === 'slow-2g' || 
                                 connection.effectiveType === '2g' ||
                                 connection.effectiveType === '3g'
      }
      
      // More aggressive CPU detection
      if (navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 4) {
        isLowPerformance.value = true
      }
      
      // More aggressive memory detection
      const memory = (navigator as any).deviceMemory
      if (memory && memory <= 4) {
        isLowPerformance.value = true
      }
      
      // Screen size based detection (mobile devices)
      if (window.innerWidth <= 1024) {
        isLowPerformance.value = true
      }
      
      // User agent based detection for older browsers
      const userAgent = navigator.userAgent.toLowerCase()
      if (userAgent.includes('mobile') || 
          userAgent.includes('android') ||
          userAgent.includes('iphone') ||
          userAgent.includes('ipad')) {
        isLowPerformance.value = true
      }
    }
  }
  
  // Throttle function for performance-sensitive operations
  const throttle = <T extends (...args: any[]) => any>(
    func: T,
    delay: number
  ): ((...args: Parameters<T>) => void) => {
    let timeoutId: NodeJS.Timeout | null = null
    let lastExecTime = 0
    
    return (...args: Parameters<T>) => {
      const currentTime = Date.now()
      
      if (currentTime - lastExecTime > delay) {
        func(...args)
        lastExecTime = currentTime
      } else {
        if (timeoutId) clearTimeout(timeoutId)
        timeoutId = setTimeout(() => {
          func(...args)
          lastExecTime = Date.now()
        }, delay - (currentTime - lastExecTime))
      }
    }
  }
  
  // Debounce function for input handling
  const debounce = <T extends (...args: any[]) => any>(
    func: T,
    delay: number
  ): ((...args: Parameters<T>) => void) => {
    let timeoutId: NodeJS.Timeout | null = null
    
    return (...args: Parameters<T>) => {
      if (timeoutId) clearTimeout(timeoutId)
      timeoutId = setTimeout(() => func(...args), delay)
    }
  }
  
  // Request animation frame with fallback
  const requestAnimationFrame = (callback: FrameRequestCallback): number => {
    if (typeof window !== 'undefined' && window.requestAnimationFrame) {
      return window.requestAnimationFrame(callback)
    }
    return setTimeout(callback, 1000 / frameRate.value) as any
  }
  
  // Cancel animation frame with fallback
  const cancelAnimationFrame = (id: number): void => {
    if (typeof window !== 'undefined' && window.cancelAnimationFrame) {
      window.cancelAnimationFrame(id)
    } else {
      clearTimeout(id)
    }
  }
  
  // Get optimal animation duration based on performance
  const getOptimalDuration = (baseDuration: number): number => {
    if (isReducedMotion.value) return 0
    if (isLowPerformance.value) return baseDuration * 0.5
    return baseDuration
  }
  
  // Get optimal frame rate for animations
  const getOptimalFrameRate = (): number => {
    if (isLowPerformance.value) return 30
    return 60
  }
  
  // Intersection Observer for lazy loading
  const createIntersectionObserver = (
    callback: IntersectionObserverCallback,
    options?: IntersectionObserverInit
  ): IntersectionObserver | null => {
    if (typeof window !== 'undefined' && 'IntersectionObserver' in window) {
      return new IntersectionObserver(callback, {
        rootMargin: '50px',
        threshold: 0.1,
        ...options
      })
    }
    return null
  }
  
  onMounted(() => {
    checkReducedMotion()
    detectPerformance()
    frameRate.value = getOptimalFrameRate()
  })
  
  return {
    isReducedMotion,
    isLowPerformance,
    frameRate,
    throttle,
    debounce,
    requestAnimationFrame,
    cancelAnimationFrame,
    getOptimalDuration,
    getOptimalFrameRate,
    createIntersectionObserver
  }
}

/**
 * Lazy loading composable for images and components
 */
export function useLazyLoading() {
  const { createIntersectionObserver } = usePerformance()
  const loadedElements = ref(new Set<string>())
  
  const observeElement = (
    element: Element,
    callback: () => void,
    id?: string
  ) => {
    if (id && loadedElements.value.has(id)) {
      callback()
      return
    }
    
    const observer = createIntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          callback()
          if (id) loadedElements.value.add(id)
          observer?.unobserve(element)
        }
      })
    })
    
    if (observer) {
      observer.observe(element)
    } else {
      // Fallback for browsers without IntersectionObserver
      callback()
      if (id) loadedElements.value.add(id)
    }
  }
  
  return {
    observeElement,
    loadedElements
  }
}