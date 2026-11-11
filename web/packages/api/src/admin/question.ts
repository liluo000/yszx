import type { HttpClient } from '../http'
import type { ShelfStatus } from './content'
import type { ImportResult, PageResult, QuestionType } from './system'

export interface QuestionOption {
  key: string
  text: string
}

/** 后台刷题题目（公共对象 PracticeQuestionView）；correct_answer 判断题为布尔，选择题为选项字母数组 */
export interface PracticeQuestionView {
  id: number
  course_id: number
  course_title: string
  question_type: QuestionType
  stem: string
  options: QuestionOption[] | null
  correct_answer: boolean | string[] | null
  reference_answer: string | null
  scoring_points: string | null
  analysis: string | null
  sort_no: number
  status: ShelfStatus
  is_answered: boolean
}

export interface PracticeQuestionForm {
  course_id: number
  question_type: QuestionType
  stem: string
  options?: QuestionOption[] | null
  correct_answer?: boolean | string[] | null
  reference_answer?: string
  /** 编辑时传空字符串表示清空 */
  scoring_points?: string
  analysis?: string
  sort_no: number
}

/** 后台面试题目（公共对象 InterviewQuestionView） */
export interface InterviewQuestionView {
  id: number
  course_id: number
  course_title: string
  stem: string
  reference_answer: string
  scoring_points: string | null
  followup_direction: string | null
  status: ShelfStatus
  is_drawn: boolean
}

export interface InterviewQuestionForm {
  course_id: number
  stem: string
  reference_answer: string
  scoring_points?: string
  followup_direction?: string
}

export interface QuestionQuery {
  page?: number
  page_size?: number
  subject_id?: number
  course_id?: number
  status?: ShelfStatus
}

export function createQuestionApi(http: HttpClient) {
  return {
    practice: {
      list: (query: QuestionQuery & { question_type?: QuestionType }) =>
        http.get<PageResult<PracticeQuestionView>>('/admin/practice-questions', { ...query }),
      get: (id: number) => http.get<PracticeQuestionView>(`/admin/practice-questions/${id}`),
      create: (body: PracticeQuestionForm) => http.post<PracticeQuestionView>('/admin/practice-questions', body),
      update: (id: number, body: Partial<PracticeQuestionForm>) =>
        http.patch<PracticeQuestionView>(`/admin/practice-questions/${id}`, body),
      changeStatus: (id: number, status: ShelfStatus) =>
        http.put<PracticeQuestionView>(`/admin/practice-questions/${id}/status`, { status }),
      remove: (id: number) => http.delete<null>(`/admin/practice-questions/${id}`),
      importQuestions: (file: File) => http.upload<ImportResult>('/admin/practice-questions/imports', file),
    },
    interview: {
      list: (query: QuestionQuery) =>
        http.get<PageResult<InterviewQuestionView>>('/admin/interview-questions', { ...query }),
      get: (id: number) => http.get<InterviewQuestionView>(`/admin/interview-questions/${id}`),
      create: (body: InterviewQuestionForm) => http.post<InterviewQuestionView>('/admin/interview-questions', body),
      update: (id: number, body: Partial<InterviewQuestionForm>) =>
        http.patch<InterviewQuestionView>(`/admin/interview-questions/${id}`, body),
      changeStatus: (id: number, status: ShelfStatus) =>
        http.put<InterviewQuestionView>(`/admin/interview-questions/${id}/status`, { status }),
      remove: (id: number) => http.delete<null>(`/admin/interview-questions/${id}`),
      importQuestions: (file: File) => http.upload<ImportResult>('/admin/interview-questions/imports', file),
    },
  }
}
