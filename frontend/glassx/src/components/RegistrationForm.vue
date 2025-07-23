<template>
  <form @submit.prevent="handleSubmit" class="space-y-6">
    <div class="space-y-4">
      <!-- Username Field -->
      <div>
        <label for="username" class="block text-sm font-medium text-white mb-2">
          {{ $t('register.form.username') }}
        </label>
        <input
          id="username"
          v-model="form.username"
          type="text"
          :placeholder="$t('register.form.username_placeholder')"
          class="glass-input w-full text-white placeholder-gray-300"
          :class="{ 'border-red-400 focus:ring-red-400': errors.username }"
          @blur="validateUsername"
        />
        <p v-if="errors.username" class="mt-1 text-sm text-red-400">{{ errors.username }}</p>
      </div>
      <!-- Email Field -->
      <div>
        <label for="email" class="block text-sm font-medium text-white mb-2">
          {{ $t('register.form.email') }}
        </label>
        <input
          id="email"
          v-model="form.email"
          type="email"
          :placeholder="$t('register.form.email_placeholder')"
          class="glass-input w-full text-white placeholder-gray-300"
          :class="{ 'border-red-400 focus:ring-red-400': errors.email }"
          @blur="validateEmail"
        />
        <p v-if="errors.email" class="mt-1 text-sm text-red-400">{{ errors.email }}</p>
      </div>
      <!-- 验证码 Field -->
      <div>
        <label for="code" class="block text-sm font-medium text-white mb-2">
          {{ $t('register.form.code') }}
        </label>
        <div class="flex flex-col sm:flex-row gap-2">
          <input
            id="code"
            v-model="form.code"
            type="text"
            :placeholder="$t('register.form.code_placeholder')"
            class="glass-input w-full text-white placeholder-gray-300"
            :class="{ 'border-red-400 focus:ring-red-400': errors.code }"
            @blur="validateCode"
          />
          <button
            type="button"
            @click="sendCode"
            :disabled="sending || !form.email"
            class="glass-button px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
          >
            {{ sending ? $t('register.sending') : $t('register.sendCode') }}
          </button>
        </div>
        <p v-if="errors.code" class="mt-1 text-sm text-red-400">{{ errors.code }}</p>
      </div>
    </div>
    <!-- Submit Button -->
    <button
      type="submit"
      :disabled="loading || !isFormValid"
      class="w-full glass-button bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 text-white font-semibold py-3 px-6 rounded-2xl transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center space-x-2"
    >
      <div v-if="loading" class="spinner"></div>
      <span>{{ $t('register.form.submit') }}</span>
    </button>
    <p v-if="message" class="mt-4 text-center text-sm" :class="messageType === 'error' ? 'text-red-400' : 'text-green-400'">{{ message }}</p>
  </form>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { apiService } from '@/services/api'
import type { RegisterRequest } from '@/services/api'

const { t } = useI18n()

const loading = ref(false)
const sending = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')

const form = reactive({
  username: '',
  email: '',
  code: ''
})

const errors = reactive({
  username: '',
  email: '',
  code: ''
})

const validateUsername = () => {
  errors.username = ''
  if (!form.username) {
    errors.username = t('register.validation.username_required')
  } else if (form.username.length < 3 || form.username.length > 16) {
    errors.username = t('register.validation.username_length')
  } else if (!/^[a-zA-Z0-9_]+$/.test(form.username)) {
    errors.username = t('register.validation.username_format')
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

const validateCode = () => {
  errors.code = ''
  if (!form.code) {
    errors.code = t('register.validation.code_required')
  }
}

const validateForm = () => {
  validateUsername()
  validateEmail()
  validateCode()
}

const isFormValid = computed(() => {
  return form.username && 
         form.email && 
         form.code &&
         !errors.username && 
         !errors.email && 
         !errors.code
})

const sendCode = async () => {
  if (sending.value) return
  validateEmail()
  if (errors.email) return
  sending.value = true
  message.value = ''
  try {
    const email = form.email.trim().toLowerCase()
    console.log('Sending code to:', email) // 调试日志
    const res = await apiService.sendCode({
      email: email,
      language: t('lang')
    })
    console.log('Send code response:', res) // 调试日志
    if (res.success) {
      message.value = t('register.codeSent')
      messageType.value = 'success'
    } else {
      message.value = res.msg && res.msg !== t('register.sendFailed') ? res.msg : t('register.sendFailed')
      messageType.value = 'error'
    }
  } catch (e) {
    console.error('Send code error:', e) // 调试日志
    message.value = t('register.sendFailed')
    messageType.value = 'error'
  } finally {
    sending.value = false
  }
}

const handleSubmit = async () => {
  if (loading.value) return
  validateForm()
  if (!isFormValid.value) return
  loading.value = true
  message.value = ''
  try {
    const registerData = {
      username: form.username,
      email: form.email.trim().toLowerCase(),
      code: form.code,
      uuid: generateUUID(),
      language: t('lang')
    }
    console.log('Submitting registration:', registerData) // 调试日志
    const response = await apiService.register(registerData)
    console.log('Registration response:', response) // 调试日志
    if (response.success) {
      message.value = t('register.success')
      messageType.value = 'success'
      Object.assign(form, {
        username: '',
        email: '',
        code: ''
      })
    } else {
      message.value = response.msg || t('register.failed')
      messageType.value = 'error'
    }
  } catch (error: any) {
    console.error('Registration error:', error) // 调试日志
    message.value = t('register.failed')
    messageType.value = 'error'
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
