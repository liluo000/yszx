import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginResult, StudentProfile } from '@yunshu/api'
import { accountApi, UNAUTHORIZED_EVENT } from '@/api'
import { clearToken, getToken, setToken } from '@/stores/token'

/**
 * 学员登录态：令牌存 localStorage（7 天内有访问保持登录由服务端控制），
 * 本人信息只放内存，刷新页面后由 restore() 通过 S-ACC-07 恢复。
 */
export const useUserStore = defineStore('student-user', () => {
  const token = ref<string | null>(getToken())
  const profile = ref<StudentProfile | null>(null)
  const restoring = ref(false)
  let restoringTask: Promise<void> | null = null

  const isLoggedIn = computed(() => Boolean(token.value))
  const nickname = computed(() => profile.value?.nickname ?? '')

  function apply(result: LoginResult) {
    setToken(result.token)
    token.value = result.token
    profile.value = result.profile
  }

  /** 恢复登录态；并发调用只发一次请求 */
  function restore(): Promise<void> {
    if (!token.value || profile.value) return Promise.resolve()
    if (restoringTask) return restoringTask
    restoring.value = true
    restoringTask = accountApi
      .me()
      .then((me) => {
        profile.value = me
      })
      .catch(() => {
        // 令牌失效（401）或账号不可用：清掉本地登录态，按游客处理
        reset()
      })
      .finally(() => {
        restoring.value = false
        restoringTask = null
      })
    return restoringTask
  }

  function signIn(result: LoginResult) {
    apply(result)
  }

  /** 重新拉取本人信息（同意协议、修改资料后刷新顶部昵称与待同意状态） */
  async function refresh() {
    if (!token.value) return
    profile.value = await accountApi.me()
  }

  function signOut() {
    return accountApi
      .logout()
      .catch(() => undefined)
      .finally(reset)
  }

  function reset() {
    clearToken()
    token.value = null
    profile.value = null
  }

  // 接口层收到 401 时同步清空内存中的登录态（store 为单例，只注册一次）
  window.addEventListener(UNAUTHORIZED_EVENT, reset)

  return { token, profile, isLoggedIn, nickname, restoring, restore, refresh, signIn, signOut, reset }
})
