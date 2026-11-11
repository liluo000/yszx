import axios, { type AxiosInstance, AxiosError } from 'axios'

/** 统一响应结构（04.API接口设计.md 2.2） */
export interface ApiSuccess<T> {
  code: 'OK'
  message: string
  data: T
  request_id: string
}

export interface ApiErrorBody {
  code: string
  message: string
  errors: unknown[]
  request_id: string
}

/** 接口错误，code 为业务错误码，网络异常时为 NETWORK_ERROR */
export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly errors: unknown[]
  readonly requestId?: string

  constructor(code: string, message: string, status: number, errors: unknown[] = [], requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.errors = errors
    this.requestId = requestId
  }
}

export interface HttpClientOptions {
  baseURL: string
  getToken: () => string | null
  /** 收到 401 时调用，用于清除登录状态并跳转登录页 */
  onUnauthorized?: () => void
}

/** 请求成功时直接返回 data，失败时抛出 ApiError */
export type HttpClient = {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T>
  post<T>(url: string, body?: unknown): Promise<T>
  put<T>(url: string, body?: unknown): Promise<T>
  patch<T>(url: string, body?: unknown): Promise<T>
  delete<T>(url: string): Promise<T>
  /** 上传单个文件（multipart，字段名 file） */
  upload<T>(url: string, file: File): Promise<T>
  /** 下载文件，返回文件内容和服务端给出的文件名；带 body 时用 POST（如按筛选条件导出） */
  download(url: string, body?: unknown): Promise<{ blob: Blob; filename: string | null }>
}

/** 下载接口出错时响应体也是 Blob，需要先解析成 JSON 才能拿到错误码 */
async function parseBlobError(data: unknown): Promise<unknown> {
  if (typeof Blob !== 'undefined' && data instanceof Blob && data.type.includes('json')) {
    try {
      return JSON.parse(await data.text())
    } catch {
      return data
    }
  }
  return data
}

function filenameFrom(disposition: string | undefined): string | null {
  if (!disposition) return null
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (encoded) return decodeURIComponent(encoded[1])
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : null
}

export function createHttpClient(options: HttpClientOptions): HttpClient {
  const instance: AxiosInstance = axios.create({ baseURL: options.baseURL, timeout: 30000 })

  instance.interceptors.request.use((config) => {
    const token = options.getToken()
    if (token) config.headers.set('Authorization', `Bearer ${token}`)
    return config
  })

  instance.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ApiErrorBody>) => {
      const body = (await parseBlobError(error.response?.data)) as ApiErrorBody | undefined
      if (error.response && body && typeof body === 'object' && 'code' in body) {
        if (error.response.status === 401) options.onUnauthorized?.()
        return Promise.reject(
          new ApiError(body.code, body.message, error.response.status, body.errors ?? [], body.request_id),
        )
      }
      const status = error.response?.status ?? 0
      return Promise.reject(new ApiError('NETWORK_ERROR', '网络异常，请稍后重试', status))
    },
  )

  const unwrap = async <T>(promise: Promise<{ data: ApiSuccess<T> }>) => (await promise).data.data

  return {
    get: (url, params) => unwrap(instance.get(url, { params })),
    post: (url, body) => unwrap(instance.post(url, body)),
    put: (url, body) => unwrap(instance.put(url, body)),
    patch: (url, body) => unwrap(instance.patch(url, body)),
    delete: (url) => unwrap(instance.delete(url)),
    upload: (url, file) => {
      const form = new FormData()
      form.append('file', file)
      return unwrap(instance.post(url, form))
    },
    download: async (url, body) => {
      const response =
        body === undefined
          ? await instance.get<Blob>(url, { responseType: 'blob' })
          : await instance.post<Blob>(url, body, { responseType: 'blob' })
      return {
        blob: response.data,
        filename: filenameFrom(response.headers['content-disposition'] as string | undefined),
      }
    },
  }
}
