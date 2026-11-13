<script setup lang="ts">
// 数据看板（需求 6.8，A-DSH-01、02）：只含统计数字；当天数据每小时更新，历史指标每天凌晨汇总
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { DashboardView, Granularity } from '@yunshu/api'
import { dashboardApi } from '@/api'
import TrendChart from '@/components/TrendChart.vue'
import { errorText, formatDateTime, saveBlob } from '@/utils/format'

/** 图表配色：单序列用品牌青绿；两条序列用经过色觉校验的蓝、橙 */
const SINGLE = '#00827f'
const SERIES_1 = '#2a78d6'
const SERIES_2 = '#eb6834'

const today = () => new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10)
function daysAgo(n: number) {
  const d = new Date(`${today()}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() - n)
  return d.toISOString().slice(0, 10)
}

const presets = [
  { label: '近 7 天', days: 7 },
  { label: '近 30 天', days: 30 },
  { label: '近 90 天', days: 90 },
]
const range = ref<[string, string]>([daysAgo(29), today()])
const granularity = ref<Granularity>('day')
const data = ref<DashboardView | null>(null)
const loading = ref(false)
const loadError = ref('')
const exporting = ref(false)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    data.value = await dashboardApi.load({
      start_date: range.value[0],
      end_date: range.value[1],
      granularity: granularity.value,
    })
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function usePreset(days: number) {
  range.value = [daysAgo(days - 1), today()]
  load()
}

function disabledDate(date: Date) {
  return date.getTime() > Date.now()
}

async function exportData() {
  exporting.value = true
  try {
    const { blob, filename } = await dashboardApi.export({
      start_date: range.value[0],
      end_date: range.value[1],
      granularity: granularity.value,
    })
    saveBlob(blob, filename ?? '数据看板.xlsx')
    ElMessage.success('导出成功（只含统计数字），已记入操作日志')
  } catch (e) {
    ElMessage.error(errorText(e, '导出失败，请稍后重试'))
  } finally {
    exporting.value = false
  }
}

onMounted(load)

const labels = computed(() => data.value?.acquisition.map((a) => a.stat_date.slice(5)) ?? [])
const last = <T,>(list: T[] | undefined) => (list && list.length ? list[list.length - 1] : undefined)

function pct(value: number | null | undefined) {
  return value === null || value === undefined ? '—' : `${Number(value).toFixed(1)}%`
}

const tiles = computed(() => {
  const d = data.value
  if (!d) return []
  return [
    { label: '新增注册', value: d.acquisition.reduce((s, a) => s + a.new_student_count, 0).toLocaleString() },
    { label: '累计学员', value: (last(d.acquisition)?.cumulative_student_count ?? 0).toLocaleString() },
    {
      label: '月活跃学员',
      value: (last(d.activity)?.monthly_active_student_count ?? 0).toLocaleString(),
      hint: '截至最后一天的近 30 天',
    },
    { label: '刷题量', value: d.practice.answer_count.toLocaleString() },
    { label: '正确率', value: pct(d.practice.correct_rate) },
    { label: '面试场次', value: d.interview.interview_count.toLocaleString(), hint: '不含 AI 服务异常结束的场次' },
    { label: '面试平均分', value: d.interview.average_score === null ? '—' : String(d.interview.average_score) },
  ]
})

const dauLabel = computed(() => (granularity.value === 'day' ? '日活跃学员' : '日活跃学员（日均）'))
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <div class="filters">
        <el-radio-group v-model="granularity" @change="load">
          <el-radio-button value="day">按日</el-radio-button>
          <el-radio-button value="week">按周</el-radio-button>
          <el-radio-button value="month">按月</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :disabled-date="disabledDate"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          range-separator="至"
          class="dates"
          @change="load"
        />
        <el-button v-for="p in presets" :key="p.days" link type="primary" @click="usePreset(p.days)">{{
          p.label
        }}</el-button>
      </div>
      <div class="meta">
        <span class="refreshed"
          >数据更新于 {{ formatDateTime(data?.refreshed_at) }}（当天每小时更新，历史指标每天凌晨汇总）</span
        >
        <el-button :loading="exporting" :disabled="!data" @click="exportData">导出</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <div v-loading="loading" class="content">
      <template v-if="data">
        <div class="tiles">
          <div v-for="t in tiles" :key="t.label" class="tile" :title="t.hint">
            <span class="tile-label">{{ t.label }}</span>
            <span class="tile-value">{{ t.value }}</span>
          </div>
        </div>

        <div class="grid">
          <article class="card">
            <h3>新增注册数</h3>
            <TrendChart
              type="bar"
              :categories="labels"
              :series="[{ name: '新增注册', data: data.acquisition.map((a) => a.new_student_count), color: SINGLE }]"
              label="新增注册数趋势"
            />
          </article>
          <article class="card">
            <h3>活跃学员</h3>
            <TrendChart
              type="line"
              :categories="labels"
              :series="[
                { name: dauLabel, data: data.activity.map((a) => a.daily_active_student_count), color: SERIES_1 },
                { name: '月活跃学员', data: data.activity.map((a) => a.monthly_active_student_count), color: SERIES_2 },
              ]"
              label="日活跃与月活跃学员趋势"
            />
          </article>
          <article class="card">
            <h3>留存率</h3>
            <TrendChart
              type="line"
              unit="%"
              :categories="labels"
              :series="[
                { name: '次日留存率', data: data.activity.map((a) => a.next_day_retention_rate), color: SERIES_1 },
                { name: '7 日留存率', data: data.activity.map((a) => a.seven_day_retention_rate), color: SERIES_2 },
              ]"
              label="次日和 7 日留存率趋势"
            />
            <p class="note">按注册日期统计；第 1 / 第 7 天结束后才有数据。</p>
          </article>
          <article class="card">
            <h3>AI 调用次数</h3>
            <TrendChart
              type="bar"
              :categories="labels"
              :series="[
                { name: '面试', data: data.ai_usage.map((a) => a.ai_interview_call_count), color: SERIES_1 },
                { name: '简答题评分', data: data.ai_usage.map((a) => a.ai_scoring_call_count), color: SERIES_2 },
              ]"
              label="AI 面试与简答题评分调用次数"
            />
            <p class="note">与扣次口径一致，失败不计。</p>
          </article>
        </div>

        <article class="card">
          <h3>各路线学习人数与完成率</h3>
          <el-table :data="data.routes" empty-text="暂无已上架路线的数据" size="small">
            <el-table-column prop="route_name" label="路线" min-width="220" />
            <el-table-column prop="learner_count" label="学习人数" width="100" />
            <el-table-column label="平均已学进度" min-width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="Number(row.average_learned_progress)"
                  :stroke-width="8"
                  :format="(p: number) => `${p}%`"
                />
              </template>
            </el-table-column>
            <el-table-column label="平均已掌握进度" min-width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="Number(row.average_mastered_progress)"
                  :stroke-width="8"
                  color="#2a78d6"
                  :format="(p: number) => `${p}%`"
                />
              </template>
            </el-table-column>
          </el-table>
          <p class="note">时点指标，取所选范围最后一天的值；分母为学过该路线的学员。</p>
        </article>

        <div class="grid">
          <article class="card">
            <h3>错误率最高的题目（前 20）</h3>
            <el-table
              :data="data.top_wrong_questions"
              empty-text="暂无作答满 10 次的题目"
              size="small"
              max-height="360"
            >
              <el-table-column type="index" label="#" width="44" />
              <el-table-column prop="stem_excerpt" label="题干" min-width="200" show-overflow-tooltip />
              <el-table-column prop="answer_count" label="作答" width="70" />
              <el-table-column label="错误率" width="80">
                <template #default="{ row }">{{ pct(row.wrong_rate) }}</template>
              </el-table-column>
            </el-table>
          </article>
          <article class="card">
            <h3>各阶段平均得分（AI 面试）</h3>
            <el-table :data="data.stage_scores" empty-text="暂无面试阶段得分" size="small" max-height="360">
              <el-table-column prop="stage_name" label="阶段" min-width="200" show-overflow-tooltip />
              <el-table-column prop="average_score" label="平均得分" width="100" />
            </el-table>
          </article>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}
.filters,
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
}
.filters .el-button + .el-button {
  margin-left: 0;
}
.filters :deep(.el-date-editor.dates) {
  flex: 0 0 auto;
  width: 260px;
}
.refreshed {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.content {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 300px;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--space-sm);
}
.tile {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-md);
  background: var(--color-cotton);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.tile-label {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.tile-value {
  color: var(--color-ink);
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: var(--space-md);
}
.card {
  min-width: 0;
  padding: var(--space-md);
  background: var(--color-cotton);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.card h3 {
  margin: 0 0 var(--space-sm);
  font-size: var(--text-sm);
}
.note {
  margin: var(--space-xs) 0 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
@media (max-width: 520px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
