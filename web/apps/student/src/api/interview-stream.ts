import { ApiError } from '@yunshu/api'
import { getToken } from '@/stores/token'
import { interviewApi } from '@/api'

/** S-IVW-06 的 text/event-stream 事件 */
export interface InterviewSseEvent {
  event: string
  data: Record<string, unknown>
}

/**
 * 用 fetch 读取 SSE（axios 不支持流）。
 * 校验类错误在响应头阶段就以统一 JSON 返回，这里转成 ApiError 交给页面提示。
 */
export async function streamAnswer(
  id: number,
  content: string,
  claim: string,
  onEvent: (event: InterviewSseEvent) => void,
): Promise<void> {
  const response = await fetch(`/api/v1/student/interviews/${id}/answers`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken() ?? ''}`,
      'X-Device-Claim': claim,
    },
    body: JSON.stringify({ content }),
  })
  if (!response.ok) {
    let code = 'NETWORK_ERROR'
    let message = '提交失败，请稍后重试'
    try {
      const body = (await response.json()) as { code?: string; message?: string }
      code = body.code ?? code
      message = body.message ?? message
    } catch {
      // 非 JSON 错误体
    }
    throw new ApiError(code, message, response.status)
  }
  if (!response.body) return
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const consume = (block: string) => {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    if (!dataLines.length) return
    try {
      onEvent({ event, data: JSON.parse(dataLines.join('\n')) as Record<string, unknown> })
    } catch {
      // 忽略无法解析的心跳帧
    }
  }
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let splitAt = buffer.indexOf('\n\n')
    while (splitAt >= 0) {
      consume(buffer.slice(0, splitAt))
      buffer = buffer.slice(splitAt + 2)
      splitAt = buffer.indexOf('\n\n')
    }
  }
  if (buffer.trim()) consume(buffer)
}

/** 一条学生端 WebSocket 通道 */
export interface WsChannel {
  send: (payload: Record<string, unknown>) => void
  close: () => void
  socket: WebSocket
}

/**
 * 调用 S-IVW-08 取一次性票据，建连后第一帧发送 auth（票据不进 URL）。
 * Vite 把 /ws 代理到后端 8080。
 */
export async function openStudentWs(
  purpose: 'voice_interview' | 'voice_input',
  onMessage: (message: Record<string, unknown>) => void,
  onClose?: () => void,
): Promise<WsChannel> {
  const ticket = await interviewApi.wsTicket(purpose)
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const path = purpose === 'voice_interview' ? 'voice-interview' : 'voice-input'
  const socket = new WebSocket(`${protocol}//${location.host}/ws/v1/student/${path}`)
  socket.onopen = () => socket.send(JSON.stringify({ type: 'auth', ticket: ticket.ticket }))
  socket.onmessage = (event) => {
    try {
      onMessage(JSON.parse(String(event.data)) as Record<string, unknown>)
    } catch {
      // 忽略非 JSON 帧
    }
  }
  socket.onclose = () => onClose?.()
  return {
    socket,
    send: (payload) => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(payload))
    },
    close: () => socket.close(),
  }
}
