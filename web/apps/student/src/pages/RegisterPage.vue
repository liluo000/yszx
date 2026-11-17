<script setup lang="ts">
// 注册（S-ACC-03）：手机号 + 短信验证码，须勾选两类协议，成功后自动登录
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { StudentAgreementType } from '@yunshu/api'
import { accountApi, errorText } from '@/api'
import { useSmsCode } from '@/composables/useSmsCode'
import { useAgreements } from '@/composables/useAgreements'
import { useUserStore } from '@/stores/user'
import AgreementDialog from '@/components/AgreementDialog.vue'

const PHONE_RE = /^1[0-9]{10}$/

const router = useRouter()
const user = useUserStore()

const phone = ref('')
const smsCode = ref('')
const agreed = ref(false)
const submitting = ref(false)
const error = ref('')
const hint = ref('')
const agreementOpen = ref<StudentAgreementType | null>(null)

const {
  captchaImage,
  captchaCode,
  captchaLoading,
  sending,
  countdown,
  sent,
  refreshCaptcha,
  send,
} = useSmsCode('register')
const { states: agreementStates, load: loadAgreements, buildRefs } = useAgreements()

const sendLabel = computed(() => {
  if (countdown.value > 0) return `${countdown.value} 秒后重发`
  if (sending.value) return '发送中…'
  return sent.value ? '重新获取' : '获取验证码'
})

onMounted(() => {
  refreshCaptcha()
  loadAgreements()
})

async function onSendCode() {
  error.value = ''
  hint.value = ''
  if (!PHONE_RE.test(phone.value)) {
    error.value = '请输入正确的手机号'
    return
  }
  try {
    await send(phone.value)
    hint.value = '验证码已发送，5 分钟内有效'
  } catch (e) {
    error.value = errorText(e, '验证码发送失败，请稍后重试')
    refreshCaptcha()
  }
}

async function onSubmit() {
  error.value = ''
  hint.value = ''
  if (!PHONE_RE.test(phone.value)) {
    error.value = '请输入正确的手机号'
    return
  }
  if (!/^[0-9]{6}$/.test(smsCode.value)) {
    error.value = '短信验证码为 6 位数字'
    return
  }
  if (!agreed.value) {
    error.value = '请阅读并同意用户协议和隐私政策'
    return
  }

  submitting.value = true
  try {
    const result = await accountApi.register({
      phone: phone.value,
      sms_code: smsCode.value,
      agreements: await buildRefs(),
    })
    user.signIn(result)
    await router.replace('/')
  } catch (e) {
    error.value = errorText(e, '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function onAgreementClick(type: StudentAgreementType) {
  if (!agreementStates.value[type].published) {
    hint.value = '协议内容尚未发布，如需了解请联系客服'
  }
  agreementOpen.value = type
}
</script>

<template>
  <div class="auth">
    <div class="auth-card card">
      <header class="auth-head">
        <h1>注册云枢智学</h1>
        <p class="muted">一个手机号只能注册一个账号，注册后自动登录。</p>
      </header>

      <form class="auth-form" novalidate @submit.prevent="onSubmit">
        <p v-if="error" class="auth-notice error" role="alert">{{ error }}</p>
        <p v-else-if="hint" class="auth-notice info">{{ hint }}</p>

        <label class="auth-field">
          <span class="auth-label">手机号</span>
          <input
            v-model.trim="phone"
            class="input fig"
            type="tel"
            inputmode="numeric"
            maxlength="11"
            autocomplete="tel"
            placeholder="请输入 11 位手机号"
          />
        </label>

        <div class="auth-field">
          <span class="auth-label">图形验证码</span>
          <div class="auth-row">
            <input
              v-model.trim="captchaCode"
              class="input"
              type="text"
              maxlength="8"
              autocomplete="off"
              placeholder="请输入右侧字符"
              aria-label="图形验证码"
            />
            <button
              type="button"
              class="auth-captcha"
              :disabled="captchaLoading"
              aria-label="图形验证码，点击刷新"
              @click="refreshCaptcha"
            >
              <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
              <span v-else class="muted">点击刷新</span>
            </button>
          </div>
        </div>

        <label class="auth-field">
          <span class="auth-label">短信验证码</span>
          <div class="auth-row">
            <input
              v-model.trim="smsCode"
              class="input fig"
              type="text"
              inputmode="numeric"
              maxlength="6"
              autocomplete="one-time-code"
              placeholder="6 位验证码"
            />
            <button type="button" class="btn auth-send" :disabled="sending || countdown > 0" @click="onSendCode">
              {{ sendLabel }}
            </button>
          </div>
        </label>

        <p class="muted note">验证码 5 分钟内有效，输错 5 次即失效。</p>

        <div class="auth-agree">
          <label class="auth-check">
            <input v-model="agreed" type="checkbox" />
            <span>我已阅读并同意</span>
          </label>
          <button type="button" class="auth-link" @click="onAgreementClick('user_agreement')">《用户协议》</button>
          <span>和</span>
          <button type="button" class="auth-link" @click="onAgreementClick('privacy_policy')">《隐私政策》</button>
        </div>

        <button type="submit" class="btn btn-primary auth-submit" :disabled="submitting">
          {{ submitting ? '注册中…' : '注册并登录' }}
        </button>
      </form>

      <footer class="auth-foot">
        <span class="muted">已有账号？<RouterLink to="/login">去登录</RouterLink></span>
      </footer>
    </div>

    <AgreementDialog
      v-if="agreementOpen"
      :open="true"
      :type="agreementOpen"
      :state="agreementStates[agreementOpen]"
      @close="agreementOpen = null"
    />
  </div>
</template>

<style scoped>
.note {
  font-size: var(--text-xs);
}

</style>
