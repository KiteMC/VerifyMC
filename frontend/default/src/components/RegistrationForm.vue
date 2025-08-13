<template>
  <!-- Minimalist registration form -->
  <div class="min-h-screen flex items-center justify-center px-4 sm:px-6 lg:px-8">
    <div class="max-w-lg w-full">
      <!-- Header -->
      <div class="text-center mb-8 fade-in">
        <div class="mx-auto h-16 w-16 flex items-center justify-center rounded-full bg-gray-100 mb-6">
          <svg class="h-8 w-8 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"></path>
          </svg>
        </div>
        <h2 class="section-title text-center">{{ $t('register.title') }}</h2>
        <p class="text-gray-600 text-sm">{{ $t('register.subtitle') }}</p>
      </div>
      
      <!-- Registration form -->
      <div class="minimal-card p-10 slide-up">
        <form @submit.prevent="submitForm" class="space-y-6">
          <!-- Email field -->
          <div>
            <label class="form-label">{{ $t('register.email') }}</label>
            <input 
              v-model="form.email" 
              type="email" 
              :placeholder="$t('register.emailPlaceholder')"
              class="input-field"
              :class="{ 'input-error': errors.email }"
              @blur="validateEmail"
            />
            <p v-if="errors.email" class="form-error">{{ errors.email }}</p>
          </div>

          <!-- Username field -->
          <div>
            <label class="form-label">{{ $t('register.username') }}</label>
            <input 
              v-model="form.username" 
              type="text" 
              :placeholder="$t('register.usernamePlaceholder')"
              class="input-field"
              :class="{ 'input-error': errors.username }"
              @blur="validateUsername"
            />
            <p v-if="errors.username" class="form-error">{{ errors.username }}</p>
          </div>

          <!-- Password field (conditional) -->
          <div v-if="config.authme?.enabled && config.authme?.require_password">
            <label class="form-label">{{ $t('register.password') }}</label>
            <input 
              v-model="form.password" 
              type="password" 
              :placeholder="$t('register.passwordPlaceholder')"
              class="input-field"
              :class="{ 'input-error': errors.password }"
              @blur="validatePassword"
            />
            <p v-if="errors.password" class="form-error">{{ errors.password }}</p>
            <p v-if="config.authme?.password_regex" class="mt-2 text-xs text-gray-500">
              {{ $t('register.passwordHint', { regex: config.authme.password_regex }) }}
            </p>
          </div>

          <!-- Verification code field -->
          <div>
            <label class="form-label">{{ $t('register.code') }}</label>
            <div class="flex flex-col sm:flex-row gap-3">
              <input 
                v-model="form.code" 
                type="text" 
                :placeholder="$t('register.codePlaceholder')"
                class="input-field flex-1"
                :class="{ 'input-error': errors.code }"
                @blur="validateCode"
              />
              <button 
                type="button"
                @click="sendCode" 
                :disabled="sending || !form.email || cooldownSeconds > 0"
                class="btn-action-outline whitespace-nowrap sm:px-4 px-6 py-2.5 sm:py-2"
              >
                {{ 
                  sending ? $t('register.sending') : 
                  cooldownSeconds > 0 ? `${cooldownSeconds}s` : 
                  $t('register.sendCode') 
                }}
              </button>
            </div>
            <p v-if="errors.code" class="form-error">{{ errors.code }}</p>
          </div>

          <!-- Submit button -->
          <button 
            type="submit" 
            :disabled="loading || !isFormValid"
            class="btn-primary w-full"
          >
            <svg v-if="loading" class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            {{ loading ? $t('common.loading') : $t('register.submit') }}
          </button>
        </form>

        <!-- Message display -->
        <div v-if="message" class="mt-6 p-4 rounded-lg" :class="messageType === 'error' ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'">
          <div class="flex items-center">
            <svg v-if="messageType === 'success'" class="h-5 w-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"></path>
            </svg>
            <svg v-else class="h-5 w-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"></path>
            </svg>
            <span class="text-sm font-medium">{{ message }}</span>
          </div>
        </div>
      </div>
      
      <!-- Back to home link -->
      <div class="text-center mt-6 fade-in">
        <router-link to="/" class="text-sm text-gray-500 hover:text-gray-700 transition-colors">
          ← {{ $t('common.back_home') }}
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, inject, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const form = reactive({
  email: '',
  username: '',
  code: '',
  password: '' // Added password field
})

