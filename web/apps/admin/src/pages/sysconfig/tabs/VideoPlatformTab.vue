<script setup lang="ts">
// 视频平台（需求 7.8，A-CFG-09~12）：新增平台只需在此配置；停用后不能用于新建课程，已有课程不受影响
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { VideoPlatformView } from '@yunshu/api'
import { configApi } from '@/api'
import { errorText } from '@/utils/format'

const DOMAIN = /^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/

const platforms = ref<VideoPlatformView[]>([])
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    platforms.value = (await configApi.videoPlatforms()).items
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const visible = ref(false)
const editing = ref<VideoPlatformView | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ name: '', domains: '' })

function parseDomains(text: string) {
  return text
    .split('\n')
    .map((d) => d.trim().toLowerCase())
    .filter(Boolean)
}

const rules: FormRules = {
  name: [
    { required: true, message: '请填写平台名称', trigger: 'blur' },
    { max: 50, message: '平台名称最多 50 个字符', trigger: 'blur' },
  ],
  domains: [
    {
      validator: (_: unknown, value: string, callback: (e?: Error) => void) => {
        const domains = parseDomains(value)
        if (!domains.length) return callback(new Error('至少填写 1 个域名'))
        if (domains.length > 20) return callback(new Error('最多 20 个域名'))
        const invalid = domains.find((d) => !DOMAIN.test(d))
        if (invalid) return callback(new Error(`域名格式不正确：${invalid}`))
        if (new Set(domains).size !== domains.length) return callback(new Error('域名不能重复'))
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function open(platform?: VideoPlatformView) {
  editing.value = platform ?? null
  form.name = platform?.name ?? ''
  form.domains = platform?.domains.join('\n') ?? ''
  visible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const body = { name: form.name.trim(), domains: parseDomains(form.domains) }
  saving.value = true
  try {
    if (editing.value) await configApi.updateVideoPlatform(editing.value.id, body)
    else await configApi.createVideoPlatform(body)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    saving.value = false
  }
}

async function toggle(platform: VideoPlatformView) {
  const disabling = platform.status === 'enabled'
  if (disabling) {
    const confirmed = await ElMessageBox.confirm(
      `停用后「${platform.name}」不能用于新建课程，已有课程不受影响。`,
      '停用视频平台',
      { confirmButtonText: '停用', cancelButtonText: '取消', type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
  }
  try {
    await configApi.changeVideoPlatformStatus(platform.id, disabling ? 'disabled' : 'enabled')
    ElMessage.success(disabling ? '已停用' : '已启用')
    load()
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}
</script>

<template>
  <div class="head">
    <p class="intro">新增平台只需在此配置，不需要修改代码；停用的平台不能用于新建课程，已有课程不受影响。</p>
    <el-button type="primary" size="small" @click="open()">新增平台</el-button>
  </div>
  <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
    <el-button size="small" @click="load">重试</el-button>
  </el-alert>
  <el-table v-loading="loading" :data="platforms" class="table" empty-text="暂无视频平台">
    <el-table-column label="平台名称" min-width="120">
      <template #default="{ row }"
        ><b>{{ row.name }}</b></template
      >
    </el-table-column>
    <el-table-column label="允许的域名" min-width="200">
      <template #default="{ row }">
        <div class="domains">
          <el-tag v-for="d in row.domains" :key="d" effect="plain" type="info">{{ d }}</el-tag>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">{{
          row.status === 'enabled' ? '启用' : '停用'
        }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="120" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" @click="open(row as VideoPlatformView)">编辑</el-button>
        <el-button
          link
          :type="row.status === 'enabled' ? 'danger' : 'primary'"
          @click="toggle(row as VideoPlatformView)"
        >
          {{ row.status === 'enabled' ? '停用' : '启用' }}
        </el-button>
      </template>
    </el-table-column>
  </el-table>

  <el-dialog v-model="visible" :title="editing ? '编辑视频平台' : '新增视频平台'" width="460px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="平台名称" prop="name">
        <el-input v-model="form.name" maxlength="50" placeholder="如 腾讯视频" />
      </el-form-item>
      <el-form-item label="允许的域名（每行一个）" prop="domains">
        <el-input
          v-model="form.domains"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 10 }"
          placeholder="example.com"
        />
        <div class="hint">课程的视频链接域名需属于这里填写的域名（含子域名）。</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}
.intro {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.domains {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2xs);
}
.hint {
  margin-top: var(--space-2xs);
  color: var(--color-muted);
  font-size: var(--text-xs);
  line-height: 1.5;
}
</style>
