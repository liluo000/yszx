import type { HttpClient } from '../http'
import type { ImportResult, PageResult } from './system'

/* ---------------- 学科管理（A-CNT-01~05） ---------------- */

export interface SubjectView {
  id: number
  name: string
  intro: string | null
  sort_no: number
  course_count: number
}

export interface SubjectForm {
  name: string
  /** 编辑时传空字符串表示清空简介 */
  intro?: string
  sort_no: number
}

export function createSubjectApi(http: HttpClient) {
  return {
    list: () => http.get<{ items: SubjectView[] }>('/admin/subjects'),
    create: (body: SubjectForm) => http.post<{ id: number }>('/admin/subjects', body),
    update: (id: number, body: Partial<SubjectForm>) => http.patch<null>(`/admin/subjects/${id}`, body),
    remove: (id: number) => http.delete<null>(`/admin/subjects/${id}`),
    sort: (items: { id: number; sort_no: number }[]) => http.put<null>('/admin/subjects/sort-order', { items }),
  }
}

/* ---------------- 课程库（A-CNT-06~12） ---------------- */

export type ShelfStatus = 'on_shelf' | 'off_shelf'

/** 后台课程（公共对象 CourseAdminView） */
export interface CourseAdminView {
  id: number
  /** 所属学科，一门课程可以属于多个学科，按课程内顺序排列 */
  subjects: { id: number; name: string }[]
  title: string
  video_platform_id: number
  platform_name: string
  video_url: string
  intro: string | null
  duration_minutes: number | null
  sort_no: number
  status: ShelfStatus
  practice_question_count: number
  interview_question_count: number
  route_reference_count: number
  created_at: string
}

export interface CourseQuery {
  page?: number
  page_size?: number
  subject_id?: number
  status?: ShelfStatus
  has_questions?: boolean
  keyword?: string
}

export interface CourseForm {
  /** 至少 1 个学科 */
  subject_ids: number[]
  title: string
  video_platform_id: number
  video_url: string
  /** 编辑时传空字符串表示清空简介 */
  intro?: string
  duration_minutes?: number | null
  sort_no: number
}

export function createCourseApi(http: HttpClient) {
  return {
    list: (query: CourseQuery) => http.get<PageResult<CourseAdminView>>('/admin/courses', { ...query }),
    get: (id: number) => http.get<CourseAdminView>(`/admin/courses/${id}`),
    create: (body: CourseForm) => http.post<CourseAdminView>('/admin/courses', body),
    update: (id: number, body: Partial<CourseForm>) => http.patch<CourseAdminView>(`/admin/courses/${id}`, body),
    changeStatus: (id: number, status: ShelfStatus) =>
      http.put<CourseAdminView>(`/admin/courses/${id}/status`, { status }),
    remove: (id: number) => http.delete<null>(`/admin/courses/${id}`),
    importCourses: (file: File) => http.upload<ImportResult>('/admin/courses/imports', file),
  }
}

/* ---------------- 学习路线（A-CNT-14~28） ---------------- */

export interface RouteAdminView {
  id: number
  name: string
  intro: string | null
  sort_no: number
  status: ShelfStatus
  stage_count: number
  course_count: number
  created_at: string
}

export interface StageCourseView {
  course_id: number
  title: string
  status: ShelfStatus
  sort_no: number
  subject_names: string[]
  practice_question_count: number
}

export interface StageAdminView {
  id: number
  name: string
  intro: string | null
  sort_no: number
  courses: StageCourseView[]
}

/** 学员端课程卡片（公共对象 CourseItem） */
export interface CourseItem {
  id: number
  title: string
  intro: string | null
  duration_minutes: number | null
  platform_name: string
  subject_names: string[]
  practice_question_count: number
  is_learned: boolean
  is_mastered: boolean
}

/** 学员端阶段（公共对象 StageDetail），预览时进度为空 */
export interface StageDetail {
  id: number
  name: string
  intro: string | null
  progress: unknown
  is_random_available: boolean
  is_exam_available: boolean
  is_interview_available: boolean
  courses: CourseItem[]
}

export interface RouteForm {
  name: string
  /** 编辑时传空字符串表示清空简介 */
  intro?: string
  sort_no: number
}

export type SortItems = { id: number; sort_no: number }[]

export function createRouteApi(http: HttpClient) {
  return {
    list: (query: { page?: number; page_size?: number; status?: ShelfStatus }) =>
      http.get<PageResult<RouteAdminView>>('/admin/routes', { ...query }),
    options: () => http.get<{ items: { id: number; name: string; status: ShelfStatus }[] }>('/admin/route-options'),
    detail: (id: number) => http.get<{ route: RouteAdminView; stages: StageAdminView[] }>(`/admin/routes/${id}`),
    create: (body: RouteForm) => http.post<RouteAdminView>('/admin/routes', body),
    update: (id: number, body: Partial<RouteForm>) => http.patch<RouteAdminView>(`/admin/routes/${id}`, body),
    changeStatus: (id: number, status: ShelfStatus) =>
      http.put<RouteAdminView>(`/admin/routes/${id}/status`, { status }),
    remove: (id: number) => http.delete<null>(`/admin/routes/${id}`),
    preview: (id: number) =>
      http.get<{ id: number; name: string; intro: string | null; stages: StageDetail[] }>(
        `/admin/routes/${id}/preview`,
      ),
    createStage: (routeId: number, body: RouteForm) =>
      http.post<StageAdminView>(`/admin/routes/${routeId}/stages`, body),
    updateStage: (stageId: number, body: Partial<RouteForm>) =>
      http.patch<StageAdminView>(`/admin/stages/${stageId}`, body),
    removeStage: (stageId: number) => http.delete<null>(`/admin/stages/${stageId}`),
    sortStages: (routeId: number, items: SortItems) =>
      http.put<null>(`/admin/routes/${routeId}/stages/sort-order`, { items }),
    addCourses: (stageId: number, courseIds: number[]) =>
      http.post<StageAdminView>(`/admin/stages/${stageId}/courses`, { course_ids: courseIds }),
    removeCourse: (stageId: number, courseId: number) =>
      http.delete<null>(`/admin/stages/${stageId}/courses/${courseId}`),
    /** items 中的 id 为课程 ID */
    sortCourses: (stageId: number, items: SortItems) =>
      http.put<null>(`/admin/stages/${stageId}/courses/sort-order`, { items }),
  }
}
