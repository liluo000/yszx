import type { HttpClient } from '../http'
// 分页结构与后台一致（04.API接口设计.md 2.3）
import type { PageResult } from '../admin/system'
import type { RouteCard, RouteProgress } from './home'

/** 路线中的课程（CourseItem）：practice_question_count 为 0 表示未配题 */
export interface RouteCourseItem {
  id: number
  title: string
  intro: string | null
  duration_minutes: number | null
  platform_name: string | null
  subject_names: string[]
  practice_question_count: number
  is_learned: boolean
  is_mastered: boolean
}

/** 路线详情中的阶段（StageDetail）：可用性由题量与最低题量决定 */
export interface RouteStageDetail {
  id: number
  name: string
  intro: string | null
  progress: RouteProgress | null
  is_random_available: boolean
  is_exam_available: boolean
  is_interview_available: boolean
  courses: RouteCourseItem[]
}

export interface RouteDetailView {
  id: number
  name: string
  intro: string | null
  progress: RouteProgress | null
  stages: RouteStageDetail[]
}

/** 搜索命中的课程及其所在的已上架路线 */
export interface CourseRef {
  learning_route_id: number
  route_name: string
  route_stage_id: number
}

export interface CourseHit {
  id: number
  title: string
  intro: string | null
  routes: CourseRef[]
}

export interface SearchResult {
  routes: RouteCard[]
  courses: CourseHit[]
}

export function createStudentRouteApi(http: HttpClient) {
  return {
    list: (query: { page?: number; page_size?: number }) =>
      http.get<PageResult<RouteCard>>('/student/routes', { ...query }),
    detail: (id: number) => http.get<RouteDetailView>(`/student/routes/${id}`),
    search: (keyword: string) => http.get<SearchResult>('/student/search', { keyword }),
  }
}
