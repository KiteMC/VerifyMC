<template>
  <!-- Minimalist admin panel design -->
  <div class="min-h-screen bg-gray-50">
    <div class="container-wide py-8">
      <!-- Login form -->
      <div v-if="!authed" class="min-h-screen flex items-center justify-center">
        <div class="max-w-lg w-full">
          <div class="text-center mb-8 fade-in">
            <div class="mx-auto h-12 w-12 flex items-center justify-center rounded-full bg-gray-100 mb-4">
              <svg class="h-6 w-6 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
              </svg>
            </div>
            <h2 class="section-title">{{ $t('admin.loginTitle') }}</h2>
            <p class="text-gray-600 text-sm">{{ $t('admin.loginSubtitle') }}</p>
          </div>
          
          <div class="minimal-card p-10 slide-up">
            <div class="space-y-6">
              <div>
                <label class="form-label">{{ $t('admin.password') }}</label>
                <input 
                  v-model="password" 
                  type="password" 
                  class="input-field" 
                  :placeholder="$t('admin.loginPasswordPlaceholder')" 
                  @keyup.enter="login" 
                />
              </div>
              
              <button @click="login" class="btn-primary w-full" :disabled="!password">
                <svg v-if="loading" class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                {{ loading ? $t('common.loading') : $t('admin.loginBtn') }}
              </button>
            </div>
          </div>
          
          <div class="text-center mt-6 fade-in">
            <router-link to="/" class="text-sm text-gray-500 hover:text-gray-700 transition-colors">
              ← {{ $t('common.back_home') }}
            </router-link>
          </div>
        </div>
      </div>
      
      <!-- Admin dashboard -->
      <div v-else class="fade-in">
        <!-- Header -->
        <div class="mb-8">
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <h1 class="page-header">{{ $t('admin.title') }}</h1>
          </div>
        </div>
        
        <!-- Tab navigation -->
        <div class="mb-10">
          <nav class="flex space-x-2 bg-gray-100 p-2 rounded-xl w-full max-w-2xl">
            <button 
              @click="showAllUsers = false" 
              :class="!showAllUsers ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'"
              class="flex-1 py-3 px-3 sm:px-6 text-sm sm:text-base font-medium rounded-lg transition-all duration-200 flex items-center justify-center"
            >
              <svg class="w-4 h-4 sm:w-5 sm:h-5 mr-2 sm:mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              <span class="truncate">{{ $t('admin.pendingList') }}</span>
            </button>
            <button 
              @click="showAllUsers = true" 
              :class="showAllUsers ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'"
              class="flex-1 py-3 px-3 sm:px-6 text-sm sm:text-base font-medium rounded-lg transition-all duration-200 flex items-center justify-center"
            >
              <svg class="w-4 h-4 sm:w-5 sm:h-5 mr-2 sm:mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197m13.5-9a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z"></path>
              </svg>
              <span class="truncate">{{ $t('admin.allUsers') }}</span>
            </button>
          </nav>
        </div>
        
        <!-- Content -->
        <div class="slide-up">
          <AdminReviewPanel v-if="!showAllUsers" :token="token" />
          <AllUsersPanel v-else :token="token" />
        </div>
      </div>
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
const loading = ref(false)

// Validate token on page load - Check if stored token is still valid
const validateStoredToken = async () => {
  const savedToken = localStorage.getItem('admin_token')
  if (!savedToken) {
    return
  }
  
  try {
    // Test token validity by making a simple API call
    const res = await fetch('/api/admin-verify', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${savedToken}`
      }
    })
    const data = await res.json()
    
    if (data.success) {
      // Token is valid, restore authentication state
      token.value = savedToken
      authed.value = true
      console.log('Stored token validated successfully')
    } else {
      // Token is invalid, clear it and show message
      localStorage.removeItem('admin_token')
      console.log('Stored token is invalid, cleared from localStorage')
      showToast(t('admin.sessionExpired'), 'warning')
    }
  } catch (error) {
    // Network error or server restart, clear invalid token and show message
    localStorage.removeItem('admin_token')
    console.log('Token validation failed, cleared from localStorage:', error)
    showToast(t('admin.sessionExpiredRestart'), 'warning')
  }
}

// Validate token on component mount
validateStoredToken()
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
  
  loading.value = true
  
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
      localStorage.setItem('admin_token', data.token)
      showToast(t('admin.loginSuccess'), 'success')
    } else {
      showToast(data.message || t('admin.loginFailed'), 'error')
    }
  } catch (e) {
    showToast(t('admin.loginFailed'), 'error')
  } finally {
    loading.value = false
  }
}

function logout() {
  authed.value = false
  token.value = ''
  password.value = ''
  localStorage.removeItem('admin_token')
  showToast(t('admin.logoutSuccess'), 'success')
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