<template>
  <div class="flex flex-col gap-6">
    <Card>
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('login.title') }}</CardTitle>
        <CardDescription>{{ $t('login.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleSubmit">
          <div class="flex flex-col gap-6">
            <div class="grid gap-2">
              <Label for="email">{{ $t('login.form.email') }}</Label>
              <Input 
                id="email" 
                type="password" 
                :placeholder="$t('login.form.email_placeholder')" 
                v-model="form.email"
              />
            </div>

            <Button type="submit" class="w-full" :disabled="loading">
              <span v-if="loading">{{ $t('common.loading') }}</span>
              <span v-else>{{ $t('login.form.submit') }}</span>
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useNotification } from '@/composables/useNotification'
import { apiService } from '@/services/api'
import Card from './ui/Card.vue'
import CardHeader from './ui/CardHeader.vue'
import CardTitle from './ui/CardTitle.vue'
import CardDescription from './ui/CardDescription.vue'
import CardContent from './ui/CardContent.vue'
import Button from './ui/Button.vue'
import Input from './ui/Input.vue'
import Label from './ui/Label.vue'

const { t, locale } = useI18n()
const router = useRouter()
const notification = useNotification()

const loading = ref(false)

const form = reactive({
  email: ''
})

const errors = reactive({
  email: ''
})

const validateEmail = () => {
  errors.email = ''
  const email = form.email.trim()
  if (!email) {
    errors.email = t('login.validation.email_required')
    return false
  }
  
  return true
}



const handleSubmit = async () => {
  // 验证表单
  const emailValid = validateEmail()
  
  if (!emailValid) {
    notification.error(t('common.error'), errors.email)
    return
  }

  loading.value = true

  try {
    const response = await apiService.adminLogin({
      password: form.email, // 使用邮箱字段作为密码
      language: locale.value
    })
    
    if (response.success) {
      localStorage.setItem('admin_token', response.token)
      notification.success(t('admin.login_success'), response.message && response.message !== t('admin.login_success') ? response.message : '')
      setTimeout(() => {
        router.push('/admin')
      }, 1000)
    } else {
      notification.error(t('login.messages.error'), response.message && response.message !== t('login.messages.error') ? response.message : '')
    }
    
  } catch (error) {
    notification.error(t('login.messages.error'), t('login.messages.invalid_credentials'))
  } finally {
    loading.value = false
  }
}
</script> 