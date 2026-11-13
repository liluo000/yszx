import type { HttpClient } from '../http'

/** 工作台（A-DSH-03）：当前角色无权查看的字段为 null */
export interface WorkbenchView {
  pending_question_correction_count: number | null
  pending_video_invalid_count: number | null
  unconfigured_course_count: number | null
  insufficient_stage_count: number | null
  today_new_student_count: number | null
  today_active_student_count: number | null
  today_interview_count: number | null
}

export function createWorkbenchApi(http: HttpClient) {
  return {
    load: () => http.get<WorkbenchView>('/admin/workbench'),
  }
}
