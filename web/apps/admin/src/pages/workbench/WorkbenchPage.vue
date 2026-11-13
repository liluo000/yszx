<script setup lang="ts">
// 工作台（需求 6.12，A-DSH-03）：按角色显示待办和当日数据，点击卡片跳转对应页面
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FeedbackItem, WorkbenchView } from '@yunshu/api'
import { feedbackApi, workbenchApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { errorText, formatDateTime } from '@/utils/format'

interface TileDef {
  key: keyof WorkbenchView
  label: string
  unit: string
  to: string
  hint?: string
}

/** 与需求 6.12 的七项内容一一对应；无权限的字段由后端返回 null，这里自动隐藏 */
const DEFS: TileDef[] = [
  { key: 'pending_question_correction_count', label: '待处理的题目纠错', unit: '条', to: '/feedbacks' },
  { key: 'pending_video_invalid_count', label: '待处理的视频失效反馈', unit: '条', to: '/feedbacks' },
  { key: 'unconfigured_course_count', label: '未配题的课程', unit: '门', to: '/courses' },
  {
    key: 'insufficient_stage_count',
    label: '题量不足的阶段',
    unit: '个',
    to: '/routes',
    hint: '刷题或面试题量低于最低题量',
  },
  { key: 'today_new_student_count', label: '今日新增注册', unit: '人', to: '/dashboard' },
  { key: 'today_active_student_count', label: '今日活跃学员', unit: '人', to: '/dashboard' },
  {
    key: 'today_interview_count',
    label: '今日面试场次',
    unit: '场',
    to: '/dashboard',
    hint: '不含 AI 服务异常结束的场次',
  },
]

const router = useRouter()
const auth = useAuthStore()
const data = ref<WorkbenchView | null>(null)
const loading = ref(false)
const loadError = ref('')
const feedbacks = ref<FeedbackItem[]>([])
const feedbackError = ref('')

const tiles = computed(() => {
  const d = data.value
  if (!d) return []
  return DEFS.flatMap((def) => {
    const value = d[def.key]
    return value === null || value === undefined ? [] : [{ ...def, value }]
  })
})

/** 只有内容运营和超级管理员能看到反馈待办 */
const showFeedbacks = computed(() => data.value?.pending_question_correction_count != null)

async function load() {
  loading.value = true
  loadError.value = ''
  feedbackError.value = ''
  try {
    data.value = await workbenchApi.load()
    if (showFeedbacks.value) {
      try {
        feedbacks.value = (await feedbackApi.list({ status: 'pending', page_size: 4 })).items
      } catch (e) {
        feedbacks.value = []
        feedbackError.value = errorText(e, '反馈列表加载失败')
      }
    }
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function targetLabel(row: FeedbackItem) {
  return row.stem ?? row.course_title ?? '—'
}

onMounted(load)
</script>

<template>
  <section class="page">
    <div class="head">
      <h2>工作台</h2>
      <p v-if="auth.user" class="greeting">你好，{{ auth.user.name }}。这里是你的待办和今天的数据，点击进入处理。</p>
      <span class="meta">当日数据按北京时间统计</span>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <template #default>
        <el-button link type="primary" @click="load">重新加载</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="content">
      <el-empty v-if="!loading && !loadError && !tiles.length" description="暂无可展示的内容" />
      <div v-else-if="tiles.length" class="tiles">
        <button v-for="tile in tiles" :key="tile.key" type="button" class="tile" @click="router.push(tile.to)">
          <span class="label">{{ tile.label }}</span>
          <span class="value">{{ tile.value.toLocaleString() }}<small>{{ tile.unit }}</small></span>
          <span v-if="tile.hint" class="hint">{{ tile.hint }}</span>
          <span class="go" aria-hidden="true">查看 ›</span>
        </button>
      </div>

      <section v-if="showFeedbacks" class="card" aria-labelledby="wbFeedback">
        <div class="card-head">
          <h3 id="wbFeedback">最新待处理反馈</h3>
          <el-button link type="primary" @click="router.push('/feedbacks')">查看全部</el-button>
        </div>
        <el-alert v-if="feedbackError" type="warning" :title="feedbackError" :closable="false" show-icon />
        <el-table v-else :data="feedbacks" empty-text="没有待处理的反馈" size="small">
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="row.feedback_type === 'video_invalid' ? 'warning' : 'primary'" effect="plain">
                {{ row.feedback_type === 'video_invalid' ? '视频失效' : '题目纠错' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="对象" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ targetLabel(row as FeedbackItem) }}</template>
          </el-table-column>
          <el-table-column label="提交次数" width="100" align="right">
            <template #default="{ row }">{{ row.submit_count }}</template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-sm);
}
.head h2 {
  margin: 0;
  font-size: var(--text-xl);
}
.greeting {
  margin: 0;
  color: var(--color-muted);
}
.meta {
  margin-left: auto;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.content {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 180px;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-sm);
}
.tile {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2xs);
  min-height: 116px;
  padding: var(--space-md);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
  background: var(--color-paper);
  color: var(--color-ink);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur-short), box-shadow var(--dur-short);
}
.tile:hover {
  border-color: var(--color-accent);
  box-shadow: var(--cast-stock);
}
.tile:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: -2px;
}
.label {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.value {
  font-family: var(--font-outlier);
  font-size: var(--text-2xl);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.value small {
  margin-left: var(--space-2xs);
  font-family: inherit;
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-muted);
}
.hint {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.go {
  margin-top: auto;
  color: var(--color-accent-2);
  font-size: var(--text-xs);
}
.card {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
  background: var(--color-paper);
  padding: var(--space-md);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
}
.card-head h3 {
  margin: 0;
  font-size: var(--text-md);
}
</style>
