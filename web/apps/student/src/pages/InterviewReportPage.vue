<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { ReportView } from '@yunshu/api'
import { errorText, interviewApi } from '@/api'

const route = useRoute()
const sessionId = computed(() => Number(route.params.id) || 0)
const report = ref<ReportView | null>(null)
const loading = ref(true)
const error = ref('')
const busy = ref(false)
let pollTimer: number | undefined

const GRADE_LABEL: Record<string, string> = {
  excellent: '优秀', good: '良好', needs_improvement: '待提升',
}
const ANSWER_LABEL: Record<string, string> = {
  answered: '已作答', unanswered: '未作答', not_asked: '未问到',
}

function timeText(raw: string | null) {
  if (!raw) return ''
  const d = new Date(raw)
  return Number.isNaN(d.getTime()) ? raw : d.toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    report.value = await interviewApi.report(sessionId.value)
    if (report.value?.status === 'generating') {
      stopPolling()
      pollTimer = window.setTimeout(() => void load(), 3000)
    } else {
      stopPolling()
    }
  } catch (e) {
    error.value = errorText(e, '面试报告加载失败')
    stopPolling()
  } finally {
    loading.value = false
  }
}

function stopPolling() {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = undefined
}

async function regenerate() {
  busy.value = true
  error.value = ''
  try {
    report.value = await interviewApi.regenerate(sessionId.value)
  } catch (e) {
    error.value = errorText(e, '重新生成失败')
  } finally {
    busy.value = false
  }
}

onMounted(load)
onUnmounted(stopPolling)
</script>

<template>
  <div class="report-page page">
    <header class="page-head has-actions">
      <div>
        <h1>面试报告</h1>
        <p>总分、分阶段得分、逐题点评与学习建议。</p>
      </div>
      <div class="head-actions">
        <RouterLink to="/interviews" class="btn btn-sm">返回面试记录</RouterLink>
      </div>
    </header>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>
    <div v-if="loading && !report" class="skeleton">报告加载中…</div>

    <template v-else-if="report">
      <div v-if="report.status === 'generating'" class="card empty">
        <p>报告生成中…</p>
        <span class="muted small">AI 正在按主问题逐一评分，页面会自动刷新。</span>
      </div>

      <div v-else-if="report.status === 'failed'" class="card empty">
        <p>报告生成失败</p>
        <span class="muted small">系统会自动重试，也可以手动重新生成，不额外扣次数。</span>
        <button type="button" class="btn btn-sm btn-primary" :disabled="busy" @click="regenerate">重新生成报告</button>
      </div>

      <template v-else>
        <div v-if="report.is_ended_by_system_failure" class="alert alert-warn">本场面试因系统异常提前结束，未问到的题目不计分。</div>

        <div class="grid score-grid">
          <div class="card score-card">
            <span class="label">总分</span>
            <b class="big-score fig">{{ report.total_score ?? '—' }}</b>
            <span class="tag" :class="{ 'tag-primary': report.grade === 'excellent' }">
              {{ GRADE_LABEL[report.grade ?? ''] ?? '—' }}
            </span>
            <span class="small muted">生成于 {{ timeText(report.generated_at) }}</span>
          </div>

          <div class="card">
            <h2>分阶段得分</h2>
            <div v-for="stage in report.stages" :key="stage.route_stage_id" class="stage-score">
              <div class="row-between">
                <span>{{ stage.stage_name }}</span>
                <b class="fig">{{ stage.score === null ? '未测评' : stage.score }}</b>
              </div>
              <div class="progress"><span :style="{ '--p': (stage.score ?? 0) / 100 }" /></div>
            </div>
            <p v-if="!report.stages.length" class="muted small">本场没有覆盖阶段。</p>
          </div>
        </div>

        <section class="section">
          <div class="section-head"><h2>逐题点评</h2></div>
          <div v-for="question in report.questions" :key="question.seq_no" class="card question">
            <details :open="question.seq_no === 1">
              <summary>
                <span class="fig">{{ String(question.seq_no).padStart(2, '0') }}</span>
                <b>{{ question.stem }}</b>
                <span class="tag">{{ ANSWER_LABEL[question.answer_status] ?? question.answer_status }}</span>
                <b class="fig score">{{ question.score === null ? '—' : question.score }} 分</b>
              </summary>
              <div class="qa">
                <div v-if="question.ai_comment"><span class="label">AI 点评</span><p>{{ question.ai_comment }}</p></div>
                <div v-if="question.missing_points"><span class="label">缺失要点</span><p>{{ question.missing_points }}</p></div>
                <div v-if="question.reference_answer"><span class="label">参考答案</span><p>{{ question.reference_answer }}</p></div>
                <div v-if="question.messages.length" class="dialogue">
                  <span class="label">本题对话</span>
                  <p v-for="msg in question.messages" :key="msg.seq_no" :class="msg.speaker">
                    {{ msg.speaker === 'ai' ? 'AI 面试官' : '我' }}：{{ msg.content }}
                  </p>
                </div>
              </div>
            </details>
          </div>
        </section>

        <section class="section">
          <div class="section-head"><h2>学习建议</h2></div>
          <div v-for="(advice, index) in report.learning_advice" :key="index" class="card advice">
            <p>{{ advice.advice }}</p>
            <div class="row wrap">
              <RouterLink v-for="course in advice.courses" :key="course.id" :to="`/practice/sequential/${course.id}`" class="btn btn-sm">
                去练习「{{ course.title }}」
              </RouterLink>
            </div>
          </div>
          <div v-if="!report.learning_advice.length" class="card empty">本场没有需要重点补强的阶段。</div>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; }
.score-grid { grid-template-columns: minmax(0, 240px) minmax(0, 1fr); align-items: start; }
.score-card { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-2xs); }
.score-card .label { color: var(--color-muted); font-size: var(--text-sm); }
.big-score { font-size: 3.2rem; line-height: 1.1; }
.stage-score { margin-top: var(--space-sm); }
.row-between { display: flex; justify-content: space-between; gap: var(--space-sm); }
.progress { margin-top: var(--space-2xs); }
.question { padding: 0; }
.question summary { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm); padding: var(--space-md); cursor: pointer; }
.question summary b:not(.score) { flex: 1; min-width: 200px; }
.question .score { margin-left: auto; }
.qa { display: flex; flex-direction: column; gap: var(--space-sm); padding: 0 var(--space-md) var(--space-md); }
.qa .label { display: block; color: var(--color-muted); font-size: var(--text-xs); }
.qa p { white-space: pre-wrap; }
.dialogue p { padding: var(--space-2xs) 0; border-top: var(--rule-hair) solid var(--color-rule); }
.dialogue p.student { color: var(--color-accent-2); }
.advice { display: flex; flex-direction: column; gap: var(--space-sm); }
@media (max-width: 60rem) { .score-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
