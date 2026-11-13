import type { Education, StudentStatus, WorkYears } from '@yunshu/api'

export const EDUCATION_LABEL: Record<Education, string> = {
  high_school_or_below: '高中及以下',
  junior_college: '大专',
  bachelor: '本科',
  master_or_above: '硕士及以上',
}

export const WORK_YEARS_LABEL: Record<WorkYears, string> = {
  student_or_graduate: '在校生 / 应届生',
  under_1_year: '1 年以内',
  '1_to_3_years': '1~3 年',
  '3_to_5_years': '3~5 年',
  over_5_years: '5 年以上',
}

export const STUDENT_STATUS_LABEL: Record<StudentStatus, string> = {
  normal: '正常',
  banned: '封禁',
  cancelling: '注销中',
}
