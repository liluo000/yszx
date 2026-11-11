import type { AdminRole } from '@yunshu/api'

export const ROLE_LABEL: Record<AdminRole, string> = {
  super_admin: '超级管理员',
  content_operator: '内容运营',
  student_operator: '学员运营',
}
