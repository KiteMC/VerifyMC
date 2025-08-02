<template>
  <div class="w-full max-w-md mx-auto">
    <div class="bg-white rounded-lg shadow-lg p-6 w-full">
      <h2 class="text-2xl font-bold mb-6 text-center text-blue-600">{{ $t('register.title') }}</h2>
      
      <!-- 邮箱注册表单 -->
      <form @submit.prevent="submitForm" class="space-y-4">
  <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ $t('register.email') }}</label>
          <input 
            v-model="form.email" 
            type="email" 
            :placeholder="$t('register.emailPlaceholder')"
            class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="{ 'border-red-500': errors.email }"
            @blur="validateEmail"
          />
          <p v-if="errors.email" class="mt-1 text-sm text-red-600">{{ errors.email }}</p>
        </div>

      <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ $t('register.username') }}</label>
          <input 
            v-model="form.username" 
            type="text" 
            :placeholder="$t('register.usernamePlaceholder')"
            class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="{ 'border-red-500': errors.username }"
            @blur="validateUsername"
          />
          <p v-if="errors.username" class="mt-1 text-sm text-red-600">{{ errors.username }}</p>
        </div>

        <!-- 密码输入框 -->
        <div v-if="config.authme?.enabled && config.authme?.require_password">
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ $t('register.password') }}</label>
          <input 
            v-model="form.password" 
            type="password" 
            :placeholder="$t('register.passwordPlaceholder')"
            class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="{ 'border-red-500': errors.password }"
            @blur="validatePassword"
          />
          <p v-if="errors.password" class="mt-1 text-sm text-red-600">{{ errors.password }}</p>
          <p v-if="config.authme?.password_regex" class="mt-1 text-xs text-gray-500">{{ $t('register.passwordHint', { regex: config.authme.password_regex }) }}</p>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ $t('register.code') }}</label>
          <div class="flex flex-col sm:flex-row gap-2">
            <input 
              v-model="form.code" 
              type="text" 
              :placeholder="$t('register.codePlaceholder')"
              class="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              :class="{ 'border-red-500': errors.code }"
              @blur="validateCode"
            />
            <button 
              type="button"
              @click="sendCode" 
              :disabled="sending || !form.email"
              class="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
            >
              {{ sending ? $t('register.sending') : $t('register.sendCode') }}
            </button>
          </div>
          <p v-if="errors.code" class="mt-1 text-sm text-red-600">{{ errors.code }}</p>
        </div>

        <button 
          type="submit" 
          :disabled="loading"
          class="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ loading ? $t('common.loading') : $t('register.submit') }}
        </button>
      </form>

      <div v-if="message" :class="messageType === 'error' ? 'text-red-600' : 'text-green-600'" class="mt-4 text-center">
        {{ message }}
      </div>
      </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, inject } from 'vue'
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

const sendCode = async () => {
  if (!validateEmail()) return
  
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
    } else {
      message.value = data.msg || t('register.sendFailed')
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