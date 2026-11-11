import { ApiError } from '@yunshu/api'

const dateTimeFormat = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

/** 接口返回 UTC 时间，页面统一按北京时间显示 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return dateTimeFormat.format(new Date(value)).replace(/\//g, '-')
}

/** 列表中隐藏手机号中间四位 */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '—'
  return phone.length === 11 ? `${phone.slice(0, 3)}****${phone.slice(7)}` : phone
}

/** 把接口错误转成可展示的提示 */
export function errorText(error: unknown, fallback = '操作失败，请稍后重试'): string {
  return error instanceof ApiError ? error.message : fallback
}

/** 字段级错误（VALIDATION_FAILED 的 errors），用于显示在对应输入框下方 */
export function fieldErrors(error: unknown): Record<string, string> {
  const result: Record<string, string> = {}
  if (error instanceof ApiError) {
    for (const item of error.errors) {
      const violation = item as { field?: string; reason?: string }
      if (violation.field && violation.reason) result[violation.field] = violation.reason
    }
  }
  return result
}

/** 浏览器保存下载的文件 */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
