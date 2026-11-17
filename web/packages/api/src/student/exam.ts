import type { HttpClient } from '../http'

/** 考试题目（来自开考时的快照） */
export interface ExamQuestion {
  id: number
  course_id: number
  question_type: 'true_false' | 'single_choice' | 'multiple_choice' | 'short_answer' | string
  stem: string
  options: { key: string; text: string }[] | null
}

/** S-EXM-01/03/06/08 共享的考试视图 */
export interface ExamQuestionView {
  seq_no: number
  question: ExamQuestion
  question_score: number
  student_answer: unknown
  judge_status?: 'pending' | 'judged' | 'failed'
  is_correct?: boolean
  earned_score?: number
  ai_score?: number | null
  ai_comment?: string | null
  correct_answer?: unknown
  reference_answer?: string | null
  analysis?: string | null
}

export interface ExamTypeScore {
  question_type: string
  earned_score: number
  question_score: number
}

export interface ExamView {
  id: number
  route_name: string
  stage_name: string
  status: 'in_progress' | 'submitted' | 'scoring_pending' | 'graded' | 'abandoned' | string
  duration_minutes: number
  started_at: string
  deadline_at: string
  server_time: string
  has_short_answer: boolean
  score: number | null
  type_scores: ExamTypeScore[] | null
  questions: ExamQuestionView[]
}

export interface StartExamRequest {
  learning_route_id: number
  route_stage_id: number
}

export function createStudentExamApi(http: HttpClient) {
  return {
    /** S-EXM-01 开考 */
    start: (body: StartExamRequest) => http.post<ExamView>('/student/exams', body),
    /** S-EXM-02 查询作答中的考试 */
    inProgress: () => http.get<{ exam: ExamView | null }>('/student/exams/in-progress'),
    /** S-EXM-03 考试详情或成绩 */
    view: (id: number) => http.get<ExamView>(`/student/exams/${id}`),
    /** S-EXM-04 在当前设备接管考试 */
    claimDevice: (id: number) =>
      http.post<{ device_claim: string }>(`/student/exams/${id}/device-claims`),
    /** S-EXM-05 保存一题答案；studentAnswer 传 null 可清空 */
    saveAnswer: (id: number, seqNo: number, studentAnswer: unknown, claim: string) =>
      http.put<{ answered_at: string }>(
        `/student/exams/${id}/answers/${seqNo}`,
        { student_answer: studentAnswer },
        { 'X-Device-Claim': claim },
      ),
    /** S-EXM-06 交卷（到时后也可调用，视为自动交卷） */
    submit: (id: number, claim: string) =>
      http.post<ExamView>(`/student/exams/${id}/submission`, undefined, { 'X-Device-Claim': claim }),
    /** S-EXM-07 放弃考试 */
    abandon: (id: number, claim: string) =>
      http.post<void>(`/student/exams/${id}/abandonment`, undefined, { 'X-Device-Claim': claim }),
    /** S-EXM-08 重新评分简答题 */
    rescore: (id: number) => http.post<ExamView>(`/student/exams/${id}/rescoring`),
  }
}
