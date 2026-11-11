<script setup lang="ts">
// 编排路线（需求 6.3，A-CNT-16~27）：路线信息、阶段增删改排序、阶段内引用课程与排序
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ApiError, type RouteAdminView, type StageAdminView, type StageCourseView } from '@yunshu/api'
import { routeApi } from '@/api'
import CoursePickerDialog from '@/components/CoursePickerDialog.vue'
import RoutePreviewDialog from '@/components/RoutePreviewDialog.vue'
import { errorText } from '@/utils/format'

const currentRoute = useRoute()
const router = useRouter()
const routeId = Number(currentRoute.params.id)

const route = ref<RouteAdminView | null>(null)
const stages = ref<StageAdminView[]>([])
const loading = ref(false)
const loadError = ref('')
const busy = ref(false)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const detail = await routeApi.detail(routeId)
    route.value = detail.route
    stages.value = detail.stages
    Object.assign(info, { name: detail.route.name, intro: detail.route.intro ?? '', sort_no: detail.route.sort_no })
  } catch (e) {
    loadError.value =
      e instanceof ApiError && e.code === 'NOT_FOUND' ? '路线不存在或已被删除' : errorText(e, '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 统一处理写操作：显示进行中状态，失败提示，成功后重新加载 */
async function run(action: () => Promise<unknown>, success?: string) {
  busy.value = true
  try {
    await action()
    if (success) ElMessage.success(success)
    await load()
    return true
  } catch (e) {
    ElMessage.error(errorText(e))
    return false
  } finally {
    busy.value = false
  }
}

/* ---------------- 路线信息 ---------------- */
const infoRef = ref<FormInstance>()
const info = reactive({ name: '', intro: '', sort_no: 1 })
const infoRules: FormRules = {
  name: [
    { required: true, whitespace: true, message: '请填写路线名称', trigger: 'blur' },
    { max: 100, message: '路线名称最多 100 个字符', trigger: 'blur' },
  ],
  intro: [{ max: 1000, message: '简介最多 1000 个字符', trigger: 'blur' }],
}
const infoChanged = computed(
  () =>
    !!route.value &&
    (info.name.trim() !== route.value.name ||
      info.intro.trim() !== (route.value.intro ?? '') ||
      info.sort_no !== route.value.sort_no),
)

async function saveInfo() {
  if (!(await infoRef.value?.validate().catch(() => false))) return
  await run(
    () => routeApi.update(routeId, { name: info.name.trim(), intro: info.intro.trim(), sort_no: info.sort_no }),
    '路线信息已保存',
  )
}

async function toggleStatus() {
  if (!route.value) return
  const offShelf = route.value.status === 'on_shelf'
  const confirmed = await ElMessageBox.confirm(
    offShelf ? '下架后学员端不再显示该路线，学习记录和报告照常可看。' : '上架后学员可以在学员端看到并学习这条路线。',
    offShelf ? '下架路线' : '上架路线',
    { confirmButtonText: offShelf ? '下架' : '上架', cancelButtonText: '取消', type: offShelf ? 'warning' : 'info' },
  ).catch(() => false)
  if (!confirmed) return
  busy.value = true
  try {
    await routeApi.changeStatus(routeId, offShelf ? 'off_shelf' : 'on_shelf')
    // 下架用红色提示，与上架区分
    if (offShelf) ElMessage.error('已下架')
    else ElMessage.success('已上架')
    await load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'ROUTE_PUBLISH_INVALID') {
      const reasons = e.errors.map((item) => (item as { reason?: string }).reason).filter(Boolean)
      ElMessageBox.alert(reasons.join('；'), '暂不能上架', { confirmButtonText: '知道了', type: 'warning' }).catch(
        () => undefined,
      )
    } else {
      ElMessage.error(errorText(e))
    }
  } finally {
    busy.value = false
  }
}

/* ---------------- 阶段 ---------------- */
const stageVisible = ref(false)
const editingStage = ref<StageAdminView | null>(null)
const stageRef = ref<FormInstance>()
const stageForm = reactive({ name: '', intro: '' })
const stageRules: FormRules = {
  name: [
    { required: true, whitespace: true, message: '请填写阶段名称', trigger: 'blur' },
    { max: 100, message: '阶段名称最多 100 个字符', trigger: 'blur' },
  ],
  intro: [{ max: 1000, message: '简介最多 1000 个字符', trigger: 'blur' }],
}

function openStage(stage?: StageAdminView) {
  editingStage.value = stage ?? null
  Object.assign(stageForm, { name: stage?.name ?? '', intro: stage?.intro ?? '' })
  stageVisible.value = true
}

async function saveStage() {
  if (!(await stageRef.value?.validate().catch(() => false))) return
  const body = { name: stageForm.name.trim(), intro: stageForm.intro.trim() }
  const ok = editingStage.value
    ? await run(() => routeApi.updateStage(editingStage.value!.id, body), '阶段已保存')
    : await run(
        () =>
          routeApi.createStage(routeId, {
            ...body,
            sort_no: Math.max(0, ...stages.value.map((s) => s.sort_no)) + 1,
          }),
        '阶段已新增',
      )
  if (ok) stageVisible.value = false
}

async function removeStage(stage: StageAdminView) {
  const confirmed = await ElMessageBox.confirm(
    `确定删除阶段「${stage.name}」吗？${stage.courses.length ? `其中引用的 ${stage.courses.length} 门课程会移出本路线（课程本身保留）。` : ''}`,
    '删除阶段',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await run(() => routeApi.removeStage(stage.id), '阶段已删除')
}

/** 上移、下移：交换位置后按 1、2、3… 重新编号 */
function swapped<T>(list: T[], index: number, offset: -1 | 1) {
  const next = [...list]
  ;[next[index], next[index + offset]] = [next[index + offset], next[index]]
  return next
}

function moveStage(index: number, offset: -1 | 1) {
  const items = swapped(stages.value, index, offset).map((s, i) => ({ id: s.id, sort_no: i + 1 }))
  run(() => routeApi.sortStages(routeId, items))
}

/* ---------------- 阶段内课程 ---------------- */
function moveCourse(stage: StageAdminView, index: number, offset: -1 | 1) {
  const items = swapped(stage.courses, index, offset).map((c, i) => ({ id: c.course_id, sort_no: i + 1 }))
  run(() => routeApi.sortCourses(stage.id, items))
}

async function removeCourse(stage: StageAdminView, course: StageCourseView) {
  const confirmed = await ElMessageBox.confirm(
    `把「${course.title}」移出阶段「${stage.name}」吗？课程自身和学员的学习状态保留，进度按新的编排计算。`,
    '移出课程',
    { confirmButtonText: '移出', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await run(() => routeApi.removeCourse(stage.id, course.course_id), '已移出')
}

const pickerVisible = ref(false)
const pickerStage = ref<StageAdminView | null>(null)
const used = computed(() => {
  const map = new Map<number, { stageId: number; stageName: string }>()
  for (const stage of stages.value) {
    for (const course of stage.courses) map.set(course.course_id, { stageId: stage.id, stageName: stage.name })
  }
  return map
})

function openPicker(stage: StageAdminView) {
  pickerStage.value = stage
  pickerVisible.value = true
}

async function addCourses(courseIds: number[]) {
  if (!pickerStage.value) return
  const ok = await run(() => routeApi.addCourses(pickerStage.value!.id, courseIds), `已引用 ${courseIds.length} 门课程`)
  if (ok) pickerVisible.value = false
}

const previewId = ref<number | null>(null)
const courseTotal = computed(() => stages.value.reduce((n, s) => n + s.courses.length, 0))
</script>

<template>
  <section class="page">
    <div class="head">
      <el-button link @click="router.push('/routes')">← 返回学习路线</el-button>
      <div v-if="route" class="head-actions">
        <el-tag :type="route.status === 'on_shelf' ? 'success' : 'info'">
          {{ route.status === 'on_shelf' ? '已上架' : '已下架' }}
        </el-tag>
        <el-button @click="previewId = routeId">预览</el-button>
        <el-button :type="route.status === 'on_shelf' ? 'warning' : 'primary'" :loading="busy" @click="toggleStatus">
          {{ route.status === 'on_shelf' ? '下架' : '上架' }}
        </el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <template v-if="route">
      <el-card shadow="never" class="card">
        <template #header><b>路线信息</b></template>
        <el-form ref="infoRef" :model="info" :rules="infoRules" label-position="top" class="info">
          <el-form-item label="路线名称" prop="name" class="name">
            <el-input v-model="info.name" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="排序号（越小越靠前）" prop="sort_no">
            <el-input-number v-model="info.sort_no" :step="1" :precision="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="简介" prop="intro" class="full">
            <el-input
              v-model="info.intro"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 6 }"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
        </el-form>
        <el-button type="primary" :disabled="!infoChanged" :loading="busy" @click="saveInfo">保存路线信息</el-button>
      </el-card>

      <div class="section-head">
        <h3>
          阶段与课程 <span class="count">{{ stages.length }} 个阶段 · {{ courseTotal }} 门课程</span>
        </h3>
        <el-button type="primary" plain @click="openStage()">新增阶段</el-button>
      </div>
      <p class="hint">同一条路线内，一门课程只能出现在一个阶段；已下架的课程在学员端不显示，也不计入进度。</p>

      <div v-loading="loading || busy" class="stages">
        <el-empty v-if="!stages.length" description="还没有阶段，点击右上角新增" :image-size="72" />
        <article v-for="(stage, si) in stages" :key="stage.id" class="stage">
          <header class="stage-head">
            <span class="stage-no">阶段 {{ si + 1 }}</span>
            <div class="stage-title">
              <b>{{ stage.name }}</b>
              <span v-if="stage.intro" class="stage-intro">{{ stage.intro }}</span>
            </div>
            <span class="count">{{ stage.courses.length }} 门课程</span>
            <div class="stage-ops">
              <el-button size="small" :disabled="si === 0" @click="moveStage(si, -1)">上移</el-button>
              <el-button size="small" :disabled="si === stages.length - 1" @click="moveStage(si, 1)">下移</el-button>
              <el-button size="small" @click="openStage(stage)">编辑</el-button>
              <el-button size="small" type="primary" @click="openPicker(stage)">引用课程</el-button>
              <el-button size="small" type="danger" plain @click="removeStage(stage)">删除</el-button>
            </div>
          </header>
          <ol v-if="stage.courses.length" class="courses">
            <li v-for="(course, ci) in stage.courses" :key="course.course_id" class="course">
              <span class="course-no">{{ String(ci + 1).padStart(2, '0') }}</span>
              <span class="course-title">{{ course.title }}</span>
              <span class="course-meta">{{ course.subject_names.join('、') }}</span>
              <el-tag v-if="course.status === 'off_shelf'" size="small" type="info">已下架</el-tag>
              <el-tag v-if="!course.practice_question_count" size="small" type="warning">未配题</el-tag>
              <span v-else class="course-meta">刷题 {{ course.practice_question_count }}</span>
              <span class="course-ops">
                <el-button link :disabled="ci === 0" @click="moveCourse(stage, ci, -1)">上移</el-button>
                <el-button link :disabled="ci === stage.courses.length - 1" @click="moveCourse(stage, ci, 1)"
                  >下移</el-button
                >
                <el-button link type="danger" @click="removeCourse(stage, course)">移出</el-button>
              </span>
            </li>
          </ol>
          <p v-else class="no-course">还没有课程，点击"引用课程"从课程库中选择</p>
        </article>
      </div>
    </template>

    <el-dialog v-model="stageVisible" :title="editingStage ? '编辑阶段' : '新增阶段'" width="480px" destroy-on-close>
      <el-form ref="stageRef" :model="stageForm" :rules="stageRules" label-position="top" @submit.prevent="saveStage">
        <el-form-item label="阶段名称" prop="name">
          <el-input v-model="stageForm.name" maxlength="100" show-word-limit placeholder="如 Python 编程基础" />
        </el-form-item>
        <el-form-item label="简介" prop="intro">
          <el-input
            v-model="stageForm.intro"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <p v-if="!editingStage" class="hint">新阶段排在最后，可用上移、下移调整顺序。</p>
        <button type="submit" hidden />
      </el-form>
      <template #footer>
        <el-button @click="stageVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="saveStage">保存</el-button>
      </template>
    </el-dialog>

    <CoursePickerDialog
      v-if="pickerStage"
      v-model="pickerVisible"
      :stage-id="pickerStage.id"
      :stage-name="pickerStage.name"
      :used="used"
      :saving="busy"
      @confirm="addCourses"
    />
    <RoutePreviewDialog :route-id="previewId" @close="previewId = null" />
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
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}
.head-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.head-actions .el-button + .el-button {
  margin-left: 0;
}
.card {
  border-color: var(--color-rule);
}
.info {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  column-gap: var(--space-lg);
}
.info .full {
  grid-column: 1 / -1;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-head h3 {
  margin: 0;
  font-size: 16px;
}
.count {
  margin-left: var(--space-xs);
  color: var(--color-muted);
  font-size: var(--text-xs);
  font-weight: 400;
  white-space: nowrap;
}
.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.stages {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 120px;
}
.stage {
  background: var(--color-cotton);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.stage-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-rule);
}
.stage-no {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
  color: var(--color-accent-2);
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
}
.stage-title {
  flex: 1;
  min-width: 160px;
  display: flex;
  flex-direction: column;
}
.stage-intro {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.stage-ops {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2xs);
}
.stage-ops .el-button + .el-button {
  margin-left: 0;
}
.courses {
  margin: 0;
  padding: 0;
  list-style: none;
}
.course {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px var(--space-md);
  border-bottom: 1px solid var(--color-rule);
}
.course:last-child {
  border-bottom: 0;
}
.course-no {
  width: 24px;
  color: var(--color-muted);
  font-variant-numeric: tabular-nums;
}
.course-title {
  flex: 1;
  min-width: 200px;
}
.course-meta {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.course-ops .el-button + .el-button {
  margin-left: var(--space-xs);
}
.no-course {
  margin: 0;
  padding: var(--space-md);
  color: var(--color-muted);
  font-size: var(--text-xs);
}
@media (max-width: 720px) {
  .info {
    grid-template-columns: 1fr;
  }
}
</style>
