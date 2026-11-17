import type { HttpClient } from '../http'

/** 帮助中心常见问题（S-HLP-03） */
export interface StudentHelpFaq {
  id: number
  question: string
  answer: string
}

/** 客服联系方式：三项均可为空 */
export interface StudentHelpContact {
  wechat_qrcode_url: string | null
  email: string | null
  phone: string | null
}

export interface StudentHelpView {
  faqs: StudentHelpFaq[]
  contact: StudentHelpContact
}

export function createStudentHelpApi(http: HttpClient) {
  return {
    /** S-HLP-03 帮助中心内容 */
    load: () => http.get<StudentHelpView>('/student/help'),
  }
}
