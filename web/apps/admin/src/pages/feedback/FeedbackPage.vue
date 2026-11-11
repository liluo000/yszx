<script setup lang="ts">
// 纠错与反馈（需求 6.6，A-FDB-01、02）：待处理的同一道题或同一门课合并显示次数，可一次处理全部
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { FeedbackErrorType, FeedbackItem, FeedbackStatus, FeedbackType } from '@yunshu/api'
import { feedbackApi } from '@/api'
import CourseSelect from '@/components/CourseSelect.vue'
import { errorText, formatDateTime } from '@/utils/format'

const TYPE_LABEL: Record<FeedbackType, string> = { question_correction: '题目纠错', video_invalid: '视频失效' }
const STATUS_LABEL: Record<FeedbackStatus, string> = { pending: '待处理', corrected: '已修正', rejected: '已驳回' }
const ERROR_LABEL: Record<FeedbackErrorType, string> = {
  stem_error: '题干有误',
  answer_error: '答案有误',
  analysis_error: '解析有误',
  other: '其他',
}
const errorOptions = Object.entries(ERROR_LABEL) as [FeedbackErrorType, string][]

const router = useRouter()
const filters = reactive<{
  type: FeedbackType | ''
  status: FeedbackStatus | ''
  courseId?: number
  errorType?: FeedbackErrorType
  dates: [string, string] | null
}>({ type: '', status: 'pending', dates: null })
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<FeedbackItem[]>([])
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await feedbackApi.list({
      page: page.value,
      page_size: pageSize.value,
      feedback_type: filters.type || undefined,
      status: filters.status || undefined,
      course_id: filters.courseId,
      error_type: filters.errorType,
      start_date: filters.dates?.[0],
      end_date: filters.dates?.[1],
    })
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

function onTypeChange() {
  if (filters.type === 'video_invalid') filters.errorType = undefined
  search()
}

onMounted(load)

/** 跳转到题目或课程的编辑（新标签页，处理完可回到本页） */
function openTarget(item: FeedbackItem) {
  const target =
    item.feedback_type === 'question_correction' && item.practice_question_id
      ? router.resolve({ path: '/practice-questions', query: { edit: item.practice_question_id } })
      : router.resolve({ path: '/courses', query: { edit: item.course_id } })
  window.open(target.href, '_blank', 'noopener')
}

function targetText(item: FeedbackItem) {
  if (item.feedback_type === 'question_correction') return item.stem ?? '题目已删除'
  return item.course_title ?? '课程已删除'
}

/* ---------------- 处理 ---------------- */
const handleVisible = ref(false)
const handling = ref<FeedbackItem | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<{ status: 'corrected' | 'rejected'; note: string }>({ status: 'corrected', note: '' })
const rules: FormRules = {
  note: [
    { required: true, whitespace: true, message: '请填写处理说明', trigger: 'blur' },
    { max: 1000, message: '处理说明最多 1000 个字符', trigger: 'blur' },
  ],
}

function openHandle(item: FeedbackItem) {
  handling.value = item
  Object.assign(form, { status: 'corrected', note: '' })
  handleVisible.value = true
}

async function submitHandle() {
  if (!handling.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const result = await feedbackApi.handle(handling.value.feedback_ids, form.status, form.note.trim())
    ElMessage.success(`已处理 ${result.updated_count} 条反馈，处理结果已通过站内信通知提交的学员`)
    handleVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(errorText(e))
    load()
  } finally {
    saving.value = false
  }
}

const dialogTitle = computed(() =>
  handling.value && handling.value.submit_count > 1
    ? `处理反馈（共 ${handling.value.submit_count} 条，一次处理全部）`
    : '处理反馈',
)
</script>

