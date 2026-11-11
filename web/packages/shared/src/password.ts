/** 密码规则：长度 6~20 位，不限字符类型（需求 5.1.3、6.9） */
export const PASSWORD_MIN_LENGTH = 6
export const PASSWORD_MAX_LENGTH = 20

export type PasswordStrength = 'low' | 'medium' | 'high'

export const PASSWORD_STRENGTH_LABEL: Record<PasswordStrength, string> = {
  low: '低',
  medium: '中',
  high: '高',
}

/**
 * 密码强度只作提示，强度为"低"也允许保存（需求 5.1.3）。
 * 按小写字母、大写字母、数字、特殊符号四类计算：1 类为低，2 类为中，3 类及以上为高。
 */
export function passwordStrength(password: string): PasswordStrength | null {
  if (!password) return null
  const kinds = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^a-zA-Z0-9]/].filter((re) => re.test(password)).length
  if (kinds >= 3) return 'high'
  if (kinds === 2) return 'medium'
  return 'low'
}

export function isPasswordLengthValid(password: string): boolean {
  return password.length >= PASSWORD_MIN_LENGTH && password.length <= PASSWORD_MAX_LENGTH
}
