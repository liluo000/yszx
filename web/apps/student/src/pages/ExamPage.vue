<script setup lang="ts">
// 模拟考试（需求 5.4.4，S-EXM-01~08）：开考、答题卡、倒计时、逐题保存、交卷、成绩与重评分
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ExamQuestionView, ExamView } from '@yunshu/api'
import { errorText, examApi, learningApi } from '@/api'

const route = useRoute()
const router = useRouter()
const stageId = computed(() => Number(route.params.sid) || 0)
const routeId = computed(() => Number(route.query.route) || 0)
const backPath = computed(() => (routeId.value ? `/route/${routeId.value}` : '/routes'))

const exam = ref<ExamView | null>(null)
const claim = ref('')
const currentSeq = ref(1)
const answers = reactive<Record<number, unknown>>({})
const selected = ref<string[]>([])
const trueFalse = ref<boolean | null>(null)
const shortText = ref('')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const intro = ref(false)
const now = ref(Date.now())
const serverOffset = ref(0)
const confirmSubmit = ref(false)
const confirmAbandon = ref(false)
const feedbackFor = ref<ExamQuestionView | null>(null)
const feedbackType = ref('other')
const feedbackText = ref('')
const feedbackDone = ref(false)
let tickTimer: number | undefined
let saveTimer: number | undefined
let autoSubmitted = false

const TYPE_LABEL: Record<string, string> = {
  true_false: '判断题', single_choice: '单选题', multiple_choice: '多选题', short_answer: '简答题',
}
const TF = [
  { key: '对', text: '正确', value: true },
  { key: '错', text: '错误', value: false },
]
const ERROR_TYPES = [
  { value: 'stem_error', label: '题干有误' },
  { value: 'answer_error', label: '答案有误' },
  { value: 'analysis_error', label: '解析有误' },
  { value: 'other', label: '其他' },
]

const current = computed(() => exam.value?.questions.find((one) => one.seq_no === currentSeq.value) ?? null)
const remaining = computed(() => {
  if (!exam.value) return 0
  const deadline = new Date(exam.value.deadline_at).getTime()
  return Math.max(0, Math.ceil((deadline - (now.value + serverOffset.value)) / 1000))
})
const timeText = computed(() => {
  const s = remaining.value
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})
const answeredCount = computed(() =>
  exam.value?.questions.filter((one) => hasAnswer(answers[one.seq_no])).length ?? 0,
)

function hasAnswer(value: unknown): boolean {
  if (value == null) return false
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'string') return value.trim().length > 0
  return true
}

function applyView(view: ExamView) {
  exam.value = view
  intro.value = false
  if (view.status !== 'in_progress') sessionStorage.setItem(LAST_KEY, String(view.id))
  serverOffset.value = new Date(view.server_time).getTime() - Date.now()
  Object.keys(answers).forEach((key) => delete answers[Number(key)])
  view.questions.forEach((one) => {
    if (one.student_answer != null) answers[one.seq_no] = one.student_answer
  })
  currentSeq.value = view.questions.find((one) => !hasAnswer(answers[one.seq_no]))?.seq_no ?? 1
  loadInput()
  if (view.status === 'in_progress') startTimer()
  else stopTimer()
}

function loadInput() {
  const q = current.value
  const value = answers[currentSeq.value]
  selected.value = []
  trueFalse.value = null
  shortText.value = ''
  if (!q) return
  if (q.question.question_type === 'short_answer') {
    shortText.value = typeof value === 'string' ? value : ''
  } else if (q.question.question_type === 'true_false') {
    trueFalse.value = typeof value === 'boolean' ? value : null
  } else if (Array.isArray(value)) {
    selected.value = [...(value as string[])]
  }
}

function captureInput(q: ExamQuestionView): unknown {
  if (q.question.question_type === 'short_answer') return shortText.value.trim() || null
  if (q.question.question_type === 'true_false') return trueFalse.value
  return selected.value.length ? [...selected.value] : null
}

function claimKey(id: number) {
  return `yunshu:exam:claim:${id}`
}

const LAST_KEY = 'yunshu:exam:last'

