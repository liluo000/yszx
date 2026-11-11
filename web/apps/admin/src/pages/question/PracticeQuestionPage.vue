<script setup lang="ts">
// 刷题题库（需求 6.4、8.7，A-QST-01~07）
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  ApiError,
  type PracticeQuestionView,
  type QuestionOption,
  type QuestionType,
  type ShelfStatus,
  type SubjectView,
} from '@yunshu/api'
import { questionApi, subjectApi } from '@/api'
import CourseSelect from '@/components/CourseSelect.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import { errorText, fieldErrors } from '@/utils/format'

const TYPE_LABEL: Record<QuestionType, string> = {
  true_false: '判断题',
  single_choice: '单选题',
  multiple_choice: '多选题',
  short_answer: '简答题',
}
const typeOptions = Object.entries(TYPE_LABEL) as [QuestionType, string][]
const KEYS = 'ABCDEFGH'

/* ---------------- 列表与筛选 ---------------- */
const subjects = ref<SubjectView[]>([])
const filters = reactive<{ subjectId?: number; courseId?: number; type?: QuestionType; status?: ShelfStatus }>({})
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<PracticeQuestionView[]>([])
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await questionApi.practice.list({
      page: page.value,
      page_size: pageSize.value,
      subject_id: filters.subjectId,
      course_id: filters.courseId,
      question_type: filters.type,
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

const currentRoute = useRoute()
const router = useRouter()

/** 从纠错与反馈跳转过来时（?edit=题目 ID）直接打开该题目的编辑 */
async function openFromQuery() {
  const id = Number(currentRoute.query.edit)
  if (!id) return
  router.replace({ query: {} })
  try {
    await openForm(await questionApi.practice.get(id))
  } catch (e) {
    ElMessage.error(errorText(e, '题目不存在或已删除'))
  }
}

onMounted(async () => {
  load()
  openFromQuery()
  subjects.value = (await subjectApi.list().catch(() => ({ items: [] }))).items
})

/* ---------------- 新增、编辑 ---------------- */
const formVisible = ref(false)
const editing = ref<PracticeQuestionView | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const serverErrors = ref<Record<string, string>>({})
const form = reactive({
  course_id: undefined as number | undefined,
  question_type: 'single_choice' as QuestionType,
  stem: '',
  options: [] as QuestionOption[],
  single: '' as string,
  multiple: [] as string[],
  trueFalse: undefined as boolean | undefined,
  reference_answer: '',
  scoring_points: '',
  analysis: '',
  sort_no: 1,
})
const isChoice = computed(() => form.question_type === 'single_choice' || form.question_type === 'multiple_choice')

const rules: FormRules = {
  course_id: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
  stem: [
    { required: true, whitespace: true, message: '请填写题干', trigger: 'blur' },
    { max: 10000, message: '题干最多 10000 个字符', trigger: 'blur' },
  ],
  reference_answer: [
    {
      validator: (_: unknown, value: string, callback: (e?: Error) => void) =>
        form.question_type === 'short_answer' && !value.trim() ? callback(new Error('请填写参考答案')) : callback(),
      trigger: 'blur',
    },
  ],
  sort_no: [{ required: true, message: '请填写排序号', trigger: 'blur' }],
}

function emptyOptions(count = 4): QuestionOption[] {
  return Array.from({ length: count }, (_, i) => ({ key: KEYS[i], text: '' }))
}

async function nextSortNo(courseId?: number) {
  if (!courseId) return 1
  try {
    const all = await questionApi.practice.list({ course_id: courseId, page: 1, page_size: 1 })
    if (!all.total) return 1
    const last = await questionApi.practice.list({ course_id: courseId, page: all.total, page_size: 1 })
    return (last.items[0]?.sort_no ?? 0) + 1
  } catch {
    return 1
  }
}

async function openForm(question?: PracticeQuestionView) {
  editing.value = question ?? null
  serverErrors.value = {}
  if (question) {
    const answer = question.correct_answer
    Object.assign(form, {
      course_id: question.course_id,
      question_type: question.question_type,
      stem: question.stem,
      options: question.options ? question.options.map((o) => ({ ...o })) : emptyOptions(),
      single: Array.isArray(answer) && question.question_type === 'single_choice' ? answer[0] : '',
      multiple: Array.isArray(answer) && question.question_type === 'multiple_choice' ? [...answer] : [],
      trueFalse: typeof answer === 'boolean' ? answer : undefined,
      reference_answer: question.reference_answer ?? '',
      scoring_points: question.scoring_points ?? '',
      analysis: question.analysis ?? '',
      sort_no: question.sort_no,
    })
  } else {
    Object.assign(form, {
      course_id: filters.courseId,
      question_type: 'single_choice',
      stem: '',
      options: emptyOptions(),
      single: '',
      multiple: [],
      trueFalse: undefined,
      reference_answer: '',
      scoring_points: '',
      analysis: '',
      sort_no: await nextSortNo(filters.courseId),
    })
  }
  formVisible.value = true
}

async function onFormCourseChange(courseId: number | undefined) {
  if (!editing.value) form.sort_no = await nextSortNo(courseId)
}

function addOption() {
  if (form.options.length < 8) form.options.push({ key: KEYS[form.options.length], text: '' })
}

/** 删除选项后按 A、B、C… 重新编号，正确答案随之调整 */
function removeOption(index: number) {
  if (form.options.length <= 2) return
  const removed = form.options[index].key
  const remap = (key: string) =>
    KEYS[form.options.findIndex((o) => o.key === key) - (KEYS.indexOf(key) > index ? 1 : 0)]
  const single = form.single && form.single !== removed ? remap(form.single) : ''
  const multiple = form.multiple.filter((k) => k !== removed).map(remap)
  form.options.splice(index, 1)
  form.options.forEach((o, i) => (o.key = KEYS[i]))
  form.single = single
  form.multiple = multiple
}

function localCheck(): string | null {
  if (form.question_type === 'true_false' && form.trueFalse === undefined) return '请选择正确答案'
  if (isChoice.value) {
    if (form.options.some((o) => !o.text.trim())) return '选项内容不能为空'
    if (form.question_type === 'single_choice' && !form.single) return '请选择 1 个正确选项'
    if (form.question_type === 'multiple_choice' && form.multiple.length < 2) return '多选题至少需要 2 个正确选项'
  }
  return null
}

async function save() {
  serverErrors.value = {}
  if (!(await formRef.value?.validate().catch(() => false))) return
  const problem = localCheck()
  if (problem) {
    ElMessage.warning(problem)
    return
  }
  const type = form.question_type
  const body = {
    course_id: form.course_id!,
    question_type: type,
    stem: form.stem.trim(),
    options: isChoice.value ? form.options.map((o) => ({ key: o.key, text: o.text.trim() })) : null,
    correct_answer:
      type === 'true_false'
        ? form.trueFalse!
        : type === 'single_choice'
          ? [form.single]
          : type === 'multiple_choice'
            ? [...form.multiple].sort()
            : null,
    reference_answer: type === 'short_answer' ? form.reference_answer.trim() : '',
    scoring_points: type === 'short_answer' ? form.scoring_points.trim() : '',
    analysis: form.analysis.trim(),
    sort_no: form.sort_no,
  }
  saving.value = true
  try {
    if (editing.value) {
      await questionApi.practice.update(editing.value.id, body)
      ElMessage.success('已保存。学员历史作答保留原题快照，错题本和之后的作答使用新版本')
    } else {
      await questionApi.practice.create(body)
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
async function toggleStatus(question: PracticeQuestionView) {
  const offShelf = question.status === 'on_shelf'
  const confirmed = await ElMessageBox.confirm(
    offShelf ? '下架后不再被顺序练习、随机抽题、模拟考试抽取；学员的历史记录和错题保留。' : '上架后题目恢复使用。',
    offShelf ? '下架题目' : '上架题目',
    { confirmButtonText: offShelf ? '下架' : '上架', cancelButtonText: '取消', type: offShelf ? 'warning' : 'info' },
  ).catch(() => false)
  if (!confirmed) return
  try {
    await questionApi.practice.changeStatus(question.id, offShelf ? 'off_shelf' : 'on_shelf')
    // 下架用红色提示，与上架区分
    if (offShelf) ElMessage.error('已下架')
    else ElMessage.success('已上架')
    load()
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}

async function remove(question: PracticeQuestionView) {
  if (question.is_answered) {
    await ElMessageBox.alert('该题目已被学员作答过，不能删除，只能下架。', '无法删除，只能下架', {
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
    await questionApi.practice.remove(question.id)
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

/* ---------------- 批量导入 ---------------- */
const importVisible = ref(false)
const importRules = [
  '按第 1 行表头识别列，顺序不限：所属课程、题型、题干必填；其他列（如难度、标签）忽略',
  '所属课程填课程名称或课程 ID；有同名课程时请填课程 ID',
  '题型填判断、单选、多选、简答；选择题填"选项A"~"选项H"列，正确答案填字母，多选用逗号分隔（如 A,C）',
  '判断题正确答案填对或错；简答题参考答案填在"参考答案"列（也可填在"正确答案"列），评分要点选填',
  '不填排序号时，排在所属课程现有题目之后；导入的题目默认上架',
]

function answerText(q: PracticeQuestionView) {
  if (typeof q.correct_answer === 'boolean') return q.correct_answer ? '对' : '错'
  if (Array.isArray(q.correct_answer)) return q.correct_answer.join('、')
  return '—'
}
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
        <el-select v-model="filters.type" placeholder="全部题型" clearable class="filter" @change="search">
          <el-option v-for="[value, label] in typeOptions" :key="value" :label="label" :value="value" />
        </el-select>
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
      <el-table-column label="题干" min-width="320">
        <template #default="{ row }">
          <div class="stem">{{ row.stem }}</div>
        </template>
      </el-table-column>
      <el-table-column label="题型" width="90">
        <template #default="{ row }">
          <el-tag effect="plain">{{ TYPE_LABEL[row.question_type as QuestionType] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="答案" width="90">
        <template #default="{ row }">{{ answerText(row as PracticeQuestionView) }}</template>
      </el-table-column>
      <el-table-column prop="course_title" label="所属课程" min-width="180" show-overflow-tooltip />
      <el-table-column prop="sort_no" label="排序号" width="80" />
      <el-table-column label="作答记录" width="90">
        <template #default="{ row }">
          <span :class="{ muted: !row.is_answered }">{{ row.is_answered ? '有' : '无' }}</span>
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
          <el-button link type="primary" @click="openForm(row as PracticeQuestionView)">编辑</el-button>
          <el-button
            link
            :type="row.status === 'on_shelf' ? 'warning' : 'primary'"
            @click="toggleStatus(row as PracticeQuestionView)"
          >
            {{ row.status === 'on_shelf' ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row as PracticeQuestionView)">删除</el-button>
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

    <el-dialog v-model="formVisible" :title="editing ? '编辑题目' : '新增题目'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="two-col">
          <el-form-item label="所属课程" prop="course_id" :error="serverErrors.course_id">
            <CourseSelect
              v-model="form.course_id"
              :initial-label="editing?.course_title"
              class="full"
              @change="onFormCourseChange"
            />
          </el-form-item>
          <el-form-item label="题型" prop="question_type">
            <el-radio-group v-model="form.question_type">
              <el-radio-button v-for="[value, label] in typeOptions" :key="value" :value="value">
                {{ label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="题干" prop="stem" :error="serverErrors.stem">
          <el-input v-model="form.stem" type="textarea" :autosize="{ minRows: 3, maxRows: 10 }" maxlength="10000" />
        </el-form-item>

        <el-form-item
          v-if="form.question_type === 'true_false'"
          label="正确答案"
          :error="serverErrors.correct_answer"
          required
        >
          <el-radio-group v-model="form.trueFalse">
            <el-radio :value="true">对</el-radio>
            <el-radio :value="false">错</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          v-if="isChoice"
          :label="
            form.question_type === 'single_choice' ? '选项（勾选 1 个正确选项）' : '选项（勾选至少 2 个正确选项）'
          "
          :error="serverErrors.options || serverErrors.correct_answer"
          required
        >
          <div class="options">
            <div v-for="(option, index) in form.options" :key="option.key" class="option">
              <el-radio v-if="form.question_type === 'single_choice'" v-model="form.single" :value="option.key">
                {{ option.key }}
              </el-radio>
              <el-checkbox
                v-else
                :model-value="form.multiple.includes(option.key)"
                @change="
                  (v: unknown) =>
                    (form.multiple = v ? [...form.multiple, option.key] : form.multiple.filter((k) => k !== option.key))
                "
              >
                {{ option.key }}
              </el-checkbox>
              <el-input v-model="option.text" maxlength="1000" :placeholder="`选项 ${option.key} 内容`" />
              <el-button link type="danger" :disabled="form.options.length <= 2" @click="removeOption(index)"
                >删除</el-button
              >
            </div>
            <el-button size="small" :disabled="form.options.length >= 8" @click="addOption">添加选项</el-button>
          </div>
        </el-form-item>

        <template v-if="form.question_type === 'short_answer'">
          <el-form-item label="参考答案" prop="reference_answer" :error="serverErrors.reference_answer" required>
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
        </template>

        <el-form-item label="解析（选填）" :error="serverErrors.analysis">
          <el-input v-model="form.analysis" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" maxlength="10000" />
        </el-form-item>
        <el-form-item label="排序号（顺序练习按此排列，越小越靠前）" prop="sort_no">
          <el-input-number v-model="form.sort_no" :step="1" :precision="0" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importVisible"
      title="批量导入刷题题目"
      template-type="practice_question"
      template-name="刷题题目导入模板.xlsx"
      :rules="importRules"
      :upload="questionApi.practice.importQuestions"
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
  white-space: pre-line;
}
.muted {
  color: var(--color-muted);
}
.pager {
  justify-content: flex-end;
}
.two-col {
  display: grid;
  grid-template-columns: 1fr auto;
  column-gap: var(--space-md);
}
.full {
  width: 100%;
}
.options {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  width: 100%;
}
.option {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.option .el-radio,
.option .el-checkbox {
  margin-right: 0;
  width: 44px;
}
@media (max-width: 720px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
