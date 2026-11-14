import type { HttpClient } from '../http'

/** 阶段摘要（StageBrief）：已上架课程数 */
export interface StageBrief {
  id: number
  name: string
  course_count: number
}

/** 路线进度（RouteProgress）；游客为 null */
export interface RouteProgress {
  learned_course_count: number
  total_course_count: number
  mastered_course_count: number
  configured_course_count: number
}

/** 路线卡片（RouteCard） */
export interface RouteCard {
  id: number
  name: string
  intro: string | null
  stages: StageBrief[]
  progress: RouteProgress | null
}

/** 继续学习（S-PTL-01）：依赖学习进度模块，未接入时为 null */
export interface ContinueLearning {
  learning_route_id: number
  route_name: string
  route_stage_id: number
  stage_name: string
  course_id: number
  course_title: string
  is_learned: boolean
  is_mastered: boolean
  progress: RouteProgress
  stage_courses: { id: number; title: string; is_learned: boolean; is_mastered: boolean }[]
}

/** 首页内容（S-PTL-01） */
export interface HomeView {
  is_guest: boolean
  continue_learning: ContinueLearning | null
  my_routes: RouteCard[]
  pending_wrong_question_count: number
  interview_remaining: number
  routes: RouteCard[]
}

export function createHomeApi(http: HttpClient) {
  return {
    load: () => http.get<HomeView>('/student/home'),
  }
}