async function claimDevice(force = false) {
  if (!exam.value || exam.value.status !== 'in_progress') return
  try {
    const data = await examApi.claimDevice(exam.value.id)
    claim.value = data.device_claim
    sessionStorage.setItem(claimKey(exam.value.id), data.device_claim)
    error.value = ''
  } catch (e) {
    if (force) error.value = errorText(e, '接管考试失败')
  }
}

function startTimer() {
  stopTimer()
  autoSubmitted = false
  tickTimer = window.setInterval(() => {
    now.value = Date.now()
    if (remaining.value <= 0 && !autoSubmitted && exam.value?.status === 'in_progress') {
      autoSubmitted = true
      submitExam(true)
    }
  }, 1000)
}

function stopTimer() {
  if (tickTimer) window.clearInterval(tickTimer)
  tickTimer = undefined
}

async function saveCurrent() {
  if (!exam.value || exam.value.status !== 'in_progress' || !claim.value || !current.value) return
  const q = current.value
  const value = captureInput(q)
  answers[q.seq_no] = value
  try {
    await examApi.saveAnswer(exam.value.id, q.seq_no, value, claim.value)
  } catch (e) {
    handleError(e)
  }
}

function scheduleSave() {
  if (saveTimer) window.clearTimeout(saveTimer)
  const q = current.value
  if (q) answers[q.seq_no] = captureInput(q)
  saveTimer = window.setTimeout(() => saveCurrent(), 500)
}

function handleError(e: unknown) {
  const code = e && typeof e === 'object' && 'code' in e ? String((e as { code?: unknown }).code) : ''
  if (code === 'DEVICE_TAKEN_OVER') {
    error.value = '已在其他设备继续考试。可在当前设备重新接管后继续作答。'
  } else if (code === 'SESSION_EXPIRED') {
    error.value = '考试已到截止时间，正在交卷…'
    submitExam(true)
  } else {
    error.value = errorText(e, '操作失败，请稍后重试')
  }
}

function resetToIntro() {
  stopTimer()
  sessionStorage.removeItem(LAST_KEY)
  exam.value = null
  intro.value = true
  error.value = ''
}

async function startExam() {
  if (!routeId.value || !stageId.value) {
    error.value = '缺少路线或阶段信息，请从路线详情开考'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const view = await examApi.start({ learning_route_id: routeId.value, route_stage_id: stageId.value })
    applyView(view)
    await claimDevice()
  } catch (e) {
    error.value = errorText(e, '开考失败，请稍后重试')
  } finally {
    busy.value = false
  }
}

async function submitExam(auto = false) {
  if (!exam.value || !claim.value || busy.value) return
  confirmSubmit.value = false
  busy.value = true
  try {
    await saveCurrent()
    const view = await examApi.submit(exam.value.id, claim.value)
    applyView(view)
    if (auto) error.value = '考试时间到，已按已保存答案自动交卷'
  } catch (e) {
    // 设备凭证失效时仍能交卷：尝试重新接管一次
    const code = e && typeof e === 'object' && 'code' in e ? String((e as { code?: unknown }).code) : ''
    if (code === 'DEVICE_TAKEN_OVER' && exam.value) {
      await claimDevice()
      try {
        const view = await examApi.submit(exam.value.id, claim.value)
        applyView(view)
      } catch (inner) {
        handleError(inner)
      }
    } else {
      handleError(e)
    }
  } finally {
    busy.value = false
  }
}

async function abandonExam() {
  if (!exam.value || !claim.value) return
  confirmAbandon.value = false
  busy.value = true
  try {
    await examApi.abandon(exam.value.id, claim.value)
    sessionStorage.removeItem(claimKey(exam.value.id))
    stopTimer()
    await router.push(backPath.value)
  } catch (e) {
    handleError(e)
  } finally {
    busy.value = false
  }
}

async function rescore() {
  if (!exam.value) return
  busy.value = true
  try {
    applyView(await examApi.rescore(exam.value.id))
  } catch (e) {
    error.value = errorText(e, '评分服务暂时不可用，请稍后重试')
  } finally {
    busy.value = false
  }
}

async function goTo(seq: number) {
  if (seq === currentSeq.value) return
  await saveCurrent()
  currentSeq.value = seq
  loadInput()
}

