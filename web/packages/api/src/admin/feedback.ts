import type { HttpClient } from '../http'
import type { PageResult } from './system'

export type FeedbackType = 'question_correction' | 'video_invalid'
export type FeedbackStatus = 'pending' | 'corrected' | 'rejected'
export type FeedbackErrorType = 'stem_error' | 'answer_error' | 'analysis_error' | 'other'

/** 后台反馈（公共对象 FeedbackItem）：待处理的按题目或课程合并 */
export interface FeedbackItem {
  feedback_ids: number[]
  feedback_type: FeedbackType
  course_id: number
  course_title: string | null
  practice_question_id: number | null
  stem: string | null
  error_type: FeedbackErrorType | null
  description: string | null
  video_url: string | null
  student_id: number
  /** 已注销时为空，显示"已注销用户" */
  student_nickname: string | null
  submit_count: number
  status: FeedbackStatus
  handling_note: string | null
  handled_at: string | null
  created_at: string
}

export interface FeedbackQuery {
  page?: number
  page_size?: number
  feedback_type?: FeedbackType
  status?: FeedbackStatus
  course_id?: number
  error_type?: FeedbackErrorType
  start_date?: string
  end_date?: string
}

export function createFeedbackApi(http: HttpClient) {
  return {
    list: (query: FeedbackQuery) => http.get<PageResult<FeedbackItem>>('/admin/feedbacks', { ...query }),
    handle: (feedbackIds: number[], status: 'corrected' | 'rejected', handlingNote: string) =>
      http.post<{ updated_count: number }>('/admin/feedback-handlings', {
        feedback_ids: feedbackIds,
        status,
        handling_note: handlingNote,
      }),
  }
}
