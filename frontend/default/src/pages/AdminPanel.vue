<template>
  <div class="w-full max-w-2xl mx-auto py-10">
    <h2 class="text-2xl font-bold mb-6 text-center text-blue-600 cursor-default">{{ $t('admin.title') }}</h2>
    <div v-if="!authed">
      <div class="bg-gradient-to-br from-blue-100 via-white to-pink-100 shadow-xl rounded-2xl p-8 flex flex-col items-center transition-transform duration-300 hover:scale-105">
        <div class="text-3xl mb-4">🔒</div>
        <div class="mb-2 text-lg font-semibold">{{ $t('admin.loginTitle') }}</div>
        <input v-model="password" type="password" class="w-full max-w-xs px-3 py-2 border rounded focus:outline-none focus:ring mb-4" :placeholder="$t('admin.passwordPlaceholder')" @keyup.enter="login" />
        <button @click="login" class="bg-blue-600 text-white px-6 py-2 rounded-lg shadow hover:bg-blue-700 transition cursor-pointer w-full max-w-xs transition-transform duration-200 hover:scale-105">{{ $t('admin.loginBtn') }}</button>
      </div>
    </div>
    <div v-else>
      <div class="flex flex-col sm:flex-row gap-4 mb-4">
        <button @click="showAllUsers = false" :class="!showAllUsers ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-700'" class="px-4 py-2 rounded font-semibold">{{ $t('admin.pendingList') }}</button>
        <button @click="showAllUsers = true" :class="showAllUsers ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-700'" class="px-4 py-2 rounded font-semibold">{{ $t('admin.allUsers') }}</button>
      </div>
      <AdminReviewPanel v-if="!showAllUsers" :token="token" />
      <AllUsersPanel v-else :token="token" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, provide } from 'vue'
import AdminReviewPanel from '../components/AdminReviewPanel.vue'
import AllUsersPanel from '../components/AllUsersPanel.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const config = inject('config') as any
const showToast = inject('showToast') as (msg: string, type?: string) => void
const authed = ref(false)
const password = ref('')
const token = ref('')
const showAllUsers = ref(false)
const codeStartLine = 31
const codeLines = [
  "  # client_secret: your_qq_client_secret",
  "  # redirect_uri: http://localhost:8080/auth/qq/callback",
  "",
  "# Admin configuration",
  "admin:",
  "  password: your_custom_password",
  "",
  "# Frontend custom configuration",
  "frontend:",
  "  # logo_url can be an external link (e.g. https://example.com/logo.png), or a file in plugins/VerifyMC/static/ (e.g. /logo.png)",
  "  logo_url: /logo.png"
]

// 提供token给子组件
provide('token', token)

function isGreen(line: string) {
  return line.trim().startsWith("# Admin configuration") ||
         line.trim() === "admin:" ||
         line.trim().startsWith("password:")
}

async function login() {
  if (!password.value) {
    showToast(t('admin.passwordRequired'), 'warning')
    return
  }
  
  try {
    const res = await fetch('/api/admin-login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: password.value, language: t('lang') })
    })
    const data = await res.json()
    if (data.success) {
      authed.value = true
      token.value = data.token
      showToast(t('admin.loginSuccess'), 'success')
    } else {
      showToast(data.message || t('admin.loginFailed'), 'error')
    }
  } catch (e) {
    showToast(t('admin.loginFailed'), 'error')
  }
}

// 添加认证头获取函数
function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  if (token.value) {
    headers['Authorization'] = `Bearer ${token.value}`
  }
  return headers
}
</script> 
<style scoped>
.github-code-block {
  background: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  box-shadow: 0 1.5px 4px 0 #0001;
  font-family: 'Fira Mono', 'Consolas', 'Menlo', monospace;
  overflow-x: auto;
  text-align: left;
}
.github-code-header {
  background: #f3f4f6;
  border-bottom: 1px solid #eaecef;
  padding: 6px 12px;
  font-size: 13px;
  color: #57606a;
  border-radius: 6px 6px 0 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.github-code-tip {
  color: #a0aec0;
  font-size: 12px;
  margin-left: 8px;
}
.github-pre {
  margin: 0;
  padding: 8px 0 8px 0;
  background: none;
  border: none;
  font-size: 13px;
  line-height: 1.6;
}
.github-line {
  display: block;
  padding: 0 12px;
  transition: background 0.2s;
}
.github-line:hover {
  background: #eaecef;
}
.github-lineno {
  display: inline-block;
  width: 2em;
  color: #a0aec0;
  user-select: none;
  text-align: right;
  margin-right: 8px;
}
</style> 