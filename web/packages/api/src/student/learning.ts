import type { HttpClient } from '../http'

/** S-LRN-01 打开课程并返回视频链接 */
export interface OpenCourseResult {
  video_url: string
  platform_name: string | null
}

export interface OpenCourseRequest {
  learning_route_id?: number
  route_stage_id?: number
}

/** S-LRN-02 标记已学 */
export interface LearnedResult {
  is_learned: boolean
  learned_at: string
}

/** S-FDB-01 提交反馈 */
export interface SubmitFeedbackRequest {
  feedback_type: 'question_correction' | 'video_invalid'
  practice_question_id?: number
  error_type?: string
  description?: string
  course_id?: number
}

/** S-LRN-04 学习轨迹（打开 / 已学 / 已掌握） */
export interface LearningTrack {
  record_type: 'opened' | 'learned' | 'mastered' | string
  course_id: number
  course_title: string
  occurred_at: string
}

export interface LearningTracksQuery {
  page?: number
  page_size?: number
  record_type?: string
  learning_route_id?: number
  start_date?: string
  end_date?: string
}

/** S-LRN-05 路线进度 */
export interface RouteProgressView {
  learned_course_count: number
  total_course_count: number
  mastered_course_count: number
  configured_course_count: number
}

export interface RouteProgressItem {
  id: number
  name: string
  intro: string | null
  stages: { id: number; name: string; course_count: number }[]
  progress: RouteProgressView
}

export function createStudentLearningApi(http: HttpClient) {
  return {
    /** 打开课程并记录；返回的链接由前端按平台规则跳转 */
    open: (courseId: number, body: OpenCourseRequest = {}) =>
      http.post<OpenCourseResult>(`/student/courses/${courseId}/opens`, body),
    /** 标记课程已学 */
    markLearned: (courseId: number) =>
      http.put<LearnedResult>(`/student/courses/${courseId}/learned`),
    /** 取消已学标记（已掌握时不可取消） */
    cancelLearned: (courseId: number) =>
      http.delete<void>(`/student/courses/${courseId}/learned`),
    /** 提交题目纠错或视频失效反馈 */
    submitFeedback: (body: SubmitFeedbackRequest) =>
      http.post<{ id: number }>('/student/feedbacks', body),
    /** S-LRN-04 学习轨迹 */
    tracks: (query: LearningTracksQuery = {}) =>
      http.get<{ items: LearningTrack[]; total: number; page: number; page_size: number }>(
        '/student/learning-records/tracks', query as Record<string, unknown>,
      ),
    /** S-LRN-05 各路线进度 */
    routeProgress: (learningRouteId?: number) =>
      http.get<{ items: RouteProgressItem[] }>(
        '/student/learning-records/route-progress',
        learningRouteId ? { learning_route_id: learningRouteId } : {},
      ),
  }
}
