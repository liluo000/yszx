<script setup lang="ts">
// 按课程顺序练习（需求 5.4.2，S-PRC-01~03）：逐题作答、立即判分、中途可续答
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { AnswerResult, PracticeSessionView, QuestionForAnswer, SubmitResult } from '@yunshu/api'
import { errorText, practiceApi } from '@/api'

const route = useRoute()

const mode = computed(() => String(route.params.mode || ''))
const courseId = computed(() => Number(route.params.key) || 0)

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

const progressText = computed(() => {
  const s = session.value
  if (!s) return ''
  return `第 ${Math.min(s.answered_count + 1, s.question_count)} / ${s.question_count} 题`
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
  if (mode.value !== 'sequential' || !courseId.value) {
    error.value = '暂不支持该练习方式'
    return
  }
  loading.value = true
  error.value = ''
  result.value = null
  needSelfAssessment.value = false
  pendingNext.value = null
  resetInput()
  try {
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
</script>

<template>
  <div class="practice">
    <header class="page-head">
      <div>
        <h1>顺序练习</h1>
        <p>按课程题目顺序作答，每题提交后立即查看对错与解析。</p>
      </div>
      <button v-if="session" type="button" class="btn btn-sm" :disabled="loading" @click="load(true)">
        重新练习
      </button>
    </header>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>

    <div v-if="loading" class="skeleton">题目加载中…</div>

    <template v-else-if="session">
      <div class="progress card">
        <span class="muted">{{ progressText }}</span>
        <div class="bar"><div :style="{ width: `${(session.answered_count / Math.max(1, session.question_count)) * 100}%` }" /></div>
        <span class="muted fig">已答 {{ session.answered_count }} · 答对 {{ session.correct_count }}</span>
      </div>

      <div v-if="finished && !result" class="result card">
        <h2>本轮已完成</h2>
        <p class="fig score">{{ session.correct_count }} / {{ session.question_count }}</p>
        <p class="muted">答错的题目已自动收进错题本，可在错题本里查看与重做。</p>
        <div class="actions">
          <RouterLink to="/wrong" class="btn btn-primary">查看错题本</RouterLink>
          <button type="button" class="btn" @click="load(true)">再练一轮</button>
        </div>
      </div>

      <template v-else-if="question">
        <article class="question card">
          <div class="meta">
            <span class="tag">{{ question.question_type === 'short_answer' ? '简答题' : '客观题' }}</span>
            <span class="muted">当日剩余 AI 评分 {{ session.remaining_scoring_quota }} 次</span>
          </div>
          <h2 class="stem">{{ question.stem }}</h2>

          <div v-if="question.question_type === 'true_false'" class="tf">
            <button type="button" class="btn" :class="{ picked: trueFalse === true }" @click="trueFalse = true">正确</button>
            <button type="button" class="btn" :class="{ picked: trueFalse === false }" @click="trueFalse = false">错误</button>
          </div>

          <div v-else-if="question.options && question.options.length" class="options">
            <label
v-for="option in question.options" :key="option.key" class="option"
                   :class="{ picked: selectedOptions.includes(option.key) }">
              <input
                :type="question.question_type === 'multiple_choice' ? 'checkbox' : 'radio'"
                :checked="selectedOptions.includes(option.key)"
                @change="toggleOption(option.key, question.question_type === 'multiple_choice')"
              />
              <span class="fig key">{{ option.key }}</span>
              <span>{{ option.text }}</span>
            </label>
          </div>

          <textarea
v-else v-model="shortText" class="input textarea" rows="5" maxlength="5000"
                    placeholder="请输入你的答案，提交后对照参考答案自评" />

          <div v-if="needSelfAssessment" class="self-assessment">
            <p class="muted">AI 评分暂未开放，请对照参考答案自评：</p>
            <p class="reference">{{ referenceAnswer || '暂无参考答案' }}</p>
            <div class="actions">
              <button type="button" class="btn btn-primary btn-sm" @click="submit('can')">会</button>
              <button type="button" class="btn btn-sm" @click="submit('cannot')">不会</button>
            </div>
          </div>

          <div v-else-if="result" class="answer-result">
            <p :class="['verdict', result.is_correct ? 'ok' : 'bad']">
              {{ result.is_correct ? '回答正确' : '回答错误' }}
              <span class="muted">（{{ result.judge_method === 'self' ? '自评' : '自动判分' }}）</span>
            </p>
            <p v-if="result.correct_answer">正确答案：{{ answerLabel(result.correct_answer) }}</p>
            <p v-if="result.reference_answer">参考答案：{{ result.reference_answer }}</p>
            <p v-if="result.analysis" class="muted">解析：{{ result.analysis }}</p>
            <div class="actions">
              <button v-if="question" type="button" class="btn btn-primary" @click="nextQuestion">
                {{ finished ? '查看结果' : '下一题' }}
              </button>
              <RouterLink v-if="finished" to="/wrong" class="btn">查看错题本</RouterLink>
            </div>
          </div>

          <div v-else class="actions">
            <button type="button" class="btn btn-primary" :disabled="!canSubmit" @click="submit()">
              {{ submitting ? '提交中…' : '提交答案' }}
            </button>
            <RouterLink to="/routes" class="btn btn-ghost">退出练习</RouterLink>
          </div>
        </article>
      </template>
    </template>
  </div>
</template>

<style scoped>
.practice {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.progress {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--text-sm);
}
.bar {
  flex: 1;
  min-width: 8rem;
  height: 0.5rem;
  border-radius: var(--radius-pill);
  background: var(--color-paper-2);
  overflow: hidden;
}
.bar div {
  height: 100%;
  background: var(--color-accent-2);
  transition: width var(--dur-short);
}
.question {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  font-size: var(--text-xs);
  color: var(--color-muted);
}
.tag {
  padding: 0 var(--space-2xs);
  border-radius: var(--radius-pill);
  background: var(--color-paper-2);
}
.stem {
  font-size: var(--text-lg);
  line-height: 1.7;
}
.options {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}
.option {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm);
  border: var(--rule-hair) solid var(--color-rule-2);
  border-radius: var(--radius-input);
  cursor: pointer;
  font-size: var(--text-sm);
}
.option.picked,
.tf .picked {
  border-color: var(--color-accent-2);
  background: var(--color-paper-2);
}
.option input {
  accent-color: var(--color-accent-2);
}
.key {
  color: var(--color-muted);
}
.tf {
  display: flex;
  gap: var(--space-sm);
}
.textarea {
  padding: var(--space-sm);
  min-height: 8rem;
  resize: vertical;
}
.answer-result,
.self-assessment {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
  padding: var(--space-md);
  border-radius: var(--radius-card);
  background: var(--color-paper-2);
  font-size: var(--text-sm);
}
.verdict {
  font-size: var(--text-md);
  font-weight: 600;
}
.ok { color: var(--color-accent-2); }
.bad { color: var(--color-error); }
.reference {
  white-space: pre-wrap;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
  margin-top: var(--space-sm);
}
.result {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-2xl) var(--space-lg);
  text-align: center;
}
.score {
  font-size: var(--text-2xl);
  color: var(--color-accent-2);
}
.alert-error {
  padding: var(--space-2xs) var(--space-sm);
  border-radius: var(--radius-input);
  background: var(--color-error-wash);
  color: var(--color-error);
  font-size: var(--text-sm);
}
</style>
