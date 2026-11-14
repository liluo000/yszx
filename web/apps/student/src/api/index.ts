import {
  createHomeApi,
  createHttpClient,
  createStudentAccountApi,
  createStudentPracticeApi,
  createStudentQuotaApi,
  createStudentRouteApi,
  createStudentWrongApi,
} from '@yunshu/api'
import { clearToken, getToken } from '@/stores/token'

/**
 * 接口错误提示：业务错误直接展示服务端中文提示，网络异常统一兜底
 */
export function errorText(error: unknown, fallback = '加载失败，请稍后重试'): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message) return message
  }
  return fallback
}

/** 令牌失效事件：登录态由 stores/user 订阅后清理，避免接口层反向依赖 store */
export const UNAUTHORIZED_EVENT = 'yunshu:student-unauthorized'

/** 学员端接口客户端：令牌失效时清除登录态 */
export const http = createHttpClient({
  baseURL: '/api/v1',
  getToken,
  onUnauthorized: () => {
    clearToken()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  },
})

export const homeApi = createHomeApi(http)
export const routeApi = createStudentRouteApi(http)
export const accountApi = createStudentAccountApi(http)
export const wrongApi = createStudentWrongApi(http)
export const practiceApi = createStudentPracticeApi(http)
export const quotaApi = createStudentQuotaApi(http)
