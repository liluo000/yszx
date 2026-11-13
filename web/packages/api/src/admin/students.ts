import type { HttpClient } from '../http'
import type { PageResult } from './system'

export type StudentStatus = 'normal' | 'banned' | 'cancelling'
export type Education = 'high_school_or_below' | 'junior_college' | 'bachelor' | 'master_or_above'
export type WorkYears = 'student_or_graduate' | 'under_1_year' | '1_to_3_years' | '3_to_5_years' | 'over_5_years'

/** 后台学员列表项（公共对象 StudentAdminItem） */
export interface StudentAdminItem {
  id: number
  nickname: string
  phone_masked: string
  created_at: string
  last_active_at: string | null
  education: Education | null
  job_intent_name: string | null
  work_years: WorkYears | null
  learned_route_count: number
  interview_count: number
  status: StudentStatus
  ban_reason: string | null
}

export interface StudentQuery {
  registered_start_date?: string
  registered_end_date?: string
  active_start_date?: string
  active_end_date?: string
  job_intent_learning_route_id?: number
  education?: Education
  work_years?: WorkYears
  learned_route_id?: number
  has_interview?: boolean
  status?: StudentStatus
  keyword?: string
}

export function createStudentApi(http: HttpClient) {
  return {
    list: (query: StudentQuery & { page?: number; page_size?: number }) =>
      http.get<PageResult<StudentAdminItem>>('/admin/students', { ...query }),
    get: (id: number) => http.get<StudentAdminItem>(`/admin/students/${id}`),
    revealPhone: (id: number) => http.post<{ phone: string }>(`/admin/students/${id}/phone-reveals`),
    ban: (id: number, banReason: string) =>
      http.put<{ status: 'banned' }>(`/admin/students/${id}/ban`, { ban_reason: banReason }),
    unban: (id: number) => http.delete<{ status: 'normal' }>(`/admin/students/${id}/ban`),
    /** 按筛选条件导出，单次最多 5000 条，注销中的账号不导出 */
    export: (query: StudentQuery) => http.download('/admin/student-exports', query),
  }
}

/* ---------------- 数据看板（A-DSH-01、02） ---------------- */

export type Granularity = 'day' | 'week' | 'month'

export interface DashboardView {
  acquisition: { stat_date: string; new_student_count: number; cumulative_student_count: number }[]
  activity: {
    stat_date: string
    daily_active_student_count: number
    monthly_active_student_count: number
    next_day_retention_rate: number | null
    seven_day_retention_rate: number | null
  }[]
  routes: {
    learning_route_id: number
    route_name: string
    learner_count: number
    average_learned_progress: number
    average_mastered_progress: number
  }[]
  practice: { answer_count: number; correct_rate: number | null }
  top_wrong_questions: {
    practice_question_id: number
    stem_excerpt: string
    answer_count: number
    wrong_count: number
    wrong_rate: number
  }[]
  interview: { interview_count: number; average_score: number | null }
  stage_scores: { route_stage_id: number; stage_name: string; average_score: number }[]
  ai_usage: { stat_date: string; ai_interview_call_count: number; ai_scoring_call_count: number }[]
  refreshed_at: string | null
}

export interface DashboardQuery {
  start_date: string
  end_date: string
  granularity?: Granularity
}

export function createDashboardApi(http: HttpClient) {
  return {
    load: (query: DashboardQuery) => http.get<DashboardView>('/admin/dashboard', { ...query }),
    export: (query: DashboardQuery) => http.download('/admin/dashboard-exports', query),
  }
}
