<script setup lang="ts">
// 学习记录（S-LRN-04、05 / S-PRC-04、05）：路线进度 + 学习轨迹 + 刷题场次与作答详情
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { AnswerDetail, LearningTrack, PracticeRecord, RouteCard } from '@yunshu/api'
import { errorText, learningApi, practiceApi } from '@/api'

const PAGE_SIZE = 20
const route = useRoute()
const router = useRouter()

const tab = ref<'track' | 'practice'>(route.query.tab === 'practice' ? 'practice' : 'track')
const records = ref<PracticeRecord[]>([])
const tracks = ref<LearningTrack[]>([])
const total = ref(0)
const currentPage = ref(Math.max(1, Number(route.query.page) || 1))
const recordType = ref(typeof route.query.type === 'string' ? route.query.type : '')
const trackType = ref(typeof route.query.track_type === 'string' ? route.query.track_type : '')
const loading = ref(false)
const error = ref('')
const openId = ref<number | null>(null)
const answers = ref<AnswerDetail[]>([])
const answersLoading = ref(false)
const routes = ref<RouteCard[]>([])

const TYPES = [
  { value: '', label: '全部类型' },
  { value: 'sequential', label: '顺序练习' },
  { value: 'random', label: '随机抽题' },
  { value: 'exam', label: '模拟考试' },
]
const TRACK_TYPES = [
  { value: '', label: '全部行为' },
  { value: 'opened', label: '打开课程' },
  { value: 'learned', label: '标记已学' },
  { value: 'mastered', label: '达成已掌握' },
]
const TRACK_LABEL: Record<string, string> = {
  opened: '打开课程',
  learned: '标记已学',
  mastered: '达成已掌握',
}

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

/** 进度条比例（0~1） */
function ratio(done: number, totalCount: number) {
  return totalCount > 0 ? Math.min(1, done / totalCount).toFixed(3) : 0
}

