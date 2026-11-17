<script setup lang="ts">
// 个人中心（需求 5.9）：个人资料、求职画像、账号安全（密码/换绑/注销）、协议查看
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type {
  EducationCode,
  RouteCard,
  StudentAgreementType,
  StudentProfile,
  WorkYearsCode,
} from '@yunshu/api'
import { accountApi, errorText, routeApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useSmsCode } from '@/composables/useSmsCode'
import { AGREEMENT_LABEL, useAgreements } from '@/composables/useAgreements'
import AgreementDialog from '@/components/AgreementDialog.vue'

const router = useRouter()
const user = useUserStore()

const profile = ref<StudentProfile | null>(null)
const loading = ref(false)
const loadError = ref('')
const notice = ref('')
const routes = ref<RouteCard[]>([])

const EDUCATION_OPTIONS = [
  { value: 'high_school_or_below', label: '高中及以下' },
  { value: 'junior_college', label: '大专' },
  { value: 'bachelor', label: '本科' },
  { value: 'master_or_above', label: '硕士及以上' },
]
const WORK_YEARS_OPTIONS = [
  { value: 'student_or_graduate', label: '在校或应届' },
  { value: 'under_1_year', label: '1 年以内' },
  { value: '1_to_3_years', label: '1~3 年' },
  { value: '3_to_5_years', label: '3~5 年' },
  { value: 'over_5_years', label: '5 年以上' },
]

const maskedPhone = computed(() => {
  const phone = profile.value?.phone ?? ''
  return /^1\d{10}$/.test(phone) ? `${phone.slice(0, 3)}****${phone.slice(7)}` : phone
})
const isCancelling = computed(() => profile.value?.status === 'cancelling')
const cancellationDate = computed(() => {
  const at = profile.value?.cancellation_effective_at
  return at ? new Date(at).toLocaleDateString('zh-CN') : ''
})

/* ===== 个人资料 ===== */
const nickname = ref('')
const profileBusy = ref(false)
const profileError = ref('')
const avatarBusy = ref(false)
const avatarError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

/* ===== 求职画像 ===== */
const education = ref('')
const workYears = ref('')
const jobRouteId = ref<string>('')
const intentBusy = ref(false)
const intentError = ref('')

/** 求职意向路线已下架时，原值仍要能显示 */
const jobIntentOffShelf = computed(() => {
  const id = profile.value?.job_intent_learning_route_id
  if (!id) return false
  return !routes.value.some((route) => route.id === id)
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const me = await accountApi.me()
    applyProfile(me)
  } catch (e) {
    loadError.value = errorText(e, '个人信息加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
  try {
    const page = await routeApi.list({ page: 1, page_size: 100 })
    routes.value = page.items
  } catch {
    routes.value = []
  }
}

function applyProfile(me: StudentProfile) {
  profile.value = me
  user.profile = me
  nickname.value = me.nickname
  education.value = me.education ?? ''
  workYears.value = me.work_years ?? ''
  jobRouteId.value = me.job_intent_learning_route_id ? String(me.job_intent_learning_route_id) : ''
}

async function saveProfile() {
  profileError.value = ''
  const value = nickname.value.trim()
  if (!value || value.length > 30) {
    profileError.value = '昵称需要 1~30 个字符'
    return
  }
  profileBusy.value = true
  try {
    applyProfile(await accountApi.updateProfile({ nickname: value }))
    notice.value = '资料已保存'
  } catch (e) {
    profileError.value = errorText(e, '保存失败，请稍后重试')
  } finally {
    profileBusy.value = false
  }
}

function pickAvatar() {
  fileInput.value?.click()
}

async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  avatarError.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    avatarError.value = '请选择 JPG 或 PNG 图片'
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    avatarError.value = '图片不能超过 2MB'
    return
  }
  avatarBusy.value = true
  try {
    const result = await accountApi.uploadAvatar(file)
    if (profile.value) applyProfile({ ...profile.value, avatar_url: result.avatar_url })
    notice.value = '头像已更新'
  } catch (e) {
    avatarError.value = errorText(e, '头像上传失败')
  } finally {
    avatarBusy.value = false
  }
}

