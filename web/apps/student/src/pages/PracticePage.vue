<script setup lang="ts">
// 按课程顺序练习（需求 5.4.2，S-PRC-01~03）：逐题作答、立即判分、中途可续答
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { AnswerResult, PracticeSessionView, QuestionForAnswer, SubmitResult } from '@yunshu/api'
import { errorText, practiceApi } from '@/api'

const route = useRoute()

const mode = computed(() => {
  if (route.params.mode) return String(route.params.mode)
  // /practice/wrong_redo 是独立路由，没有 :mode 段
  return route.path.includes('wrong_redo') ? 'wrong_redo' : ''
})
const courseId = computed(() => Number(route.params.key) || 0)
/** random 模式：key 是阶段 ID，路线 ID 从 ?route= 取 */
const routeId = computed(() => Number(route.query.route) || 0)
/** 错题重做：错题记录 ID 来自 URL ?ids=1,2,3 */
const wrongRecordIds = computed(() => {
  if (mode.value !== 'wrong_redo') return []
  return String(route.query.ids ?? '')
    .split(',')
    .map((one) => Number(one.trim()))
    .filter((one) => Number.isInteger(one) && one > 0)
})

const pageTitle = computed(() => {
  if (mode.value === 'wrong_redo') return '错题重做'
  if (mode.value === 'random') return '随机抽题'
  return '顺序练习'
})
const pageDesc = computed(() => {
  if (mode.value === 'wrong_redo') return '重做选出的错题，答对后自动移出错题本。'
  if (mode.value === 'random') return '从本阶段题库随机抽取，逐题判分并立即查看解析。'
  return '按顺序作答，每题提交后立即显示结果。'
})
const backPath = computed(() => {
  if (mode.value === 'wrong_redo') return '/wrong'
  if (mode.value === 'random' && routeId.value) return `/route/${routeId.value}`
  return '/routes'
})

const session = ref<PracticeSessionView | null>(null)
const question = ref<QuestionForAnswer | null>(null)
const result = ref<AnswerResult | null>(null)
const referenceAnswer = ref<string | null>(null)
const needSelfAssessment = ref(false)
const pendingNext = ref<QuestionForAnswer | null>(null)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const finished = ref(false)

// 作答输入：判断题布尔、选择题选项数组、简答题文本
const selectedOptions = ref<string[]>([])
const trueFalse = ref<boolean | null>(null)
const shortText = ref('')

const TYPE_LABEL: Record<string, string> = {
  true_false: '判断题',
  single_choice: '单选题',
  multiple_choice: '多选题',
  short_answer: '简答题',
}
const TF_OPTIONS = [
  { key: '对', text: '正确', value: true },
  { key: '错', text: '错误', value: false },
]

const typeLabel = computed(() => (question.value ? TYPE_LABEL[question.value.question_type] ?? '题目' : ''))

const progressText = computed(() => {
  const s = session.value
  if (!s) return ''
  return `${Math.min(s.answered_count + 1, s.question_count)} / ${s.question_count}`
})

const progressRatio = computed(() => {
  const s = session.value
  if (!s || s.question_count <= 0) return 0
  return Math.min(1, s.answered_count / s.question_count).toFixed(3)
})

const finishNote = computed(() => {
  if (mode.value === 'wrong_redo') return '答对的题目已移出错题本，答错的会继续留在错题本里。'
  if (mode.value === 'random') return '随机抽题的成绩不计入「已掌握」判定；答错的题目仍会收进错题本。'
  return '答错的题目已自动收进错题本，可在错题本里查看与重做。'
})

const accuracy = computed(() => {
  const s = session.value
  if (!s || s.question_count <= 0) return 0
  return Math.round((s.correct_count / s.question_count) * 100)
})

function resetInput() {
  selectedOptions.value = []
  trueFalse.value = null
  shortText.value = ''
}

function applyView(view: PracticeSessionView) {
  session.value = view
  question.value = view.next_question
  finished.value = view.status === 'completed' || !view.next_question
}

