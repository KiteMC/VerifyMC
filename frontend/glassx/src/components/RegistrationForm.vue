<template>
  <div class="bg-white/10 backdrop-blur-xl border border-white/20 rounded-lg p-6 shadow-2xl relative overflow-hidden" :class="shouldShowPassword ? 'w-full max-w-md' : 'w-full max-w-sm'">
    <!-- 添加内部光效 -->
    <div class="absolute inset-0 bg-gradient-to-br from-white/5 to-transparent pointer-events-none"></div>
    
    <h2 class="text-2xl font-bold text-white mb-3 relative z-10">{{ $t('register.title') }}</h2>
    <p class="text-gray-300 mb-4 relative z-10">{{ $t('register.subtitle') }}</p>
    
    <form @submit.prevent="handleSubmit" class="space-y-4 relative z-10">
      <div class="space-y-3">
        <!-- Username Field -->
        <div>
          <label for="username" class="block text-sm font-medium text-white mb-1">
            {{ $t('register.form.username') }}
          </label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            :placeholder="$t('register.form.username_placeholder')"
            class="w-full px-4 py-2.5 bg-white/15 backdrop-blur-xl border border-white/25 rounded-lg text-white placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent transition-all duration-300 shadow-inner"
            :class="{ 'border-red-400 focus:ring-red-400': errors.username }"
            @blur="validateUsername"
          />
          <p v-if="errors.username" class="mt-1 text-sm text-red-400">{{ errors.username }}</p>
        </div>
        
        <!-- Email Field -->
        <div>
          <label for="email" class="block text-sm font-medium text-white mb-1">
            {{ $t('register.form.email') }}
          </label>
          <input
            id="email"
            v-model="form.email"
            type="email"
            :placeholder="$t('register.form.email_placeholder')"
            class="w-full px-4 py-2.5 bg-white/15 backdrop-blur-xl border border-white/25 rounded-lg text-white placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent transition-all duration-300 shadow-inner"
            :class="{ 'border-red-400 focus:ring-red-400': errors.email }"
            @blur="validateEmail"
          />
          <p v-if="errors.email" class="mt-1 text-sm text-red-400">{{ errors.email }}</p>
        </div>
        
        <!-- Password Field -->
        <div v-if="shouldShowPassword">
          <label for="password" class="block text-sm font-medium text-white mb-1">
            {{ $t('register.form.password') }}
          </label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            :placeholder="$t('register.form.password_placeholder')"
            class="w-full px-4 py-2.5 bg-white/15 backdrop-blur-xl border border-white/25 rounded-lg text-white placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent transition-all duration-300 shadow-inner"
            :class="{ 'border-red-400 focus:ring-red-400': errors.password }"
            @blur="validatePassword"
          />
          <p v-if="errors.password" class="mt-1 text-sm text-red-400">{{ errors.password }}</p>
          <p v-if="authmeConfig.password_regex" class="mt-1 text-xs text-gray-300">{{ $t('register.form.password_hint', { regex: authmeConfig.password_regex }) }}</p>
        </div>
        
        <!-- 验证码 Field -->
        <div>
          <label for="code" class="block text-sm font-medium text-white mb-1">
            {{ $t('register.form.code') }}
          </label>
          <div class="flex flex-col sm:flex-row gap-2">
            <input
              id="code"
              v-model="form.code"
              type="text"
              :placeholder="$t('register.form.code_placeholder')"
              class="w-full px-4 py-2.5 bg-white/15 backdrop-blur-xl border border-white/25 rounded-lg text-white placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent transition-all duration-300 shadow-inner"
              :class="{ 'border-red-400 focus:ring-red-400': errors.code }"
              @blur="validateCode"
            />
            <button
              type="button"
              @click="sendCode"
              :disabled="sending || !form.email || cooldownSeconds > 0"
              class="px-4 py-2.5 bg-white/15 backdrop-blur-xl border border-white/25 text-white rounded-lg hover:bg-white/25 hover:shadow-lg disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap transition-all duration-300 shadow-inner"
            >
              {{ 
                sending ? $t('register.sending') : 
                cooldownSeconds > 0 ? `${cooldownSeconds}s` : 
                $t('register.sendCode') 
              }}
            </button>
          </div>
          <p v-if="errors.code" class="mt-1 text-sm text-red-400">{{ errors.code }}</p>
        </div>
      </div>
      
      <!-- Submit Button -->
      <button
        type="submit"
        :disabled="loading || !isFormValid"
        class="w-full bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 text-white font-semibold py-3 px-6 rounded-lg transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center space-x-2 shadow-lg hover:shadow-xl transform hover:-translate-y-0.5"
      >
        <div v-if="loading" class="spinner"></div>
        <span>{{ $t('register.form.submit') }}</span>
      </button>
      

    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { apiService } from '@/services/api'
import type { RegisterRequest, ConfigResponse } from '@/services/api'
import { useNotification } from '../composables/useNotification'

const { t } = useI18n()
const { success, error, info } = useNotification()

const loading = ref(false)
const sending = ref(false)
const config = ref<ConfigResponse>({
  login: { enable_email: false, email_smtp: '' },
  admin: {},
  frontend: { theme: '', logo_url: '', announcement: '', web_server_prefix: '', username_regex: '' },
  authme: { enabled: false, require_password: false, auto_register: false, auto_unregister: false, password_regex: '' }
})

// 使用computed来确保类型正确
const authmeConfig = computed(() => config.value.authme)
const shouldShowPassword = computed(() => authmeConfig.value?.enabled && authmeConfig.value?.require_password)