async function saveIntent() {
  intentError.value = ''
  intentBusy.value = true
  try {
    applyProfile(
      await accountApi.updateProfile({
        education: (education.value || null) as EducationCode | null,
        work_years: (workYears.value || null) as WorkYearsCode | null,
        job_intent_learning_route_id: jobRouteId.value ? Number(jobRouteId.value) : null,
      }),
    )
    notice.value = '求职画像已保存'
  } catch (e) {
    intentError.value = errorText(e, '保存失败，请稍后重试')
  } finally {
    intentBusy.value = false
  }
}

/* ===== 账号安全 ===== */
const pwdOpen = ref(false)
const phoneOpen = ref(false)
const cancelOpen = ref(false)
const pwdError = ref('')
const pwdBusy = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const pwdSmsCode = ref('')
const pwdSms = useSmsCode('reset_password')

const strength = computed(() => {
  const value = newPassword.value
  if (!value) return { level: '', text: '' }
  let kinds = 0
  if (/[a-z]/.test(value)) kinds += 1
  if (/[A-Z]/.test(value)) kinds += 1
  if (/[0-9]/.test(value)) kinds += 1
  if (/[^A-Za-z0-9]/.test(value)) kinds += 1
  if (kinds >= 3) return { level: 'high', text: '强度：高' }
  if (kinds === 2) return { level: 'medium', text: '强度：中' }
  return { level: 'low', text: '强度：低' }
})

function openPassword() {
  pwdError.value = ''
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  pwdSmsCode.value = ''
  pwdSms.refreshCaptcha()
  pwdOpen.value = true
}

async function submitPassword() {
  pwdError.value = ''
  if (newPassword.value.length < 6 || newPassword.value.length > 20) {
    pwdError.value = '密码长度需要 6~20 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    pwdError.value = '两次输入的密码不一致'
    return
  }
  pwdBusy.value = true
  try {
    await accountApi.changePassword(
      profile.value?.has_password
        ? { old_password: oldPassword.value, new_password: newPassword.value }
        : { sms_code: pwdSmsCode.value.trim(), new_password: newPassword.value },
    )
    pwdOpen.value = false
    await afterCredentialChange('密码已修改，请重新登录')
  } catch (e) {
    pwdError.value = errorText(e, '密码修改失败')
  } finally {
    pwdBusy.value = false
  }
}

const phoneStep = ref<'code' | 'password'>('code')
const newPhone = ref('')
const newSmsCode = ref('')
const oldSmsCode = ref('')
const oldPasswordForPhone = ref('')
const phoneError = ref('')
const phoneBusy = ref(false)
const newPhoneSms = useSmsCode('change_phone_new')
const oldPhoneSms = useSmsCode('change_phone_old')

function openPhone() {
  phoneError.value = ''
  newPhone.value = ''
  newSmsCode.value = ''
  oldSmsCode.value = ''
  oldPasswordForPhone.value = ''
  phoneStep.value = 'code'
  newPhoneSms.refreshCaptcha()
  oldPhoneSms.refreshCaptcha()
  phoneOpen.value = true
}

async function submitPhone() {
  phoneError.value = ''
  if (!/^1\d{10}$/.test(newPhone.value)) {
    phoneError.value = '请输入正确的手机号'
    return
  }
  phoneBusy.value = true
  try {
    await accountApi.changePhone({
      new_phone: newPhone.value,
      new_sms_code: newSmsCode.value.trim(),
      ...(phoneStep.value === 'code'
        ? { old_sms_code: oldSmsCode.value.trim() }
        : { password: oldPasswordForPhone.value }),
    })
    phoneOpen.value = false
    await afterCredentialChange('手机号已更换，请用新手机号重新登录')
  } catch (e) {
    phoneError.value = errorText(e, '换绑失败')
  } finally {
    phoneBusy.value = false
  }
}

const cancelSmsCode = ref('')
const cancelConfirmed = ref(false)
const cancelError = ref('')
const cancelBusy = ref(false)
const cancelSms = useSmsCode('cancel_account')

function openCancel() {
  cancelError.value = ''
  cancelSmsCode.value = ''
  cancelConfirmed.value = false
  cancelSms.refreshCaptcha()
  cancelOpen.value = true
}

async function submitCancel() {
  cancelError.value = ''
  if (!cancelConfirmed.value) {
    cancelError.value = '请先勾选确认'
    return
  }
  cancelBusy.value = true
  try {
    await accountApi.requestCancellation({ sms_code: cancelSmsCode.value.trim(), confirmed: true })
    cancelOpen.value = false
    user.reset()
    await router.replace('/')
    notice.value = ''
  } catch (e) {
    cancelError.value = errorText(e, '申请注销失败')
  } finally {
    cancelBusy.value = false
  }
}

