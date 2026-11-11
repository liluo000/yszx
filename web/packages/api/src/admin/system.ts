import type { HttpClient } from '../http'
import type { AdminRole, SysUserView } from './auth'

/** 分页结构（04.API接口设计.md 2.4） */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  page_size: number
}

export interface PageParams {
  page?: number
  page_size?: number
}

/** 批量导入结果（公共对象 ImportResult） */
export interface ImportResult {
  success_count: number
  failure_count: number
  failures: { row_no: number; reason: string }[]
}

export type EnableStatus = 'enabled' | 'disabled'

/* ---------------- 系统用户、角色（A-IAM-05~10） ---------------- */

export interface SysUserQuery extends PageParams {
  role?: AdminRole
  status?: EnableStatus
}

export interface SysUserForm {
  name: string
  phone: string
  role: AdminRole
}

export interface RoleView {
  role: AdminRole
  description: string
  permissions: string[]
}

export function createSysUserApi(http: HttpClient) {
  return {
    list: (query: SysUserQuery) => http.get<PageResult<SysUserView>>('/admin/sys-users', { ...query }),
    create: (body: SysUserForm & { account: string }) =>
      http.post<{ user: SysUserView; temporary_password: string }>('/admin/sys-users', body),
    update: (id: number, body: Partial<SysUserForm>) => http.patch<SysUserView>(`/admin/sys-users/${id}`, body),
    changeStatus: (id: number, status: EnableStatus) =>
      http.put<SysUserView>(`/admin/sys-users/${id}/status`, { status }),
    resetPassword: (id: number) => http.post<{ temporary_password: string }>(`/admin/sys-users/${id}/password-resets`),
    roles: () => http.get<{ items: RoleView[] }>('/admin/roles'),
  }
}

/* ---------------- 操作日志（A-AUD-01） ---------------- */

export interface OperationLogView {
  id: number
  sys_user_id: number
  sys_user_name: string
  role: AdminRole
  module: string
  action: string
  target_type: string | null
  target_id: number | null
  target_label: string | null
  ip_address: string | null
  created_at: string
}

export interface OperationLogQuery extends PageParams {
  sys_user_id?: number
  module?: string
  /** 北京时间日期 YYYY-MM-DD */
  start_date?: string
  end_date?: string
}

export function createAuditApi(http: HttpClient) {
  return {
    list: (query: OperationLogQuery) => http.get<PageResult<OperationLogView>>('/admin/operation-logs', { ...query }),
    /** 按筛选条件导出 Excel（A-AUD-02），单次最多 1 万条 */
    export: (filter: Omit<OperationLogQuery, 'page' | 'page_size'>) =>
      http.download('/admin/operation-log-exports', filter),
  }
}

/* ---------------- 系统配置（A-CFG-01~18） ---------------- */

export type QuestionType = 'true_false' | 'single_choice' | 'multiple_choice' | 'short_answer'

export type PaperRule = Record<QuestionType, { count: number; score: number }>

export interface GradeThresholds {
  excellent: number
  good: number
}

export interface HelpContact {
  wechat_qrcode_url: string | null
  email: string | null
  phone: string | null
}

export interface ConfigView {
  config_key: string
  config_value: unknown
  description: string
  updated_at: string
}

export type AgreementType = 'user_agreement' | 'privacy_policy'

export interface AgreementView {
  agreement_type: AgreementType
  version_no: number | null
  content: string | null
  published_at: string | null
}

export interface SensitiveWordView {
  id: number
  word: string
  created_at: string
}

export interface VideoPlatformView {
  id: number
  name: string
  status: EnableStatus
  domains: string[]
}

export interface FaqView {
  id: number
  question: string
  answer: string
  sort_no: number
}

export function createConfigApi(http: HttpClient) {
  return {
    configs: () => http.get<{ items: ConfigView[] }>('/admin/configs'),
    updateConfig: (key: string, value: unknown) => http.put<null>(`/admin/configs/${key}`, { config_value: value }),

    agreements: () => http.get<{ items: AgreementView[] }>('/admin/agreements'),
    publishAgreement: (type: AgreementType, content: string) =>
      http.post<{ version_no: number }>(`/admin/agreements/${type}/versions`, { content }),

    sensitiveWords: (query: PageParams & { keyword?: string }) =>
      http.get<PageResult<SensitiveWordView>>('/admin/sensitive-words', { ...query }),
    addSensitiveWord: (word: string) => http.post<{ id: number }>('/admin/sensitive-words', { word }),
    deleteSensitiveWord: (id: number) => http.delete<null>(`/admin/sensitive-words/${id}`),
    importSensitiveWords: (file: File) => http.upload<ImportResult>('/admin/sensitive-words/imports', file),
    downloadTemplate: (type: 'sensitive_word') => http.download(`/admin/import-templates/${type}`),

    videoPlatforms: () => http.get<{ items: VideoPlatformView[] }>('/admin/video-platforms'),
    createVideoPlatform: (body: { name: string; domains: string[] }) =>
      http.post<{ id: number }>('/admin/video-platforms', body),
    updateVideoPlatform: (id: number, body: { name?: string; domains?: string[] }) =>
      http.patch<null>(`/admin/video-platforms/${id}`, body),
    changeVideoPlatformStatus: (id: number, status: EnableStatus) =>
      http.put<null>(`/admin/video-platforms/${id}/status`, { status }),

    faqs: () => http.get<{ items: FaqView[] }>('/admin/faqs'),
    createFaq: (body: Omit<FaqView, 'id'>) => http.post<{ id: number }>('/admin/faqs', body),
    updateFaq: (id: number, body: Partial<Omit<FaqView, 'id'>>) => http.patch<null>(`/admin/faqs/${id}`, body),
    deleteFaq: (id: number) => http.delete<null>(`/admin/faqs/${id}`),
    sortFaqs: (items: { id: number; sort_no: number }[]) => http.put<null>('/admin/faqs/sort-order', { items }),

    uploadImage: (file: File) => http.upload<{ url: string }>('/admin/uploads', file),
  }
}