// 加载配置
onMounted(async () => {
  try {
    const res = await apiService.getConfig()
    config.value = res
    console.log('Config loaded:', config.value) // 调试日志
    console.log('Authme config:', config.value.authme) // 调试日志
    console.log('Authme enabled:', config.value.authme?.enabled) // 调试日志
    console.log('Authme require_password:', config.value.authme?.require_password) // 调试日志
    console.log('Should show password field:', config.value.authme?.enabled && config.value.authme?.require_password) // 调试日志
  } catch (e) {
    console.error('Failed to load config:', e)
  }
})

const form = reactive({
  username: '',
  email: '',
  code: '',
  password: '' // Added password field
})

const errors = reactive({
  username: '',
  email: '',
  code: '',
  password: '' // Added password error
})

const validateUsername = () => {
  errors.username = ''
  if (!form.username) {
    errors.username = t('register.validation.username_required')
  } else if (config.value.frontend?.username_regex && !new RegExp(config.value.frontend.username_regex).test(form.username)) {
    errors.username = t('register.validation.username_format', { regex: config.value.frontend.username_regex })
  } else if (!config.value.frontend?.username_regex && !/^[a-zA-Z0-9_]+$/.test(form.username)) {
    // 如果没有配置正则表达式，使用默认规则
    errors.username = t('register.validation.username_format', { regex: '^[a-zA-Z0-9_]+$' })
  }
}

const validateEmail = () => {
  errors.email = ''
  if (!form.email) {
    errors.email = t('register.validation.email_required')
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    errors.email = t('register.validation.email_format')
  }
}

const validatePassword = () => {
  errors.password = ''
  if (shouldShowPassword.value) {
    if (!form.password) {
      errors.password = t('register.validation.password_required')
    } else if (authmeConfig.value?.password_regex && !new RegExp(authmeConfig.value.password_regex).test(form.password)) {
      errors.password = t('register.validation.password_format', { regex: authmeConfig.value.password_regex })
    }
  }
}

const validateCode = () => {
  errors.code = ''
  if (!form.code) {
    errors.code = t('register.validation.code_required')
  }
}

const validateForm = () => {
  validateUsername()
  validateEmail()
  validatePassword() // Added password validation
  validateCode()
}

const isFormValid = computed(() => {
  const baseValid = form.username && 
         form.email && 
         form.code &&
         !errors.username && 
         !errors.email && 
         !errors.code
  
  // 如果启用了Authme且要求密码，检查密码
  if (shouldShowPassword.value) {
    return baseValid && form.password && !errors.password
  }
  
  return baseValid
})

// Rate limiting state for send code button
const cooldownSeconds = ref(0)
const cooldownTimer = ref<NodeJS.Timeout | null>(null)

// Start countdown timer for rate limiting
const startCooldown = (seconds: number) => {
  cooldownSeconds.value = seconds
  if (cooldownTimer.value) {
    clearInterval(cooldownTimer.value)
  }
  cooldownTimer.value = setInterval(() => {
    cooldownSeconds.value--
    if (cooldownSeconds.value <= 0) {
      if (cooldownTimer.value) {
        clearInterval(cooldownTimer.value)
      }
      cooldownTimer.value = null
    }
  }, 1000)
}

// Show rate limit notification with dynamic countdown
const showRateLimitNotification = (seconds: number) => {
  // Show initial notification
  error(t('register.rateLimited', { seconds: seconds }), '', 0)
  
  // Update notification every second during countdown
  const notificationTimer = setInterval(() => {
    if (cooldownSeconds.value > 0) {
      // Update the notification with new countdown
      error(t('register.rateLimited', { seconds: cooldownSeconds.value }), '', 0)
    } else {
      clearInterval(notificationTimer)
    }
  }, 1000)
}

const sendCode = async () => {
  if (sending.value || cooldownSeconds.value > 0) return
  validateEmail()
  if (errors.email) return
  sending.value = true
  
  try {
    const email = form.email.trim().toLowerCase()
    console.log('Sending code to:', email) // Debug log
    const res = await apiService.sendCode({
      email: email,
      language: t('lang')
    })
    console.log('Send code response:', res) // Debug log
    if (res.success) {
      // Show success notification
      success(t('register.codeSent'), '', 3000)
      // Start 60 second cooldown after successful send
      startCooldown(60)
    } else {
      // Handle rate limiting response
      if ((res as any).remaining_seconds && (res as any).remaining_seconds > 0) {
        startCooldown((res as any).remaining_seconds)
        // Show rate limit notification with dynamic countdown
        showRateLimitNotification((res as any).remaining_seconds)
      } else {
        // Show error notification
        error(t('register.sendFailed'), res.msg || '', 5000)
      }
    }
  } catch (e) {
    console.error('Send code error:', e) // Debug log
    error(t('register.sendFailed'), '', 5000)
  } finally {
    sending.value = false
  }
}

const handleSubmit = async () => {
  if (loading.value) return
  validateForm()
  if (!isFormValid.value) return
  loading.value = true
  
  try {
    const registerData = {
      username: form.username,
      email: form.email.trim().toLowerCase(),
      code: form.code,
      password: form.password, // Added password to registration data
      uuid: generateUUID(),
      language: t('lang')
    }
    console.log('Submitting registration:', registerData) // 调试日志
    const response = await apiService.register(registerData)
    console.log('Registration response:', response) // 调试日志
    if (response.success) {
      // Show success notification
      success(t('register.success'), '', 5000)
      Object.assign(form, {
        username: '',
        email: '',
        code: '',
        password: '' // Clear password on success
      })
    } else {
      // Show error notification
      error(t('register.failed'), response.msg || '', 5000)
    }
  } catch (error: any) {
    console.error('Registration error:', error) // 调试日志
    error(t('register.failed'), '', 5000)
  } finally {
    loading.value = false
  }
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0
    const v = c == 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}
</script>
