// Global type definitions for the application

export interface User {
  uuid: string
  username: string
  email?: string
  qq?: string
  regTime: number
  status: 'pending' | 'approved' | 'rejected' | 'banned'
}

export interface ApiResponse<T = any> {
  success: boolean
  message?: string
  msg?: string
  data?: T
  users?: User[]
  pagination?: PaginationInfo
}

export interface PaginationInfo {
  currentPage: number
  pageSize: number
  totalCount: number
  totalPages: number
  hasNext: boolean
  hasPrev: boolean
}

export interface Config {
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
    username_regex: string
  }
  authme: {
    enabled: boolean
    require_password: boolean
    auto_register: boolean
    auto_unregister: boolean
    password_regex: string
  }
}

export interface VersionInfo {
  currentVersion: string
  latestVersion: string
  updateAvailable: boolean
  releasesUrl: string
}