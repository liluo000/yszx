import type { HttpClient } from '../http'

/** 当日 AI 次数（S-QTA-01） */
export interface AiQuotaView {
  quota_date: string
  interview_limit: number
  interview_remaining: number
  scoring_limit: number
  scoring_remaining: number
}

/** 作答时的题目，不含答案 */
export interface QuestionForAnswer {
  id: number
  course_id: number
  question_type: 'true_false' | 'single_choice' | 'multiple_choice' | 'short_answer'
  stem: string
  options: { key: string; text: string }[] | null
}

/** 判分结果 */
export interface AnswerResult {
  is_correct: boolean
  judge_method: 'auto' | 'ai' | 'self'
  ai_score: number | null
  ai_comment: string | null
  correct_answer: unknown
  reference_answer: string | null
  analysis: string | null
}

/** 练习场次（S-PRC-01、02） */
export interface PracticeSessionView {
  id: number
  practice_mode: string
  status: 'in_progress' | 'completed' | 'unfinished'
  course_id: number | null
  route_stage_id: number | null
  question_count: number
  answered_count: number
  correct_count: number
  next_question: QuestionForAnswer | null
  remaining_scoring_quota: number
}

export interface StartPracticeRequest {
  practice_mode: 'sequential' | 'wrong_redo' | 'random'
  /** sequential 必填 */
  course_id?: number
  restart?: boolean
  /** wrong_redo 必填：错题记录 ID，1~200 项 */
  wrong_question_ids?: number[]
  /** random 必填 */
  learning_route_id?: number
  route_stage_id?: number
}

export interface SubmitAnswerRequest {
  practice_question_id: number
  student_answer: unknown
  /** 简答题自评：can 会、cannot 不会 */
  self_assessment?: 'can' | 'cannot'
}

/** S-PRC-03 提交结果；need_self_assessment 为 true 时本题尚未入库 */
export interface SubmitResult {
  need_self_assessment: boolean
  reference_answer: string | null
  result: AnswerResult | null
  next_question: QuestionForAnswer | null
  session_status: string
  remaining_scoring_quota: number
}

/** S-PRC-05 作答详情 */
export interface AnswerDetail {
  question_snapshot: Record<string, unknown> | null
  student_answer: unknown
  is_correct: boolean
  judge_method: string
  ai_score: number | null
  ai_comment: string | null
  answered_at: string
}

/** S-PRC-04 刷题记录 */
export interface PracticeRecord {
  id: number
  practice_mode: string
  course_id: number | null
  course_title: string | null
  route_name: string | null
  stage_name: string | null
  status: string
  question_count: number
  answered_count: number
  correct_count: number
  started_at: string
  finished_at: string | null
}

export interface PracticeRecordQuery {
  page?: number
  page_size?: number
  record_type?: string
  start_date?: string
  end_date?: string
}

export function createStudentPracticeApi(http: HttpClient) {
  return {
    /** S-PRC-01 开始或继续顺序练习 */
    start: (body: StartPracticeRequest) =>
      http.post<PracticeSessionView>('/student/practice-sessions', body),
    /** S-PRC-02 练习进度 */
    view: (id: number) => http.get<PracticeSessionView>(`/student/practice-sessions/${id}`),
    /** S-PRC-03 提交一题答案 */
    submit: (id: number, body: SubmitAnswerRequest) =>
      http.post<SubmitResult>(`/student/practice-sessions/${id}/answers`, body),
    /** S-PRC-05 作答详情 */
    answers: (id: number) =>
      http.get<{ items: AnswerDetail[] }>(`/student/practice-sessions/${id}/answers`),
    /** S-PRC-04 刷题记录 */
    records: (query: PracticeRecordQuery = {}) =>
      http.get<{ items: PracticeRecord[]; total: number; page: number; page_size: number }>(
        '/student/practice-records', { ...query }),
  }
}

export function createStudentQuotaApi(http: HttpClient) {
  return {
    /** S-QTA-01 当日 AI 剩余次数 */
    current: () => http.get<AiQuotaView>('/student/ai-quota'),
  }
}
