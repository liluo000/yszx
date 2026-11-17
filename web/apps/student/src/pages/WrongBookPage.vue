<script setup lang="ts">
// 错题本（需求 5.5，S-WRG-01、02）：收录刷题答错的题目，可筛选、查看解析、手动移除
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { WrongFilterOptions, WrongItem, WrongQuestionQuery } from '@yunshu/api'
import { QUESTION_TYPE_LABEL } from '@yunshu/api'
import { errorText, wrongApi } from '@/api'

const PAGE_SIZE = 20

const route = useRoute()
const router = useRouter()

const items = ref<WrongItem[]>([])
const filters = ref<WrongFilterOptions>({ courses: [], subjects: [], routes: [] })
const total = ref(0)
const loading = ref(false)
const error = ref('')
const removingId = ref<number | null>(null)
const confirmingId = ref<number | null>(null)

const QUESTION_TYPES = Object.entries(QUESTION_TYPE_LABEL) as [WrongItem['question_type'], string][]

const current = computed(() => ({
  course: text(route.query.course),
  subject: text(route.query.subject),
  type: text(route.query.type),
  route: text(route.query.route),
  page: Math.max(1, Number(text(route.query.page)) || 1),
}))

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const hasFilter = computed(() => Boolean(current.value.course || current.value.subject
  || current.value.type || current.value.route))
const redoableCount = computed(() => items.value.filter((item) => item.is_redoable).length)
const redoableIds = computed(() => items.value.filter((item) => item.is_redoable).map((item) => item.id))

/** 发起错题重做：把当前筛选下的错题记录 ID 带给练习页 */
function startRedo(ids: number[]) {
  if (!ids.length) return
  router.push({ path: '/practice/wrong_redo', query: { ids: ids.join(',') } })
}

