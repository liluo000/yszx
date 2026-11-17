import type { HttpClient } from '../http'

/** S-IVW-01 发起面试选项 */
export interface InterviewStageOption {
  route_stage_id: number
  stage_name: string
  is_interview_available: boolean
}
export interface InterviewOptions {
  interview_remaining: number
  main_question_count: number
  duration_minutes: number
  stages: InterviewStageOption[]
}

/** S-IVW-02 发起 */
export interface StartInterviewRequest {
  learning_route_id: number
  scope_type: 'whole_route' | 'selected_stages'
  route_stage_ids?: number[]
}

/** InterviewMessageView */
export interface InterviewMessage {
  seq_no: number
  speaker: 'ai' | 'student'
  message_type: 'opening' | 'main_question' | 'followup' | 'answer' | 'transition' | 'closing' | string
  content: string
  created_at: string
}

/** InterviewView */
export interface InterviewView {
  id: number
  route_name: string
  interview_mode: 'text' | 'voice'
  status: 'in_progress' | 'ended'
  end_reason: 'completed' | 'student_ended' | 'timeout' | 'ai_failure' | null
  main_question_count: number
  current_seq_no: number
  started_at: string
  deadline_at: string
  server_time: string
  messages: InterviewMessage[]
}

/** S-IVW-08 WebSocket 票据 */
export interface WsTicket {
  ticket: string
  expires_in: number
}

/** S-RPT-01 面试记录 */
export interface InterviewListItem {
  id: number
  started_at: string
  route_name: string
  stage_names: string[]
  interview_mode: 'text' | 'voice'
  total_score: number | null
  grade: 'excellent' | 'good' | 'needs_improvement' | null
  record_status: 'in_progress' | 'ended' | 'report_generating' | 'report_failed' | 'system_failed'
  can_resume: boolean
  is_route_off_shelf: boolean
}

/** S-RPT-02 报告 */
export interface ReportStage {
  route_stage_id: number
  stage_name: string
  score: number | null
}
export interface ReportQuestion {
  seq_no: number
  stem: string
  answer_status: 'not_asked' | 'answered' | 'unanswered'
  score: number | null
  ai_comment: string | null
  missing_points: string | null
  reference_answer: string | null
  messages: InterviewMessage[]
}
export interface LearningAdvice {
  route_stage_id: number
  advice: string
  courses: { id: number; title: string }[]
}
export interface ReportView {
  interview_session_id: number
  status: 'generating' | 'generated' | 'failed'
  total_score: number | null
  grade: 'excellent' | 'good' | 'needs_improvement' | null
  is_ended_by_system_failure: boolean
  stages: ReportStage[]
  questions: ReportQuestion[]
  learning_advice: LearningAdvice[]
  generated_at: string | null
}

export function createStudentInterviewApi(http: HttpClient) {
  return {
    /** S-IVW-01 获取发起面试的选项 */
    options: (learningRouteId: number) =>
      http.get<InterviewOptions>('/student/interviews/options', { learning_route_id: learningRouteId }),
    /** S-IVW-02 发起文字面试 */
    start: (body: StartInterviewRequest) => http.post<InterviewView>('/student/interviews', body),
    /** S-IVW-03 查询进行中的面试 */
    inProgress: () => http.get<{ interview: InterviewView | null }>('/student/interviews/in-progress'),
    /** S-IVW-04 面试进度（流式中断后补全） */
    view: (id: number) => http.get<InterviewView>(`/student/interviews/${id}`),
    /** S-IVW-05 设备接管 */
    claimDevice: (id: number) =>
      http.post<{ device_claim: string }>(`/student/interviews/${id}/device-claims`),
    /** S-IVW-07 主动结束 */
    terminate: (id: number, claim: string) =>
      http.post<{ status: string; end_reason: string }>(
        `/student/interviews/${id}/termination`, undefined, { 'X-Device-Claim': claim },
      ),
    /** S-IVW-08 WebSocket 一次性票据 */
    wsTicket: (purpose: 'voice_interview' | 'voice_input') =>
      http.post<WsTicket>('/student/ws-tickets', { purpose }),
    /** S-RPT-01 面试记录列表 */
    records: (query: { page?: number; page_size?: number } = {}) =>
      http.get<{ items: InterviewListItem[]; total: number; page: number; page_size: number }>(
        '/student/interviews', query as Record<string, unknown>,
      ),
    /** S-RPT-02 查看报告 */
    report: (id: number) => http.get<ReportView>(`/student/interviews/${id}/report`),
    /** S-RPT-03 重新生成报告 */
    regenerate: (id: number) => http.post<ReportView>(`/student/interviews/${id}/report/regeneration`),
  }
}
