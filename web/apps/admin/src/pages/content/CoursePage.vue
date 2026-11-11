<script setup lang="ts">
// 课程库（需求 6.2、8.7，A-CNT-06~13）
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ApiError, type CourseAdminView, type ShelfStatus, type SubjectView, type VideoPlatformView } from '@yunshu/api'
import { configApi, courseApi, subjectApi } from '@/api'
import ImportDialog from '@/components/ImportDialog.vue'
import { errorText, fieldErrors } from '@/utils/format'

/* ---------------- 学科、平台选项 ---------------- */
const subjects = ref<SubjectView[]>([])
const platforms = ref<VideoPlatformView[]>([])

async function loadOptions() {
  try {
    const [s, p] = await Promise.all([subjectApi.list(), configApi.videoPlatforms()])
    subjects.value = s.items
    platforms.value = p.items
  } catch (e) {
    ElMessage.error(errorText(e, '学科或视频平台加载失败'))
  }
}

/* ---------------- 列表与筛选 ---------------- */
const filters = reactive<{ keyword: string; subjectId?: number; status?: ShelfStatus; configured?: 'yes' | 'no' }>({
  keyword: '',
})
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<CourseAdminView[]>([])
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await courseApi.list({
      page: page.value,
      page_size: pageSize.value,
      subject_id: filters.subjectId,
      status: filters.status,
      has_questions: filters.configured === undefined ? undefined : filters.configured === 'yes',
      keyword: filters.keyword.trim() || undefined,
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

const currentRoute = useRoute()
const router = useRouter()

/** 从纠错与反馈跳转过来时（?edit=课程 ID）直接打开该课程的编辑 */
async function openFromQuery() {
  const id = Number(currentRoute.query.edit)
  if (!id) return
  router.replace({ query: {} })
  try {
    await openForm(await courseApi.get(id))
  } catch (e) {
    ElMessage.error(errorText(e, '课程不存在或已删除'))
  }
}

onMounted(async () => {
  load()
  await loadOptions()
  openFromQuery()
})

/* ---------------- 新增、编辑 ---------------- */
const formVisible = ref(false)
const editing = ref<CourseAdminView | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const serverErrors = ref<Record<string, string>>({})
const form = reactive({
  title: '',
  subject_ids: [] as number[],
  video_platform_id: undefined as number | undefined,
  video_url: '',
  intro: '',
  duration_minutes: undefined as number | undefined,
  sort_no: 1,
})

/** 停用的平台不能用于新建课程；编辑已有课程时仍显示它原来的平台 */
const platformOptions = computed(() =>
  platforms.value.filter((p) => p.status === 'enabled' || p.id === editing.value?.video_platform_id),
)
const selectedPlatform = computed(() => platforms.value.find((p) => p.id === form.video_platform_id))

/** 与后端规则一致：http/https，域名等于平台域名或其子域名；最终以后端校验为准 */
function urlBelongsTo(url: string, domains: string[]) {
  try {
    const parsed = new URL(url.trim())
    if (!['http:', 'https:'].includes(parsed.protocol)) return false
    const host = parsed.hostname.toLowerCase()
    return domains.some((d) => host === d || host.endsWith(`.${d}`))
  } catch {
    return false
  }
}

const rules: FormRules = {
  title: [
    { required: true, whitespace: true, message: '请填写课程名称', trigger: 'blur' },
    { max: 100, message: '课程名称最多 100 个字符', trigger: 'blur' },
  ],
  subject_ids: [{ type: 'array', required: true, min: 1, message: '请至少选择 1 个学科', trigger: 'change' }],
  video_platform_id: [{ required: true, message: '请选择视频平台', trigger: 'change' }],
  video_url: [
    { required: true, whitespace: true, message: '请填写视频链接', trigger: 'blur' },
    { max: 1000, message: '视频链接最多 1000 个字符', trigger: 'blur' },
    {
      validator: (_: unknown, value: string, callback: (e?: Error) => void) => {
        const platform = selectedPlatform.value
        if (!value || !platform || urlBelongsTo(value, platform.domains)) return callback()
        callback(new Error(`链接域名需属于「${platform.name}」：${platform.domains.join('、')}`))
      },
      trigger: 'blur',
    },
  ],
  intro: [{ max: 1000, message: '简介最多 1000 个字符', trigger: 'blur' }],
  sort_no: [{ required: true, message: '请填写排序号', trigger: 'blur' }],
}

/** 新课程默认排在最后：列表按排序号升序，取最后一门课程的排序号加 1 */
async function nextSortNo() {
  try {
    const all = await courseApi.list({ page: 1, page_size: 1 })
    if (!all.total) return 1
    const last = await courseApi.list({ page: all.total, page_size: 1 })
    return (last.items[0]?.sort_no ?? 0) + 1
  } catch {
    return 1
  }
}

async function openForm(course?: CourseAdminView) {
  editing.value = course ?? null
  serverErrors.value = {}
  const defaultPlatform = platforms.value.find((p) => p.status === 'enabled')?.id
  Object.assign(
    form,
    course
      ? {
          title: course.title,
          subject_ids: course.subjects.map((s) => s.id),
          video_platform_id: course.video_platform_id,
          video_url: course.video_url,
          intro: course.intro ?? '',
          duration_minutes: course.duration_minutes ?? undefined,
          sort_no: course.sort_no,
        }
      : {
          title: '',
          subject_ids: filters.subjectId ? [filters.subjectId] : [],
          video_platform_id: defaultPlatform,
          video_url: '',
          intro: '',
          duration_minutes: undefined,
          sort_no: await nextSortNo(),
        },
  )
  formVisible.value = true
}

const urlPlaceholder = 'https://www.bilibili.com/video/...'

function platformLabel(platform: VideoPlatformView) {
  return platform.status === 'enabled' ? platform.name : `${platform.name}（已停用）`
}

function onPlatformChange() {
  if (form.video_url) formRef.value?.validateField('video_url').catch(() => undefined)
}

async function save() {
  serverErrors.value = {}
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  const body = {
    title: form.title.trim(),
    subject_ids: [...form.subject_ids],
    video_platform_id: form.video_platform_id!,
    video_url: form.video_url.trim(),
    intro: form.intro.trim(),
    duration_minutes: form.duration_minutes ?? null,
    sort_no: form.sort_no,
  }
  try {
    if (editing.value) await courseApi.update(editing.value.id, body)
    else await courseApi.create(body)
    ElMessage.success('已保存')
    formVisible.value = false
    load()
  } catch (e) {
    serverErrors.value = fieldErrors(e)
    if (e instanceof ApiError && e.code === 'VIDEO_DOMAIN_NOT_ALLOWED') serverErrors.value.video_url = e.message
    if (!Object.keys(serverErrors.value).length) ElMessage.error(errorText(e))
  } finally {
    saving.value = false
  }
}

/* ---------------- 上架、下架 ---------------- */
async function toggleStatus(course: CourseAdminView) {
  const offShelf = course.status === 'on_shelf'
  const confirmed = await ElMessageBox.confirm(
    offShelf
      ? '下架后：引用它的路线中不再显示该课程，也不计入进度；关联的刷题和面试题目停用；学员的历史记录和错题保留。重新上架后全部恢复。'
      : '上架后课程将在引用它的路线中显示，关联题目恢复使用。',
    offShelf ? `下架「${course.title}」` : `上架「${course.title}」`,
    { confirmButtonText: offShelf ? '下架' : '上架', cancelButtonText: '取消', type: offShelf ? 'warning' : 'info' },
  ).catch(() => false)
  if (!confirmed) return
  try {
    await courseApi.changeStatus(course.id, offShelf ? 'off_shelf' : 'on_shelf')
    // 下架用红色提示，与上架区分
    if (offShelf) ElMessage.error('已下架')
    else ElMessage.success('已上架')
    load()
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}

/* ---------------- 删除：被路线引用、有题目或有学习记录时只能下架 ---------------- */
async function remove(course: CourseAdminView) {
  const confirmed = await ElMessageBox.confirm(`确定删除「${course.title}」吗？删除后不能恢复。`, '删除课程', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  try {
    await courseApi.remove(course.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'CONTENT_IN_USE') {
      const reasons = e.errors.map((item) => (item as { reason?: string }).reason).filter(Boolean)
      await ElMessageBox.alert(
        `课程需同时满足"未被路线引用、下面没有题目、没有学员学习记录"才能删除，可以改为下架。原因：${reasons.join('；')}。`,
        '无法删除，只能下架',
        { confirmButtonText: '知道了', type: 'warning' },
      ).catch(() => undefined)
    } else {
      ElMessage.error(errorText(e))
    }
  }
}

/* ---------------- 批量导入 ---------------- */
const importVisible = ref(false)
const importRules = [
  '按第 1 行表头识别列，顺序不限：课程名称、所属学科、视频平台（或"来源"）、视频链接（或"播放链接"）为必填，简介和时长（整数分钟）选填，其他列忽略',
  '学科和视频平台填写名称（忽略空格和大小写），须已存在；多个学科用逗号或顿号分隔；视频平台须已启用，链接域名须属于该平台',
  '导入的课程默认上架，按文件顺序排在现有课程之后；路线编排仍在学习路线页面操作',
]

function formatDuration(minutes: number | null) {
  if (minutes === null || minutes === undefined) return ''
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`
}
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索课程名称"
          clearable
          maxlength="100"
          class="keyword"
          aria-label="搜索课程名称"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-model="filters.subjectId"
          placeholder="全部学科"
          clearable
          filterable
          class="filter"
          @change="search"
        >
          <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter" @change="search">
          <el-option label="已上架" value="on_shelf" />
          <el-option label="已下架" value="off_shelf" />
        </el-select>
        <el-select v-model="filters.configured" placeholder="是否已配题" clearable class="filter" @change="search">
          <el-option label="已配题" value="yes" />
          <el-option label="未配题" value="no" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>
      <div class="actions">
        <el-button @click="importVisible = true">批量导入</el-button>
        <el-button type="primary" :disabled="!subjects.length" @click="openForm()">新增课程</el-button>
      </div>
    </div>
    <el-alert
      v-if="!loading && !subjects.length && !loadError"
      type="info"
      :closable="false"
      show-icon
      title="还没有学科，请先在「学科管理」中新增学科，再添加课程。"
    />

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" class="table" empty-text="暂无课程">
      <el-table-column label="课程名称" min-width="220">
        <template #default="{ row }">
          <a :href="row.video_url" target="_blank" rel="noopener noreferrer" class="title">{{ row.title }}</a>
          <div v-if="row.duration_minutes !== null" class="sub">{{ formatDuration(row.duration_minutes) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="学科" min-width="140">
        <template #default="{ row }">
          <div class="subjects">
            <el-tag v-for="s in row.subjects" :key="s.id" size="small" effect="plain" type="info">{{ s.name }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="视频平台" width="110">
        <template #default="{ row }">
          <el-tag effect="plain">{{ row.platform_name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="刷题数" width="90">
        <template #default="{ row }">
          <span v-if="row.practice_question_count">{{ row.practice_question_count }}</span>
          <el-tag v-else type="warning" size="small">未配题</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="interview_question_count" label="面试题数" width="90" />
      <el-table-column prop="route_reference_count" label="引用路线数" width="100" />
      <el-table-column prop="sort_no" label="排序号" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'on_shelf' ? 'success' : 'info'">
            {{ row.status === 'on_shelf' ? '已上架' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row as CourseAdminView)">编辑</el-button>
          <el-button
            link
            :type="row.status === 'on_shelf' ? 'warning' : 'primary'"
            @click="toggleStatus(row as CourseAdminView)"
          >
            {{ row.status === 'on_shelf' ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row as CourseAdminView)">删除</el-button>
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

    <el-dialog v-model="formVisible" :title="editing ? '编辑课程' : '新增课程'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="课程名称" prop="title" :error="serverErrors.title">
          <el-input v-model="form.title" maxlength="100" show-word-limit />
        </el-form-item>
        <div class="two-col">
          <el-form-item label="所属学科（可多选）" prop="subject_ids" :error="serverErrors.subject_ids">
            <el-select
              v-model="form.subject_ids"
              placeholder="请选择"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
            >
              <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="视频平台" prop="video_platform_id" :error="serverErrors.video_platform_id">
            <el-select v-model="form.video_platform_id" placeholder="请选择" @change="onPlatformChange">
              <el-option v-for="p in platformOptions" :key="p.id" :label="platformLabel(p)" :value="p.id" />
            </el-select>
            <div class="hint">平台列表在「系统配置 → 视频平台」中维护</div>
          </el-form-item>
        </div>
        <el-form-item label="视频链接" prop="video_url" :error="serverErrors.video_url">
          <el-input v-model="form.video_url" maxlength="1000" :placeholder="urlPlaceholder" />
          <div class="hint">可以是单个视频，也可以是合集或多 P 视频的入口；域名需属于所选平台</div>
        </el-form-item>
        <el-form-item label="简介" prop="intro" :error="serverErrors.intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <div class="two-col">
          <el-form-item label="时长（分钟）" prop="duration_minutes" :error="serverErrors.duration_minutes">
            <el-input-number
              v-model="form.duration_minutes"
              :min="0"
              :max="100000"
              :precision="0"
              controls-position="right"
              placeholder="选填"
            />
          </el-form-item>
          <el-form-item label="排序号（越小越靠前）" prop="sort_no" :error="serverErrors.sort_no">
            <el-input-number v-model="form.sort_no" :step="1" :precision="0" controls-position="right" />
          </el-form-item>
        </div>
        <p v-if="!editing" class="hint">新建的课程默认上架。</p>
        <button type="submit" hidden />
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importVisible"
      title="批量导入课程"
      template-type="course"
      template-name="课程导入模板.xlsx"
      :rules="importRules"
      :upload="courseApi.importCourses"
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
.keyword {
  width: 200px;
}
.filter {
  width: 140px;
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.title {
  color: var(--color-ink);
  font-weight: 600;
  text-decoration: none;
}
.title:hover {
  color: var(--color-accent-2);
  text-decoration: underline;
}
.subjects {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2xs);
}
.sub {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.pager {
  justify-content: flex-end;
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--space-md);
}
.two-col :deep(.el-select),
.two-col :deep(.el-input-number) {
  width: 100%;
}
.hint {
  margin: var(--space-2xs) 0 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
  line-height: 1.5;
}
</style>
