<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { InterviewMessage, InterviewOptions, InterviewView } from '@yunshu/api'
import { errorText, interviewApi, routeApi } from '@/api'
import { streamAnswer, openStudentWs, type WsChannel } from '@/api/interview-stream'

type RouteItem = Awaited<ReturnType<typeof routeApi.list>>['items'][number]

const route = useRoute()
const router = useRouter()

type Stage = 'setup' | 'text' | 'voice'
const stage = ref<Stage>('setup')
const routes = ref<RouteItem[]>([])
const routeId = ref(0)
const scopeType = ref<'whole_route' | 'selected_stages'>('whole_route')
const selectedStages = ref<number[]>([])
const mode = ref<'text' | 'voice'>('text')
const options = ref<InterviewOptions | null>(null)
const view = ref<InterviewView | null>(null)
const claim = ref('')
const input = ref('')
const sending = ref(false)
const error = ref('')
const loading = ref(true)
const ended = ref(false)
const remainingText = ref('')
const confirmEnd = ref(false)
let timer: number | undefined

/** 语音面试（WS-01） */
const voice = ref({ step: 0, queue: 0, started: false, subtitles: [] as string[], captions: true, draft: '' })
const ws = ref<WsChannel | null>(null)
const voiceInput = ref({ listening: false, draft: '' })
/** 输入框左下角「+」：插入常用句式，避免放一个没有功能的装饰按钮 */
const quickOpen = ref(false)
const quickWrap = ref<HTMLElement | null>(null)
const QUICK_PHRASES = [
  '我会结合具体项目经验来说明：',
  '可以从背景、方案、遇到的问题和验证结果四方面回答：',
  '这道题我的理解是：',
]
function insertPhrase(phrase: string) {
  input.value = input.value.trim() ? `${input.value.trimEnd()}\n${phrase}` : phrase
  quickOpen.value = false
}
/** 点到菜单和「+」以外的任何地方都收起菜单，Esc 也可以关 */
function onQuickPointerDown(event: PointerEvent) {
  if (!quickOpen.value) return
  const target = event.target
  if (target instanceof Node && quickWrap.value?.contains(target)) return
  quickOpen.value = false
}
function onQuickKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && quickOpen.value) quickOpen.value = false
}
const voiceInputWs = ref<WsChannel | null>(null)
let pendingStart: { learning_route_id: number; scope_type: string; route_stage_ids: number[] } | null = null

const availableStages = computed(() => options.value?.stages.filter((s) => s.is_interview_available) ?? [])

/** 一个气泡 = 同一方的一轮发言；服务端把每句话存成一条消息，这里再并回来 */
interface ChatItem {
  key: number
  speaker: 'ai' | 'student'
  message_type: string
  content: string
  lines: string[]
  created_at: string
}
/** 正在逐字吐出的那一轮：lineIndex 是当前句，charIndex 是这句已吐出的字数 */
interface ActiveTurn {
  key: number
  lines: string[]
  lineIndex: number
  charIndex: number
}

const visible = ref<ChatItem[]>([])
const pending = ref<ChatItem[]>([])
const active = ref<ActiveTurn | null>(null)
const aiReplyStarted = ref(false)
const chatBox = ref<HTMLElement | null>(null)
const captionQueue = ref<string[]>([])
let keySeed = 0
let drainTimer: number | undefined
let captionTimer: number | undefined
/** 正在流式接收的气泡：句子吐完但流未结束时先等下一句，不要提前收尾 */
let streamingKey: number | null = null

/** 逐条播放节奏（毫秒），模拟真实面试的一问一答 */
const FIRST_GAP = 240
const AI_GAP = 420
const STUDENT_GAP = 160
/** AI 气泡的打字机节奏 */
const CHARS_PER_TICK = 2
const CHAR_GAP = 30
const LINE_PAUSE = 140
const CAPTION_GAP = 480

/** 回放动画进行中 */
const replaying = computed(() => active.value !== null || pending.value.length > 0)
/** 播放中或等待 AI 回复时不允许继续发送，避免对话顺序错乱 */
const busy = computed(() => sending.value || replaying.value)
/** 等 AI 第一句话时给一个轻量提示，不再额外造一个「AI 面试官」气泡 */
const waitingAi = computed(() => sending.value && !aiReplyStarted.value)

function makeItem(speaker: 'ai' | 'student', type: string, content: string): ChatItem {
  return { key: ++keySeed, speaker, message_type: type, content: '',
    lines: content ? [content] : [], created_at: new Date().toISOString() }
}

/** 相邻且同一方的消息合成一个气泡，中间有学员回答就自然断开 */
function mergeMessages(items: InterviewMessage[]): ChatItem[] {
  const bubbles: ChatItem[] = []
  for (const msg of items) {
    const last = bubbles[bubbles.length - 1]
    if (last && last.speaker === msg.speaker) last.lines.push(msg.content)
    else bubbles.push({ key: ++keySeed, speaker: msg.speaker, message_type: msg.message_type,
      content: '', lines: [msg.content], created_at: msg.created_at })
  }
  return bubbles
}

