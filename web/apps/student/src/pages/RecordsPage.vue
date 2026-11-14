<script setup lang="ts">
// 学习记录 · 刷题记录（S-PRC-04、05）：本批展示顺序练习场次与本场作答详情
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { AnswerDetail, PracticeRecord } from '@yunshu/api'
import { errorText, practiceApi } from '@/api'

const PAGE_SIZE = 20
const route = useRoute()
const router = useRouter()

const records = ref<PracticeRecord[]>([])
const total = ref(0)
const currentPage = ref(Math.max(1, Number(route.query.page) || 1))
const recordType = ref(typeof route.query.type === 'string' ? route.query.type : '')
const loading = ref(false)
const error = ref('')
const openId = ref<number | null>(null)
const answers = ref<AnswerDetail[]>([])
const answersLoading = ref(false)

const TYPES = [
  { value: '', label: '全部类型' },
  { value: 'sequential', label: '顺序练习' },
  { value: 'random', label: '随机抽题' },
  { value: 'exam', label: '模拟考试' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await practiceApi.records({
      page: currentPage.value,
      page_size: PAGE_SIZE,
      record_type: recordType.value || undefined,
    })
    records.value = data.items
    total.value = data.total
  } catch (e) {
    error.value = errorText(e, '刷题记录加载失败，请稍后重试')
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch([currentPage, recordType], load, { immediate: true })

function changeType(value: string) {
  recordType.value = value
  currentPage.value = 1
  const query: Record<string, string> = {}
  if (value) query.type = value
  router.replace({ path: '/records', query })
}

function changePage(page: number) {
  currentPage.value = page
  const query: Record<string, string> = {}
  if (recordType.value) query.type = recordType.value
  if (page > 1) query.page = String(page)
  router.replace({ path: '/records', query })
}

async function toggleAnswers(id: number) {
  if (openId.value === id) {
    openId.value = null
    return
  }
  openId.value = id
  answers.value = []
  answersLoading.value = true
  try {
    const data = await practiceApi.answers(id)
    answers.value = data.items
  } catch (e) {
    error.value = errorText(e, '作答详情加载失败')
  } finally {
    answersLoading.value = false
  }
}

function timeText(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', { hour12: false })
}

function modeLabel(mode: string): string {
  const labels: Record<string, string> = {
    sequential: '顺序练习', random: '随机抽题', exam: '模拟考试',
  }
  return labels[mode] ?? mode
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    in_progress: '进行中', completed: '已完成', unfinished: '未完成',
  }
  return labels[status] ?? status
}

function answerLabel(value: unknown): string {
  if (value == null) return '未作答'
  const list = Array.isArray(value) ? value : [value]
  return list.map((one) => (typeof one === 'boolean' ? (one ? '正确' : '错误') : String(one))).join('、')
}

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
</script>

<template>
  <div class="records">
    <header class="page-head">
      <div>
        <h1>学习记录</h1>
        <p>查看历次刷题场次与作答详情；答错的题目可在错题本里重做。</p>
      </div>
      <select
class="input type-select" :value="recordType" aria-label="按记录类型筛选"
              @change="changeType(($event.target as HTMLSelectElement).value)">
        <option v-for="item in TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
    </header>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="skeleton">记录加载中…</div>

    <div v-else-if="records.length" class="list card">
      <article v-for="item in records" :key="item.id" class="record">
        <div class="row-head">
          <div>
            <span class="tag">{{ modeLabel(item.practice_mode) }}</span>
            <strong>{{ item.course_title || item.route_name || '练习场次' }}</strong>
            <span v-if="item.stage_name" class="muted">· {{ item.stage_name }}</span>
          </div>
          <span class="muted">{{ statusLabel(item.status) }}</span>
        </div>
        <div class="meta muted">
          <span>{{ timeText(item.started_at) }}</span>
          <span>已答 <b class="fig">{{ item.answered_count }}</b> / {{ item.question_count }}</span>
          <span>答对 <b class="fig">{{ item.correct_count }}</b></span>
        </div>
        <button type="button" class="btn btn-sm btn-ghost" @click="toggleAnswers(item.id)">
          {{ openId === item.id ? '收起作答' : '查看作答' }}
        </button>

        <div v-if="openId === item.id" class="answers">
          <p v-if="answersLoading" class="muted">加载中…</p>
          <p v-else-if="!answers.length" class="muted">本场暂无作答记录。</p>
          <div v-for="(answer, index) in answers" :key="index" class="answer-item">
            <p class="stem">{{ String(answer.question_snapshot?.stem ?? '题目已删除') }}</p>
            <p class="muted">
              你的答案：{{ answerLabel(answer.student_answer) }}
              · <span :class="answer.is_correct ? 'ok' : 'bad'">{{ answer.is_correct ? '答对' : '答错' }}</span>
            </p>
          </div>
        </div>
      </article>
    </div>

    <div v-else class="empty card">
      <p>还没有刷题记录。</p>
      <RouterLink to="/routes" class="btn btn-sm">去学习路线开始练习</RouterLink>
    </div>

    <div v-if="pageCount > 1" class="pager">
      <button type="button" class="btn btn-sm" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">
        上一页
      </button>
      <span class="muted">第 {{ currentPage }} / {{ pageCount }} 页，共 {{ total }} 场</span>
      <button type="button" class="btn btn-sm" :disabled="currentPage >= pageCount" @click="changePage(currentPage + 1)">
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped>
.records {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.type-select {
  width: auto;
  min-width: 8rem;
  min-height: var(--control-h-sm);
}
.list {
  padding: 0;
  display: flex;
  flex-direction: column;
}
.record {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-xs);
  padding: var(--space-lg);
  border-bottom: var(--rule-hair) solid var(--color-rule);
}
.record:last-child {
  border-bottom: 0;
}
.row-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  width: 100%;
}
.tag {
  margin-right: var(--space-xs);
  padding: 0 var(--space-2xs);
  border-radius: var(--radius-pill);
  background: var(--color-paper-2);
  font-size: var(--text-xs);
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
  font-size: var(--text-sm);
}
.answers {
  width: 100%;
  padding: var(--space-sm);
  border-radius: var(--radius-card);
  background: var(--color-paper-2);
}
.answer-item {
  padding: var(--space-xs) 0;
  border-bottom: var(--rule-hair) solid var(--color-rule);
  font-size: var(--text-sm);
}
.answer-item:last-child {
  border-bottom: 0;
}
.stem {
  margin-bottom: var(--space-3xs);
}
.ok { color: var(--color-accent-2); }
.bad { color: var(--color-error); }
.pager,
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  font-size: var(--text-sm);
}
.empty {
  flex-direction: column;
  padding: var(--space-2xl) var(--space-lg);
  color: var(--color-muted);
}
.alert-error {
  padding: var(--space-2xs) var(--space-sm);
  border-radius: var(--radius-input);
  background: var(--color-error-wash);
  color: var(--color-error);
  font-size: var(--text-sm);
}
</style>
