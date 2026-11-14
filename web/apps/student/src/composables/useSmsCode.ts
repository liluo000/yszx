import { computed, onUnmounted, ref } from 'vue'
import type { Captcha } from '@yunshu/api'
import { accountApi } from '@/api'

/** 短信验证码场景（S-ACC-02） */
export type SmsScene = 'register' | 'login' | 'reset_password' | 'change_phone_old' | 'change_phone_new'
  | 'cancel_account'

/**
 * 图形验证 + 短信验证码发送：先通过图形验证（S-ACC-01）才能获取验证码（S-ACC-02）。
 * 验证码一次核销，发送成功后自动换一张图；倒计时按服务端返回的重发间隔开始。
 */
export function useSmsCode(scene: SmsScene) {
  const captcha = ref<Captcha | null>(null)
  const captchaCode = ref('')
  const captchaLoading = ref(false)
  const sending = ref(false)
  const countdown = ref(0)
  const sent = ref(false)
  let timer: ReturnType<typeof setInterval> | undefined

  const captchaImage = computed(() =>
    captcha.value ? `data:image/png;base64,${captcha.value.image_base64}` : '',
  )

  function stopTimer() {
    if (timer) {
      clearInterval(timer)
      timer = undefined
    }
  }

  function startCountdown(seconds: number) {
    stopTimer()
    countdown.value = seconds
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) stopTimer()
    }, 1000)
  }

  async function refreshCaptcha() {
    captchaLoading.value = true
    captchaCode.value = ''
    try {
      captcha.value = await accountApi.captcha()
    } catch {
      captcha.value = null
    } finally {
      captchaLoading.value = false
    }
  }

  /** 发送验证码；失败时抛出错误由页面展示 */
  async function send(phone: string) {
    if (!captcha.value) {
      throw new Error('图形验证已失效，请点击图片刷新')
    }
    if (!captchaCode.value.trim()) {
      throw new Error('请先输入图形验证码')
    }
    sending.value = true
    try {
      const result = await accountApi.sendSmsCode({
        phone,
        scene,
        captcha_id: captcha.value.captcha_id,
        captcha_code: captchaCode.value.trim(),
      })
      sent.value = true
      startCountdown(result.resend_after)
      await refreshCaptcha()
    } finally {
      sending.value = false
    }
  }

  onUnmounted(stopTimer)

  return {
    captcha,
    captchaImage,
    captchaCode,
    captchaLoading,
    sending,
    countdown,
    sent,
    refreshCaptcha,
    send,
  }
}
