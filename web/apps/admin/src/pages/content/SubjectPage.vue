<script setup lang="ts">
// 学科管理（需求 6.1，A-CNT-01~05）：名称唯一；下面有课程时不能删除；排序号越小越靠前
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ApiError, type SubjectView } from '@yunshu/api'
import { subjectApi } from '@/api'
import { errorText, fieldErrors } from '@/utils/format'

const subjects = ref<SubjectView[]>([])
const loading = ref(false)
const loadError = ref('')
const sorting = ref(false)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    subjects.value = (await subjectApi.list()).items
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ---------------- 新增、编辑 ---------------- */
const formVisible = ref(false)
const editing = ref<SubjectView | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const serverErrors = ref<Record<string, string>>({})
const form = reactive({ name: '', intro: '', sort_no: 1 })

const rules: FormRules = {
  name: [
    { required: true, whitespace: true, message: '请填写学科名称', trigger: 'blur' },
    { max: 50, message: '学科名称最多 50 个字符', trigger: 'blur' },
  ],
  intro: [{ max: 500, message: '简介最多 500 个字符', trigger: 'blur' }],
  sort_no: [{ required: true, message: '请填写排序号', trigger: 'blur' }],
}

function openForm(subject?: SubjectView) {
  editing.value = subject ?? null
  serverErrors.value = {}
  const nextSort = Math.max(0, ...subjects.value.map((s) => s.sort_no)) + 1
  Object.assign(
    form,
    subject
      ? { name: subject.name, intro: subject.intro ?? '', sort_no: subject.sort_no }
      : { name: '', intro: '', sort_no: nextSort },
  )
  formVisible.value = true
}

async function save() {
  serverErrors.value = {}
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  const body = { name: form.name.trim(), intro: form.intro.trim(), sort_no: form.sort_no }
  try {
    if (editing.value) await subjectApi.update(editing.value.id, body)
    else await subjectApi.create(body)
    ElMessage.success('已保存')
    formVisible.value = false
    load()
  } catch (e) {
    serverErrors.value = fieldErrors(e)
    if (e instanceof ApiError && e.code === 'SUBJECT_NAME_EXISTS') {
      serverErrors.value = { name: '学科名称已存在' }
    } else if (!Object.keys(serverErrors.value).length) {
      ElMessage.error(errorText(e))
    }
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除 ---------------- */
async function remove(subject: SubjectView) {
  if (subject.course_count > 0) {
    await ElMessageBox.alert(
      `学科「${subject.name}」下仍有 ${subject.course_count} 门课程，不允许删除。请先在课程库中移走或删除这些课程。`,
      '无法删除',
      { confirmButtonText: '知道了', type: 'warning' },
    ).catch(() => undefined)
    return
  }
  const confirmed = await ElMessageBox.confirm(`确定删除学科「${subject.name}」吗？删除后不能恢复。`, '删除学科', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  try {
    await subjectApi.remove(subject.id)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error(errorText(e))
  }
  load()
}

/* ---------------- 上移、下移：交换位置后按 1、2、3… 重新编号 ---------------- */
async function move(index: number, offset: -1 | 1) {
  const list = [...subjects.value]
  const target = index + offset
  ;[list[index], list[target]] = [list[target], list[index]]
  sorting.value = true
  try {
    await subjectApi.sort(list.map((s, i) => ({ id: s.id, sort_no: i + 1 })))
    await load()
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    sorting.value = false
  }
}
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <p class="hint">学科是知识体系的分类，课程归属于学科。排序号越小越靠前，学员端按此顺序展示。</p>
      <el-button type="primary" @click="openForm()">新增学科</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-table v-loading="loading || sorting" :data="subjects" class="table" empty-text="还没有学科，点击右上角新增">
      <el-table-column prop="sort_no" label="排序号" width="90" />
      <el-table-column label="学科名称" min-width="160">
        <template #default="{ row }">
          <b>{{ row.name }}</b>
        </template>
      </el-table-column>
      <el-table-column label="简介" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">{{ row.intro || '—' }}</template>
      </el-table-column>
      <el-table-column prop="course_count" label="课程数" width="90" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row, $index }">
          <el-button link type="primary" @click="openForm(row as SubjectView)">编辑</el-button>
          <el-button link :disabled="$index === 0 || sorting" @click="move($index, -1)">上移</el-button>
          <el-button link :disabled="$index === subjects.length - 1 || sorting" @click="move($index, 1)"
            >下移</el-button
          >
          <el-button link type="danger" @click="remove(row as SubjectView)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="editing ? '编辑学科' : '新增学科'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="学科名称" prop="name" :error="serverErrors.name">
          <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="如 Python、大模型应用" />
        </el-form-item>
        <el-form-item label="简介" prop="intro" :error="serverErrors.intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序号（越小越靠前）" prop="sort_no" :error="serverErrors.sort_no">
          <el-input-number v-model="form.sort_no" :step="1" :precision="0" controls-position="right" />
        </el-form-item>
        <button type="submit" hidden />
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}
.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
</style>