function scrollChat() {
  void nextTick(() => {
    if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
  })
}

function findBubble(key: number) {
  return visible.value.find((item) => item.key === key)
}

/** 流式到达的一句话补进当前 AI 气泡，而不是新开一个气泡 */
function appendLine(key: number, line: string) {
  const bubble = findBubble(key) ?? pending.value.find((item) => item.key === key)
  if (!bubble) return
  bubble.lines.push(line)
  if (active.value?.key === key) {
    if (!drainTimer) runDrain()
    return
  }
  // 用户点了「跳过动画」后流还在继续：新句子直接补到气泡末尾
  if (!active.value && findBubble(key)) {
    bubble.content = bubble.content ? `${bubble.content}\n${line}` : line
    scrollChat()
  }
}

/** 按 turn 的游标重建气泡里已经吐出来的文字 */
function syncBubble(turn: ActiveTurn) {
  const bubble = findBubble(turn.key)
  if (!bubble) return
  const parts = turn.lines.slice(0, turn.lineIndex)
  if (turn.lineIndex < turn.lines.length) {
    parts.push((turn.lines[turn.lineIndex] ?? '').slice(0, turn.charIndex))
  }
  bubble.content = parts.join('\n')
}

/** 气泡按顺序出现，AI 气泡里的文字一个字一个字吐出来 */
function enqueue(items: ChatItem[]) {
  if (!items.length) return
  pending.value.push(...items)
  runDrain()
}

function runDrain() {
  if (drainTimer) return
  const step = () => {
    const turn = active.value
    if (turn) {
      if (turn.lineIndex < turn.lines.length) {
        const line = turn.lines[turn.lineIndex] ?? ''
        if (turn.charIndex < line.length) {
          turn.charIndex = Math.min(line.length, turn.charIndex + CHARS_PER_TICK)
          syncBubble(turn)
          scrollChat()
          drainTimer = window.setTimeout(step, CHAR_GAP)
          return
        }
        // 一句吐完，短暂停顿再吐下一句
        turn.lineIndex++
        turn.charIndex = 0
        if (turn.lineIndex < turn.lines.length) {
          drainTimer = window.setTimeout(step, LINE_PAUSE)
          return
        }
        // 流式回复可能还没给完句子，等一等再收尾
        if (streamingKey === turn.key) {
          drainTimer = window.setTimeout(step, 80)
          return
        }
      }
      active.value = null
    }
    const next = pending.value.shift()
    if (!next) {
      drainTimer = undefined
      return
    }
    visible.value.push(next)
    if (next.speaker === 'ai') {
      next.content = ''
      active.value = { key: next.key, lines: next.lines, lineIndex: 0, charIndex: 0 }
      scrollChat()
      drainTimer = window.setTimeout(step, turn ? AI_GAP : CHAR_GAP)
    } else {
      // 自己发的消息整条出现，不打字
      next.content = next.lines.join('\n')
      scrollChat()
      drainTimer = window.setTimeout(step, STUDENT_GAP)
    }
  }
  drainTimer = window.setTimeout(step, FIRST_GAP)
}

function stopReplay() {
  if (drainTimer) window.clearTimeout(drainTimer)
  drainTimer = undefined
  pending.value = []
  active.value = null
}

/** 不想等动画时一次显示全部 */
function skipReplay() {
  if (drainTimer) window.clearTimeout(drainTimer)
  drainTimer = undefined
  const turn = active.value
  if (turn) {
    const bubble = findBubble(turn.key)
    if (bubble) bubble.content = turn.lines.join('\n')
    active.value = null
  }
  while (pending.value.length) {
    const bubble = pending.value.shift()!
    bubble.content = bubble.lines.join('\n')
    visible.value.push(bubble)
  }
  scrollChat()
}

/** 刷新恢复长历史时直接显示，避免几十条消息重放一遍 */
function showHistory(items: InterviewMessage[]) {
  stopReplay()
  visible.value = mergeMessages(items).map((bubble) => ({ ...bubble, content: bubble.lines.join('\n') }))
  scrollChat()
}

/** 新开一场面试（或开场阶段的刷新）逐条播放 */
function replayAll(items: InterviewMessage[]) {
  stopReplay()
  visible.value = []
  enqueue(mergeMessages(items))
}

/** 语音面试字幕同样逐条出现 */
function enqueueCaption(line: string) {
  captionQueue.value.push(line)
  if (captionTimer) return
  const step = () => {
    const next = captionQueue.value.shift()
    if (!next) {
      captionTimer = undefined
      return
    }
    voice.value.subtitles.push(next)
    captionTimer = window.setTimeout(step, CAPTION_GAP)
  }
  captionTimer = window.setTimeout(step, FIRST_GAP)
}

function resetCaptions(lines: string[]) {
  if (captionTimer) window.clearTimeout(captionTimer)
  captionTimer = undefined
  captionQueue.value = []
  voice.value.subtitles = lines
}
const canStart = computed(() => {
  if (!options.value || options.value.interview_remaining <= 0 || routeId.value === 0) return false
  if (scopeType.value === 'selected_stages') return selectedStages.value.length > 0
  return availableStages.value.length > 0
})
const mainN = computed(() => options.value?.main_question_count ?? 5)
const grade = computed(() => (view.value ? `第 ${view.value.current_seq_no} / ${view.value.main_question_count} 题` : ''))

