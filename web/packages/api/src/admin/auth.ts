import type { HttpClient } from '../http'

export type AdminRole = 'super_admin' | 'content_operator' | 'student_operator'

/** 后台账号（04.API接口设计.md 公共对象 SysUserView） */
export interface SysUserView {
  id: number
  account: string
  name: string
  phone: string
  role: AdminRole
  status: 'enabled' | 'disabled'
  last_login_at: string | null
  created_at: string
}

export interface LoginResponse {
  token: string
  must_change_password: boolean
  user: SysUserView
}

export interface MeResponse {
  user: SysUserView
  must_change_password: boolean
  permissions: string[]
}

/** 后台登录相关接口（A-IAM-01~04） */
export function createAdminAuthApi(http: HttpClient) {
  return {
    login: (account: string, password: string) => http.post<LoginResponse>('/admin/sessions', { account, password }),
    logout: () => http.delete<null>('/admin/sessions/current'),
    me: () => http.get<MeResponse>('/admin/me'),
    changePassword: (oldPassword: string, newPassword: string) =>
      http.put<null>('/admin/me/password', { old_password: oldPassword, new_password: newPassword }),
  }
}
