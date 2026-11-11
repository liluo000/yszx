<script setup lang="ts">
// 学习路线列表（需求 6.3，A-CNT-14~20）：新建默认下架，编排完成后再上架
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ApiError, type RouteAdminView, type ShelfStatus } from '@yunshu/api'
import { routeApi } from '@/api'
import RoutePreviewDialog from '@/components/RoutePreviewDialog.vue'
import { errorText } from '@/utils/format'

const router = useRouter()
const statusFilter = ref<ShelfStatus>()
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<RouteAdminView[]>([])
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await routeApi.list({ page: page.value, page_size: pageSize.value, status: statusFilter.value })
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

onMounted(load)

/* ---------------- 新增：创建后进入编排页 ---------------- */
const createVisible = ref(false)
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ name: '', intro: '', sort_no: 1 })
const rules: FormRules = {
  name: [
    { required: true, whitespace: true, message: '请填写路线名称', trigger: 'blur' },
    { max: 100, message: '路线名称最多 100 个字符', trigger: 'blur' },
  ],
  intro: [{ max: 1000, message: '简介最多 1000 个字符', trigger: 'blur' }],
  sort_no: [{ required: true, message: '请填写排序号', trigger: 'blur' }],
}

async function openCreate() {
  let next = 1
  try {
    const all = await routeApi.list({ page: 1, page_size: 1 })
    if (all.total) next = ((await routeApi.list({ page: all.total, page_size: 1 })).items[0]?.sort_no ?? 0) + 1
  } catch {
    // 取不到时用 1
  }
  Object.assign(form, { name: '', intro: '', sort_no: next })
  createVisible.value = true
}

async function create() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const route = await routeApi.create({ name: form.name.trim(), intro: form.intro.trim(), sort_no: form.sort_no })
    createVisible.value = false
    ElMessage.success('路线已创建（默认下架），请继续编排阶段与课程')
    router.push(`/routes/${route.id}`)
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    saving.value = false
  }
}

/* ---------------- 上架、下架、删除 ---------------- */
async function toggleStatus(route: RouteAdminView) {
  const offShelf = route.status === 'on_shelf'
  const confirmed = await ElMessageBox.confirm(
    offShelf
      ? '下架后：路线列表、搜索、继续学习、我的路线进度都不再显示该路线，不能发起新的模拟考试或 AI 面试；学习记录、面试记录和报告照常可看。'
      : '上架后学员可以在学员端看到并学习这条路线。',
    offShelf ? `下架「${route.name}」` : `上架「${route.name}」`,
    { confirmButtonText: offShelf ? '下架' : '上架', cancelButtonText: '取消', type: offShelf ? 'warning' : 'info' },
  ).catch(() => false)
  if (!confirmed) return
  try {
    await routeApi.changeStatus(route.id, offShelf ? 'off_shelf' : 'on_shelf')
    // 下架用红色提示，与上架区分
    if (offShelf) ElMessage.error('已下架')
    else ElMessage.success('已上架')
    load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'ROUTE_PUBLISH_INVALID') {
      const reasons = e.errors.map((item) => (item as { reason?: string }).reason).filter(Boolean)
      ElMessageBox.alert(reasons.join('；'), '暂不能上架', { confirmButtonText: '知道了', type: 'warning' }).catch(
        () => undefined,
      )
    } else {
      ElMessage.error(errorText(e))
    }
  }
}

async function remove(route: RouteAdminView) {
  const confirmed = await ElMessageBox.confirm(
    `确定删除「${route.name}」吗？阶段和课程引用会一起删除（课程本身保留），删除后不能恢复。`,
    '删除路线',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  try {
    await routeApi.remove(route.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'CONTENT_IN_USE') {
      ElMessageBox.alert(`${e.message}。`, '无法删除，只能下架', {
        confirmButtonText: '知道了',
        type: 'warning',
      }).catch(() => undefined)
    } else {
      ElMessage.error(errorText(e))
    }
  }
}

const previewId = ref<number | null>(null)
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="filter" @change="search">
        <el-option label="已上架" value="on_shelf" />
        <el-option label="已下架" value="off_shelf" />
      </el-select>
      <el-button type="primary" @click="openCreate">新增路线</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" class="table" empty-text="还没有学习路线，点击右上角新增">
      <el-table-column prop="sort_no" label="排序号" width="80" />
      <el-table-column label="路线名称" min-width="220">
        <template #default="{ row }">
          <RouterLink :to="`/routes/${row.id}`" class="name">{{ row.name }}</RouterLink>
          <div v-if="row.intro" class="intro">{{ row.intro }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="stage_count" label="阶段数" width="90" />
      <el-table-column prop="course_count" label="课程数" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'on_shelf' ? 'success' : 'info'">
            {{ row.status === 'on_shelf' ? '已上架' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/routes/${row.id}`)">编辑阶段与课程</el-button>
          <el-button link type="primary" @click="previewId = row.id">预览</el-button>
          <el-button
            link
            :type="row.status === 'on_shelf' ? 'warning' : 'primary'"
            @click="toggleStatus(row as RouteAdminView)"
          >
            {{ row.status === 'on_shelf' ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row as RouteAdminView)">删除</el-button>
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

    <el-dialog v-model="createVisible" title="新增路线" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="create">
        <el-form-item label="路线名称" prop="name">
          <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="如 AI 大模型应用开发" />
        </el-form-item>
        <el-form-item label="简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序号（越小越靠前）" prop="sort_no">
          <el-input-number v-model="form.sort_no" :step="1" :precision="0" controls-position="right" />
        </el-form-item>
        <p class="hint">新建的路线默认下架；编排好阶段与课程后再上架。</p>
        <button type="submit" hidden />
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建并编排</el-button>
      </template>
    </el-dialog>

    <RoutePreviewDialog :route-id="previewId" @close="previewId = null" />
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
.filter {
  width: 140px;
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.name {
  color: var(--color-ink);
  font-weight: 600;
  text-decoration: none;
}
.name:hover {
  color: var(--color-accent-2);
  text-decoration: underline;
}
.intro {
  margin-top: 2px;
  color: var(--color-muted);
  font-size: var(--text-xs);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pager {
  justify-content: flex-end;
}
.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
</style>