function claimKey(id: number) {
  return `yunshu:interview:claim:${id}`
}

function applyView(next: InterviewView, reveal: 'history' | 'replay' | 'none' = 'history') {
  view.value = next
  ended.value = next.status === 'ended'
  stage.value = next.interview_mode === 'voice' ? 'voice' : 'text'
  if (reveal === 'history') showHistory(next.messages)
  else if (reveal === 'replay') replayAll(next.messages)
  startTimer(next)
}

function startTimer(next: InterviewView) {
  stopTimer()
  const offset = Date.parse(next.deadline_at) - Date.parse(next.server_time)
  const tick = () => {
    const left = Math.max(0, Math.floor(offset / 1000))
    const m = Math.floor(left / 60)
    const s = left % 60
    remainingText.value = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    if (left <= 0 && view.value?.status === 'in_progress') void onTimeout()
  }
  tick()
  timer = window.setInterval(tick, 1000)
}

function stopTimer() {
  if (timer) window.clearInterval(timer)
  timer = undefined
}

async function loadOptions() {
  if (!routeId.value) return
  try {
    options.value = await interviewApi.options(routeId.value)
    if (!selectedStages.value.length && options.value.stages.length) {
      selectedStages.value = options.value.stages.filter((s) => s.is_interview_available).slice(0, 1).map((s) => s.route_stage_id)
    }
  } catch (e) {
    error.value = errorText(e, '面试选项加载失败')
  }
}

watch(routeId, loadOptions)

onMounted(async () => {
  document.addEventListener('pointerdown', onQuickPointerDown)
  document.addEventListener('keydown', onQuickKeydown)
  try {
    const list = await routeApi.list({ page: 1, page_size: 100 })
    routes.value = list.items
    routeId.value = Number(route.query.route) || list.items[0]?.id || 0
    await loadOptions()
    const progress = await interviewApi.inProgress()
    if (progress.interview) {
      const claimed = await interviewApi.claimDevice(progress.interview.id)
      claim.value = claimed.device_claim
      sessionStorage.setItem(claimKey(progress.interview.id), claimed.device_claim)
      // 开场阶段（消息还很少）刷新后也逐条播放；长历史直接显示
      applyView(progress.interview, progress.interview.messages.length <= 8 ? 'replay' : 'history')
      if (progress.interview.interview_mode === 'voice') await connectVoiceInterview(false)
    }
  } catch (e) {
    error.value = errorText(e, '面试信息加载失败')
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', onQuickPointerDown)
  document.removeEventListener('keydown', onQuickKeydown)
  stopTimer()
  stopReplay()
  if (captionTimer) window.clearTimeout(captionTimer)
  ws.value?.close()
  voiceInputWs.value?.close()
})

function toggleStage(id: number) {
  const index = selectedStages.value.indexOf(id)
  if (index >= 0) selectedStages.value.splice(index, 1)
  else if (selectedStages.value.length < mainN.value) selectedStages.value.push(id)
}

async function startTextInterview() {
  if (!canStart.value) return
  error.value = ''
  try {
    const created = await interviewApi.start({
      learning_route_id: routeId.value,
      scope_type: scopeType.value,
      route_stage_ids: scopeType.value === 'selected_stages' ? selectedStages.value : undefined,
    })
    const claimed = await interviewApi.claimDevice(created.id)
    claim.value = claimed.device_claim
    sessionStorage.setItem(claimKey(created.id), claimed.device_claim)
    applyView(created, 'replay')
  } catch (e) {
    error.value = errorText(e, '发起面试失败')
  }
}

/** 文字面试：提交回答；一次回答后 AI 的所有句子都落在同一个气泡里 */
async function sendAnswer() {
  const content = input.value.trim()
  if (!content || busy.value || !view.value || ended.value) return
  input.value = ''
  quickOpen.value = false
  error.value = ''
  sending.value = true
  aiReplyStarted.value = false
  enqueue([makeItem('student', 'answer', content)])
  let currentType = 'followup'
  let liveKey: number | null = null
  let finished = false
  try {
    await streamAnswer(view.value.id, content, claim.value, (event) => {
      if (event.event === 'message_start') {
        currentType = String(event.data.message_type ?? 'followup')
        aiReplyStarted.value = true
        // 后续 segment（过渡语、下一题）继续写进同一个气泡，不再拆成多个
        if (liveKey === null) {
          const bubble = makeItem('ai', currentType, '')
          liveKey = bubble.key
          streamingKey = bubble.key
          enqueue([bubble])
        }
      } else if (event.event === 'sentence') {
        if (liveKey !== null) appendLine(liveKey, String(event.data.content ?? ''))
      } else if (event.event === 'interview_ended') {
        finished = true
      } else if (event.event === 'error') {
        error.value = String(event.data.message ?? 'AI 回复失败')
      } else if (event.event === 'ai_recovering') {
        error.value = 'AI 服务异常，正在尝试恢复…'
      }
    })
    // 流已经把消息逐条渲染，服务端只用于同步场次状态，不再覆盖对话
    const fresh = await interviewApi.view(view.value.id)
    applyView(fresh, 'none')
    ended.value = finished || fresh.status === 'ended'
  } catch (e) {
    error.value = errorText(e, '提交回答失败')
    await refreshMessages()
  } finally {
    streamingKey = null
    sending.value = false
  }
}

/** 回车发送、Shift+Enter 换行；输入法选字时的回车不当作发送 */
function onComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  void sendAnswer()
}

