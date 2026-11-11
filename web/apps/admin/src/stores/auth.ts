import { defineStore } from 'pinia'
import type { SysUserView } from '@yunshu/api'
import { authApi } from '@/api'
import { clearToken, getToken, setToken } from './token'

interface AuthState {
  token: string | null
  user: SysUserView | null
  permissions: string[]
  mustChangePassword: boolean
  loaded: boolean
}

export const useAuthStore = defineStore('auth', {
  // 令牌放在状态中以便响应式更新，同时保存在本机，刷新页面后仍保持登录
  state: (): AuthState => ({
    token: getToken(),
    user: null,
    permissions: [],
    mustChangePassword: false,
    loaded: false,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
  },
  actions: {
    async login(account: string, password: string) {
      const result = await authApi.login(account, password)
      setToken(result.token)
      this.token = result.token
      await this.fetchMe()
    },
    async fetchMe() {
      const me = await authApi.me()
      this.user = me.user
      this.permissions = me.permissions
      this.mustChangePassword = me.must_change_password
      this.loaded = true
    },
    hasPermission(permission: string) {
      return this.permissions.includes(permission)
    },
    /** 修改密码后后端会让所有设备退出，这里同步清除本机登录状态 */
    async changePassword(oldPassword: string, newPassword: string) {
      await authApi.changePassword(oldPassword, newPassword)
      this.reset()
    },
    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.reset()
      }
    },
    /** 清除本机登录状态；$reset 会重新读取本机令牌，所以要先清除存储 */
    reset() {
      clearToken()
      this.$reset()
    },
  },
})
