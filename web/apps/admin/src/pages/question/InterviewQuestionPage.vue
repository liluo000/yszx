<script setup lang="ts">
// 面试题库（需求 6.5、8.7，A-QST-08~14）
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ApiError, type InterviewQuestionView, type ShelfStatus, type SubjectView } from '@yunshu/api'
import { questionApi, subjectApi } from '@/api'
import CourseSelect from '@/components/CourseSelect.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import { errorText, fieldErrors } from '@/utils/format'

const subjects = ref<SubjectView[]>([])
const filters = reactive<{ subjectId?: number; courseId?: number; status?: ShelfStatus }>({})
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<InterviewQuestionView[]>([])
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await questionApi.interview.list({
      page: page.value,
      page_size: pageSize.value,
      subject_id: filters.subjectId,
      course_id: filters.courseId,
      status: filters.status,
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

function onSubjectChange() {
  filters.courseId = undefined
  search()
}

onMounted(async () => {
  load()
  subjects.value = (await subjectApi.list().catch(() => ({ items: [] }))).items
})

/* ---------------- 新增、编辑 ---------------- */
const formVisible = ref(false)
const editing = ref<InterviewQuestionView | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const serverErrors = ref<Record<string, string>>({})
const form = reactive({
  course_id: undefined as number | undefined,
  stem: '',
  reference_answer: '',
  scoring_points: '',
  followup_direction: '',
})
const rules: FormRules = {
  course_id: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
  stem: [{ required: true, whitespace: true, message: '请填写题干', trigger: 'blur' }],
  reference_answer: [{ required: true, whitespace: true, message: '请填写参考答案', trigger: 'blur' }],
}

function openForm(question?: InterviewQuestionView) {
  editing.value = question ?? null
  serverErrors.value = {}
  Object.assign(form, {
    course_id: question?.course_id ?? filters.courseId,
    stem: question?.stem ?? '',
    reference_answer: question?.reference_answer ?? '',
    scoring_points: question?.scoring_points ?? '',
    followup_direction: question?.followup_direction ?? '',
  })
  formVisible.value = true
}

async function save() {
  serverErrors.value = {}
  if (!(await formRef.value?.validate().catch(() => false))) return
  const body = {
    course_id: form.course_id!,
    stem: form.stem.trim(),
    reference_answer: form.reference_answer.trim(),
    scoring_points: form.scoring_points.trim(),
    followup_direction: form.followup_direction.trim(),
  }
  saving.value = true
  try {
    if (editing.value) {
      await questionApi.interview.update(editing.value.id, body)
      ElMessage.success('已保存，已生成的面试报告不受影响')
    } else {
      await questionApi.interview.create(body)
      ElMessage.success('已保存')
    }
    formVisible.value = false
    load()
  } catch (e) {
    serverErrors.value = fieldErrors(e)
    ElMessage.error(errorText(e))
  } finally {
    saving.value = false
  }
}

/* ---------------- 上架、下架、删除 ---------------- */
async function toggleStatus(question: InterviewQuestionView) {
  const offShelf = question.status === 'on_shelf'
  const confirmed = await ElMessageBox.confirm(
    offShelf ? '下架后不再被 AI 面试抽取；已生成的面试报告不受影响。' : '上架后题目恢复用于 AI 面试。',
    offShelf ? '下架题目' : '上架题目',
    { confirmButtonText: offShelf ? '下架' : '上架', cancelButtonText: '取消', type: offShelf ? 'warning' : 'info' },
  ).catch(() => false)
  if (!confirmed) return
  try {
    await questionApi.interview.changeStatus(question.id, offShelf ? 'off_shelf' : 'on_shelf')
    // 下架用红色提示，与上架区分
    if (offShelf) ElMessage.error('已下架')
    else ElMessage.success('已上架')
    load()
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}

async function remove(question: InterviewQuestionView) {
  if (question.is_drawn) {
    await ElMessageBox.alert('该题目已在面试中被抽取过，不能删除，只能下架。', '无法删除，只能下架', {
      confirmButtonText: '知道了',
      type: 'warning',
    }).catch(() => undefined)
    return
  }
  const confirmed = await ElMessageBox.confirm('确定删除该题目吗？删除后不能恢复。', '删除题目', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  try {
    await questionApi.interview.remove(question.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'CONTENT_IN_USE') {
      ElMessageBox.alert(e.message, '无法删除，只能下架', { type: 'warning' }).catch(() => undefined)
    } else {
      ElMessage.error(errorText(e))
    }
  }
}

const importVisible = ref(false)
const importRules = [
  '按第 1 行表头识别列，顺序不限：所属课程、题干、参考答案必填；评分要点、追问方向选填；其他列（如难度、主题、标签）忽略',
  '所属课程填课程名称或课程 ID；有同名课程时请填课程 ID',
  '导入的题目默认上架',
]
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <div class="filters">
        <el-select
          v-model="filters.subjectId"
          placeholder="全部学科"
          clearable
          filterable
          class="filter"
          @change="onSubjectChange"
        >
          <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <CourseSelect
          v-model="filters.courseId"
          :subject-id="filters.subjectId"
          placeholder="全部课程"
          clearable
          class="course-filter"
          @change="search"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter" @change="search">
          <el-option label="已上架" value="on_shelf" />
          <el-option label="已下架" value="off_shelf" />
        </el-select>
      </div>
      <div class="actions">
        <el-button @click="importVisible = true">批量导入</el-button>
        <el-button type="primary" @click="openForm()">新增题目</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" class="table" empty-text="暂无题目">
      <el-table-column type="expand">
        <template #default="{ row }">
          <dl class="detail">
            <dt>参考答案</dt>
            <dd>{{ row.reference_answer }}</dd>
            <dt>评分要点</dt>
            <dd>{{ row.scoring_points || '未填写（AI 评分只对照参考答案）' }}</dd>
            <dt>追问方向</dt>
            <dd>{{ row.followup_direction || '未填写' }}</dd>
          </dl>
        </template>
      </el-table-column>
      <el-table-column label="题干" min-width="320">
        <template #default="{ row }">
          <div class="stem">{{ row.stem }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="course_title" label="所属课程" min-width="200" show-overflow-tooltip />
      <el-table-column label="评分要点" width="90">
        <template #default="{ row }">
          <span :class="{ muted: !row.scoring_points }">{{ row.scoring_points ? '有' : '无' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="面试抽取" width="90">
        <template #default="{ row }">
          <span :class="{ muted: !row.is_drawn }">{{ row.is_drawn ? '已抽取' : '未抽取' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'on_shelf' ? 'success' : 'info'">
            {{ row.status === 'on_shelf' ? '已上架' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row as InterviewQuestionView)">编辑</el-button>
          <el-button
            link
            :type="row.status === 'on_shelf' ? 'warning' : 'primary'"
            @click="toggleStatus(row as InterviewQuestionView)"
          >
            {{ row.status === 'on_shelf' ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row as InterviewQuestionView)">删除</el-button>
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

    <el-dialog v-model="formVisible" :title="editing ? '编辑面试题' : '新增面试题'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="所属课程" prop="course_id" :error="serverErrors.course_id">
          <CourseSelect v-model="form.course_id" :initial-label="editing?.course_title" class="full" />
        </el-form-item>
        <el-form-item label="题干" prop="stem" :error="serverErrors.stem">
          <el-input v-model="form.stem" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" maxlength="10000" />
        </el-form-item>
        <el-form-item label="参考答案" prop="reference_answer" :error="serverErrors.reference_answer">
          <el-input
            v-model="form.reference_answer"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 10 }"
            maxlength="10000"
          />
        </el-form-item>
        <el-form-item
          label="评分要点（选填，供 AI 评分使用；不填时只对照参考答案）"
          :error="serverErrors.scoring_points"
        >
          <el-input
            v-model="form.scoring_points"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 8 }"
            maxlength="10000"
          />
        </el-form-item>
        <el-form-item label="追问方向（选填，AI 面试追问时参考）" :error="serverErrors.followup_direction">
          <el-input
            v-model="form.followup_direction"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            maxlength="10000"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importVisible"
      title="批量导入面试题目"
      template-type="interview_question"
      template-name="面试题目导入模板.xlsx"
      :rules="importRules"
      :upload="questionApi.interview.importQuestions"
      @imported="search"
    />
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
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.actions .el-button + .el-button {
  margin-left: 0;
}
.filter {
  width: 140px;
}
.course-filter {
  width: 240px;
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.stem {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.muted {
  color: var(--color-muted);
}
.pager {
  justify-content: flex-end;
}
.full {
  width: 100%;
}
.detail {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: var(--space-xs) var(--space-md);
  margin: 0;
  padding: var(--space-sm) var(--space-lg);
}
.detail dt {
  color: var(--color-muted);
}
.detail dd {
  margin: 0;
  white-space: pre-line;
  line-height: 1.6;
}
</style>