async function undoCancel() {
  cancelError.value = ''
  try {
    applyProfile(await accountApi.cancelCancellation())
    notice.value = '已撤销注销，账号恢复正常'
  } catch (e) {
    cancelError.value = errorText(e, '撤销失败')
  }
}

/** 密码或手机号变更后服务端已作废全部会话，前端清态并去登录页 */
async function afterCredentialChange(message: string) {
  user.reset()
  await router.replace({ path: '/login', query: { notice: message } })
}

/* ===== 协议 ===== */
const AGREEMENT_TYPES: StudentAgreementType[] = ['user_agreement', 'privacy_policy']
const agreements = useAgreements()
const agreementOpen = ref<StudentAgreementType | null>(null)

function openAgreement(type: StudentAgreementType) {
  agreementOpen.value = type
}

onMounted(async () => {
  await load()
  await agreements.load()
})
</script>

<template>
  <div class="profile">
    <header class="page-head">
      <div>
        <h1>个人中心</h1>
        <p>维护个人资料与求职画像，管理账号安全。</p>
      </div>
    </header>

    <p v-if="loadError" class="alert-error" role="alert">{{ loadError }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div v-if="loading" class="skeleton">个人信息加载中…</div>

    <template v-else-if="profile">
      <section class="card block">
        <div class="block-head">
          <h2>个人资料</h2>
        </div>
        <div class="avatar-row">
          <img v-if="profile.avatar_url" class="avatar" :src="profile.avatar_url" alt="当前头像" />
          <span v-else class="avatar avatar-empty" aria-hidden="true">{{ profile.nickname.slice(0, 1) }}</span>
          <div class="avatar-actions">
            <button type="button" class="btn btn-sm" :disabled="avatarBusy" @click="pickAvatar">
              {{ avatarBusy ? '上传中…' : '更换头像' }}
            </button>
            <span class="muted">JPG 或 PNG，不超过 2MB</span>
            <input ref="fileInput" type="file" accept="image/png,image/jpeg" hidden @change="onAvatarChange" />
          </div>
        </div>
        <p v-if="avatarError" class="field-error" role="alert">{{ avatarError }}</p>

        <div class="field">
          <label class="field-label" for="nickname">昵称</label>
          <input id="nickname" v-model="nickname" class="input" maxlength="30" />
        </div>
        <p v-if="profileError" class="field-error" role="alert">{{ profileError }}</p>
        <div class="block-foot">
          <button type="button" class="btn btn-primary" :disabled="profileBusy" @click="saveProfile">
            {{ profileBusy ? '保存中…' : '保存资料' }}
          </button>
        </div>
      </section>

      <section class="card block">
        <div class="block-head">
          <h2>求职画像</h2>
          <span class="muted">均为选填</span>
        </div>
        <div class="field-grid">
          <div class="field">
            <label class="field-label" for="education">学历</label>
            <span class="select-wrap">
              <select id="education" v-model="education" class="select">
                <option value="">未填写</option>
                <option v-for="item in EDUCATION_OPTIONS" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </span>
          </div>
          <div class="field">
            <label class="field-label" for="work-years">工作年限</label>
            <span class="select-wrap">
              <select id="work-years" v-model="workYears" class="select">
                <option value="">未填写</option>
                <option v-for="item in WORK_YEARS_OPTIONS" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </span>
          </div>
          <div class="field field-wide">
            <label class="field-label" for="job-intent">求职意向</label>
            <span class="select-wrap">
              <select id="job-intent" v-model="jobRouteId" class="select">
                <option value="">未填写</option>
                <option
                  v-if="jobIntentOffShelf && profile.job_intent_learning_route_id"
                  :value="String(profile.job_intent_learning_route_id)"
                >
                  {{ profile.job_intent_name }}（已下架）
                </option>
                <option v-for="route in routes" :key="route.id" :value="String(route.id)">
                  {{ route.name }}
                </option>
              </select>
            </span>
            <span v-if="jobIntentOffShelf" class="field-hint">原求职意向已下架，可保留或重新选择</span>
          </div>
        </div>
        <p v-if="intentError" class="field-error" role="alert">{{ intentError }}</p>
        <div class="block-foot">
          <button type="button" class="btn btn-primary" :disabled="intentBusy" @click="saveIntent">
            {{ intentBusy ? '保存中…' : '保存画像' }}
          </button>
        </div>
      </section>

      <section class="card block">
        <div class="block-head">
          <h2>账号安全</h2>
        </div>
        <ul class="security">
          <li>
            <div>
              <b>手机号</b>
              <span class="muted">{{ maskedPhone }}</span>
            </div>
            <button type="button" class="btn btn-sm" :disabled="isCancelling" @click="openPhone">换绑手机号</button>
          </li>
          <li>
            <div>
              <b>登录密码</b>
              <span class="muted">{{ profile.has_password ? '已设置' : '未设置，可用验证码登录' }}</span>
            </div>
            <button type="button" class="btn btn-sm" :disabled="isCancelling" @click="openPassword">
              {{ profile.has_password ? '修改密码' : '设置密码' }}
            </button>
          </li>
          <li>
            <div>
              <b>注销账号</b>
              <span v-if="isCancelling" class="muted">将于 {{ cancellationDate }} 注销</span>
              <span v-else class="muted">申请后有 7 天冷静期，期间可撤销</span>
            </div>
            <button v-if="isCancelling" type="button" class="btn btn-sm btn-primary" @click="undoCancel">
              撤销注销
            </button>
            <button v-else type="button" class="btn btn-sm" @click="openCancel">申请注销</button>
          </li>
        </ul>
        <p v-if="cancelError" class="field-error" role="alert">{{ cancelError }}</p>
      </section>

      <section class="card block">
        <div class="block-head">
          <h2>协议</h2>
        </div>
        <div class="agreements">
          <button
            v-for="type in AGREEMENT_TYPES"
            :key="type"
            type="button"
            class="btn btn-ghost btn-sm"
            @click="openAgreement(type)"
          >
            {{ AGREEMENT_LABEL[type] }}
          </button>
        </div>
      </section>
    </template>

    <!-- 设置或修改密码 -->
    <div v-if="pwdOpen" class="mask" role="dialog" aria-modal="true" aria-label="设置密码" @click.self="pwdOpen = false">
      <div class="dialog card">
        <header class="dialog-head">
          <h2>{{ profile?.has_password ? '修改密码' : '设置密码' }}</h2>
          <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="pwdOpen = false">✕</button>
        </header>
        <div v-if="profile?.has_password" class="field">
          <label class="field-label" for="old-password">原密码</label>
          <input id="old-password" v-model="oldPassword" class="input" type="password" autocomplete="current-password" />
        </div>
        <div v-else class="field">
          <label class="field-label" for="pwd-sms">短信验证码</label>
          <div class="sms-row">
            <input id="pwd-sms" v-model="pwdSmsCode" class="input" maxlength="6" placeholder="6 位验证码" />
            <button
              type="button"
              class="btn btn-sm"
              :disabled="pwdSms.sending.value || pwdSms.countdown.value > 0"
              @click="pwdSms.send(profile?.phone ?? '')"
            >
              {{ pwdSms.countdown.value > 0 ? `${pwdSms.countdown.value}s` : '发送验证码' }}
            </button>
          </div>
          <div class="captcha-row">
            <input v-model="pwdSms.captchaCode.value" class="input" maxlength="8" placeholder="图形验证码" />
            <img
              v-if="pwdSms.captchaImage.value"
              class="captcha"
              :src="pwdSms.captchaImage.value"
              alt="图形验证码，点击刷新"
              @click="pwdSms.refreshCaptcha()"
            />
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="new-password">新密码</label>
          <input id="new-password" v-model="newPassword" class="input" type="password" autocomplete="new-password" />
          <span v-if="strength.text" class="strength" :class="strength.level">{{ strength.text }}</span>
        </div>
        <div class="field">
          <label class="field-label" for="confirm-password">确认新密码</label>
          <input id="confirm-password" v-model="confirmPassword" class="input" type="password" autocomplete="new-password" />
        </div>
        <p v-if="pwdError" class="field-error" role="alert">{{ pwdError }}</p>
        <footer class="dialog-foot">
          <button type="button" class="btn" @click="pwdOpen = false">取消</button>
          <button type="button" class="btn btn-primary" :disabled="pwdBusy" @click="submitPassword">
            {{ pwdBusy ? '提交中…' : '确定' }}
          </button>
        </footer>
      </div>
    </div>

    <!-- 换绑手机号 -->
    <div
      v-if="phoneOpen"
      class="mask"
      role="dialog"
      aria-modal="true"
      aria-label="换绑手机号"
      @click.self="phoneOpen = false"
    >
      <div class="dialog card">
        <header class="dialog-head">
          <h2>换绑手机号</h2>
          <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="phoneOpen = false">✕</button>
        </header>
        <p class="muted tip">当前手机号 {{ maskedPhone }}。原手机号收不到验证码时，可用登录密码验证。</p>
        <div class="field">
          <label class="field-label" for="old-verify">原手机号验证</label>
          <div class="tabs">
            <button
              type="button"
              class="tab"
              :class="{ active: phoneStep === 'code' }"
              @click="phoneStep = 'code'"
            >
              短信验证码
            </button>
            <button
              type="button"
              class="tab"
              :class="{ active: phoneStep === 'password' }"
              @click="phoneStep = 'password'"
            >
              登录密码
            </button>
          </div>
          <div v-if="phoneStep === 'code'" class="sms-row">
            <input v-model="oldSmsCode" class="input" maxlength="6" placeholder="原手机号验证码" />
            <button
              type="button"
              class="btn btn-sm"
              :disabled="oldPhoneSms.sending.value || oldPhoneSms.countdown.value > 0"
              @click="oldPhoneSms.send(profile?.phone ?? '')"
            >
              {{ oldPhoneSms.countdown.value > 0 ? `${oldPhoneSms.countdown.value}s` : '发送验证码' }}
            </button>
          </div>
          <input
            v-else
            v-model="oldPasswordForPhone"
            class="input"
            type="password"
            autocomplete="current-password"
            placeholder="登录密码"
          />
          <div v-if="phoneStep === 'code'" class="captcha-row">
            <input v-model="oldPhoneSms.captchaCode.value" class="input" maxlength="8" placeholder="图形验证码" />
            <img
              v-if="oldPhoneSms.captchaImage.value"
              class="captcha"
              :src="oldPhoneSms.captchaImage.value"
              alt="图形验证码，点击刷新"
              @click="oldPhoneSms.refreshCaptcha()"
            />
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="new-phone">新手机号</label>
          <input id="new-phone" v-model="newPhone" class="input" maxlength="11" placeholder="11 位手机号" />
        </div>
        <div class="field">
          <label class="field-label" for="new-sms">新手机号验证码</label>
          <div class="sms-row">
            <input id="new-sms" v-model="newSmsCode" class="input" maxlength="6" />
            <button
              type="button"
              class="btn btn-sm"
              :disabled="newPhoneSms.sending.value || newPhoneSms.countdown.value > 0 || !/^1\d{10}$/.test(newPhone)"
              @click="newPhoneSms.send(newPhone)"
            >
              {{ newPhoneSms.countdown.value > 0 ? `${newPhoneSms.countdown.value}s` : '发送验证码' }}
            </button>
          </div>
          <div class="captcha-row">
            <input v-model="newPhoneSms.captchaCode.value" class="input" maxlength="8" placeholder="图形验证码" />
            <img
              v-if="newPhoneSms.captchaImage.value"
              class="captcha"
              :src="newPhoneSms.captchaImage.value"
              alt="图形验证码，点击刷新"
              @click="newPhoneSms.refreshCaptcha()"
            />
          </div>
        </div>
        <p v-if="phoneError" class="field-error" role="alert">{{ phoneError }}</p>
        <footer class="dialog-foot">
          <button type="button" class="btn" @click="phoneOpen = false">取消</button>
          <button type="button" class="btn btn-primary" :disabled="phoneBusy" @click="submitPhone">
            {{ phoneBusy ? '提交中…' : '确认换绑' }}
          </button>
        </footer>
      </div>
    </div>

    <!-- 申请注销 -->
    <div
      v-if="cancelOpen"
      class="mask"
      role="dialog"
      aria-modal="true"
      aria-label="申请注销账号"
      @click.self="cancelOpen = false"
    >
      <div class="dialog card">
        <header class="dialog-head">
          <h2>申请注销账号</h2>
          <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="cancelOpen = false">✕</button>
        </header>
        <p class="warn">注销生效后，学习、刷题、面试记录将全部删除且不可恢复。申请后有 7 天冷静期，期间登录可撤销。</p>
        <div class="field">
          <label class="field-label" for="cancel-sms">短信验证码</label>
          <div class="sms-row">
            <input id="cancel-sms" v-model="cancelSmsCode" class="input" maxlength="6" />
            <button
              type="button"
              class="btn btn-sm"
              :disabled="cancelSms.sending.value || cancelSms.countdown.value > 0"
              @click="cancelSms.send(profile?.phone ?? '')"
            >
              {{ cancelSms.countdown.value > 0 ? `${cancelSms.countdown.value}s` : '发送验证码' }}
            </button>
          </div>
          <div class="captcha-row">
            <input v-model="cancelSms.captchaCode.value" class="input" maxlength="8" placeholder="图形验证码" />
            <img
              v-if="cancelSms.captchaImage.value"
              class="captcha"
              :src="cancelSms.captchaImage.value"
              alt="图形验证码，点击刷新"
              @click="cancelSms.refreshCaptcha()"
            />
          </div>
        </div>
        <label class="check">
          <input v-model="cancelConfirmed" type="checkbox" />
          <span>我已阅读并确认，知悉数据不可恢复</span>
        </label>
        <p v-if="cancelError" class="field-error" role="alert">{{ cancelError }}</p>
        <footer class="dialog-foot">
          <button type="button" class="btn" @click="cancelOpen = false">取消</button>
          <button type="button" class="btn btn-primary" :disabled="cancelBusy" @click="submitCancel">
            {{ cancelBusy ? '提交中…' : '确认注销' }}
          </button>
        </footer>
      </div>
    </div>

    <AgreementDialog
      v-if="agreementOpen"
      :open="true"
      :type="agreementOpen"
      :state="agreements.states.value[agreementOpen]"
      @close="agreementOpen = null"
    />
  </div>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.notice {
  padding: var(--space-sm) var(--space-md);
  border: var(--rule-hair) solid var(--color-accent-2);
  border-radius: var(--radius-card);
  background: var(--color-paper-2);
  color: var(--color-ink);
  font-size: var(--text-sm);
}

.block {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.block-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-sm);
}

