import { sessionService } from '@/services/session'
import type { PendingUser, ServerStatusData } from '@/types'

const API_BASE = '/api'

export interface ApiResponse<T = unknown> {
  success: boolean
  message?: string
  data?: T
}

export interface ConfigResponse {
  authMethods: string[]
  theme: string
  logoUrl: string
  announcement: string
  webServerPrefix: string
  wsPort?: number
  usernameRegex: string
  authme: {
    enabled: boolean
    requirePassword: boolean
    passwordRegex: string
  }
  captcha?: {
    enabled: boolean
    emailEnabled: boolean
    type: string
  }
  discord?: {
    enabled: boolean
    required: boolean
  }
  questionnaire?: {
    enabled: boolean
    passScore: number
    hasTextQuestions?: boolean
  }
  bedrock?: {
    enabled: boolean
    prefix: string
    usernameRegex: string
  }
  emailDomainWhitelist?: string[]
  enableEmailDomainWhitelist?: boolean
  enableEmailAliasLimit?: boolean
  language?: string
}

export interface QuestionnaireAnswer {
  type: string
  selectedOptionIds: number[]
  textAnswer: string
}

export interface QuestionOption {
  id: number
  text: string
}

export interface QuestionInputMeta {
  minSelections?: number
  maxSelections?: number
  minLength?: number
  maxLength?: number
  multiline?: boolean
  placeholder?: string
}

export type QuestionType = 'single_choice' | 'multiple_choice' | 'text'

export interface Question {
  id: number
  question: string
  type: QuestionType
  required: boolean
  options?: QuestionOption[]
  input?: QuestionInputMeta
}

export interface SubmitQuestionnaireResponse {
  success: boolean
  passed: boolean
  score: number
  passScore: number
  manualReviewRequired?: boolean
  token?: string
  submittedAt?: number
  expiresAt?: number
  message?: string
}

export interface QuestionnaireSubmission {
  passed: boolean
  score: number
  passScore: number
  manualReviewRequired?: boolean
  answers: Record<string, QuestionnaireAnswer>
  token: string
  submittedAt: number
  expiresAt: number
}

export interface CaptchaResponse {
  success: boolean
  token?: string
  image?: string
  message?: string
}

export interface SendCodeRequest {
  email: string
  language: string
}

export interface SendCodeResponse {
  success: boolean
  message: string
  remainingSeconds?: number
}

export interface RegisterRequest {
  email: string
  code?: string
  username: string
  password?: string
  captchaToken?: string
  captchaAnswer?: string
  language: string
  platform?: 'java' | 'bedrock'
  questionnaire?: QuestionnaireSubmission
}

export interface RegisterResponse {
  success: boolean
  message: string
}

export interface AdminLoginRequest {
  username: string
  password: string
  language: string
}

export interface AdminLoginResponse {
  success: boolean
  token: string
  message: string
  isAdmin?: boolean
  username?: string
}

export interface PendingListResponse {
  success: boolean
  users: PendingUser[]
  message?: string
}

export interface ReviewRequest {
  username: string
  action: 'approve' | 'reject'
  reason?: string
  language: string
}

export interface ReviewResponse {
  success: boolean
  message: string
}

export interface ChangePasswordRequest {
  username: string
  password: string
  language: string
}

export interface AuditRecord {
  id?: number
  action: string
  operator: string
  target: string
  detail: string
  timestamp: number
}

export interface AuditListResponse {
  success: boolean
  audits: AuditRecord[]
  message?: string
}

export interface DownloadResource {
  id: string
  name: string
  description: string
  version?: string
  size?: string
  url: string
  icon?: string
}

class ApiService {
  private getAuthHeaders(): Record<string, string> {
    const token = sessionService.getToken()
    if (token) {
      return {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    }
    return {
      'Content-Type': 'application/json'
    }
  }

  private getResponseMessage(payload: { message?: string } | null | undefined): string {
    return payload?.message || ''
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const url = `${API_BASE}${endpoint}`
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), 30000)

