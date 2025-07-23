<template>
  <div class="flex flex-col gap-6 relative z-20">
    <Card>
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('register.title') }}</CardTitle>
        <CardDescription>{{ $t('register.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleSubmit">
          <div class="flex flex-col gap-6">
            <div class="grid gap-2">
              <Label for="username">{{ $t('register.form.username') }}</Label>
              <Input 
                id="username" 
                type="text" 
                :placeholder="$t('register.form.username_placeholder')" 
                v-model="form.username"
              />
            </div>
            <div class="grid gap-2">
              <Label for="email">{{ $t('register.form.email') }}</Label>
              <Input 
                id="email" 
                type="email" 
                :placeholder="$t('register.form.email_placeholder')" 
                v-model="form.email"
              />
            </div>
            <div class="grid gap-2">
              <Label for="code">{{ $t('register.form.code') }}</Label>
              <div class="flex flex-col sm:flex-row gap-2">
                <Input 
                  id="code" 
                  type="text" 
                  :placeholder="$t('register.form.code_placeholder')"
                  v-model="form.code"
                  class="flex-1"
                />
                <button 
                  type="button" 
                  @click="sendCode"
                  :disabled="codeSending || !form.email.trim()"
                  class="px-4 py-2 border border-white/20 bg-white/10 backdrop-blur-sm hover:bg-white/20 text-white rounded-md whitespace-nowrap cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <span v-if="codeSending">{{ $t('register.sending') }}</span>
                  <span v-else>{{ $t('register.send_code') }}</span>
                </button>
              </div>
            </div>

            <Button type="submit" class="w-full" :disabled="loading">
              <span v-if="loading">{{ $t('common.loading') }}</span>
              <span v-else>{{ $t('register.form.submit') }}</span>
            </Button>
          </div>

        </form>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useNotification } from '@/composables/useNotification'
import { apiService } from '@/services/api'

const { t, locale } = useI18n()
import Card from './ui/Card.vue'
import CardHeader from './ui/CardHeader.vue'
import CardTitle from './ui/CardTitle.vue'
import CardDescription from './ui/CardDescription.vue'
import CardContent from './ui/CardContent.vue'
import Button from './ui/Button.vue'
import Input from './ui/Input.vue'
import Label from './ui/Label.vue'

const router = useRouter()
const notification = useNotification()

const loading = ref(false)
const codeSending = ref(false)

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

onMounted(() => {
  // 组件已加载
})

const validateUsername = () => {
  errors.username = ''
  const username = form.username.trim()
  if (!username) {
    errors.username = t('register.validation.username_required')
    return false
  }
  
  if (username.length < 3) {
    errors.username = t('register.validation.username_length')
    return false
  }
  
  return true
}

const validateEmail = () => {
  errors.email = ''
  const email = form.email.trim()
  if (!email) {
    errors.email = t('register.validation.email_required')
    return false
  }
  
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(email)) {
    errors.email = t('register.validation.email_format')
    return false
  }
  
  return true
}



const validateCode = () => {
  errors.code = ''
  const code = form.code.trim()
  if (!code) {
    errors.code = t('register.validation.code_required')
    return false
  }
  
  return true
}

const sendCode = async () => {
  if (!form.email) {
    notification.error(t('common.error'), t('register.validation.email_required'))
    return
  }
  
  if (!validateEmail()) {
    return
  }
  
  codeSending.value = true
  
  try {
    const email = form.email.trim().toLowerCase()
    
    const response = await apiService.sendCode({
      email: email,
      language: locale.value
    })
    
    if (response.success) {
      notification.success(t('register.code_sent'), response.msg)
    } else {
      notification.error(t('register.send_failed'), response.msg)
    }
  } catch (error: any) {
    console.error('Send code error:', error)
    notification.error(t('register.send_failed'), t('register.send_failed'))
  } finally {
    codeSending.value = false
  }
}





const handleSubmit = async () => {
  // 验证表单
  const usernameValid = validateUsername()
  const emailValid = validateEmail()
  const codeValid = validateCode()
  
  if (!usernameValid || !emailValid || !codeValid) {
    // 显示第一个错误通知
    if (!usernameValid) {
      notification.error(t('common.error'), errors.username)
    } else if (!emailValid) {
      notification.error(t('common.error'), errors.email)
    } else if (!codeValid) {
      notification.error(t('common.error'), errors.code)
    }
    return
  }

  loading.value = true

  try {
    // 生成标准UUID格式
    const uuid = (() => {
      if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID()
      }
      // 降级方案：生成标准UUID格式
      const generateUUID = () => {
        const hex = '0123456789abcdef'
        const uuid = []
        for (let i = 0; i < 36; i++) {
          if (i === 8 || i === 13 || i === 18 || i === 23) {
            uuid.push('-')
          } else {
            uuid.push(hex[Math.floor(Math.random() * 16)])
          }
        }
        return uuid.join('')
      }
      return generateUUID()
    })()
    
    const registerData = {
      email: form.email.trim().toLowerCase(),
      code: form.code,
      uuid: uuid,
      username: form.username,
      language: locale.value
    }
    
    const response = await apiService.register(registerData)
    
    if (response.success) {
      notification.success(t('register.success'), response.msg && response.msg !== t('register.success') ? response.msg : '')
      
      // 清空表单
      Object.assign(form, {
        username: '',
        email: '',
        code: ''
      })
      
      // 注册成功后不跳转，让用户等待审核
      // setTimeout(() => {
      //   router.push('/login')
      // }, 2000)
    } else {
      notification.error(t('register.failed'), response.msg && response.msg !== t('register.failed') ? response.msg : '')
    }
    
  } catch (error) {
    console.error('Registration error:', error)
    notification.error(t('register.failed'), t('register.failed'))
  } finally {
    loading.value = false
  }
}
</script> 