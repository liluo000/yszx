import type { HttpClient } from '../http'

/** S-MSG-01 站内信 */
export interface StudentMessageItem {
  id: number
  message_type: string
  title: string
  content: string
  business_id: number | null
  is_read: boolean
  read_at: string | null
  created_at: string
}

export interface StudentMessagePage {
  items: StudentMessageItem[]
  total: number
  page: number
  page_size: number
}

export function createStudentMessageApi(http: HttpClient) {
  return {
    /** S-MSG-01 站内信列表 */
    list: (query: { page?: number; page_size?: number; is_read?: boolean } = {}) =>
      http.get<StudentMessagePage>('/student/messages', query as Record<string, unknown>),
    /** S-MSG-02 未读数（前端轮询） */
    unreadCount: () => http.get<{ unread_count: number }>('/student/messages/unread-count'),
    /** S-MSG-03 标记已读 */
    markRead: (id: number) => http.put<void>(`/student/messages/${id}/read`),
    /** S-MSG-04 全部已读 */
    readAll: () => http.put<void>('/student/messages/read-all'),
  }
}