const errors = reactive({
  email: '',
  username: '',
  code: '',
  password: '' // Added password error
})

const loading = ref(false)
const sending = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const config = inject('config', ref({}))

const isFormValid = computed(() => {
  return form.email && form.username && form.code && !errors.email && !errors.username && !errors.code
})

// 配置已经在main.ts中加载，这里不需要重复加载

const validateEmail = () => {
  errors.email = ''
  if (!form.email) {
    errors.email = t('register.emailRequired')
    return false
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(form.email)) {
    errors.email = t('register.invalid_email')
    return false
  }
  return true
}

const validateUsername = () => {
  errors.username = ''
  if (!form.username) {
    errors.username = t('register.usernameRequired')
    return false
  }
  if (form.username.length < 2) {
    errors.username = t('register.invalid_username')
    return false
  }
  return true
}

const validatePassword = () => {
  errors.password = ''
  if (config.authme?.require_password) {
    if (!form.password) {
      errors.password = t('register.passwordRequired')
      return false
    }
    if (config.authme?.password_regex && !new RegExp(config.authme.password_regex).test(form.password)) {
      errors.password = t('register.invalid_password', { regex: config.authme.password_regex })
      return false
    }
  }
  return true
}

const validateCode = () => {
  errors.code = ''
  if (!form.code) {
    errors.code = t('register.codeRequired')
    return false
  }
  return true
}

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

// Update rate limit message with dynamic countdown
const updateRateLimitMessage = (seconds: number) => {
  message.value = t('register.rateLimited', { seconds: seconds })
  messageType.value = 'error'
  
  // Update message every second during countdown
  const messageTimer = setInterval(() => {
    if (cooldownSeconds.value > 0) {
      message.value = t('register.rateLimited', { seconds: cooldownSeconds.value })
    } else {
      clearInterval(messageTimer)
    }
  }, 1000)
}

const sendCode = async () => {
  if (!validateEmail() || cooldownSeconds.value > 0) return
  
  sending.value = true
  message.value = ''
  
  try {
    const email = form.email.trim().toLowerCase();
    const res = await fetch('/api/send_code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        email: email, 
        language: t('lang') 
      })
    })
    const data = await res.json()
    
    if (data.success) {
      message.value = t('register.codeSent')
      messageType.value = 'success'
      // Start 60 second cooldown after successful send
      startCooldown(60)
    } else {
      // Handle rate limiting response
      if ((data as any).remaining_seconds && (data as any).remaining_seconds > 0) {
        startCooldown((data as any).remaining_seconds)
        // Use dynamic message that updates with countdown
        updateRateLimitMessage((data as any).remaining_seconds)
      } else {
        message.value = data.msg || t('register.sendFailed')
      }
      messageType.value = 'error'
    }
  } catch (e) {
    message.value = t('register.sendFailed')
    messageType.value = 'error'
  } finally {
    sending.value = false
  }
}

const submitForm = async () => {
  // 验证表单
  const emailValid = validateEmail()
  const usernameValid = validateUsername()
  const codeValid = validateCode()
  
  if (!emailValid || !usernameValid || !codeValid) {
    return
  }
  
  // 如果启用了密码要求，验证密码
  if (config.authme?.require_password) {
    if (!validatePassword()) {
      return
    }
  }
  
  loading.value = true
  message.value = ''
  
  try {
    // 生成UUID (这里使用简单的随机UUID生成)
    const uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0
      const v = c == 'x' ? r : (r & 0x3 | 0x8)
      return v.toString(16)
    })
    const email = form.email.trim().toLowerCase();
    const res = await fetch('/api/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: email,
        code: form.code,
        uuid: uuid,
        username: form.username,
        password: form.password, // 添加密码字段
        language: t('lang')
      })
    })
    
    const data = await res.json()
    
    if (data.success) {
      message.value = t('register.success')
      messageType.value = 'success'
      // 清空表单
      form.email = ''
      form.username = ''
      form.code = ''
    } else {
      message.value = data.msg || t('register.failed')
      messageType.value = 'error'
    }
  } catch (e) {
    message.value = t('register.failed')
    messageType.value = 'error'
  } finally {
    loading.value = false
  }
}
</script> 