    try {
      const response = await fetch(url, {
        headers: this.getAuthHeaders(),
        ...options,
        signal: controller.signal,
      })

      // 处理 401/403 认证错误
      if (response.status === 401 || response.status === 403) {
        sessionService.handleUnauthorized()
        throw new Error('Authentication required')
      }

      // 检查 HTTP 状态码，非 2xx 响应抛出错误
      if (!response.ok) {
        let errorMessage = `HTTP error: ${response.status}`
        try {
          const errorData = await response.json()
          errorMessage = errorData?.message || errorMessage
        } catch {
          // 无法解析 JSON，使用默认错误消息
        }
        throw new Error(errorMessage)
      }

      const data = await response.json()
      const responseMessage = this.getResponseMessage(data)
      if (data && data.success === false && responseMessage.includes('Authentication required')) {
        sessionService.handleUnauthorized()
        throw new Error('Authentication required')
      }
      return data
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        throw new Error('Request timeout')
      }
      throw error
    } finally {
      clearTimeout(timeoutId)
    }
  }

  // 获取配置
  async getConfig(): Promise<ConfigResponse> {
    const response = await this.request<{ success: boolean; config: ConfigResponse }>('/config')
    // 运行时验证：优先使用 config 字段，否则验证 response 是否符合 ConfigResponse 结构
    if (response.config) {
      return response.config
    }
    // 检查 response 本身是否符合 ConfigResponse 结构
    const candidate = response as unknown
    if (
      candidate &&
      typeof candidate === 'object' &&
      'authMethods' in candidate &&
      Array.isArray((candidate as Record<string, unknown>).authMethods)
    ) {
      return candidate as ConfigResponse
    }
    throw new Error('Invalid config response format')
  }

  // 获取验证码
  async getCaptcha(): Promise<CaptchaResponse> {
    return this.request<CaptchaResponse>('/captcha')
  }

  // 发送验证码
  async sendCode(data: SendCodeRequest): Promise<SendCodeResponse> {
    return this.request<SendCodeResponse>('/verify/send', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // 注册用户
  async register(data: RegisterRequest): Promise<RegisterResponse> {
    return this.request<RegisterResponse>('/register', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // 获取问卷
  async getQuestionnaire(language: string): Promise<{
    success: boolean
    data?: {
      enabled: boolean
      questions: Question[]
    }
    message?: string
  }> {
    return this.request(`/questionnaire/config?language=${encodeURIComponent(language)}`)
  }

  // 提交问卷
  async submitQuestionnaire(payload: {
    answers: Record<string, QuestionnaireAnswer>
    language: string
  }): Promise<SubmitQuestionnaireResponse> {
    return this.request<SubmitQuestionnaireResponse>('/questionnaire/submit', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  }

  // 统一登录接口（管理员和玩家共用）
  async login(data: AdminLoginRequest): Promise<AdminLoginResponse> {
    return this.request<AdminLoginResponse>('/login', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // 兼容旧方法名
  async adminLogin(data: AdminLoginRequest): Promise<AdminLoginResponse> {
    return this.login(data)
  }

  // 获取待审核用户列表
  async getPendingList(language: string = 'zh'): Promise<PendingListResponse> {
    return this.request<PendingListResponse>(`/admin/users?language=${language}&status=pending`)
  }

  // 审核用户
  async reviewUser(data: ReviewRequest): Promise<ReviewResponse> {
    const endpoint = data.action === 'approve' ? '/admin/user/approve' : '/admin/user/reject'
    return this.request<ReviewResponse>(endpoint, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // 删除用户
  async deleteUser(username: string, language: string = 'zh'): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/admin/user/delete', {
      method: 'POST',
      body: JSON.stringify({ username, language }),
    })
  }

  // 封禁用户
  async banUser(username: string, language: string = 'zh', reason?: string): Promise<ReviewResponse> {
    const body: Record<string, string> = { username, language }
    if (reason) body.reason = reason
    return this.request<ReviewResponse>('/admin/user/ban', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  }

  // 解封用户
  async unbanUser(username: string, language: string = 'zh'): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/admin/user/unban', {
      method: 'POST',
      body: JSON.stringify({ username, language }),
    })
  }

  // 获取所有用户
  async getAllUsers(): Promise<{ success: boolean; users: PendingUser[]; message?: string }> {
    return this.request<{ success: boolean; users: PendingUser[]; message?: string }>('/admin/users')
  }

  // 获取分页用户列表
  async getUsersPaginated(page: number = 1, pageSize: number = 10, search: string = ''): Promise<{
    success: boolean;
    users: PendingUser[];
    pagination: {
      currentPage: number;
      pageSize: number;
      totalCount: number;
      totalPages: number;
      hasNext: boolean;
      hasPrev: boolean;
    };
    message?: string;
  }> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: pageSize.toString(),
    });

    if (search.trim()) {
      params.append('search', search.trim());
    }

    return this.request(`/admin/users?${params.toString()}`);
  }

  // 获取待审核用户列表 (兼容方法)
  async getPendingUsers(): Promise<{ success: boolean; data: PendingUser[]; message?: string }> {
    const response = await this.getPendingList()
    return {
      success: response.success,
      data: response.users,
      message: response.message
    }
  }

  // 获取用户状态
  async getUserStatus(language: string = 'zh'): Promise<{ success: boolean; data: { status: string; reason?: string }; message?: string }> {
    const params = new URLSearchParams({ language })
    const username = sessionService.getUserInfo()?.username

    if (username) {
      params.set('username', username)
    }

    return this.request<{ success: boolean; data: { status: string; reason?: string }; message?: string }>(`/user/status?${params.toString()}`)
  }

  // 修改用户密码
  async changePassword(data: ChangePasswordRequest): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/admin/user/password', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // 检查认证状态
  isAuthenticated(): boolean {
    const token = sessionService.getToken()
    return token !== null
  }

  // 登出
  logout(): void {
    sessionService.clearToken()
  }

  // 版本检查
  async checkVersion(): Promise<{
    success: boolean;
    currentVersion?: string;
    latestVersion?: string;
    updateAvailable?: boolean;
    releasesUrl?: string;
    error?: string;
  }> {
    return this.request('/version')
  }

  // Discord OAuth - 获取授权 URL
  async getDiscordAuthUrl(username: string, language: string = 'en'): Promise<{
    success: boolean;
    authUrl?: string;
    message?: string;
  }> {
    return this.request(`/discord/auth?username=${encodeURIComponent(username)}&language=${language}`)
  }

  // Discord OAuth - 检查绑定状态
  async getDiscordStatus(username: string, language: string = 'en'): Promise<{
    success: boolean;
    linked: boolean;
    user?: {
      id: string;
      username: string;
      discriminator: string;
      avatar?: string;
      globalName?: string;
    };
    message?: string;
  }> {
    return this.request(`/discord/status?username=${encodeURIComponent(username)}&language=${language}`)
  }

  // Discord OAuth - 解绑 Discord 账号
  async unlinkDiscord(username: string, language: string = 'en'): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/discord/unlink', {
      method: 'POST',
      body: JSON.stringify({ username, language }),
    })
  }

  // 获取审计日志
  async getAuditLogs(): Promise<AuditListResponse> {
    return this.request<AuditListResponse>('/admin/audits')
  }

  // 获取下载资源列表（预留接口，后端可能未实现）
  async getDownloadResources(): Promise<{ success: boolean; resources?: DownloadResource[]; message?: string }> {
    return this.request('/downloads')
  }

  // 获取服务器状态（预留接口，后端可能未实现）
  async getServerStatus(): Promise<{
    success: boolean
    data?: ServerStatusData
    message?: string
  }> {
    return this.request('/server/status')
  }

  // 更新用户信息（预留接口）
  async updateUserInfo(data: {
    email?: string
    code?: string
    language?: string
  }): Promise<{ success: boolean; message?: string }> {
    return this.request('/user/update', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // 用户修改密码
  async userChangePassword(data: {
    currentPassword: string
    newPassword: string
    language: string
  }): Promise<{ success: boolean; message?: string }> {
    return this.request('/user/password', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // AuthMe 同步
  async syncAuthme(language: string = 'en'): Promise<{ success: boolean; message?: string; msg?: string }> {
    return this.request<{ success: boolean; message?: string; msg?: string }>('/admin/sync', {
      method: 'POST',
      body: JSON.stringify({ language }),
    })
  }
}

export const apiService = new ApiService()
