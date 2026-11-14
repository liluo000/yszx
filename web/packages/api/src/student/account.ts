import type { HttpClient } from '../http'

/** 协议版本引用（AgreementRef）：注册与验证码登录时必须提交两类协议的最新版本 */
export interface AgreementRef {
  agreement_type: StudentAgreementType
  version_no: number
}

export type StudentAgreementType = 'user_agreement' | 'privacy_policy'

/** 图形验证（S-ACC-01）：image_base64 为 PNG 内容的 base64，不含 data URL 前缀 */
export interface Captcha {
  captcha_id: string
  image_base64: string
  expires_in: number
}

/** 短信验证码（S-ACC-02） */
export interface SmsCodeResult {
  expires_in: number
  resend_after: number
}

export interface SendSmsCodeRequest {
  phone: string
  /** register、login、reset_password、change_phone_old、change_phone_new、cancel_account */
  scene: string
  captcha_id: string
  captcha_code: string
}

/** 学员本人信息（StudentProfile） */
export interface StudentProfile {
  id: number
  phone: string
  nickname: string
  avatar_url: string | null
  education: string | null
  job_intent_learning_route_id: number | null
  job_intent_name: string | null
  is_job_intent_off_shelf: boolean
  work_years: string | null
  /** normal、cancelling */
  status: string
  has_password: boolean
  cancellation_effective_at: string | null
  pending_agreements: AgreementRef[]
}

/** 登录与注册结果（S-ACC-03、04） */
export interface LoginResult {
  token: string
  /** 本次是否自动注册（验证码登录未注册时），为 true 时引导填写求职画像 */
  is_new_student: boolean
  profile: StudentProfile
}

export interface RegisterRequest {
  phone: string
  sms_code: string
  agreements: AgreementRef[]
}

export interface LoginRequest {
  login_type: 'sms' | 'password'
  phone: string
  sms_code?: string
  password?: string
  /** 验证码登录必填：登录页勾选的协议 */
  agreements?: AgreementRef[]
}

/** 协议最新版本（S-HLP-01） */
export interface AgreementDetail {
  agreement_type: StudentAgreementType
  version_no: number
  content: string
  published_at: string
}

export function createStudentAccountApi(http: HttpClient) {
  return {
    /** S-ACC-01 获取图形验证 */
    captcha: () => http.post<Captcha>('/student/captchas'),
    /** S-ACC-02 发送短信验证码 */
    sendSmsCode: (body: SendSmsCodeRequest) => http.post<SmsCodeResult>('/student/sms-codes', body),
    /** S-ACC-03 手机号注册，成功后自动登录 */
    register: (body: RegisterRequest) => http.post<LoginResult>('/student/registrations', body),
    /** S-ACC-04 登录；验证码登录时手机号未注册会自动注册 */
    login: (body: LoginRequest) => http.post<LoginResult>('/student/sessions', body),
    /** S-ACC-07 本人信息 */
    me: () => http.get<StudentProfile>('/student/me'),
    /** S-ACC-05 退出当前设备 */
    logout: () => http.delete<void>('/student/sessions/current'),
    /** S-HLP-01 协议最新版本 */
    agreement: (type: StudentAgreementType) => http.get<AgreementDetail>(`/student/agreements/${type}`),
  }
}