/** 流式中断时用 S-IVW-04 补全；正常结束不重放已显示的消息 */
async function refreshMessages(reveal: 'history' | 'none' = 'history') {
  if (!view.value) return
  try {
    applyView(await interviewApi.view(view.value.id), reveal)
  } catch {
    // 保留本地消息
  }
}

async function terminate(reason: 'manual' | 'timeout') {
  if (!view.value) return
  try {
    await interviewApi.terminate(view.value.id, claim.value)
  } catch (e) {
    if (reason === 'manual') error.value = errorText(e, '结束面试失败')
  }
  await refreshMessages('none')
  ended.value = true
  confirmEnd.value = false
}

async function onTimeout() {
  stopTimer()
  await terminate('timeout')
}

function goReport() {
  if (view.value) void router.push(`/report/${view.value.id}`)
}

/** WS-02：文字面试的语音输入（厂商未接入时用文字模拟听写） */
async function toggleVoiceInput() {
  if (voiceInput.value.listening) {
    voiceInputWs.value?.send({ type: 'stop' })
    voiceInputWs.value?.close()
    voiceInputWs.value = null
    voiceInput.value.listening = false
    return
  }
  try {
    voiceInput.value.listening = true
    voiceInput.value.draft = ''
    voiceInputWs.value = await openStudentWs('voice_input', (msg) => {
      if (msg.type === 'transcript' && msg.is_final === false) {
        input.value += (input.value ? ' ' : '') + String(msg.text ?? '')
      } else if (msg.type === 'no_speech') {
        error.value = '没有听到声音，可以再试一次'
      } else if (msg.type === 'unavailable') {
        error.value = '语音输入暂不可用，请直接打字'
      }
    })
    voiceInputWs.value.send({ type: 'start' })
  } catch (e) {
    voiceInput.value.listening = false
    error.value = errorText(e, '语音输入连接失败')
  }
}

function pushVoiceInputDraft() {
  const text = voiceInput.value.draft.trim()
  if (!text) return
  voiceInputWs.value?.send({ type: 'dev_transcript', text })
  voiceInput.value.draft = ''
}

/** WS-01：语音面试 */
function beginVoiceSetup() {
  if (!canStart.value) return
  pendingStart = {
    learning_route_id: routeId.value,
    scope_type: scopeType.value,
    route_stage_ids: scopeType.value === 'selected_stages' ? selectedStages.value : [],
  }
  voice.value = { step: 0, queue: 0, started: false, subtitles: [], captions: true, draft: '' }
  stage.value = 'voice'
  void connectVoiceInterview(true)
}

async function connectVoiceInterview(fresh: boolean) {
  ws.value?.close()
  ws.value = await openStudentWs('voice_interview', (msg) => onVoiceMessage(msg), () => {
    if (voice.value.started) error.value = '语音连接已断开，可重新进入继续面试'
  })
  // auth_ok 后由服务端确认，这里等连接建立后发送 start/resume
  const sendWhenOpen = () => {
    if (ws.value?.socket.readyState !== WebSocket.OPEN) {
      window.setTimeout(sendWhenOpen, 100)
      return
    }
    // openStudentWs 已在 onopen 发送 auth，稍等一帧再发业务消息
    window.setTimeout(() => {
      if (fresh && pendingStart) ws.value?.send({ type: 'start', ...pendingStart })
      else if (!fresh && view.value) ws.value?.send({ type: 'resume', interview_session_id: view.value.id })
    }, 150)
  }
  sendWhenOpen()
}

function onVoiceMessage(msg: Record<string, unknown>) {
  switch (msg.type) {
    case 'auth_ok':
      break
    case 'queue_update':
      voice.value.queue = Number(msg.position ?? 0)
      break
    case 'device_check_required':
      voice.value.step = 1
      voice.value.queue = 0
      break
    case 'device_check_result':
      voice.value.step = 2
      break
    case 'interview_started':
      voice.value.started = true
      voice.value.step = 3
      void onVoiceStarted(Number(msg.interview_session_id))
      break
    case 'resumed':
      voice.value.started = true
      voice.value.step = 3
      if (Array.isArray(msg.messages)) {
        resetCaptions((msg.messages as InterviewMessage[])
          .filter((m) => m.speaker === 'ai').map((m) => m.content))
      }
      break
    case 'ai_text':
      enqueueCaption(String(msg.content ?? ''))
      break
    case 'transcript':
      if (msg.is_final) enqueueCaption(`我：${String(msg.text ?? '')}`)
      break
    case 'sensitive_blocked':
      error.value = '这句话包含不适宜内容，请换一种说法重新回答'
      break
    case 'silence_prompt':
      enqueueCaption('（面试官提醒：可以先说说你的思路）')
      break
    case 'question_unanswered':
      enqueueCaption('（本题记为未作答，进入下一题）')
      break
    case 'error':
      error.value = String(msg.message ?? '语音面试出错')
      break
    case 'interview_ended':
      voice.value.started = false
      ended.value = true
      break
    default:
      break
  }
}

