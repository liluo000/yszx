// 学员登录令牌：存在 localStorage，多设备同时登录由服务端控制（需求 5.1.6）
const TOKEN_KEY = 'yunshu:student:token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}
