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

/** 学历取值（student.education） */
export type EducationCode = 'high_school_or_below' | 'junior_college' | 'bachelor' | 'master_or_above'

/** 工作年限取值（student.work_years） */
export type WorkYearsCode =
  | 'student_or_graduate'
  | 'under_1_year'
  | '1_to_3_years'
  | '3_to_5_years'
  | 'over_5_years'

/** 修改资料与求职画像（S-ACC-08）：字段缺省表示不修改，显式传 null 表示清空 */
export interface UpdateProfileRequest {
  nickname?: string
  education?: EducationCode | null
  job_intent_learning_route_id?: number | null
  work_years?: WorkYearsCode | null
}

/** 重置密码（S-ACC-06） */
export interface ResetPasswordRequest {
  phone: string
  sms_code: string
  new_password: string
}

/** 设置或修改密码（S-ACC-10）：首次设置传 sms_code，已设置过传 old_password */
export interface ChangePasswordRequest {
  old_password?: string
  sms_code?: string
  new_password: string
}

/** 换绑手机号（S-ACC-11）：旧手机验证码与登录密码二选一 */
export interface ChangePhoneRequest {
  new_phone: string
  new_sms_code: string
  old_sms_code?: string
  password?: string
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
    /** S-ACC-05 退出当前设备 */
    logout: () => http.delete<void>('/student/sessions/current'),
    /** S-ACC-06 通过短信验证码重置密码，成功后全设备退出 */
    resetPassword: (body: ResetPasswordRequest) => http.post<void>('/student/password-resets', body),
    /** S-ACC-07 本人信息 */
    me: () => http.get<StudentProfile>('/student/me'),
    /** S-ACC-08 修改资料与求职画像 */
    updateProfile: (body: UpdateProfileRequest) => http.patch<StudentProfile>('/student/me', body),
    /** S-ACC-09 上传头像 */
    uploadAvatar: (file: File) => http.upload<{ avatar_url: string }>('/student/me/avatar', file),
    /** S-ACC-10 设置或修改密码，成功后全设备退出 */
    changePassword: (body: ChangePasswordRequest) => http.put<void>('/student/me/password', body),
    /** S-ACC-11 换绑手机号，成功后全设备退出 */
    changePhone: (body: ChangePhoneRequest) => http.put<void>('/student/me/phone', body),
    /** S-ACC-12 申请注销，返回注销生效时间 */
    requestCancellation: (body: { sms_code: string; confirmed: boolean }) =>
      http.post<{ cancellation_effective_at: string }>('/student/me/cancellation', body),
    /** S-ACC-13 撤销注销 */
    cancelCancellation: () => http.delete<StudentProfile>('/student/me/cancellation'),
    /** S-HLP-01 协议最新版本 */
    agreement: (type: StudentAgreementType) => http.get<AgreementDetail>(`/student/agreements/${type}`),
    /** S-HLP-02 同意最新协议（协议内容更新后重新同意） */
    acceptAgreements: (agreements: AgreementRef[]) =>
      http.post<void>('/student/agreement-consents', { agreements }),
  }
}
