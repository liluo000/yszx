<script setup lang="ts">
// 登录（S-ACC-04）：验证码登录支持手机号未注册时自动注册，密码登录只针对已设置密码的学员
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { StudentAgreementType } from '@yunshu/api'
import { accountApi, errorText } from '@/api'
import { useSmsCode } from '@/composables/useSmsCode'
import { useAgreements } from '@/composables/useAgreements'
import { useUserStore } from '@/stores/user'
import AgreementDialog from '@/components/AgreementDialog.vue'

const PHONE_RE = /^1[0-9]{10}$/
const MODES = [
  { value: 'sms', label: '验证码登录' },
  { value: 'password', label: '密码登录' },
] as const

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const mode = ref<'sms' | 'password'>('sms')
const phone = ref('')
const smsCode = ref('')
const password = ref('')
const agreed = ref(false)
const submitting = ref(false)
const error = ref('')
// 个人中心改密码/换绑手机号后会作废全部会话，跳回登录页并带上提示
const hint = ref(typeof route.query.notice === 'string' ? route.query.notice : '')
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
} = useSmsCode('login')
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

function switchMode(next: 'sms' | 'password') {
  mode.value = next
  error.value = ''
  hint.value = ''
}

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

/** 登录成功后回到来源页；只接受站内相对路径，避免被外部地址利用 */
function redirectTarget(): string {
  const raw = route.query.redirect
  const target = typeof raw === 'string' ? raw : '/'
  return target.startsWith('/') && !target.startsWith('//') ? target : '/'
}