function applySubmit(data: SubmitResult) {
  if (data.need_self_assessment) {
    needSelfAssessment.value = true
    referenceAnswer.value = data.reference_answer
    return
  }
  needSelfAssessment.value = false
  referenceAnswer.value = null
  result.value = data.result
  // 结果面板仍展示刚才那道题，点「下一题」后再切换
  pendingNext.value = data.next_question
  if (session.value) {
    session.value = {
      ...session.value,
      answered_count: session.value.answered_count + 1,
      correct_count: session.value.correct_count + (data.result?.is_correct ? 1 : 0),
      status: data.session_status as PracticeSessionView['status'],
    }
  }
  finished.value = data.session_status === 'completed' || !data.next_question
  resetInput()
}

async function load(restart = false) {
  loading.value = true
  error.value = ''
  result.value = null
  needSelfAssessment.value = false
  pendingNext.value = null
  resetInput()
  try {
    if (mode.value === 'wrong_redo') {
      if (!wrongRecordIds.value.length) {
        error.value = '请从错题本选择要重做的题目'
        return
      }
      applyView(await practiceApi.start({ practice_mode: 'wrong_redo', wrong_question_ids: wrongRecordIds.value }))
      return
    }
    if (mode.value === 'random') {
      if (!routeId.value || !courseId.value) {
        error.value = '缺少路线或阶段信息，请从路线详情发起随机抽题'
        return
      }
      applyView(
        await practiceApi.start({
          practice_mode: 'random',
          learning_route_id: routeId.value,
          route_stage_id: courseId.value,
        }),
      )
      return
    }
    if (mode.value !== 'sequential' || !courseId.value) {
      error.value = '暂不支持该练习方式'
      return
    }
    applyView(await practiceApi.start({ practice_mode: 'sequential', course_id: courseId.value, restart }))
  } catch (e) {
    error.value = errorText(e, '练习加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

function toggleOption(key: string, multiple: boolean) {
  if (multiple) {
    selectedOptions.value = selectedOptions.value.includes(key)
      ? selectedOptions.value.filter((one) => one !== key)
      : [...selectedOptions.value, key]
  } else {
    selectedOptions.value = [key]
  }
}

function buildAnswer(q: QuestionForAnswer): unknown {
  if (q.question_type === 'true_false') return trueFalse.value
  if (q.question_type === 'short_answer') return shortText.value.trim()
  return selectedOptions.value
}

const canSubmit = computed(() => {
  const q = question.value
  if (!q || result.value || needSelfAssessment.value || submitting.value) return false
  if (q.question_type === 'true_false') return trueFalse.value !== null
  if (q.question_type === 'short_answer') return shortText.value.trim().length > 0
  return selectedOptions.value.length > 0
})

async function submit(selfAssessment?: 'can' | 'cannot') {
  const q = question.value
  const s = session.value
  if (!q || !s) return
  submitting.value = true
  error.value = ''
  try {
    const data = await practiceApi.submit(s.id, {
      practice_question_id: q.id,
      student_answer: q.question_type === 'short_answer' ? shortText.value.trim() : buildAnswer(q),
      ...(selfAssessment ? { self_assessment: selfAssessment } : {}),
    })
    applySubmit(data)
  } catch (e) {
    error.value = errorText(e, '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function nextQuestion() {
  question.value = pendingNext.value
  pendingNext.value = null
  result.value = null
  referenceAnswer.value = null
  resetInput()
}

function answerLabel(value: unknown): string {
  if (value == null) return '—'
  const list = Array.isArray(value) ? value : [value]
  return list.map((one) => {
    if (typeof one === 'boolean') return one ? '正确' : '错误'
    return String(one)
  }).join('、')
}

/** 选项是否被选中（判断题的「对 / 错」映射到布尔答案） */
function chosen(key: string): boolean {
  const q = question.value
  if (!q) return false
  if (q.question_type === 'true_false') return (key === '对') === trueFalse.value
  return selectedOptions.value.includes(key)
}

/** 判分后正确答案的选项键；后端对判断题返回布尔值 */
function correctKeys(): string[] {
  const correct = result.value?.correct_answer
  if (correct == null) return []
  const list = Array.isArray(correct) ? correct : [correct]
  return list.map((one) => (typeof one === 'boolean' ? (one ? '对' : '错') : String(one)))
}

/** 选项状态：选中 sel、答对 right、答错 wrong、判分后 locked */
function optClass(key: string) {
  const on = chosen(key)
  if (!result.value) return { sel: on }
  const right = correctKeys().includes(key)
  return { sel: on, right, wrong: on && !right, locked: true }
}
</script>

<template>
  <div class="quiz-wrap">
    <RouterLink :to="backPath" class="back">返回</RouterLink>

    <header class="quiz-top">
      <div>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageDesc }}</p>
      </div>
      <div class="quiz-side">
        <span v-if="session" class="muted small">
          今日 AI 评分剩余 <span class="fig">{{ session.remaining_scoring_quota }}</span> 次
        </span>
        <span v-if="session && !finished" class="muted small">
          第 <span class="fig">{{ progressText }}</span> 题
        </span>
        <button
          v-if="session && mode === 'sequential'"
          type="button"
          class="btn btn-sm"
          :disabled="loading"
          @click="load(true)"
        >
          重新练习
        </button>
      </div>
    </header>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>

    <div v-if="loading" class="skeleton">题目加载中…</div>

    <template v-else-if="session">
      <div
        v-if="!finished"
        class="progress mb-16"
        role="progressbar"
        aria-label="答题进度"
        aria-valuemin="0"
        :aria-valuemax="session.question_count"
        :aria-valuenow="session.answered_count"
      >
        <span :style="{ '--p': progressRatio }" />
      </div>

      <!-- 本轮完成 -->
      <div v-if="finished && !result" class="card">
        <h1>练习完成</h1>
        <p class="muted mt-8">{{ pageTitle }} · 共 {{ session.question_count }} 题</p>
        <div class="score-row mt-24">
          <div class="big-score">{{ accuracy }}<small>%</small></div>
          <div class="text-2">
            答对
            <span class="fig">{{ session.correct_count }}/{{ session.question_count }}</span>
            题
          </div>
        </div>
        <div class="alert alert-info mt-24">{{ finishNote }}</div>
        <div class="row wrap mt-24">
          <RouterLink to="/routes" class="btn btn-primary">返回路线</RouterLink>
          <button type="button" class="btn" @click="load(true)">重新练习</button>
          <RouterLink to="/wrong" class="btn btn-ghost">查看错题本</RouterLink>
        </div>
      </div>

      <div v-else-if="question" class="card">
        <div class="meta">
          <span class="tag">{{ typeLabel }}</span>
          <span v-if="question.question_type === 'short_answer'">提交后对照参考答案自评</span>
          <span v-else>选好后提交，立即判分并给出解析</span>
        </div>

        <div class="q-stem">{{ question.stem }}</div>

        <div v-if="question.question_type === 'true_false'" class="opts">
          <button
            v-for="item in TF_OPTIONS"
            :key="item.key"
            type="button"
            class="opt"
            :class="optClass(item.key)"
            :disabled="!!result"
            @click="trueFalse = item.value"
          >
            <span class="key" aria-hidden="true">{{ item.key }}</span>
            <span>{{ item.text }}</span>
          </button>
        </div>

        <div v-else-if="question.options && question.options.length" class="opts">
          <button
            v-for="option in question.options"
            :key="option.key"
            type="button"
            class="opt"
            :class="[optClass(option.key), { multi: question.question_type === 'multiple_choice' }]"
            :disabled="!!result"
            @click="toggleOption(option.key, question.question_type === 'multiple_choice')"
          >
            <span class="key" aria-hidden="true">{{ option.key }}</span>
            <span>{{ option.text }}</span>
          </button>
        </div>

        <textarea
          v-else
          v-model="shortText"
          class="textarea"
          rows="5"
          maxlength="5000"
          placeholder="写下你的答案，提交后对照参考答案自评"
          :disabled="!!result"
        />

        <!-- 自评（AI 评分未接入时） -->
        <div v-if="needSelfAssessment" class="result">
          <div class="alert alert-warn">AI 评分暂未开放，请对照参考答案自评。</div>
          <dl>
            <dt>参考答案</dt>
            <dd>{{ referenceAnswer || '暂无参考答案' }}</dd>
          </dl>
          <div class="row wrap mt-12">
            <span class="text-2">你觉得自己答得怎么样？</span>
            <button type="button" class="btn btn-sm" :disabled="submitting" @click="submit('can')">会</button>
            <button type="button" class="btn btn-sm btn-danger" :disabled="submitting" @click="submit('cannot')">
              不会
            </button>
          </div>
        </div>

        <!-- 判分结果 -->
        <div v-else-if="result" class="result">
          <div class="verdict" :class="result.is_correct ? 'ok' : 'bad'">
            {{ result.is_correct ? '回答正确' : '回答错误' }}
            <span class="small muted">
              （{{ result.judge_method === 'self' ? '自评' : result.judge_method === 'ai' ? 'AI 评分' : '自动判分' }}）
            </span>
          </div>
          <dl>
            <template v-if="result.correct_answer != null">
              <dt>正确答案</dt>
              <dd><b>{{ answerLabel(result.correct_answer) }}</b></dd>
            </template>
            <template v-if="result.reference_answer">
              <dt>参考答案</dt>
              <dd>{{ result.reference_answer }}</dd>
            </template>
            <template v-if="result.analysis">
              <dt>解析</dt>
              <dd>{{ result.analysis }}</dd>
            </template>
          </dl>
        </div>

        <div class="quiz-foot">
          <RouterLink to="/routes" class="btn btn-ghost btn-sm">退出练习</RouterLink>
          <div class="row">
            <button
              v-if="!result && !needSelfAssessment"
              type="button"
              class="btn btn-primary"
              :disabled="!canSubmit"
              @click="submit()"
            >
              {{ submitting ? '提交中…' : '提交答案' }}
            </button>
            <button v-else-if="result" type="button" class="btn btn-primary" @click="nextQuestion">
              {{ finished ? '完成练习' : '下一题' }}
            </button>
            <RouterLink v-if="result && finished" to="/wrong" class="btn">查看错题本</RouterLink>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 刷题区固定 800px 居中，视线不来回扫（原型 quiz-wrap） */
.quiz-wrap {
  max-width: 800px;
  margin: 0 auto;
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
.quiz-side {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
}
.quiz-side .fig {
  color: var(--color-ink);
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
  transition: box-shadow var(--dur-micro) var(--ease-out), background-color var(--dur-micro) var(--ease-out);
}
.opt:hover {
  box-shadow: inset 0 0 0 1px var(--color-muted);
}
.opt.sel {
  background: var(--color-cotton);
  box-shadow: inset 0 0 0 2px var(--color-ink);
}
.opt.right {
  background: var(--color-success-wash);
  box-shadow: inset 0 0 0 2px var(--color-success);
}
.opt.wrong {
  background: var(--color-error-wash);
  box-shadow: inset 0 0 0 2px var(--color-error);
}
.opt.locked {
  cursor: default;
  pointer-events: none;
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
.opt.right .key {
  background: var(--color-success);
  color: var(--color-board-type);
  box-shadow: none;
}
.opt.wrong .key {
  background: var(--color-error);
  color: var(--color-board-type);
  box-shadow: none;
}

.result {
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: var(--rule-hair) solid var(--color-rule);
}
.verdict {
  font-size: var(--text-md);
  font-weight: 600;
}
.verdict.ok {
  color: var(--color-success);
}
.verdict.bad {
  color: var(--color-error);
}
.result dl {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: var(--space-2xs) var(--space-sm);
  margin: var(--space-sm) 0 0;
}
.result dt {
  color: var(--color-muted);
}
.result dd {
  margin: 0;
  white-space: pre-wrap;
}
.result dd b {
  font-weight: 600;
}

.quiz-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xs);
  margin-top: var(--space-lg);
}

/* 完成页 */
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

@media (max-width: 40rem) {
  .result dl {
    grid-template-columns: minmax(0, 1fr);
  }
  .big-score {
    font-size: 48px;
  }
}
</style>