.block-head h2 {
  font-size: var(--text-lg);
  font-weight: 600;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  border: var(--rule-hair) solid var(--color-rule);
}

.avatar-empty {
  display: grid;
  place-items: center;
  background: var(--color-paper-2);
  color: var(--color-accent-2);
  font-size: var(--text-xl);
  font-weight: 600;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2xs);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-sm);
}

.field-wide {
  grid-column: 1 / -1;
}

.field-label {
  color: var(--color-ink-2);
  font-size: var(--text-sm);
}

.field-hint {
  color: var(--color-muted);
  font-size: var(--text-xs);
}

.field-error {
  color: var(--color-error);
  font-size: var(--text-sm);
}

.block-foot {
  display: flex;
  justify-content: flex-start;
}

.security {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.security li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
  border-top: var(--rule-hair) solid var(--color-rule);
}

.security li:first-child {
  border-top: 0;
}

.security li div {
  display: flex;
  flex-direction: column;
  gap: var(--space-3xs);
}

.security b {
  font-size: var(--text-sm);
  font-weight: 600;
}

.agreements {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}
.tip {
  font-size: var(--text-sm);
}

.warn {
  padding: var(--space-sm);
  border-radius: var(--radius-input);
  background: var(--color-error-wash);
  color: var(--color-ink);
  font-size: var(--text-sm);
  line-height: 1.6;
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

.tabs {
  display: flex;
  gap: var(--space-2xs);
  margin-bottom: var(--space-2xs);
}

.tab {
  padding: var(--space-3xs) var(--space-sm);
  border: var(--rule-hair) solid var(--color-rule-2);
  border-radius: var(--radius-pill);
  background: var(--color-cotton);
  color: var(--color-ink-2);
  font: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
}

.tab.active {
  border-color: var(--color-accent-2);
  background: var(--color-paper-2);
  color: var(--color-accent-2);
  font-weight: 600;
}

.strength {
  font-size: var(--text-xs);
}

.strength.low {
  color: var(--color-error);
}

.strength.medium {
  color: var(--color-ink-2);
}

.strength.high {
  color: var(--color-accent-2);
}

.check {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2xs);
  font-size: var(--text-sm);
  line-height: 1.5;
}

.check input {
  margin-top: 3px;
}

</style>