function text(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function toNumber(value: string): number | undefined {
  return value ? Number(value) : undefined
}

async function load() {
  const query: WrongQuestionQuery = {
    page: current.value.page,
    page_size: PAGE_SIZE,
    course_id: toNumber(current.value.course),
    subject_id: toNumber(current.value.subject),
    question_type: current.value.type || undefined,
    learning_route_id: toNumber(current.value.route),
  }
  loading.value = true
  error.value = ''
  try {
    const data = await wrongApi.list(query)
    items.value = data.items
    filters.value = data.filters
    total.value = data.total
  } catch (e) {
    error.value = errorText(e, '错题本加载失败，请稍后重试')
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch(() => route.fullPath, load, { immediate: true })

/** 筛选变化时回到第一页，并同步到地址栏（刷新、前进后退都能保持筛选） */
function applyFilter(key: 'course' | 'subject' | 'type' | 'route', value: string) {
  const next: Record<string, string> = { ...route.query } as Record<string, string>
  if (value) next[key] = value
  else delete next[key]
  delete next.page
  router.replace({ path: '/wrong', query: next })
}

function goPage(page: number) {
  if (page < 1 || page > pageCount.value) return
  const next: Record<string, string> = { ...route.query } as Record<string, string>
  if (page === 1) delete next.page
  else next.page = String(page)
  router.replace({ path: '/wrong', query: next })
}

async function remove(item: WrongItem) {
  if (confirmingId.value !== item.id) {
    confirmingId.value = item.id
    return
  }
  confirmingId.value = null
  removingId.value = item.id
  try {
    await wrongApi.remove(item.id)
    // 移除后当前页可能空掉，往前退一页
    if (items.value.length === 1 && current.value.page > 1) {
      await goPage(current.value.page - 1)
    } else {
      await load()
    }
  } catch (e) {
    error.value = errorText(e, '移除失败，请稍后重试')
  } finally {
    removingId.value = null
  }
}

function typeLabel(type: WrongItem['question_type']): string {
  return QUESTION_TYPE_LABEL[type] ?? type
}

/** 答案统一成可读文本：判断题翻译成正确/错误，多选与简答按列表拼接 */
function answerText(value: unknown, type: WrongItem['question_type']): string {
  if (value == null) return '未作答'
  const list = Array.isArray(value) ? value : [value]
  if (list.length === 0) return '未作答'
  return list.map((one) => {
    if (type === 'true_false') {
      const flag = String(one).toLowerCase()
      if (flag === 'true') return '正确'
      if (flag === 'false') return '错误'
    }
    return typeof one === 'string' ? one : JSON.stringify(one)
  }).join('、')
}

function correctText(item: WrongItem): string {
  if (item.question_type === 'short_answer') return '见参考答案'
  return answerText(item.correct_answer, item.question_type)
}

function timeText(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + ` ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<template>
  <div class="wrong">
    <header class="page-head has-actions">
      <div>
        <h1>错题本</h1>
        <p>收录刷题中答错的题目，在这里重做答对后自动移出。</p>
      </div>
      <div class="head-actions">
        <button
          type="button"
          class="btn btn-primary"
          :disabled="redoableCount === 0"
          :title="redoableCount === 0 ? '当前筛选下没有可重做的错题' : '答对后自动移出错题本'"
          @click="startRedo(redoableIds)"
        >
          重做筛选出的 {{ redoableCount }} 道题
        </button>
      </div>
    </header>

    <div class="filter-row" role="group" aria-label="筛选错题">
      <span class="select-wrap">
        <select
          class="select"
          :class="{ active: current.course }"
          :value="current.course"
          aria-label="按课程筛选"
          @change="applyFilter('course', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">全部课程</option>
          <option v-for="course in filters.courses" :key="course.id" :value="String(course.id)">
            {{ course.title }}
          </option>
        </select>
      </span>
      <span class="select-wrap">
        <select
          class="select"
          :class="{ active: current.subject }"
          :value="current.subject"
          aria-label="按学科筛选"
          @change="applyFilter('subject', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">全部学科</option>
          <option v-for="subject in filters.subjects" :key="subject.id" :value="String(subject.id)">
            {{ subject.name }}
          </option>
        </select>
      </span>
      <span class="select-wrap">
        <select
          class="select"
          :class="{ active: current.type }"
          :value="current.type"
          aria-label="按题型筛选"
          @change="applyFilter('type', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">全部题型</option>
          <option v-for="[value, label] in QUESTION_TYPES" :key="value" :value="value">{{ label }}</option>
        </select>
      </span>
      <span class="select-wrap">
        <select
          class="select"
          :class="{ active: current.route }"
          :value="current.route"
          aria-label="按路线筛选"
          @change="applyFilter('route', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">全部路线</option>
          <option v-for="item in filters.routes" :key="item.id" :value="String(item.id)">{{ item.name }}</option>
        </select>
      </span>
    </div>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>

    <div v-if="loading" class="skeleton">错题加载中…</div>

    <template v-else-if="items.length">
      <div class="list card">
        <article v-for="item in items" :key="item.id" class="wrong-item">
          <div class="meta">
            <span class="tag">{{ typeLabel(item.question_type) }}</span>
            <span>{{ item.course_title || '课程已删除' }}</span>
            <span v-if="item.subject_names.length" class="muted">{{ item.subject_names.join(' / ') }}</span>
            <span v-if="!item.is_redoable" class="tag tag-orange">已下架</span>
            <span class="grow"></span>
            <span class="muted">累计答错 <b class="fig">{{ item.wrong_count }}</b> 次</span>
            <span class="muted fig">{{ timeText(item.last_wrong_at) }}</span>
          </div>

          <h3 class="stem">{{ item.stem }}</h3>

          <ol v-if="item.options && item.options.length" class="options">
            <li v-for="option in item.options" :key="option.key">
              <span class="fig key">{{ option.key }}</span>
              <span>{{ option.text }}</span>
            </li>
          </ol>

          <dl class="answers">
            <div>
              <dt>你上次的答案</dt>
              <dd class="bad">{{ answerText(item.last_student_answer, item.question_type) }}</dd>
            </div>
            <div>
              <dt>正确答案</dt>
              <dd class="ok">{{ correctText(item) }}</dd>
            </div>
          </dl>

          <details v-if="item.analysis || item.reference_answer" class="analysis">
            <summary>查看解析</summary>
            <p v-if="item.reference_answer">参考答案：{{ item.reference_answer }}</p>
            <p v-if="item.analysis">解析：{{ item.analysis }}</p>
          </details>

          <div class="actions">
            <button
              type="button"
              class="btn btn-sm btn-primary"
              :disabled="!item.is_redoable"
              :title="item.is_redoable ? '答对后自动移出错题本' : '题目已下架，不能重做'"
              @click="startRedo([item.id])"
            >
              重做
            </button>
            <button
              type="button"
              class="btn btn-sm"
              :class="{ danger: confirmingId === item.id }"
              :disabled="removingId === item.id"
              @click="remove(item)"
            >
              {{ confirmingId === item.id ? '确认移除' : '移出错题本' }}
            </button>
            <button
v-if="confirmingId === item.id" type="button" class="btn btn-sm btn-ghost"
                    @click="confirmingId = null">
              取消
            </button>
          </div>
        </article>
      </div>

      <div v-if="pageCount > 1" class="pager">
        <button type="button" class="btn btn-sm" :disabled="current.page <= 1" @click="goPage(current.page - 1)">
          上一页
        </button>
        <span class="muted">第 {{ current.page }} / {{ pageCount }} 页，共 {{ total }} 道</span>
        <button
type="button" class="btn btn-sm" :disabled="current.page >= pageCount"
                @click="goPage(current.page + 1)">
          下一页
        </button>
      </div>
    </template>

    <div v-else class="empty card">
      <p>{{ hasFilter ? '没有符合筛选条件的错题。' : '错题本是空的，答错的题会自动收进来。' }}</p>
      <RouterLink to="/routes" class="btn btn-sm">去学习路线刷题</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.wrong {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.page-head {
  align-items: flex-start;
}
/* 头部操作与筛选各自独立一行，靠左对齐 */
.head-toolbar {
  display: flex;
  justify-content: flex-start;
}
/* 四个下拉等宽：否则 select 会被最长选项文字撑开，出现宽度参差 */
.filter-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 200px));
  gap: var(--space-xs);
  margin-bottom: var(--space-md);
}
@media (max-width: 48rem) {
  .filter-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.list {
  display: flex;
  flex-direction: column;
  padding-block: 0;
}
.wrong-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding: var(--space-lg) 0;
  border-bottom: var(--rule-hair) solid var(--color-rule);
}
.wrong-item:last-child {
  border-bottom: 0;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--text-xs);
  color: var(--color-ink-2);
}
.grow {
  flex: 1;
}
.tag {
  padding: 0 var(--space-2xs);
  border-radius: var(--radius-pill);
  background: var(--color-paper-2);
  color: var(--color-ink-2);
  font-size: var(--text-xs);
}
.tag-warn {
  background: var(--color-error-wash);
  color: var(--color-error);
}
.stem {
  font-size: var(--text-md);
  font-weight: 600;
  line-height: 1.6;
}
.options {
  display: flex;
  flex-direction: column;
  gap: var(--space-3xs);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
}
.options li {
  display: flex;
  gap: var(--space-2xs);
}
.key {
  flex: none;
  width: 1.25rem;
  color: var(--color-muted);
}
.answers {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-lg);
  margin: 0;
  font-size: var(--text-sm);
}
.answers dt {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.answers dd {
  margin: var(--space-3xs) 0 0;
  font-weight: 600;
}
.bad {
  color: var(--color-error);
}
.ok {
  color: var(--color-accent-2);
}
.analysis {
  font-size: var(--text-sm);
  color: var(--color-ink-2);
}
.analysis summary {
  cursor: pointer;
  color: var(--color-accent-2);
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2xs);
}
.actions .danger {
  border-color: var(--color-error);
  color: var(--color-error);
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  font-size: var(--text-sm);
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-2xl) var(--space-lg);
  color: var(--color-muted);
  text-align: center;
}
</style>
