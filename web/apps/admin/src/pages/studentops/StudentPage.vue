<script setup lang="ts">
// 学员管理（需求 6.7，A-STU-01~06）：列表筛选、详情、查看完整手机号、封禁解封、导出名单
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Education, StudentAdminItem, StudentQuery, StudentStatus, WorkYears } from '@yunshu/api'
import { routeApi, studentApi } from '@/api'
import { EDUCATION_LABEL, STUDENT_STATUS_LABEL, WORK_YEARS_LABEL } from '@/constants/students'
import { errorText, formatDateTime, saveBlob } from '@/utils/format'

const educationOptions = Object.entries(EDUCATION_LABEL) as [Education, string][]
const workYearsOptions = Object.entries(WORK_YEARS_LABEL) as [WorkYears, string][]
const statusOptions = Object.entries(STUDENT_STATUS_LABEL) as [StudentStatus, string][]

const routes = ref<{ id: number; name: string }[]>([])
const filters = reactive({
  keyword: '',
  registered: null as [string, string] | null,
  active: null as [string, string] | null,
  jobIntent: undefined as number | undefined,
  education: undefined as Education | undefined,
  workYears: undefined as WorkYears | undefined,
  learnedRoute: undefined as number | undefined,
  hasInterview: undefined as 'yes' | 'no' | undefined,
  status: undefined as StudentStatus | undefined,
})
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<StudentAdminItem[]>([])
const loading = ref(false)
const loadError = ref('')

