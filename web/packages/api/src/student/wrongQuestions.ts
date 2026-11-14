import type { HttpClient } from '../http'

/** 题目的选项（单选、多选） */
export interface WrongOption {
  key: string
  text: string
}

/** 错题列表项（S-WRG-01）：题目内容为最新版本，last_student_answer 保留作答时的原样 */
export interface WrongItem {
  id: number
  practice_question_id: number
  course_id: number
  course_title: string | null
  question_type: 'true_false' | 'single_choice' | 'multiple_choice' | 'short_answer'
  stem: string
  options: WrongOption[] | null
  correct_answer: string[] | null
  reference_answer: string | null
  analysis: string | null
  last_student_answer: unknown
  wrong_count: number
  last_wrong_at: string
  /** 题目已下架时为 false：可查看但不能重做 */
  is_redoable: boolean
  subject_names: string[]
}

/** 筛选项：课程与学科只列该学员错题覆盖的范围，路线为全部已上架路线 */
export interface WrongFilterOptions {
  courses: { id: number; title: string }[]
  subjects: { id: number; name: string }[]
  routes: { id: number; name: string }[]
}

export interface WrongQuestionList {
  items: WrongItem[]
  total: number
  page: number
  page_size: number
  filters: WrongFilterOptions
}

export interface WrongQuestionQuery {
  page?: number
  page_size?: number
  course_id?: number
  subject_id?: number
  question_type?: string
  learning_route_id?: number
}

export function createStudentWrongApi(http: HttpClient) {
  return {
    /** S-WRG-01 错题列表 */
    list: (query: WrongQuestionQuery = {}) =>
      http.get<WrongQuestionList>('/student/wrong-questions', { ...query }),
    /** S-WRG-02 手动移除错题 */
    remove: (id: number) => http.delete<void>(`/student/wrong-questions/${id}`),
  }
}

/** 题型名称，与后台题库的取值一致 */
export const QUESTION_TYPE_LABEL: Record<WrongItem['question_type'], string> = {
  true_false: '判断题',
  single_choice: '单选题',
  multiple_choice: '多选题',
  short_answer: '简答题',
}
