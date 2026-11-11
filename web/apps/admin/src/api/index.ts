import { createAdminAuthApi, createAuditApi, createConfigApi, createHttpClient, createSysUserApi } from '@yunshu/api'
import { getToken } from '@/stores/token'

let unauthorizedHandler: () => void = () => {}

/** 令牌失效（401）时的处理，在 main.ts 中注册：清除登录状态并跳转登录页 */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

/** 管理后台的接口客户端 */
export const http = createHttpClient({
  baseURL: '/api/v1',
  getToken,
  onUnauthorized: () => unauthorizedHandler(),
})

export const authApi = createAdminAuthApi(http)
export const sysUserApi = createSysUserApi(http)
export const auditApi = createAuditApi(http)
export const configApi = createConfigApi(http)
