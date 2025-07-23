import { useI18n } from 'vue-i18n'

const API_BASE = '/api'

export interface ApiResponse<T = any> {
  success: boolean
  message?: string
  data?: T
}

export interface ConfigResponse {
  login: {
    enable_email: boolean
    email_smtp: string
  }
  admin: any
  frontend: {
    theme: string
    logo_url: string
    announcement: string
    web_server_prefix: string
  }
}

export interface SendCodeRequest {
  email: string
  language: string
}

export interface SendCodeResponse {
  success: boolean
  msg: string
}

export interface RegisterRequest {
  email: string
  code: string
  uuid: string
  username: string
  language: string
}

export interface RegisterResponse {
  success: boolean
  msg: string
}

export interface AdminLoginRequest {
  password: string
  language: string
}

export interface AdminLoginResponse {
  success: boolean
  token: string
  message: string
}

export interface PendingUser {
  uuid: string
  username: string
  email: string
  status: string
  regTime: string
}

export interface PendingListResponse {
  success: boolean
  users: PendingUser[]
  message?: string
}

export interface ReviewRequest {
  uuid: string
  action: 'approve' | 'reject'
  reason?: string
  language: string
}

export interface ReviewResponse {
  success: boolean
  msg: string
}

class ApiService {
  private getAuthHeaders(): Record<string, string> {
    const token = localStorage.getItem('admin_token')
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

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const url = `${API_BASE}${endpoint}`
    const response = await fetch(url, {
      headers: this.getAuthHeaders(),
      ...options,
    })

    if (response.status === 401 || response.status === 403) {
      localStorage.removeItem('admin_token')
      window.location.href = '/login'
      throw new Error('Authentication required')
    }

    const data = await response.json()
    if (data && data.success === false && data.message && data.message.includes('Authentication required')) {
      localStorage.removeItem('admin_token')
      window.location.href = '/login'
      throw new Error('Authentication required')
    }
    return data
  }

  // 获取配置
  async getConfig(): Promise<ConfigResponse> {
    return this.request<ConfigResponse>('/config')
  }

  // 发送验证码
  async sendCode(data: SendCodeRequest): Promise<SendCodeResponse> {
    return this.request<SendCodeResponse>('/send_code', {
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

  // 管理员登录
  async adminLogin(data: AdminLoginRequest): Promise<AdminLoginResponse> {
    return this.request<AdminLoginResponse>('/admin-login', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // 获取待审核用户列表
  async getPendingList(language: string = 'zh'): Promise<PendingListResponse> {
    return this.request<PendingListResponse>(`/pending-list?language=${language}`)
  }

  // 审核用户
  async reviewUser(data: ReviewRequest): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/review', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  // 删除用户
  async deleteUser(uuid: string, language: string = 'zh'): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/delete-user', {
      method: 'POST',
      body: JSON.stringify({ uuid, language }),
    })
  }

  // 封禁用户
  async banUser(uuid: string, language: string = 'zh'): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/ban-user', {
      method: 'POST',
      body: JSON.stringify({ uuid, language }),
    })
  }

  // 解封用户
  async unbanUser(uuid: string, language: string = 'zh'): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/unban-user', {
      method: 'POST',
      body: JSON.stringify({ uuid, language }),
    })
  }

  // 更新公告
  async updateAnnouncement(content: string, language: string = 'zh'): Promise<ReviewResponse> {
    return this.request<ReviewResponse>('/update-announcement', {
      method: 'POST',
      body: JSON.stringify({ content, language }),
    })
  }

  // 重载配置
  async reloadConfig(): Promise<{ success: boolean; message: string }> {
    return this.request<{ success: boolean; message: string }>('/reload-config', {
      method: 'POST',
    })
  }

  // 获取所有用户
  async getAllUsers(): Promise<{ success: boolean; users: PendingUser[]; message?: string }> {
    return this.request<{ success: boolean; users: PendingUser[]; message?: string }>('/all-users')
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
  async getUserStatus(): Promise<{ success: boolean; data: { status: string; reason?: string }; message?: string }> {
    return this.request<{ success: boolean; data: { status: string; reason?: string }; message?: string }>('/user-status')
  }

  // 检查认证状态
  isAuthenticated(): boolean {
    const token = localStorage.getItem('admin_token')
    return token !== null
  }

  // 登出
  logout(): void {
    localStorage.removeItem('admin_token')
  }
}

export const apiService = new ApiService()
