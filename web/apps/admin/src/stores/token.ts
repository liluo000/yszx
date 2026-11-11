/** 后台登录令牌保存在本机，学员端使用不同的键，两套账号互不影响 */
const TOKEN_KEY = 'yunshu.admin.token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    // 浏览器禁用存储时只在本次页面中保持登录
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 忽略
  }
}