async function onSubmit() {
  error.value = ''
  hint.value = ''
  if (!PHONE_RE.test(phone.value)) {
    error.value = '请输入正确的手机号'
    return
  }
  if (mode.value === 'sms') {
    if (!/^[0-9]{6}$/.test(smsCode.value)) {
      error.value = '短信验证码为 6 位数字'
      return
    }
    if (!agreed.value) {
      error.value = '请阅读并同意用户协议和隐私政策'
      return
    }
  } else if (password.value.length < 6 || password.value.length > 20) {
    error.value = '密码长度为 6~20 位'
    return
  }

  submitting.value = true
  try {
    const result =
      mode.value === 'sms'
        ? await accountApi.login({
            login_type: 'sms',
            phone: phone.value,
            sms_code: smsCode.value,
            agreements: await buildRefs(),
          })
        : await accountApi.login({
            login_type: 'password',
            phone: phone.value,
            password: password.value,
          })
    user.signIn(result)
    await router.replace(redirectTarget())
  } catch (e) {
    error.value = errorText(e, '登录失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

/** 忘记密码：用 S-ACC-06 走短信验证码重置密码 */
function onForgot() {
  resetError.value = ''
  resetPhone.value = PHONE_RE.test(phone.value) ? phone.value : ''
  resetCode.value = ''
  resetPasswordValue.value = ''
  resetConfirm.value = ''
  resetSms.refreshCaptcha()
  resetOpen.value = true
}

const resetOpen = ref(false)
const resetPhone = ref('')
const resetCode = ref('')
const resetPasswordValue = ref('')
const resetConfirm = ref('')
const resetError = ref('')
const resetBusy = ref(false)
const resetSms = useSmsCode('reset_password')

async function submitReset() {
  resetError.value = ''
  if (!PHONE_RE.test(resetPhone.value)) {
    resetError.value = '请输入正确的手机号'
    return
  }
  if (resetPasswordValue.value.length < 6 || resetPasswordValue.value.length > 20) {
    resetError.value = '密码长度需要 6~20 位'
    return
  }
  if (resetPasswordValue.value !== resetConfirm.value) {
    resetError.value = '两次输入的密码不一致'
    return
  }
  resetBusy.value = true
  try {
    await accountApi.resetPassword({
      phone: resetPhone.value,
      sms_code: resetCode.value.trim(),
      new_password: resetPasswordValue.value,
    })
    resetOpen.value = false
    switchMode('password')
    phone.value = resetPhone.value
    password.value = ''
    hint.value = '密码已重置，请用新密码登录'
  } catch (e) {
    resetError.value = errorText(e, '重置失败，请稍后重试')
  } finally {
    resetBusy.value = false
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
        <h1>登录云枢智学</h1>
        <p class="muted">登录后保存学习进度、错题与面试记录。</p>
      </header>

      <div class="auth-tabs" role="tablist" aria-label="登录方式">
        <button
          v-for="item in MODES"
          :key="item.value"
          type="button"
          role="tab"
          class="auth-tab"
          :class="{ active: mode === item.value }"
          :aria-selected="mode === item.value"
          @click="switchMode(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

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

        <template v-if="mode === 'sms'">
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
        </template>

        <label v-else class="auth-field">
          <span class="auth-label">密码</span>
          <input
            v-model="password"
            class="input"
            type="password"
            maxlength="20"
            autocomplete="current-password"
            placeholder="6~20 位密码"
          />
        </label>

        <div v-if="mode === 'sms'" class="auth-agree">
          <label class="auth-check">
            <input v-model="agreed" type="checkbox" />
            <span>我已阅读并同意</span>
          </label>
          <button type="button" class="auth-link" @click="onAgreementClick('user_agreement')">《用户协议》</button>
          <span>和</span>
          <button type="button" class="auth-link" @click="onAgreementClick('privacy_policy')">《隐私政策》</button>
        </div>

        <button type="submit" class="btn btn-primary auth-submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>

      <footer class="auth-foot">
        <button type="button" class="auth-link" @click="onForgot">忘记密码？</button>
        <span class="muted">没有账号？<RouterLink to="/register">立即注册</RouterLink></span>
      </footer>
    </div>

    <AgreementDialog
      v-if="agreementOpen"
      :open="true"
      :type="agreementOpen"
      :state="agreementStates[agreementOpen]"
      @close="agreementOpen = null"
    />

    <!-- 忘记密码：短信验证码重置 -->
    <div
      v-if="resetOpen"
      class="mask"
      role="dialog"
      aria-modal="true"
      aria-label="重置密码"
      @click.self="resetOpen = false"
    >
      <div class="dialog card">
        <header class="dialog-head">
          <h2>重置密码</h2>
          <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="resetOpen = false">✕</button>
        </header>
        <p class="muted tip">通过手机号验证码重置，重置后所有设备需重新登录。</p>
        <div class="field">
          <label class="field-label" for="reset-phone">手机号</label>
          <input id="reset-phone" v-model="resetPhone" class="input" maxlength="11" />
        </div>
        <div class="field">
          <label class="field-label" for="reset-code">短信验证码</label>
          <div class="sms-row">
            <input id="reset-code" v-model="resetCode" class="input" maxlength="6" />
            <button
              type="button"
              class="btn btn-sm"
              :disabled="resetSms.sending.value || resetSms.countdown.value > 0"
              @click="resetSms.send(resetPhone)"
            >
              {{ resetSms.countdown.value > 0 ? `${resetSms.countdown.value}s` : '获取验证码' }}
            </button>
          </div>
          <div class="captcha-row">
            <input v-model="resetSms.captchaCode.value" class="input" maxlength="8" placeholder="图形验证码" />
            <img
              v-if="resetSms.captchaImage.value"
              class="captcha"
              :src="resetSms.captchaImage.value"
              alt="图形验证码，点击刷新"
              @click="resetSms.refreshCaptcha()"
            />
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="reset-pwd">新密码</label>
          <input id="reset-pwd" v-model="resetPasswordValue" class="input" type="password" autocomplete="new-password" />
        </div>
        <div class="field">
          <label class="field-label" for="reset-pwd2">确认新密码</label>
          <input id="reset-pwd2" v-model="resetConfirm" class="input" type="password" autocomplete="new-password" />
        </div>
        <p v-if="resetError" class="dialog-error" role="alert">{{ resetError }}</p>
        <footer class="dialog-foot">
          <button type="button" class="btn" @click="resetOpen = false">取消</button>
          <button type="button" class="btn btn-primary" :disabled="resetBusy" @click="submitReset">
            {{ resetBusy ? '提交中…' : '确认重置' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tip {
  font-size: var(--text-sm);
}

.dialog-error {
  color: var(--color-error);
  font-size: var(--text-sm);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
}

.field-label {
  color: var(--color-ink-2);
  font-size: var(--text-sm);
}

.sms-row {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.sms-row .btn {
  flex: none;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-top: var(--space-2xs);
}

.captcha {
  flex: none;
  height: var(--control-h);
  border: var(--rule-hair) solid var(--color-rule-2);
  border-radius: var(--radius-input);
  cursor: pointer;
}

</style>
