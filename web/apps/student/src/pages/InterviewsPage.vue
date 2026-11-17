<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { InterviewListItem } from '@yunshu/api'
import { errorText, interviewApi } from '@/api'

const items = ref<InterviewListItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(true)
const error = ref('')
const busyId = ref(0)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const STATUS_LABEL: Record<string, string> = {
  in_progress: '进行中',
  ended: '已结束',
  report_generating: '报告生成中',
  report_failed: '报告生成失败',
  system_failed: '因系统异常结束',
}
const GRADE_LABEL: Record<string, string> = {
  excellent: '优秀', good: '良好', needs_improvement: '待提升',
}

function timeText(raw: string) {
  const d = new Date(raw)
  return Number.isNaN(d.getTime()) ? raw : d.toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await interviewApi.records({ page: page.value, page_size: pageSize })
    items.value = data.items
    total.value = data.total
  } catch (e) {
    error.value = errorText(e, '面试记录加载失败')
  } finally {
    loading.value = false
  }
}

function changePage(next: number) {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load()
}

async function regenerate(id: number) {
  busyId.value = id
  error.value = ''
  try {
    await interviewApi.regenerate(id)
    await load()
  } catch (e) {
    error.value = errorText(e, '重新生成失败')
  } finally {
    busyId.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="records page">
    <header class="page-head has-actions">
      <div>
        <h1>面试记录</h1>
        <p>历次 AI 面试的时间、范围、模式与成绩；点击可查看报告。</p>
      </div>
      <div class="head-actions">
        <RouterLink to="/interview" class="btn btn-sm btn-primary">发起面试</RouterLink>
      </div>
    </header>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="skeleton">记录加载中…</div>

    <div v-else-if="items.length" class="card">
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>面试时间</th>
              <th>路线与范围</th>
              <th>模式</th>
              <th>成绩</th>
              <th>状态</th>
              <th class="ops"><span class="visually-hidden">操作</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td class="fig">{{ timeText(item.started_at) }}</td>
              <td>
                <b>{{ item.route_name }}</b>
                <span v-if="item.is_route_off_shelf" class="tag tag-orange">路线已下架</span>
                <div class="small muted">{{ item.stage_names.join('、') || '整条路线' }}</div>
              </td>
              <td>{{ item.interview_mode === 'text' ? '文字' : '语音' }}</td>
              <td>
                <template v-if="item.total_score !== null">
                  <b class="fig">{{ item.total_score }}</b>
                  <span class="small muted"> · {{ GRADE_LABEL[item.grade ?? ''] ?? '' }}</span>
                </template>
                <span v-else class="muted small">—</span>
              </td>
              <td>
                <span class="tag" :class="{ 'tag-primary': item.record_status === 'ended', 'tag-warn': item.record_status === 'report_failed' || item.record_status === 'system_failed' }">
                  {{ STATUS_LABEL[item.record_status] ?? item.record_status }}
                </span>
              </td>
              <td class="ops">
                <RouterLink v-if="item.record_status === 'ended'" :to="`/report/${item.id}`" class="btn-link">查看报告</RouterLink>
                <RouterLink v-else-if="item.can_resume" to="/interview" class="btn-link">继续作答</RouterLink>
                <button v-if="item.record_status === 'report_failed'" type="button" class="btn-link" :disabled="busyId === item.id" @click="regenerate(item.id)">重新生成</button>
                <span v-if="item.record_status === 'report_generating'" class="small muted">请稍后刷新</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else class="card empty">
      <p>还没有面试记录。</p>
      <RouterLink to="/interview" class="btn btn-sm">去发起第一场 AI 面试</RouterLink>
    </div>

    <div v-if="pageCount > 1" class="pager">
      <button type="button" class="btn btn-sm" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ pageCount }} 页，共 {{ total }} 场</span>
      <button type="button" class="btn btn-sm" :disabled="page >= pageCount" @click="changePage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; }
.table .small { margin-top: var(--space-3xs); }
.tag-orange { border-color: var(--color-warn); color: var(--color-warn); }
</style>
