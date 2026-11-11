<script setup lang="ts">
// 系统用户（需求 6.9，A-IAM-05~09）
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { AdminRole, EnableStatus, SysUserView } from '@yunshu/api'
import { sysUserApi } from '@/api'
import { ROLE_LABEL } from '@/constants/roles'
import { useAuthStore } from '@/stores/auth'
import TemporaryPasswordDialog from '@/components/TemporaryPasswordDialog.vue'
import { errorText, fieldErrors, formatDateTime, maskPhone } from '@/utils/format'

const auth = useAuthStore()
const roleOptions = Object.entries(ROLE_LABEL) as [AdminRole, string][]

const filters = reactive<{ role?: AdminRole; status?: EnableStatus }>({})
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<SysUserView[]>([])
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await sysUserApi.list({ ...filters, page: page.value, page_size: pageSize.value })
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

/* ---------------- 新建、编辑 ---------------- */
const formVisible = ref(false)
const editing = ref<SysUserView | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const serverErrors = ref<Record<string, string>>({})
const form = reactive({ account: '', name: '', phone: '', role: 'content_operator' as AdminRole })

const rules: FormRules = {
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { max: 50, message: '账号最多 50 个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.-]+$/, message: '账号只能包含字母、数字、下划线、点和短横线', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名最多 50 个字符', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[0-9]{10}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function openForm(user?: SysUserView) {
  editing.value = user ?? null
  serverErrors.value = {}
  Object.assign(
    form,
    user
      ? { account: user.account, name: user.name, phone: user.phone, role: user.role }
      : { account: '', name: '', phone: '', role: 'content_operator' },
  )
  formVisible.value = true
}

const credential = reactive({ visible: false, title: '', account: '', password: '' })

async function save() {
  serverErrors.value = {}
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const body = { name: form.name.trim(), phone: form.phone.trim(), role: form.role }
    if (editing.value) {
      await sysUserApi.update(editing.value.id, body)
      ElMessage.success('已保存')
    } else {
      const result = await sysUserApi.create({ ...body, account: form.account.trim() })
      Object.assign(credential, {
        visible: true,
        title: '账号已创建',
        account: result.user.account,
        password: result.temporary_password,
      })
    }
    formVisible.value = false
    load()
  } catch (e) {
    serverErrors.value = fieldErrors(e)
    if (!Object.keys(serverErrors.value).length) ElMessage.error(errorText(e))
  } finally {
    saving.value = false
  }
}

/* ---------------- 启用、停用、重置密码 ---------------- */
async function toggleStatus(user: SysUserView) {
  const disabling = user.status === 'enabled'
  const confirmed = await ElMessageBox.confirm(
    disabling ? `停用后「${user.name}」将立即退出登录，且无法登录后台。` : `启用后「${user.name}」可正常登录后台。`,
    disabling ? '停用账号' : '启用账号',
    { confirmButtonText: disabling ? '停用' : '启用', cancelButtonText: '取消', type: disabling ? 'warning' : 'info' },
  ).catch(() => false)
  if (!confirmed) return
  try {
    await sysUserApi.changeStatus(user.id, disabling ? 'disabled' : 'enabled')
    ElMessage.success(disabling ? '已停用' : '已启用')
    load()
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}

async function resetPassword(user: SysUserView) {
  const confirmed = await ElMessageBox.confirm(
    `将为「${user.name}」生成新的临时密码，该账号会立即退出登录，密码输错锁定也会解除。`,
    '重置密码',
    { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  try {
    const result = await sysUserApi.resetPassword(user.id)
    Object.assign(credential, {
      visible: true,
      title: '密码已重置',
      account: user.account,
      password: result.temporary_password,
    })
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="filters.role" placeholder="全部角色" clearable class="filter" @change="search">
          <el-option v-for="[value, label] in roleOptions" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter" @change="search">
          <el-option label="启用" value="enabled" />
          <el-option label="停用" value="disabled" />
        </el-select>
      </div>
      <el-button type="primary" @click="openForm()">新增账号</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon class="load-error">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" class="table" empty-text="暂无账号">
      <el-table-column label="账号" min-width="140">
        <template #default="{ row }">
          <b>{{ row.account }}</b>
          <el-tag v-if="row.id === auth.user?.id" size="small" type="info" class="self">当前账号</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="姓名" min-width="110" />
      <el-table-column label="手机号" min-width="130">
        <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="110">
        <template #default="{ row }">
          <el-tag effect="plain">{{ ROLE_LABEL[row.role as AdminRole] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">
            {{ row.status === 'enabled' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.last_login_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row as SysUserView)">编辑</el-button>
          <el-tooltip :disabled="row.id !== auth.user?.id" content="不能停用自己的账号" placement="top">
            <span>
              <el-button
                link
                :type="row.status === 'enabled' ? 'danger' : 'primary'"
                :disabled="row.id === auth.user?.id"
                @click="toggleStatus(row as SysUserView)"
              >
                {{ row.status === 'enabled' ? '停用' : '启用' }}
              </el-button>
            </span>
          </el-tooltip>
          <el-button link type="primary" @click="resetPassword(row as SysUserView)">重置密码</el-button>
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

    <el-dialog v-model="formVisible" :title="editing ? '编辑账号' : '新增账号'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="账号" prop="account" :error="serverErrors.account">
          <el-input
            v-model="form.account"
            :disabled="!!editing"
            maxlength="50"
            placeholder="用于登录，创建后不能修改"
          />
        </el-form-item>
        <div class="two-col">
          <el-form-item label="姓名" prop="name" :error="serverErrors.name">
            <el-input v-model="form.name" maxlength="50" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone" :error="serverErrors.phone">
            <el-input v-model="form.phone" maxlength="11" />
          </el-form-item>
        </div>
        <el-form-item label="角色" prop="role" :error="serverErrors.role">
          <el-radio-group v-model="form.role">
            <el-radio v-for="[value, label] in roleOptions" :key="value" :value="value">{{ label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <p v-if="!editing" class="hint">保存后系统生成临时密码，对方首次登录时需修改密码。</p>
        <button type="submit" hidden />
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <TemporaryPasswordDialog
      v-model="credential.visible"
      :title="credential.title"
      :account="credential.account"
      :password="credential.password"
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
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.filter {
  width: 150px;
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.self {
  margin-left: var(--space-xs);
}
.pager {
  justify-content: flex-end;
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--space-md);
}
.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.load-error :deep(.el-alert__description) {
  margin-top: var(--space-xs);
}
</style>
