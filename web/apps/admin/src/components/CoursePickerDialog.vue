<script setup lang="ts">
// 为阶段引用课程（A-CNT-25）：同一路线内已被其他阶段引用的课程不能再选
import { computed, reactive, ref, watch } from 'vue'
import type { CourseAdminView, SubjectView } from '@yunshu/api'
import { courseApi, subjectApi } from '@/api'
import { errorText } from '@/utils/format'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{
  stageName: string
  /** 本路线已引用的课程：课程 ID → 所在阶段名称 */
  used: Map<number, { stageId: number; stageName: string }>
  stageId: number
  saving: boolean
}>()
const emit = defineEmits<{ confirm: [courseIds: number[]] }>()

const subjects = ref<SubjectView[]>([])
const filters = reactive<{ keyword: string; subjectId?: number }>({ keyword: '' })
const page = ref(1)
const total = ref(0)
const rows = ref<CourseAdminView[]>([])
const loading = ref(false)
const error = ref('')
const selected = ref<Map<number, string>>(new Map())

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await courseApi.list({
      page: page.value,
      page_size: 20,
      keyword: filters.keyword.trim() || undefined,
      subject_id: filters.subjectId,
    })
    rows.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = errorText(e, '课程加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

watch(visible, async (open) => {
  if (!open) return
  selected.value = new Map()
  filters.keyword = ''
  filters.subjectId = undefined
  page.value = 1
  load()
  if (!subjects.value.length) {
    subjects.value = (await subjectApi.list().catch(() => ({ items: [] }))).items
  }
})

function usedElsewhere(course: CourseAdminView) {
  const use = props.used.get(course.id)
  return use && use.stageId !== props.stageId ? use.stageName : null
}
function inThisStage(course: CourseAdminView) {
  return props.used.get(course.id)?.stageId === props.stageId
}
function toggle(course: CourseAdminView, checked: boolean) {
  const next = new Map(selected.value)
  if (checked) next.set(course.id, course.title)
  else next.delete(course.id)
  selected.value = next
}
const selectedTitles = computed(() => [...selected.value.values()])
</script>

<template>
  <el-dialog v-model="visible" :title="`引用课程到「${stageName}」`" width="720px" destroy-on-close>
    <div class="filters">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索课程名称"
        clearable
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
        class="subject"
        @change="search"
      >
        <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-button @click="search">查询</el-button>
    </div>
    <p class="hint">同一条路线内，已被其他阶段引用的课程不能重复引用；已下架的课程可以引用，但在学员端不显示。</p>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <div v-loading="loading" class="list">
      <label
        v-for="course in rows"
        :key="course.id"
        class="row"
        :class="{ disabled: usedElsewhere(course) || inThisStage(course) }"
      >
        <el-checkbox
          :model-value="inThisStage(course) || selected.has(course.id)"
          :disabled="!!usedElsewhere(course) || inThisStage(course)"
          @change="(v: unknown) => toggle(course, !!v)"
        />
        <span class="title">{{ course.title }}</span>
        <span class="meta">{{ course.subjects.map((s) => s.name).join('、') }}</span>
        <el-tag v-if="course.status === 'off_shelf'" size="small" type="info">已下架</el-tag>
        <el-tag v-if="inThisStage(course)" size="small" type="success">已在本阶段</el-tag>
        <el-tag v-else-if="usedElsewhere(course)" size="small">已在「{{ usedElsewhere(course) }}」</el-tag>
      </label>
      <el-empty v-if="!loading && !rows.length" description="没有匹配的课程" :image-size="56" />
    </div>
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      small
      class="pager"
      @current-change="load"
    />
    <template #footer>
      <span class="picked" :title="selectedTitles.join('\n')">已选 {{ selected.size }} 门</span>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="!selected.size"
        :loading="saving"
        @click="emit('confirm', [...selected.keys()])"
      >
        确定引用
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.keyword {
  width: 260px;
}
.subject {
  width: 180px;
}
.hint {
  margin: var(--space-sm) 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.list {
  min-height: 160px;
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px var(--space-md);
  border-bottom: 1px solid var(--color-rule);
  cursor: pointer;
}
.row:last-child {
  border-bottom: 0;
}
.row:hover {
  background: var(--color-paper);
}
.row.disabled {
  cursor: default;
  opacity: 0.6;
}
.title {
  flex: 1;
  min-width: 0;
}
.meta {
  color: var(--color-muted);
  font-size: var(--text-xs);
  white-space: nowrap;
}
.pager {
  margin-top: var(--space-sm);
  justify-content: flex-end;
}
.picked {
  margin-right: auto;
  float: left;
  line-height: 32px;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
</style>