function query(): StudentQuery {
  return {
    keyword: filters.keyword.trim() || undefined,
    registered_start_date: filters.registered?.[0],
    registered_end_date: filters.registered?.[1],
    active_start_date: filters.active?.[0],
    active_end_date: filters.active?.[1],
    job_intent_learning_route_id: filters.jobIntent,
    education: filters.education,
    work_years: filters.workYears,
    learned_route_id: filters.learnedRoute,
    has_interview: filters.hasInterview === undefined ? undefined : filters.hasInterview === 'yes',
    status: filters.status,
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await studentApi.list({ ...query(), page: page.value, page_size: pageSize.value })
    rows.value = result.items
    total.value = result.total
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  Object.assign(filters, {
    keyword: '',
    registered: null,
    active: null,
    jobIntent: undefined,
    education: undefined,
    workYears: undefined,
    learnedRoute: undefined,
    hasInterview: undefined,
    status: undefined,
  })
  search()
}

onMounted(async () => {
  load()
  routes.value = (await routeApi.options().catch(() => ({ items: [] }))).items
})

/* ---------------- 导出 ---------------- */
const exporting = ref(false)
async function exportStudents() {
  const confirmed = await ElMessageBox.confirm(
    '将按当前筛选条件导出学员名单（含完整手机号），注销中的账号不导出，单次最多 5000 条。本次导出会记入导出记录和操作日志。',
    '导出学员名单',
    { confirmButtonText: '导出', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  exporting.value = true
  try {
    const { blob, filename } = await studentApi.export(query())
    saveBlob(blob, filename ?? '学员名单.xlsx')
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(errorText(e, '导出失败，请稍后重试'))
  } finally {
    exporting.value = false
  }
}

/* ---------------- 详情：资料、完整手机号、封禁解封 ---------------- */
const detailVisible = ref(false)
const detail = ref<StudentAdminItem | null>(null)
const fullPhone = ref('')
const busy = ref(false)

async function openDetail(student: StudentAdminItem) {
  fullPhone.value = ''
  detail.value = student
  detailVisible.value = true
  try {
    detail.value = await studentApi.get(student.id)
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}

async function revealPhone() {
  if (!detail.value) return
  busy.value = true
  try {
    fullPhone.value = (await studentApi.revealPhone(detail.value.id)).phone
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    busy.value = false
  }
}

async function refreshDetail() {
  if (!detail.value) return
  detail.value = await studentApi.get(detail.value.id)
  load()
}

async function ban() {
  if (!detail.value) return
  const result = await ElMessageBox.prompt(
    '封禁后该学员无法登录；进行中的模拟考试和 AI 面试不能再继续，到截止时间后自动结束，次数不退还。',
    `封禁「${detail.value.nickname}」`,
    {
      confirmButtonText: '封禁',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请填写封禁原因（最多 200 字），将记入操作日志',
      inputValidator: (v: string) =>
        !v || !v.trim() ? '请填写封禁原因' : v.trim().length > 200 ? '封禁原因最多 200 个字符' : true,
      type: 'warning',
    },
  ).catch(() => null)
  if (!result) return
  busy.value = true
  try {
    await studentApi.ban(detail.value.id, result.value.trim())
    ElMessage.error('已封禁')
    await refreshDetail()
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    busy.value = false
  }
}

async function unban() {
  if (!detail.value) return
  const confirmed = await ElMessageBox.confirm(`解封后「${detail.value.nickname}」可以正常登录。`, '解封学员', {
    confirmButtonText: '解封',
    cancelButtonText: '取消',
    type: 'info',
  }).catch(() => false)
  if (!confirmed) return
  busy.value = true
  try {
    await studentApi.unban(detail.value.id)
    ElMessage.success('已解封')
    await refreshDetail()
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    busy.value = false
  }
}

const statusType = (s: StudentStatus) => (s === 'normal' ? 'success' : s === 'banned' ? 'danger' : 'warning')
</script>

<template>
  <section class="page">
    <div class="filters">
      <el-input
        v-model="filters.keyword"
        placeholder="昵称或手机号后四位"
        clearable
        maxlength="30"
        class="keyword"
        aria-label="搜索学员"
        @keyup.enter="search"
        @clear="search"
      />
      <el-date-picker
        v-model="filters.registered"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="注册开始"
        end-placeholder="注册结束"
        range-separator="至"
        class="dates"
        @change="search"
      />
      <el-date-picker
        v-model="filters.active"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="活跃开始"
        end-placeholder="活跃结束"
        range-separator="至"
        class="dates"
        @change="search"
      />
      <el-select
        v-model="filters.jobIntent"
        placeholder="求职意向"
        clearable
        filterable
        class="filter"
        @change="search"
      >
        <el-option v-for="r in routes" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-select v-model="filters.education" placeholder="学历" clearable class="filter" @change="search">
        <el-option v-for="[v, l] in educationOptions" :key="v" :label="l" :value="v" />
      </el-select>
      <el-select v-model="filters.workYears" placeholder="工作年限" clearable class="filter" @change="search">
        <el-option v-for="[v, l] in workYearsOptions" :key="v" :label="l" :value="v" />
      </el-select>
      <el-select
        v-model="filters.learnedRoute"
        placeholder="学习过的路线"
        clearable
        filterable
        class="filter"
        @change="search"
      >
        <el-option v-for="r in routes" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-select v-model="filters.hasInterview" placeholder="是否参加过面试" clearable class="filter" @change="search">
        <el-option label="参加过" value="yes" />
        <el-option label="未参加" value="no" />
      </el-select>
      <el-select v-model="filters.status" placeholder="账号状态" clearable class="filter" @change="search">
        <el-option v-for="[v, l] in statusOptions" :key="v" :label="l" :value="v" />
      </el-select>
      <el-button @click="search">查询</el-button>
      <el-button link @click="reset">重置</el-button>
      <el-button class="export" :loading="exporting" @click="exportStudents">导出名单</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-table
      v-loading="loading"
      :data="rows"
      class="table"
      empty-text="暂无学员（学员端上线后，注册的学员会显示在这里）"
    >
      <el-table-column label="昵称" min-width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row as StudentAdminItem)">{{ row.nickname }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="phone_masked" label="手机号" width="130" />
      <el-table-column label="注册时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="最近活跃" width="160">
        <template #default="{ row }">{{ formatDateTime(row.last_active_at) }}</template>
      </el-table-column>
      <el-table-column label="学历" width="100">
        <template #default="{ row }">{{ row.education ? EDUCATION_LABEL[row.education as Education] : '—' }}</template>
      </el-table-column>
      <el-table-column label="求职意向" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.job_intent_name ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="工作年限" width="120">
        <template #default="{ row }">{{
          row.work_years ? WORK_YEARS_LABEL[row.work_years as WorkYears] : '—'
        }}</template>
      </el-table-column>
      <el-table-column prop="learned_route_count" label="学习路线数" width="100" />
      <el-table-column prop="interview_count" label="面试场次" width="90" />
      <el-table-column label="状态" width="90" fixed="right">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status as StudentStatus)">{{
            STUDENT_STATUS_LABEL[row.status as StudentStatus]
          }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      class="pager"
      @current-change="load"
      @size-change="search"
    />

    <el-drawer v-model="detailVisible" :title="detail?.nickname ?? '学员详情'" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)">{{ STUDENT_STATUS_LABEL[detail.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.ban_reason" label="封禁原因">{{ detail.ban_reason }}</el-descriptions-item>
          <el-descriptions-item label="手机号">
            <span class="phone">{{ fullPhone || detail.phone_masked }}</span>
            <el-button v-if="!fullPhone" link type="primary" :loading="busy" @click="revealPhone">查看</el-button>
            <span v-else class="hint">本次查看已记入操作日志</span>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatDateTime(detail.created_at) }}</el-descriptions-item>
          <el-descriptions-item label="最近活跃">{{ formatDateTime(detail.last_active_at) }}</el-descriptions-item>
          <el-descriptions-item label="学历">{{
            detail.education ? EDUCATION_LABEL[detail.education] : '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="求职意向">{{ detail.job_intent_name ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="工作年限">
            {{ detail.work_years ? WORK_YEARS_LABEL[detail.work_years] : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="学习路线数">{{ detail.learned_route_count }}</el-descriptions-item>
          <el-descriptions-item label="面试场次">{{ detail.interview_count }}</el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-button v-if="detail.status === 'normal'" type="danger" plain :loading="busy" @click="ban">封禁</el-button>
          <el-button v-else-if="detail.status === 'banned'" type="primary" :loading="busy" @click="unban"
            >解封</el-button
          >
          <span v-else class="hint">注销中的账号不提供封禁操作</span>
        </div>

        <el-tabs class="records">
          <el-tab-pane label="学习记录"><el-empty description="学员端上线后提供" :image-size="56" /></el-tab-pane>
          <el-tab-pane label="刷题记录"><el-empty description="学员端上线后提供" :image-size="56" /></el-tab-pane>
          <el-tab-pane label="面试记录"
            ><el-empty description="学员端上线后提供（含面试报告）" :image-size="56"
          /></el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
}
.filters .el-button + .el-button {
  margin-left: 0;
}
.keyword {
  width: 180px;
}
.filter {
  width: 140px;
}
.filters :deep(.el-date-editor.dates) {
  flex: 0 0 auto;
  width: 260px;
}
.export {
  margin-left: auto !important;
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.pager {
  justify-content: flex-end;
}
.phone {
  margin-right: var(--space-sm);
  font-variant-numeric: tabular-nums;
}
.hint {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.actions {
  margin: var(--space-md) 0;
}
.records {
  margin-top: var(--space-sm);
}
</style>