async function onVoiceStarted(id: number) {
  try {
    const claimed = await interviewApi.claimDevice(id)
    claim.value = claimed.device_claim
    sessionStorage.setItem(claimKey(id), claimed.device_claim)
    applyView(await interviewApi.view(id), 'none')
    stage.value = 'voice'
  } catch (e) {
    error.value = errorText(e, '语音面试状态加载失败')
  }
}

function sendVoiceDraft() {
  const text = voice.value.draft.trim()
  if (!text) return
  voice.value.draft = ''
  ws.value?.send({ type: 'dev_transcript', text })
}

function passDeviceCheck() {
  ws.value?.send({ type: 'device_check_audio_end' })
  window.setTimeout(() => {
    ws.value?.send({ type: 'device_check_passed' })
  }, 250)
}

function failDeviceCheck() {
  ws.value?.send({ type: 'device_check_failed', reason: '设备不可用' })
  ws.value?.close()
  stage.value = 'setup'
  error.value = '设备检测未通过，未扣除次数，可改用文字面试'
}

function endVoice() {
  ws.value?.send({ type: 'end_interview' })
  ended.value = true
}
</script>

<template>
  <div class="interview page">
    <header class="page-head has-actions">
      <div>
        <h1>AI 面试</h1>
        <p>选好路线、范围和模式，与 AI 面试官过一遍，结束后生成面试报告。</p>
      </div>
      <div class="head-actions">
        <RouterLink to="/interviews" class="btn btn-sm">面试记录</RouterLink>
      </div>
    </header>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="skeleton">正在加载面试信息…</div>

    <!-- 发起配置 -->
    <template v-else-if="stage === 'setup'">
      <div class="grid setup-grid">
        <div class="card setup-main">
          <section class="fs">
            <h2>选择学习路线</h2>
            <span class="select-wrap">
              <select v-model.number="routeId" class="select" aria-label="学习路线">
                <option v-for="item in routes" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </span>
          </section>

          <section class="fs">
            <h2>选择面试范围</h2>
            <div class="seg">
              <button type="button" class="seg-item" :class="{ active: scopeType === 'whole_route' }" @click="scopeType = 'whole_route'">整条路线</button>
              <button type="button" class="seg-item" :class="{ active: scopeType === 'selected_stages' }" @click="scopeType = 'selected_stages'">选择阶段</button>
            </div>
            <div v-if="scopeType === 'selected_stages'" class="stage-list">
              <p class="small muted">最多选 {{ mainN }} 个阶段，没有面试题的阶段不能选。</p>
              <label v-for="item in options?.stages" :key="item.route_stage_id" class="stage-check" :class="{ disabled: !item.is_interview_available }">
                <input type="checkbox" :value="item.route_stage_id" :checked="selectedStages.includes(item.route_stage_id)" :disabled="!item.is_interview_available" @change="toggleStage(item.route_stage_id)" />
                <span>{{ item.stage_name }}</span>
                <span class="small muted">{{ item.is_interview_available ? '有面试题' : '暂无面试题' }}</span>
              </label>
            </div>
            <p v-else class="small muted">覆盖本路线有面试题的 {{ availableStages.length }} 个阶段，主问题平均分到各阶段。</p>
          </section>

          <section class="fs">
            <h2>选择面试模式</h2>
            <div class="grid grid-2">
              <button type="button" class="choice" :class="{ sel: mode === 'text' }" @click="mode = 'text'">
                <h3>文字面试</h3>
                <p>AI 用文字提问，你用文字作答，适合不方便说话的时候。</p>
              </button>
              <button type="button" class="choice" :class="{ sel: mode === 'voice' }" @click="mode = 'voice'">
                <h3>语音面试</h3>
                <p>实时语音对话，开始前先做设备检测，通过后才扣次数。</p>
              </button>
            </div>
            <p class="small muted">整场面试保持所选模式，中途不能切换。</p>
          </section>
        </div>

        <aside class="card summary">
          <h2>本场面试</h2>
          <dl>
            <div><dt>主问题</dt><dd><b class="fig">{{ mainN }}</b> 道</dd></div>
            <div><dt>时长上限</dt><dd><b class="fig">{{ options?.duration_minutes ?? 30 }}</b> 分钟</dd></div>
            <div><dt>今日剩余</dt><dd><b class="fig">{{ options?.interview_remaining ?? 0 }}</b> 次</dd></div>
            <div><dt>模式</dt><dd>{{ mode === 'text' ? '文字面试' : '语音面试' }}</dd></div>
          </dl>
          <p v-if="options && options.interview_remaining <= 0" class="alert alert-warn">今日次数已用完，北京时间 0 点重置。</p>
          <p v-else-if="!canStart" class="alert alert-warn">所选范围的面试题还不够，题目建设中。</p>
          <button v-if="mode === 'text'" type="button" class="btn btn-primary full" :disabled="!canStart" @click="startTextInterview">开始文字面试</button>
          <button v-else type="button" class="btn btn-primary full" :disabled="!canStart" @click="beginVoiceSetup">开始语音面试</button>
        </aside>
      </div>
    </template>

    <!-- 文字面试 -->
    <template v-else-if="stage === 'text' && view">
      <div class="chat-shell card">
        <div class="chat-head">
          <div>
            <h2>{{ view.route_name }}</h2>
            <span class="small muted">{{ grade }} · {{ mode === 'text' ? '文字面试' : '语音面试' }}</span>
          </div>
          <div class="row">
            <span class="timer fig">{{ remainingText }}</span>
            <button v-if="!ended" type="button" class="btn btn-sm" @click="confirmEnd = true">结束面试</button>
            <button type="button" class="btn btn-sm btn-primary" @click="goReport">查看报告</button>
          </div>
        </div>

        <div ref="chatBox" class="chat">
          <div v-for="msg in visible" :key="msg.key" class="bubble" :class="msg.speaker">
            <span class="who small">{{ msg.speaker === 'ai' ? 'AI 面试官' : '我' }}</span>
            <p>{{ msg.content }}<span v-if="active?.key === msg.key" class="caret" aria-hidden="true"></span></p>
          </div>
          <div v-if="waitingAi" class="typing-hint" aria-live="polite">
            <span class="dots" aria-label="AI 正在输入"><i></i><i></i><i></i></span>
          </div>
          <button v-if="replaying" type="button" class="btn-link skip" @click="skipReplay">跳过动画 ›</button>
          <p v-if="ended" class="alert alert-info">本场面试已结束，报告生成后可在「面试记录」中查看。</p>
        </div>

        <div v-if="!ended" class="composer">
          <textarea
            v-model="input"
            class="composer-input"
            rows="2"
            :placeholder="busy ? '面试官正在说话，稍候即可作答…' : '输入你的回答，尽量结合具体项目经验…'"
            :disabled="busy"
            @keydown="onComposerKeydown"
          />
          <div class="composer-bar">
            <div ref="quickWrap" class="composer-left">
              <button
                type="button"
                class="composer-plus"
                :disabled="busy"
                :aria-expanded="quickOpen"
                aria-label="快捷用语"
                @click="quickOpen = !quickOpen"
              >+</button>
              <div v-if="quickOpen" class="quick-menu" role="menu">
                <button v-for="phrase in QUICK_PHRASES" :key="phrase" type="button" role="menuitem" @click="insertPhrase(phrase)">
                  {{ phrase }}
                </button>
              </div>
            </div>
            <span class="composer-label">{{ voiceInput.listening ? '正在收音…' : 'AI 面试官 · 文字模式' }}</span>
            <div class="composer-right">
              <button
                type="button"
                class="composer-icon"
                :class="{ active: voiceInput.listening }"
                :aria-label="voiceInput.listening ? '结束语音输入' : '语音输入'"
                @click="toggleVoiceInput"
              >
                <svg viewBox="0 0 20 20" aria-hidden="true">
                  <rect x="7.5" y="2.5" width="5" height="9" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <path d="M5 9.5a5 5 0 0 0 10 0M10 14.5v3" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                </svg>
              </button>
              <button
                type="button"
                class="composer-send"
                :disabled="busy || !input.trim()"
                :aria-label="busy ? '面试官正在说话' : '发送回答'"
                @click="sendAnswer"
              >
                <svg viewBox="0 0 20 20" aria-hidden="true">
                  <path d="M10 15.5V5m0 0-4.5 4.5M10 5l4.5 4.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
            </div>
          </div>
          <div v-if="voiceInput.listening" class="dev-row composer-dev">
            <span class="small muted">语音服务待接入，可用文字模拟听写：</span>
            <input v-model="voiceInput.draft" class="input" placeholder="输入一句，追加到上方输入框" @keydown.enter="pushVoiceInputDraft" />
            <button type="button" class="btn btn-sm" @click="pushVoiceInputDraft">送入</button>
          </div>
        </div>
      </div>

      <div v-if="confirmEnd" class="mask">
        <div class="dialog card">
          <div class="dialog-head"><h2>结束面试</h2></div>
          <p>结束时会按已作答部分生成报告，未问到的题目计 0 分，且不会退还本次面试次数。</p>
          <div class="dialog-foot">
            <button type="button" class="btn" @click="confirmEnd = false">继续作答</button>
            <button type="button" class="btn btn-primary" @click="terminate('manual')">确认结束</button>
          </div>
        </div>
      </div>
    </template>

    <!-- 语音面试 -->
    <template v-else-if="stage === 'voice'">
      <div class="card voice-card">
        <div class="row-between">
          <h2>{{ view?.route_name ?? '语音面试' }}</h2>
          <span v-if="view" class="timer fig">{{ remainingText }}</span>
        </div>

        <div v-if="!voice.started" class="device">
          <p v-if="voice.queue > 0" class="alert alert-warn">当前面试人数较多，前面还有 {{ voice.queue }} 人，排队期间不扣次数。</p>
          <ol class="device-steps">
            <li :class="{ done: voice.step >= 1 }">申请麦克风权限并保留名额</li>
            <li :class="{ done: voice.step >= 2 }">录一句话试听，确认识别结果</li>
            <li :class="{ done: voice.step >= 3 }">播放测试音，确认能听到</li>
          </ol>
          <p class="small muted">语音识别与合成服务待接入（架构 T05）；当前用文字模拟说话，协议链路完整。检测通过后才会扣除次数。</p>
          <div class="row">
            <button type="button" class="btn btn-primary" :disabled="voice.step < 1" @click="passDeviceCheck">检测通过，开始面试</button>
            <button type="button" class="btn" @click="failDeviceCheck">检测未通过，改用文字面试</button>
          </div>
        </div>

        <template v-else>
          <div v-if="voice.captions" class="captions">
            <p v-for="(line, index) in voice.subtitles" :key="index">{{ line }}</p>
            <p v-if="!voice.subtitles.length" class="muted">面试官正在准备题目…</p>
          </div>
          <div class="row">
            <button type="button" class="btn btn-sm" @click="voice.captions = !voice.captions">
              {{ voice.captions ? '关闭字幕' : '打开字幕' }}
            </button>
            <button type="button" class="btn btn-sm" @click="endVoice">结束面试</button>
            <button type="button" class="btn btn-sm btn-primary" @click="goReport">查看报告</button>
          </div>
          <div class="dev-row">
            <input v-model="voice.draft" class="input" placeholder="语音服务待接入，输入文字模拟回答…" @keydown.enter="sendVoiceDraft" />
            <button type="button" class="btn btn-sm btn-primary" :disabled="!voice.draft.trim()" @click="sendVoiceDraft">我答完了</button>
          </div>
        </template>
      </div>
      <p v-if="ended" class="alert alert-info">本场面试已结束，报告可在「面试记录」中查看。</p>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; }