function toggleOption(key: string, multiple: boolean) {
  if (multiple) {
    selected.value = selected.value.includes(key)
      ? selected.value.filter((one) => one !== key)
      : [...selected.value, key]
  } else {
    selected.value = [key]
  }
  scheduleSave()
}

function pickTrueFalse(value: boolean) {
  trueFalse.value = value
  scheduleSave()
}

async function submitFeedback() {
  if (!feedbackFor.value || !feedbackText.value.trim()) return
  busy.value = true
  try {
    await learningApi.submitFeedback({
      feedback_type: 'question_correction',
      practice_question_id: feedbackFor.value.question.id,
      course_id: feedbackFor.value.question.course_id,
      error_type: feedbackType.value,
      description: feedbackText.value.trim(),
    })
    feedbackDone.value = true
  } catch (e) {
    error.value = errorText(e, '纠错提交失败')
  } finally {
    busy.value = false
  }
}

function answerLabel(value: unknown): string {
  if (value == null) return '未作答'
  const list = Array.isArray(value) ? value : [value]
  return list.map((one) => (typeof one === 'boolean' ? (one ? '正确' : '错误') : String(one))).join('、')
}

function timeLabel(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(async () => {
  loading.value = true
  try {
    const data = await examApi.inProgress()
    if (data.exam) {
      applyView(data.exam)
      claim.value = sessionStorage.getItem(claimKey(data.exam.id)) ?? ''
      await claimDevice()
    } else {
      // 没有进行中的考试时，先展示最近一场成绩，方便刷新后回看
      const lastId = Number(sessionStorage.getItem(LAST_KEY))
      if (lastId > 0) {
        try {
          applyView(await examApi.view(lastId))
        } catch {
          sessionStorage.removeItem(LAST_KEY)
          intro.value = true
        }
      } else {
        intro.value = true
      }
    }
  } catch (e) {
    error.value = errorText(e, '考试信息加载失败')
    intro.value = true
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  stopTimer()
  if (saveTimer) window.clearTimeout(saveTimer)
})
</script>

<template>
  <div class="exam">
    <RouterLink :to="backPath" class="back">返回路线，倒计时继续</RouterLink>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="skeleton">考试信息加载中…</div>

    <!-- 开考前说明 -->
    <template v-else-if="intro">
      <header class="page-head">
        <div>
          <h1>模拟考试</h1>
          <p>按组卷规则自动出题，限时作答；交卷后统一判分并展示逐题解析。</p>
        </div>
      </header>
      <section class="card intro-card">
        <h2>开考须知</h2>
        <ul>
          <li>试卷由判断题、单选题、多选题与简答题组成，题量不足时按实际题量组卷并换算为百分制。</li>
          <li>作答过程中不展示对错；关闭页面后倒计时继续，到时自动交卷。</li>
          <li>试卷含简答题时，开考会预留 1 次当日 AI 评分次数，交卷评分成功后才正式扣减；放弃考试会释放。</li>
          <li>同一时间只能有一场进行中的考试；在新设备打开会由新设备接管。</li>
        </ul>
        <div class="row wrap mt-16">
          <button type="button" class="btn btn-primary" :disabled="busy" @click="startExam">开始考试</button>
          <RouterLink :to="backPath" class="btn">暂不开考</RouterLink>
        </div>
      </section>
    </template>

    <!-- 作答中 -->
    <template v-else-if="exam && exam.status === 'in_progress' && current">
      <header class="quiz-top">
        <div>
          <h1>模拟考试</h1>
          <p>{{ exam.route_name }} · {{ exam.stage_name }}</p>
        </div>
        <div class="quiz-side">
          <span class="muted small">已答 <span class="fig">{{ answeredCount }}</span> / {{ exam.questions.length }}</span>
          <span class="timer" :class="{ warn: remaining < 300 }" role="timer">{{ timeText }}</span>
        </div>
      </header>

      <div class="grid exam-grid">
        <article class="card question-card">
          <div class="row-between wrap">
            <div class="meta">
              <span class="tag">{{ TYPE_LABEL[current.question.question_type] ?? current.question.question_type }}</span>
              <span class="fig">{{ current.question_score }} 分</span>
            </div>
            <span class="small muted">第 <span class="fig">{{ currentSeq }}/{{ exam.questions.length }}</span> 题</span>
          </div>
          <div class="q-stem">{{ current.question.stem }}</div>

          <div v-if="current.question.question_type === 'true_false'" class="opts">
            <button
              v-for="item in TF" :key="item.key" type="button" class="opt"
              :class="{ sel: trueFalse === item.value }" @click="pickTrueFalse(item.value)"
            >
              <span class="key" aria-hidden="true">{{ item.key }}</span><span>{{ item.text }}</span>
            </button>
          </div>
          <div v-else-if="current.question.options?.length" class="opts">
            <button
              v-for="option in current.question.options" :key="option.key" type="button" class="opt"
              :class="[{ sel: selected.includes(option.key) }, { multi: current.question.question_type === 'multiple_choice' }]"
              @click="toggleOption(option.key, current.question.question_type === 'multiple_choice')"
            >
              <span class="key" aria-hidden="true">{{ option.key }}</span><span>{{ option.text }}</span>
            </button>
          </div>
          <textarea
            v-else v-model="shortText" class="textarea" rows="6" maxlength="5000"
            placeholder="写下你的答案，交卷后统一评分" @input="scheduleSave"
          />

          <div class="quiz-foot">
            <button type="button" class="btn btn-sm" :disabled="currentSeq <= 1" @click="goTo(currentSeq - 1)">上一题</button>
            <div class="row">
              <button
                v-if="currentSeq < exam.questions.length" type="button" class="btn btn-sm btn-primary"
                @click="goTo(currentSeq + 1)"
              >
                下一题
              </button>
              <button type="button" class="btn btn-sm btn-primary" @click="confirmSubmit = true">交卷</button>
            </div>
          </div>
        </article>

        <aside class="card exam-side" aria-label="答题卡">
          <div class="row-between"><span class="muted">剩余时间</span><b class="timer" :class="{ warn: remaining < 300 }">{{ timeText }}</b></div>
          <p v-if="exam.has_short_answer" class="small muted mt-8">已预留 1 次 AI 评分次数，交卷评分成功后扣减。</p>
          <h4 class="mt-16 mb-8">答题卡</h4>
          <div class="qnav">
            <button
              v-for="one in exam.questions" :key="one.seq_no" type="button"
              :class="{ done: hasAnswer(answers[one.seq_no]), cur: one.seq_no === currentSeq }"
              :aria-label="`第 ${one.seq_no} 题`" @click="goTo(one.seq_no)"
            >
              {{ one.seq_no }}
            </button>
          </div>
          <button type="button" class="btn btn-primary btn-block mt-16" :disabled="busy" @click="confirmSubmit = true">交卷</button>
          <button type="button" class="btn btn-ghost btn-block mt-8" :disabled="busy" @click="confirmAbandon = true">放弃考试</button>
          <button
            v-if="error.includes('其他设备')" type="button" class="btn btn-block mt-8" @click="claimDevice(true)"
          >
            在当前设备继续
          </button>
        </aside>
      </div>
    </template>

    <!-- 成绩 / 评分中 -->
    <template v-else-if="exam">
      <header class="page-head">
        <div>
          <h1>{{ exam.status === 'scoring_pending' ? '简答题评分中' : '考试完成' }}</h1>
          <p>{{ exam.route_name }} · {{ exam.stage_name }} · {{ timeLabel(exam.started_at) }}</p>
        </div>
      </header>
      <section v-if="exam.status === 'scoring_pending'" class="card">
        <p>客观题已判分，简答题评分暂时不可用（不扣次数）。可点击重新评分继续。</p>
        <button type="button" class="btn btn-primary mt-16" :disabled="busy" @click="rescore">重新评分</button>
      </section>
      <template v-else-if="exam.status === 'graded'">
        <section class="card result-card">
          <div class="score-row">
            <div class="big-score">{{ exam.score }}<small>分</small></div>
            <div>
              <p class="muted">百分制总分</p>
              <p class="fig">答对 {{ exam.questions.filter((one) => one.is_correct).length }} / {{ exam.questions.length }} 题</p>
            </div>
          </div>
          <div class="type-scores">
            <div v-for="item in exam.type_scores ?? []" :key="item.question_type" class="type-score">
              <span class="muted small">{{ TYPE_LABEL[item.question_type] ?? item.question_type }}</span>
              <b class="fig">{{ item.earned_score }} / {{ item.question_score }}</b>
            </div>
          </div>
        </section>

        <section class="card result-list">
          <article v-for="one in exam.questions" :key="one.seq_no" class="result-item">
            <div class="row-between wrap">
              <div class="row wrap">
                <span class="fig small muted">{{ String(one.seq_no).padStart(2, '0') }}</span>
                <span class="tag" :class="one.is_correct ? 'tag-green' : 'tag-red'">{{ one.is_correct ? '答对' : '答错' }}</span>
                <span class="small muted">{{ TYPE_LABEL[one.question.question_type] }}</span>
              </div>
              <b class="fig">{{ one.earned_score ?? 0 }} / {{ one.question_score }} 分</b>
            </div>
            <p class="stem">{{ one.question.stem }}</p>
            <dl class="result-dl">
              <template v-if="one.question.question_type !== 'short_answer'">
                <dt>正确答案</dt><dd>{{ answerLabel(one.correct_answer) }}</dd>
              </template>
              <dt>你的答案</dt><dd>{{ answerLabel(one.student_answer) }}</dd>
              <template v-if="one.reference_answer"><dt>参考答案</dt><dd>{{ one.reference_answer }}</dd></template>
              <template v-if="one.ai_score != null"><dt>AI 评分</dt><dd>{{ one.ai_score }} 分 · {{ one.ai_comment }}</dd></template>
              <template v-if="one.analysis"><dt>解析</dt><dd>{{ one.analysis }}</dd></template>
            </dl>
            <button type="button" class="btn-link small" @click="feedbackFor = one; feedbackDone = false; feedbackText = ''">题目纠错</button>
          </article>
        </section>
      </template>
      <div class="row wrap mt-16">
        <RouterLink :to="backPath" class="btn btn-primary">返回路线</RouterLink>
        <RouterLink to="/wrong" class="btn">查看错题本</RouterLink>
        <button type="button" class="btn" @click="resetToIntro">再考一场</button>
      </div>
    </template>

    <!-- 确认弹层 -->
    <div v-if="confirmSubmit || confirmAbandon" class="mask" @click.self="confirmSubmit = false; confirmAbandon = false">
      <div class="dialog card">
        <header class="dialog-head">
          <h2>{{ confirmSubmit ? '确认交卷' : '放弃考试' }}</h2>
          <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="confirmSubmit = false; confirmAbandon = false">✕</button>
        </header>
        <p class="muted">
          {{ confirmSubmit
            ? `还有 ${exam ? exam.questions.length - answeredCount : 0} 道题未作答，交卷后未作答的题计 0 分。`
            : '放弃后本场考试不出成绩，预留的 AI 评分次数将释放。' }}
        </p>
        <footer class="dialog-foot">
          <button type="button" class="btn" @click="confirmSubmit = false; confirmAbandon = false">取消</button>
          <button v-if="confirmSubmit" type="button" class="btn btn-primary" :disabled="busy" @click="submitExam()">确认交卷</button>
          <button v-else type="button" class="btn btn-primary" :disabled="busy" @click="abandonExam">放弃考试</button>
        </footer>
      </div>
    </div>

    <!-- 题目纠错 -->
    <div v-if="feedbackFor" class="mask" @click.self="feedbackFor = null">
      <div class="dialog card">
        <header class="dialog-head">
          <h2>题目纠错</h2>
          <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="feedbackFor = null">✕</button>
        </header>
        <template v-if="feedbackDone">
          <p class="muted">已提交，运营处理后会在消息中心通知你。</p>
          <div class="dialog-foot"><button type="button" class="btn btn-primary" @click="feedbackFor = null">完成</button></div>
        </template>
        <template v-else>
          <p class="muted small">{{ feedbackFor.question.stem }}</p>
          <label class="field">
            <span class="field-label">错误类型</span>
            <span class="select-wrap">
              <select v-model="feedbackType" class="select">
                <option v-for="item in ERROR_TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </span>
          </label>
          <label class="field">
            <span class="field-label">补充说明</span>
            <textarea v-model="feedbackText" class="textarea" rows="3" maxlength="1000" placeholder="请描述你发现的问题" />
          </label>
          <div class="dialog-foot">
            <button type="button" class="btn" @click="feedbackFor = null">取消</button>
            <button type="button" class="btn btn-primary" :disabled="busy || !feedbackText.trim()" @click="submitFeedback">提交纠错</button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.exam {
  display: flex;
  flex-direction: column;
}
.back {
  margin-bottom: var(--space-sm);
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.quiz-top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}
.quiz-top h1 {
  font-size: var(--text-2xl);
}
.quiz-top p {
  margin-top: var(--space-2xs);
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.exam-grid {
  grid-template-columns: minmax(0, 1fr) 280px;
  align-items: start;
}
.exam-side {
  position: sticky;
  top: calc(var(--bar-h) + var(--space-md));
}
.timer {
  font-family: var(--font-outlier);
  font-variant-numeric: tabular-nums;
  font-size: var(--text-lg);
}
.timer.warn {
  color: var(--color-error);
}
.q-stem {
  margin: var(--space-sm) 0 var(--space-md);
  font-size: var(--text-lg);
  font-weight: 600;
  line-height: 1.55;
}
.opts {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}
.opt {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  width: 100%;
  padding: var(--space-sm) var(--space-md);
  border: 0;
  border-radius: var(--radius-card);
  background: var(--color-paper);
  box-shadow: inset 0 0 0 1px var(--color-rule);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.opt:hover {
  box-shadow: inset 0 0 0 1px var(--color-muted);
}
.opt.sel {
  background: var(--color-cotton);
  box-shadow: inset 0 0 0 2px var(--color-ink);
}
.opt .key {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-cotton);
  box-shadow: var(--edge-stock);
  font-size: var(--text-xs);
  font-weight: 600;
}
.opt.multi .key {
  border-radius: 5px;
}
.opt.sel .key {
  background: var(--color-board);
  color: var(--color-board-type);
  box-shadow: none;
}
.quiz-foot {
  display: flex;
  justify-content: space-between;
  gap: var(--space-xs);
  margin-top: var(--space-lg);
}
.qnav {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(36px, 1fr));
  gap: var(--space-xs);
}
.qnav button {
  height: 36px;
  border: 0;
  border-radius: var(--radius-input);
  background: var(--color-paper);
  box-shadow: inset 0 0 0 1px var(--color-rule);
  color: var(--color-ink);
  font-family: var(--font-outlier);
  font-size: var(--text-xs);
  cursor: pointer;
}
.qnav button.done {
  background: var(--color-paper-3);
  box-shadow: none;
}
.qnav button.cur {
  box-shadow: inset 0 0 0 2px var(--color-ink);
}
.intro-card ul {
  display: grid;
  gap: var(--space-xs);
  margin: var(--space-md) 0 0;
  padding-left: var(--space-lg);
  color: var(--color-ink-2);
  font-size: var(--text-sm);
}
.result-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}
.score-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-lg);
}
.big-score {
  font-family: var(--font-outlier);
  font-variant-numeric: tabular-nums;
  font-size: 64px;
  line-height: 1;
}
.big-score small {
  color: var(--color-muted);
  font-size: var(--text-md);
}
.type-scores {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-lg);
}
.type-score {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
}
.result-list {
  margin-top: var(--space-md);
  padding-block: 0;
}
.result-item {
  padding: var(--space-lg) 0;
  border-bottom: var(--rule-hair) solid var(--color-rule);
}
.result-item:last-child {
  border-bottom: 0;
}
.result-item .stem {
  margin: var(--space-sm) 0;
  font-size: var(--text-md);
  font-weight: 600;
}
.result-dl {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: var(--space-2xs) var(--space-sm);
  margin: 0 0 var(--space-sm);
  font-size: var(--text-sm);
}
.result-dl dt {
  color: var(--color-muted);
}
.result-dl dd {
  margin: 0;
  white-space: pre-wrap;
}
.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
  margin-bottom: var(--space-sm);
}
@media (max-width: 60rem) {
  .exam-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .exam-side {
    position: static;
  }
}
</style>