/** S-LRN-05：路线进度卡片，失败不影响下方列表 */
async function loadRoutes() {
  try {
    const data = await learningApi.routeProgress()
    routes.value = data.items as RouteCard[]
  } catch {
    routes.value = []
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (tab.value === 'track') {
      const data = await learningApi.tracks({
        page: currentPage.value,
        page_size: PAGE_SIZE,
        record_type: trackType.value || undefined,
      })
      tracks.value = data.items
      total.value = data.total
    } else {
      const data = await practiceApi.records({
        page: currentPage.value,
        page_size: PAGE_SIZE,
        record_type: recordType.value || undefined,
      })
      records.value = data.items
      total.value = data.total
    }
  } catch (e) {
    error.value = errorText(e, tab.value === 'track' ? '学习轨迹加载失败' : '刷题记录加载失败，请稍后重试')
    records.value = []
    tracks.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch([tab, currentPage, recordType, trackType], load, { immediate: true })
loadRoutes()

function setTab(value: 'track' | 'practice') {
  tab.value = value
  currentPage.value = 1
  syncQuery()
}

function changeType(value: string) {
  recordType.value = value
  currentPage.value = 1
  syncQuery()
}

function changeTrackType(value: string) {
  trackType.value = value
  currentPage.value = 1
  syncQuery()
}

function changePage(page: number) {
  currentPage.value = page
  syncQuery()
}

function syncQuery() {
  const query: Record<string, string> = {}
  if (tab.value !== 'track') query.tab = tab.value
  if (recordType.value) query.type = recordType.value
  if (trackType.value) query.track_type = trackType.value
  if (currentPage.value > 1) query.page = String(currentPage.value)
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

function answerLabel(value: unknown): string {
  if (value == null) return '未作答'
  const list = Array.isArray(value) ? value : [value]
  return list.map((one) => (typeof one === 'boolean' ? (one ? '正确' : '错误') : String(one))).join('、')
}
</script>

<template>
  <div class="records">
    <header class="page-head has-actions">
      <div>
        <h1>学习记录</h1>
        <p>各条路线的学习进度、课程学习轨迹与每次刷题的成绩。</p>
      </div>
      <div class="head-actions">
        <span v-if="tab === 'track'" class="select-wrap">
          <select
            class="select type-select"
            :class="{ active: trackType }"
            :value="trackType"
            aria-label="按行为类型筛选"
            @change="changeTrackType(($event.target as HTMLSelectElement).value)"
          >
            <option v-for="item in TRACK_TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </span>
        <span v-else class="select-wrap">
          <select
            class="select type-select"
            :class="{ active: recordType }"
            :value="recordType"
            aria-label="按记录类型筛选"
            @change="changeType(($event.target as HTMLSelectElement).value)"
          >
            <option v-for="item in TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </span>
      </div>
    </header>

    <!-- S-LRN-05：各路线已学 / 已掌握进度 -->
    <div v-if="routes.length" class="route-grid">
      <div v-for="item in routes" :key="item.id" class="card card-sm">
        <h3>{{ item.name }}</h3>
        <div class="col mt-8">
          <div>
            <div class="progress-label">
              <span>已学</span>
              <span class="fig">{{ item.progress?.learned_course_count ?? 0 }}/{{ item.progress?.total_course_count ?? 0 }}</span>
            </div>
            <div class="progress">
              <span :style="{ '--p': ratio(item.progress?.learned_course_count ?? 0, item.progress?.total_course_count ?? 0) }" />
            </div>
          </div>
          <div>
            <div class="progress-label">
              <span>已掌握</span>
              <span class="fig">{{ item.progress?.mastered_course_count ?? 0 }}/{{ item.progress?.configured_course_count ?? 0 }}</span>
            </div>
            <div class="progress teal">
              <span :style="{ '--p': ratio(item.progress?.mastered_course_count ?? 0, item.progress?.configured_course_count ?? 0) }" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="tabs" role="tablist">
      <button type="button" class="tab" :class="{ active: tab === 'track' }" role="tab" :aria-selected="tab === 'track'" @click="setTab('track')">
        学习轨迹
      </button>
      <button type="button" class="tab" :class="{ active: tab === 'practice' }" role="tab" :aria-selected="tab === 'practice'" @click="setTab('practice')">
        刷题记录
      </button>
    </div>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="skeleton">记录加载中…</div>

    <!-- S-LRN-04 学习轨迹 -->
    <template v-else-if="tab === 'track'">
      <div v-if="tracks.length" class="card">
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>行为</th><th>课程</th><th>发生时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in tracks" :key="`${item.occurred_at}-${item.course_id}-${index}`">
                <td><span class="tag" :class="{ 'tag-primary': item.record_type === 'mastered' }">{{ TRACK_LABEL[item.record_type] ?? item.record_type }}</span></td>
                <td>{{ item.course_title }}</td>
                <td class="fig">{{ timeText(item.occurred_at) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else class="card empty"><p>还没有学习轨迹，打开一门课程后就会记录。</p></div>
    </template>

    <!-- S-PRC-04/05 刷题记录 -->
    <template v-else>
      <div v-if="records.length" class="card">
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>练习</th><th>成绩</th><th>时间</th><th class="ops"><span class="visually-hidden">操作</span></th></tr>
            </thead>
            <tbody>
              <template v-for="item in records" :key="item.id">
                <tr>
                  <td>
                    <div class="row wrap">
                      <span class="tag">{{ modeLabel(item.practice_mode) }}</span>
                      <b>{{ item.course_title || item.route_name || '练习场次' }}</b>
                      <span v-if="item.stage_name" class="small muted">{{ item.stage_name }}</span>
                    </div>
                  </td>
                  <td class="fig">答对 {{ item.correct_count }}/{{ item.answered_count }}</td>
                  <td class="fig">{{ timeText(item.started_at) }}</td>
                  <td class="ops">
                    <button type="button" class="btn-link" @click="toggleAnswers(item.id)">
                      {{ openId === item.id ? '收起作答' : '查看作答' }}
                    </button>
                  </td>
                </tr>
                <tr v-if="openId === item.id">
                  <td colspan="4">
                    <p v-if="answersLoading" class="muted small">加载中…</p>
                    <p v-else-if="!answers.length" class="muted small">本场暂无作答记录。</p>
                    <div v-for="(answer, index) in answers" :key="index" class="answer-item">
                      <p class="stem">{{ String(answer.question_snapshot?.stem ?? '题目已删除') }}</p>
                      <p class="small muted">
                        你的答案：{{ answerLabel(answer.student_answer) }} ·
                        <span :class="answer.is_correct ? 'ok' : 'bad'">{{ answer.is_correct ? '答对' : '答错' }}</span>
                      </p>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else class="card empty">
        <p>还没有刷题记录。</p>
        <RouterLink to="/routes" class="btn btn-sm">去学习路线开始练习</RouterLink>
      </div>
    </template>

    <div v-if="pageCount > 1" class="pager">
      <button type="button" class="btn btn-sm" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
      <span>第 {{ currentPage }} / {{ pageCount }} 页，共 {{ total }} 条</span>
      <button type="button" class="btn btn-sm" :disabled="currentPage >= pageCount" @click="changePage(currentPage + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.records {
  display: flex;
  flex-direction: column;
}
.type-select {
  width: auto;
  min-width: 9rem;
}
.tabs {
  display: flex;
  gap: var(--space-lg);
  margin: var(--space-lg) 0 var(--space-md);
  border-bottom: var(--rule-hair) solid var(--color-rule);
}
.tab {
  padding: var(--space-xs) 0;
  margin-bottom: -1px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--color-muted);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
}
.tab:hover {
  color: var(--color-ink);
  text-decoration: none;
}
.tab.active {
  border-bottom-color: var(--color-ink);
  color: var(--color-ink);
}
.answer-item + .answer-item {
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: var(--rule-hair) solid var(--color-rule);
}
.stem {
  font-size: var(--text-sm);
  font-weight: 600;
}
.ok {
  color: var(--color-success);
}
.bad {
  color: var(--color-error);
}
</style>