.setup-grid { grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); align-items: start; }
.setup-main { display: flex; flex-direction: column; gap: var(--space-lg); }
.fs { display: flex; flex-direction: column; gap: var(--space-sm); }
.fs h2 { font-size: var(--text-lg); }
.fs > .select-wrap { max-width: 320px; }
.summary { display: flex; flex-direction: column; gap: var(--space-sm); }
.summary dl { display: flex; flex-direction: column; }
.summary dl div { display: flex; justify-content: space-between; padding: var(--space-2xs) 0; border-bottom: var(--rule-hair) solid var(--color-rule); }
.summary dt { color: var(--color-muted); font-size: var(--text-sm); }
.full { width: 100%; justify-content: center; }
.seg { display: inline-flex; gap: var(--space-2xs); }
.seg-item { padding: var(--space-2xs) var(--space-sm); border: var(--rule-hair) solid var(--color-rule-2); border-radius: var(--radius-input); background: var(--color-cotton); font: inherit; font-size: var(--text-sm); cursor: pointer; }
.seg-item.active { border-color: var(--color-accent-2); background: var(--color-paper-2); color: var(--color-accent-2); }
.stage-list { display: flex; flex-direction: column; gap: var(--space-2xs); }
.stage-check { display: flex; align-items: center; gap: var(--space-xs); padding: var(--space-2xs) var(--space-sm); border: var(--rule-hair) solid var(--color-rule); border-radius: var(--radius-input); }
.stage-check.disabled { opacity: 0.5; }
.stage-check .muted { margin-left: auto; }
.choice { display: flex; flex-direction: column; gap: var(--space-2xs); padding: var(--space-md); border-radius: var(--radius-stock); background: var(--color-cotton); box-shadow: var(--edge-stock); text-align: left; cursor: pointer; }
.choice.sel { outline: 2px solid var(--color-accent-2); }
.choice h3 { font-size: var(--text-md); }
.choice p { color: var(--color-muted); font-size: var(--text-sm); }
/* 会话区与输入区共用一张卡片，靠内部分隔线分区，不再各起一个框 */
.chat-shell { display: flex; flex-direction: column; padding: 0; overflow: hidden; }
.chat-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-sm); padding: var(--space-md) var(--space-lg); border-bottom: var(--rule-hair) solid var(--color-rule); }
.chat-head h2 { font-size: var(--text-lg); }
.timer { font-size: var(--text-md); }
.chat { display: flex; flex-direction: column; gap: var(--space-sm); flex: 1 1 auto; min-height: 300px; max-height: 52vh; overflow-y: auto; padding: var(--space-md) var(--space-lg); }
.bubble { max-width: 78%; padding: var(--space-sm) var(--space-md); border-radius: var(--radius-stock); box-shadow: var(--edge-stock); background: var(--color-paper-2); }
.bubble.student { align-self: flex-end; background: var(--color-accent-wash); }
.bubble .who { display: block; color: var(--color-muted); }
.bubble p { white-space: pre-wrap; line-height: 1.7; }
.caret { display: inline-block; width: 2px; height: 1em; margin-left: 2px; vertical-align: -0.16em; background: var(--color-ink-2); animation: iv-caret 900ms steps(1) infinite; }
@keyframes iv-caret { 50% { opacity: 0; } }
.composer {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
  padding: var(--space-sm) var(--space-lg) var(--space-xs);
  border-top: var(--rule-hair) solid var(--color-rule);
  background: transparent;
}
.composer:focus-within { border-top-color: var(--color-ink-2); }
.composer-input {
  width: 100%;
  min-height: 44px;
  padding: 0 var(--space-2xs);
  border: 0;
  background: none;
  color: var(--color-ink);
  font: inherit;
  font-size: var(--text-md);
  line-height: 1.45;
  resize: none;
}
.composer-input::placeholder { color: var(--color-muted); opacity: 0.75; }
.composer-input:focus { outline: none; }
.composer-input:disabled { opacity: 0.65; }
.composer-bar { display: flex; align-items: center; gap: var(--space-xs); }
.composer-left { position: relative; }
.composer-plus {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: none;
  color: var(--color-ink);
  font: inherit;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}