<template>
  <section class="page">
    <div class="segments">
      <el-radio-group v-model="filters.type" @change="onTypeChange">
        <el-radio-button value="">全部类型</el-radio-button>
        <el-radio-button value="question_correction">题目纠错</el-radio-button>
        <el-radio-button value="video_invalid">视频失效反馈</el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="filters.status" @change="search">
        <el-radio-button value="pending">待处理</el-radio-button>
        <el-radio-button value="corrected">已修正</el-radio-button>
        <el-radio-button value="rejected">已驳回</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
    </div>
    <div class="filters">
      <CourseSelect
        v-model="filters.courseId"
        placeholder="全部课程"
        clearable
        class="course-filter"
        @change="search"
      />
      <el-select
        v-model="filters.errorType"
        placeholder="全部错误类型"
        clearable
        :disabled="filters.type === 'video_invalid'"
        class="filter"
        @change="search"
      >
        <el-option v-for="[value, label] in errorOptions" :key="value" :label="label" :value="value" />
      </el-select>
      <el-date-picker
        v-model="filters.dates"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="提交开始日期"
        end-placeholder="提交结束日期"
        range-separator="至"
        class="dates"
        @change="search"
      />
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" class="table" empty-text="暂无反馈">
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.feedback_type === 'video_invalid' ? 'warning' : 'primary'" effect="plain">
            {{ TYPE_LABEL[row.feedback_type as FeedbackType] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="对象" min-width="280">
        <template #default="{ row }">
          <el-button link type="primary" class="target" @click="openTarget(row as FeedbackItem)">
            {{ targetText(row as FeedbackItem) }}
          </el-button>
          <div v-if="row.feedback_type === 'question_correction'" class="sub">
            {{ row.course_title ?? '课程已删除' }}
          </div>
          <div v-else class="sub url">{{ row.video_url }}</div>
        </template>
      </el-table-column>
      <el-table-column label="错误类型" width="100">
        <template #default="{ row }">{{
          row.error_type ? ERROR_LABEL[row.error_type as FeedbackErrorType] : '—'
        }}</template>
      </el-table-column>
      <el-table-column label="说明" min-width="200">
        <template #default="{ row }">
          <div class="desc">{{ row.description || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="提交次数" width="90">
        <template #default="{ row }">
          <b>{{ row.submit_count }}</b> 次
        </template>
      </el-table-column>
      <el-table-column label="最近提交" width="170">
        <template #default="{ row }">
          <div>{{ row.student_nickname ?? '已注销用户' }}</div>
          <div class="sub">{{ formatDateTime(row.created_at) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'pending' ? 'warning' : row.status === 'corrected' ? 'success' : 'info'">
            {{ STATUS_LABEL[row.status as FeedbackStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作 / 处理说明" min-width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'pending'"
            type="primary"
            size="small"
            @click="openHandle(row as FeedbackItem)"
          >
            处理
          </el-button>
          <template v-else>
            <div class="desc">{{ row.handling_note }}</div>
            <div class="sub">{{ formatDateTime(row.handled_at) }}</div>
          </template>
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

    <el-dialog v-model="handleVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <div v-if="handling" class="summary">
        <div class="meta">
          <el-tag :type="handling.feedback_type === 'video_invalid' ? 'warning' : 'primary'" effect="plain">
            {{ TYPE_LABEL[handling.feedback_type] }}
          </el-tag>
          <span v-if="handling.error_type">{{ ERROR_LABEL[handling.error_type] }}</span>
          <span>{{ handling.course_title ?? '课程已删除' }}</span>
        </div>
        <p class="summary-target">{{ targetText(handling) }}</p>
        <p v-if="handling.description" class="summary-desc">最新说明：{{ handling.description }}</p>
        <el-button link type="primary" @click="openTarget(handling)">
          打开{{ handling.feedback_type === 'question_correction' ? '题目' : '课程' }}编辑（新标签页）
        </el-button>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="处理结果" required>
          <el-radio-group v-model="form.status">
            <el-radio value="corrected">已修正</el-radio>
            <el-radio value="rejected">已驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明" prop="note">
          <el-input
            v-model="form.note"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            maxlength="1000"
            show-word-limit
            placeholder="将通过站内信发送给提交的学员"
          />
        </el-form-item>
      </el-form>
      <p class="hint">每位提交的学员收到一条站内信；同一学员多次提交只发一条。</p>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.segments,
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.filter {
  width: 150px;
}
.course-filter {
  width: 240px;
}
.filters :deep(.el-date-editor.dates) {
  flex: 0 0 auto;
  width: 320px;
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.target {
  height: auto;
  padding: 0;
  white-space: normal;
  text-align: left;
  line-height: 1.5;
}
.sub {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.url {
  word-break: break-all;
}
.desc {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-line;
}
.pager {
  justify-content: flex-end;
}
.summary {
  margin-bottom: var(--space-md);
  padding: var(--space-md);
  background: var(--color-paper);
  border-radius: var(--radius);
}
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.summary-target {
  margin: var(--space-sm) 0 var(--space-xs);
  font-weight: 600;
  white-space: pre-line;
}
.summary-desc {
  margin: 0 0 var(--space-xs);
  color: var(--color-ink-2);
}
.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
</style>
