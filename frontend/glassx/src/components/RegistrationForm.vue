<template>
  <div class="registration-card" :class="shouldShowPassword ? 'w-full max-w-3xl' : 'w-full max-w-2xl'">
    <div class="absolute inset-0 rounded-2xl bg-gradient-to-r from-blue-500/20 via-purple-500/20 to-pink-500/20 blur-xl opacity-60 animate-gradient-shift"></div>

    <div class="relative bg-white/[0.08] backdrop-blur-2xl border border-white/[0.15] rounded-2xl p-8 shadow-2xl overflow-hidden">
      <div class="absolute inset-0 bg-gradient-to-br from-white/10 via-transparent to-transparent pointer-events-none"></div>
      <div class="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-white/30 to-transparent"></div>

      <div class="relative z-10 text-center mb-6">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-500/20 to-purple-600/20 border border-white/10 mb-4 shadow-lg">
          <svg class="w-8 h-8 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"></path>
          </svg>
        </div>
        <h2 class="text-2xl font-bold text-white mb-2 tracking-tight">{{ $t('register.title') }}</h2>
        <p class="text-white/60 text-sm">{{ $t('register.subtitle') }}</p>
      </div>

      <div class="relative z-10 flex items-center justify-center gap-2 mb-6 text-xs md:text-sm">
        <div class="step-chip" :class="currentStep === 'basic' ? 'step-chip-active' : ''">1. {{ $t('register.steps.basic') }}</div>
        <div class="step-separator"></div>
        <template v-if="questionnaireEnabled">
          <div class="step-chip" :class="currentStep === 'questionnaire' ? 'step-chip-active' : ''">2. {{ $t('register.steps.questionnaire') }}</div>
          <div class="step-separator"></div>
          <div class="step-chip" :class="currentStep === 'submit' ? 'step-chip-active' : ''">3. {{ $t('register.steps.submit') }}</div>
        </template>
        <div v-else class="step-chip" :class="currentStep === 'submit' ? 'step-chip-active' : ''">2. {{ $t('register.steps.submit') }}</div>
      </div>

      <form v-if="currentStep === 'basic'" @submit.prevent="goToQuestionnaire" class="space-y-5 relative z-10">
        <div class="space-y-3">
          <div v-if="bedrockEnabled">
            <label for="platform" class="block text-sm font-medium text-white mb-1">{{ $t('register.form.platform') }}</label>
            <select id="platform" v-model="form.platform" class="glass-input">
              <option value="java">{{ $t('register.form.platform_java') }}</option>
              <option value="bedrock">{{ $t('register.form.platform_bedrock') }}</option>
            </select>
          </div>

          <div>
            <label for="username" class="block text-sm font-medium text-white mb-1">{{ $t('register.form.username') }}</label>
            <input id="username" v-model="form.username" type="text" :placeholder="$t('register.form.username_placeholder')" class="glass-input" :class="{ 'glass-input-error': errors.username }" @blur="validateUsername" />
            <p v-if="errors.username" class="mt-1 text-sm text-red-400">{{ errors.username }}</p>
          </div>

          <div>
            <label for="email" class="block text-sm font-medium text-white mb-1">{{ $t('register.form.email') }}</label>
            <input id="email" v-model="form.email" type="email" :placeholder="$t('register.form.email_placeholder')" class="glass-input" :class="{ 'glass-input-error': errors.email }" @blur="validateEmail" />
            <p v-if="errors.email" class="mt-1 text-sm text-red-400">{{ errors.email }}</p>
          </div>

          <div v-if="shouldShowPassword">
            <label for="password" class="block text-sm font-medium text-white mb-1">{{ $t('register.form.password') }}</label>
            <input id="password" v-model="form.password" type="password" :placeholder="$t('register.form.password_placeholder')" class="glass-input" :class="{ 'glass-input-error': errors.password }" @blur="validatePassword" />
            <p v-if="errors.password" class="mt-1 text-sm text-red-400">{{ errors.password }}</p>
            <p v-if="authmeConfig.password_regex" class="mt-1 text-xs text-gray-300">{{ $t('register.form.password_hint', { regex: authmeConfig.password_regex }) }}</p>
          </div>

          <div v-if="emailEnabled">
            <label for="code" class="block text-sm font-medium text-white mb-1">{{ $t('register.form.code') }}</label>
            <div class="flex flex-col sm:flex-row gap-2">
              <input id="code" v-model="form.code" type="text" :placeholder="$t('register.form.code_placeholder')" class="glass-input" :class="{ 'glass-input-error': errors.code }" @blur="validateCode" />
              <button type="button" @click="sendCode" :disabled="sending || !form.email || cooldownSeconds > 0" class="glass-button-secondary">
                {{ sending ? $t('register.sending') : cooldownSeconds > 0 ? `${cooldownSeconds}s` : $t('register.sendCode') }}
              </button>
            </div>
            <p v-if="errors.code" class="mt-1 text-sm text-red-400">{{ errors.code }}</p>
          </div>

          <div v-if="captchaEnabled">
            <label for="captcha" class="block text-sm font-medium text-white mb-1">{{ $t('register.form.captcha') }}</label>
            <div class="flex flex-col sm:flex-row gap-2 items-center">
              <input id="captcha" v-model="form.captchaAnswer" type="text" :placeholder="$t('register.form.captcha_placeholder')" class="glass-input" :class="{ 'glass-input-error': errors.captcha }" @blur="validateCaptcha" />
              <div class="captcha-image-container cursor-pointer border border-white/20 rounded-lg overflow-hidden bg-white/10 backdrop-blur-sm hover:bg-white/20 hover:border-white/30 transition-all duration-300 flex-shrink-0 shadow-lg" @click="refreshCaptcha" :title="$t('register.form.captcha_refresh')">
                <img v-if="captchaImage" :src="captchaImage" alt="captcha" class="h-11 w-auto" />
                <div v-else class="h-11 w-28 flex items-center justify-center text-white/60 text-sm">{{ $t('common.loading') }}</div>
              </div>
            </div>
            <p v-if="errors.captcha" class="mt-1 text-sm text-red-400">{{ errors.captcha }}</p>
            <p class="mt-1 text-xs text-gray-300">{{ $t('register.form.captcha_hint') }}</p>
          </div>

          <div v-if="discordEnabled" class="pt-2">
            <label class="block text-sm font-medium text-white mb-2">Discord {{ discordRequired ? '*' : '' }}</label>
            <DiscordLink :username="normalizedUsername" :required="discordRequired" @linked="onDiscordLinked" @unlinked="onDiscordUnlinked" />
            <p v-if="errors.discord" class="mt-1 text-sm text-red-400">{{ errors.discord }}</p>
          </div>
        </div>

        <button type="submit" :disabled="!isBasicStepValid" class="submit-button">
          <span>{{ questionnaireEnabled ? $t('register.actions.next_questionnaire') : $t('register.steps.submit') }}</span>
          <div class="button-shine"></div>
        </button>
      </form>

      <div v-else-if="currentStep === 'questionnaire'" class="relative z-10">
        <QuestionnaireForm @back="currentStep = 'basic'" @skip="onQuestionnaireSkipped" @passed="onQuestionnairePassed" />
      </div>

      <div v-else class="space-y-4 relative z-10">
        <div class="rounded-lg border border-white/15 bg-white/5 p-4 text-sm text-white/80">
          <p class="mb-1"><strong>{{ $t('register.summary.username') }}:</strong> {{ form.username }}</p>
          <p class="mb-1"><strong>{{ $t('register.summary.email') }}:</strong> {{ form.email }}</p>
          <p v-if="questionnaireResult"><strong>{{ $t('register.summary.questionnaire') }}:</strong> {{ questionnaireResult.passed ? $t('questionnaire.passed') : $t('questionnaire.failed') }} ({{ questionnaireResult.score }}/{{ questionnaireResult.pass_score }})</p>
        </div>

        <div class="rounded-lg border border-white/10 bg-white/5 p-6 text-center">
          <div v-if="loading" class="flex flex-col items-center gap-3 text-white/70">
            <div class="spinner"></div>
            <p>{{ $t('common.loading') }}</p>
          </div>
          <p v-else-if="registrationSubmitted" class="text-green-300 font-medium">{{ $t('register.success') }}</p>
          <p v-else class="text-red-300 font-medium">{{ $t('register.failed') }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { apiService } from '@/services/api'
import { useNotification } from '@/composables/useNotification'
import DiscordLink from '@/components/DiscordLink.vue'
import QuestionnaireForm from '@/components/QuestionnaireForm.vue'
import type { ConfigResponse, QuestionnaireSubmission } from '@/services/api'

const { t, locale } = useI18n()
const { success, error } = useNotification()

type RegisterStep = 'basic' | 'questionnaire' | 'submit'
const currentStep = ref<RegisterStep>('basic')

const loading = ref(false)
const sending = ref(false)
const registrationSubmitted = ref(false)
const config = ref<ConfigResponse>({
  login: { enable_email: false, email_smtp: '' },
  admin: {},
  frontend: { theme: '', logo_url: '', announcement: '', web_server_prefix: '', username_regex: '' },
  authme: { enabled: false, require_password: false, auto_register: false, auto_unregister: false, password_regex: '' },
  captcha: { enabled: false, email_enabled: true, type: 'math' },
  questionnaire: { enabled: false, pass_score: 60, auto_approve_on_pass: false, require_pass_before_register: false },
  bedrock: { enabled: false, prefix: '.', username_regex: '^\\.[a-zA-Z0-9_\\s]{3,16}$' }
})

const captchaImage = ref('')
const captchaToken = ref('')
const captchaEnabled = computed(() => config.value.captcha?.enabled || false)
const emailEnabled = computed(() => config.value.captcha?.email_enabled !== false)
const bedrockEnabled = computed(() => config.value.bedrock?.enabled || false)

const normalizedUsername = computed(() => {
  const username = form.username.trim()
  if (!bedrockEnabled.value || form.platform !== 'bedrock') return username

  const prefix = config.value.bedrock?.prefix || '.'
  if (!prefix) return username
  return username.startsWith(prefix) ? username : `${prefix}${username}`
})

const discordLinked = ref(false)
const discordEnabled = computed(() => config.value.discord?.enabled || false)
const discordRequired = computed(() => config.value.discord?.required || false)

const questionnaireResult = ref<QuestionnaireSubmission | null>(null)
const questionnaireEnabled = computed(() => config.value.questionnaire?.enabled || false)
const questionnaireRequired = computed(() => questionnaireEnabled.value && (config.value.questionnaire?.require_pass_before_register ?? false))

const authmeConfig = computed(() => config.value.authme)
const shouldShowPassword = computed(() => authmeConfig.value?.enabled && authmeConfig.value?.require_password)

onMounted(async () => {
  try {
    const res = await apiService.getConfig()
    config.value = res
    if (config.value.captcha?.enabled) {
      await refreshCaptcha()
    }
  } catch (e) {
    console.error('Failed to load config:', e)
  }
})

const refreshCaptcha = async () => {
  try {
    const response = await apiService.getCaptcha()
    if (response.success && response.token && response.image) {
      captchaToken.value = response.token
      captchaImage.value = response.image
    }
  } catch (e) {
    console.error('Failed to load captcha:', e)
  }
}

const form = reactive({ username: '', email: '', code: '', password: '', captchaAnswer: '', platform: 'java' as 'java' | 'bedrock' })
const errors = reactive({ username: '', email: '', code: '', password: '', captcha: '', discord: '' })

const onDiscordLinked = () => {
  discordLinked.value = true
  errors.discord = ''
}
const onDiscordUnlinked = () => {
  discordLinked.value = false
}

const validateDiscord = () => {
  errors.discord = ''
  if (discordRequired.value && !discordLinked.value) {
    errors.discord = t('discord.required')
  }
}
const validateUsername = () => {
  errors.username = ''
  const submitUsername = normalizedUsername.value
  if (!submitUsername) {
    errors.username = t('register.validation.username_required')
    return
  }

  const usernameRegex = bedrockEnabled.value && form.platform === 'bedrock'
    ? (config.value.bedrock?.username_regex || '^\\.[a-zA-Z0-9_\\s]{3,16}$')
    : (config.value.frontend?.username_regex || '^[a-zA-Z0-9_]+$')

  if (!new RegExp(usernameRegex).test(submitUsername)) {
    errors.username = t('register.validation.username_format', { regex: usernameRegex })
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
  if (emailEnabled.value && !form.code) {
    errors.code = t('register.validation.code_required')
  }
}
const validateCaptcha = () => {
  errors.captcha = ''
  if (captchaEnabled.value && !form.captchaAnswer) {
    errors.captcha = t('register.validation.captcha_required')
  }
}

const validateForm = () => {
  validateUsername()
  validateEmail()
  validatePassword()
  validateCode()
  validateCaptcha()
  validateDiscord()
}

const isBasicStepValid = computed(() => {
  let valid = form.username && form.email && !errors.username && !errors.email
  if (emailEnabled.value) valid = valid && !!form.code && !errors.code
  if (captchaEnabled.value) valid = valid && !!form.captchaAnswer && !errors.captcha
  if (shouldShowPassword.value) valid = valid && !!form.password && !errors.password
  if (discordRequired.value) valid = valid && discordLinked.value && !errors.discord
  return valid
})

const isFinalStepValid = computed(() => {
  if (!isBasicStepValid.value) return false
  if (!questionnaireEnabled.value) return true
  if (!questionnaireResult.value) return !questionnaireRequired.value
  return questionnaireRequired.value ? questionnaireResult.value.passed : true
})

const goToQuestionnaire = () => {
  validateForm()
  if (!isBasicStepValid.value) return
  if (questionnaireEnabled.value) {
    currentStep.value = 'questionnaire'
    return
  }
  currentStep.value = 'submit'
  void handleSubmit()
}

const onQuestionnaireSkipped = () => {
  if (questionnaireRequired.value) {
    error(t('register.questionnaire.required'))
    return
  }
  questionnaireResult.value = null
  currentStep.value = 'submit'
  void handleSubmit()
}

const onQuestionnairePassed = async (result: QuestionnaireSubmission) => {
  questionnaireResult.value = result
  currentStep.value = 'submit'
  await handleSubmit()
}

const cooldownSeconds = ref(0)
const cooldownTimer = ref<any>(null)
const startCooldown = (seconds: number) => {
  cooldownSeconds.value = seconds
  if (cooldownTimer.value) clearInterval(cooldownTimer.value)
  cooldownTimer.value = setInterval(() => {
    cooldownSeconds.value--
    if (cooldownSeconds.value <= 0) {
      clearInterval(cooldownTimer.value)
      cooldownTimer.value = null
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
    const res = await apiService.sendCode({ email, language: locale.value })
    if (res.success) {
      success(t('register.codeSent'))
      startCooldown(60)
    } else if ((res as any).remaining_seconds && (res as any).remaining_seconds > 0) {
      startCooldown((res as any).remaining_seconds)
      error(res.msg || t('register.sendFailed'))
    } else {
      error(res.msg || t('register.sendFailed'))
    }
  } catch {
    error(t('register.sendFailed'))
  } finally {
    sending.value = false
  }
}

const handleSubmit = async () => {
  if (loading.value) return
  validateForm()
  if (!isFinalStepValid.value) return
  loading.value = true
  registrationSubmitted.value = false
  try {
    const registerData: any = {
      username: normalizedUsername.value,
      email: form.email.trim().toLowerCase(),
      uuid: generateUUID(),
      language: locale.value,
      platform: form.platform
    }

    if (emailEnabled.value) registerData.code = form.code
    if (captchaEnabled.value) {
      registerData.captchaToken = captchaToken.value
      registerData.captchaAnswer = form.captchaAnswer
    }
    if (shouldShowPassword.value) registerData.password = form.password
    if (questionnaireResult.value) registerData.questionnaire = questionnaireResult.value

    const response = await apiService.register(registerData)
    if (response.success) {
      registrationSubmitted.value = true
      success(t('register.success'))
    } else {
      registrationSubmitted.value = false
      error(response.msg || t('register.failed'))
      if (captchaEnabled.value) await refreshCaptcha()
    }
  } catch {
    registrationSubmitted.value = false
    error(t('register.failed'))
    if (captchaEnabled.value) await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = Math.random() * 16 | 0
    const v = c == 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}
</script>

<style scoped>
.registration-card { position: relative; padding: 2px; }
@keyframes gradient-shift { 0%,100% { opacity: .4; transform: scale(1);} 50% { opacity:.6; transform: scale(1.02);} }
.animate-gradient-shift { animation: gradient-shift 4s ease-in-out infinite; }
.glass-input { width: 100%; padding: .75rem 1rem; background: rgba(255,255,255,.08); backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,.15); border-radius: 12px; color: #fff; font-size: .95rem; transition: all .3s cubic-bezier(.4,0,.2,1); outline: none; }
.glass-input::placeholder { color: rgba(255,255,255,.4); }
.glass-input:hover { background: rgba(255,255,255,.1); border-color: rgba(255,255,255,.25); }
.glass-input:focus { background: rgba(255,255,255,.12); border-color: rgba(59,130,246,.5); box-shadow: 0 0 0 3px rgba(59,130,246,.15), 0 0 20px rgba(59,130,246,.2); }
.glass-input-error { border-color: rgba(239,68,68,.5)!important; }
.glass-input-error:focus { border-color: rgba(239,68,68,.6)!important; box-shadow: 0 0 0 3px rgba(239,68,68,.15), 0 0 20px rgba(239,68,68,.2)!important; }
.glass-button-secondary { padding: .75rem 1.25rem; background: rgba(255,255,255,.1); backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,.2); border-radius: 12px; color: #fff; font-weight: 500; white-space: nowrap; cursor: pointer; transition: all .3s cubic-bezier(.4,0,.2,1); }
.glass-button-secondary:hover:not(:disabled) { background: rgba(255,255,255,.18); border-color: rgba(255,255,255,.3); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,.2); }
.glass-button-secondary:disabled { opacity: .5; cursor: not-allowed; }
.submit-button { position: relative; width: 100%; padding: .875rem 1.5rem; background: linear-gradient(135deg,#3b82f6 0%,#8b5cf6 100%); border: none; border-radius: 12px; color: #fff; font-weight: 600; font-size: 1rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: .5rem; overflow: hidden; transition: all .3s cubic-bezier(.4,0,.2,1); box-shadow: 0 4px 15px rgba(59,130,246,.3), inset 0 1px 0 rgba(255,255,255,.2); }
.submit-button:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 8px 25px rgba(59,130,246,.4), inset 0 1px 0 rgba(255,255,255,.2); }
.submit-button:disabled { opacity: .6; cursor: not-allowed; transform: none; }
.button-shine { position: absolute; top: 0; left: -100%; width: 100%; height: 100%; background: linear-gradient(90deg,transparent,rgba(255,255,255,.3),transparent); transition: left .6s ease; }
.submit-button:hover:not(:disabled) .button-shine { left: 100%; }
.spinner { width: 16px; height: 16px; border: 2px solid rgba(255,255,255,.3); border-top-color: #fff; border-radius: 50%; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg);} }
.step-chip { color: rgba(255,255,255,.55); border: 1px solid rgba(255,255,255,.15); background: rgba(255,255,255,.06); border-radius: 9999px; padding: .25rem .65rem; }
.step-chip-active { color: #fff; border-color: rgba(59,130,246,.8); background: rgba(59,130,246,.2); }
.step-separator { width: 16px; height: 1px; background: rgba(255,255,255,.3); }
</style>