.composer-plus:hover:not(:disabled) { background: var(--color-paper-2); }
.composer-plus:disabled { opacity: 0.4; cursor: default; }
.quick-menu {
  position: absolute;
  bottom: calc(100% + var(--space-xs));
  left: 0;
  z-index: var(--z-sticky);
  display: flex;
  flex-direction: column;
  min-width: 260px;
  padding: var(--space-2xs);
  border: var(--rule-hair) solid var(--color-rule);
  border-radius: var(--radius-card);
  background: var(--color-cotton);
  box-shadow: var(--shadow-pop);
}
.quick-menu button {
  padding: var(--space-xs) var(--space-sm);
  border: 0;
  border-radius: var(--radius-input);
  background: none;
  color: var(--color-ink-2);
  font: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}
.quick-menu button:hover { background: var(--color-paper-2); }
.composer-label { margin-left: auto; color: var(--color-ink-2); font-size: var(--text-sm); white-space: nowrap; }
.composer-right { display: flex; align-items: center; gap: var(--space-2xs); margin-left: var(--space-2xs); }
.composer-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: none;
  color: var(--color-ink);
  cursor: pointer;
}
.composer-icon:hover { background: var(--color-paper-2); }
.composer-icon.active { background: var(--color-accent-wash); color: var(--color-accent-2); }
.composer-icon svg, .composer-send svg { width: 18px; height: 18px; }
.composer-send {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--color-board);
  color: var(--color-board-type);
  box-shadow: var(--cast-stock);
  cursor: pointer;
  transition: box-shadow var(--dur-short), transform var(--dur-short);
}
.composer-send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: var(--cast-stock-lift); }
.composer-send:active:not(:disabled) { box-shadow: var(--press-groove); }
.composer-send:disabled { background: var(--color-paper-3); color: var(--color-muted); box-shadow: none; cursor: default; }
.composer-dev { padding-top: var(--space-xs); border-top: var(--rule-hair) solid var(--color-rule); }
.dev-row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-xs); }
.dev-row .input { flex: 1; min-width: 180px; }
.btn.active { border-color: var(--color-accent-2); color: var(--color-accent-2); }
.voice-card { display: flex; flex-direction: column; gap: var(--space-md); }
.device { display: flex; flex-direction: column; gap: var(--space-sm); }
.device-steps { padding-left: var(--space-lg); }
.device-steps li.done { color: var(--color-accent-2); font-weight: 600; }
.captions { min-height: 220px; max-height: 44vh; overflow-y: auto; padding: var(--space-md); border-radius: var(--radius-stock); background: var(--color-paper-2); }
.captions p { padding: var(--space-2xs) 0; }
@media (max-width: 60rem) { .setup-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 40rem) { .composer-label { display: none; } }
.chat .bubble { animation: iv-pop 260ms cubic-bezier(0.16, 1, 0.3, 1) both; }
@keyframes iv-pop { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
.typing-hint { align-self: flex-start; padding: var(--space-xs) var(--space-md); border-radius: var(--radius-stock); background: var(--color-paper-2); }
.dots { display: inline-flex; align-items: center; gap: 4px; }
.dots i { width: 6px; height: 6px; border-radius: 50%; background: var(--color-muted); animation: iv-blink 1.2s infinite; }
.dots i:nth-child(2) { animation-delay: 0.2s; }
.dots i:nth-child(3) { animation-delay: 0.4s; }
@keyframes iv-blink { 0%, 60%, 100% { opacity: 0.25; } 30% { opacity: 1; } }
.skip { align-self: flex-end; margin-top: var(--space-2xs); }
</style>
