<script setup lang="ts">
// 协议文本（需求 7.5，A-CFG-03、04）：每次发布生成新版本，学员需重新同意
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AgreementType, AgreementView } from '@yunshu/api'
import { configApi } from '@/api'
import { errorText, formatDateTime } from '@/utils/format'

const LABEL: Record<AgreementType, string> = { user_agreement: '用户协议', privacy_policy: '隐私政策' }
const TYPES = Object.keys(LABEL) as AgreementType[]
const MAX_LENGTH = 1_000_000

const current = ref<Partial<Record<AgreementType, AgreementView>>>({})
const draft = reactive<Record<AgreementType, string>>({ user_agreement: '', privacy_policy: '' })
const loading = ref(false)
const loadError = ref('')
const publishing = ref(false)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const items = (await configApi.agreements()).items
    current.value = Object.fromEntries(items.map((a) => [a.agreement_type, a]))
    for (const type of TYPES) draft[type] = current.value[type]?.content ?? ''
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function changedTypes() {
  return TYPES.filter((t) => draft[t].trim() && draft[t] !== (current.value[t]?.content ?? ''))
}

async function publish() {
  const types = changedTypes()
  if (!types.length) {
    ElMessage.info('协议内容没有修改')
    return
  }
  const confirmed = await ElMessageBox.confirm(
    `将发布${types.map((t) => `「${LABEL[t]}」`).join('、')}的新版本。发布后学员需重新同意，并会收到"协议已更新"站内信。`,
    '发布更新',
    { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  publishing.value = true
  try {
    for (const type of types) await configApi.publishAgreement(type, draft[type])
    ElMessage.success('协议已发布，已记入操作日志')
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '发布失败，请稍后重试'))
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
    <el-button size="small" @click="load">重试</el-button>
  </el-alert>
  <div v-loading="loading" class="agreements">
    <div v-for="type in TYPES" :key="type" class="item">
      <div class="head">
        <b>{{ LABEL[type] }}</b>
        <span class="meta">
          <template v-if="current[type]?.version_no">
            当前第 {{ current[type]?.version_no }} 版，发布于 {{ formatDateTime(current[type]?.published_at) }}
          </template>
          <template v-else>尚未发布</template>
        </span>
      </div>
      <el-input
        v-model="draft[type]"
        type="textarea"
        :autosize="{ minRows: 8, maxRows: 20 }"
        :maxlength="MAX_LENGTH"
        :aria-label="LABEL[type]"
        :placeholder="`请输入${LABEL[type]}正文`"
      />
    </div>
  </div>
  <el-alert type="warning" :closable="false" show-icon class="notice">
    发布更新后：未登录学员在下次登录时勾选同意；已登录学员在下次打开页面时弹窗要求同意，不同意则退出登录。
  </el-alert>
  <div class="actions">
    <el-button type="primary" :loading="publishing" @click="publish">发布更新</el-button>
  </div>
</template>

<style scoped>
.agreements {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}
.item {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-xs);
}
.meta {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.notice {
  margin-top: var(--space-lg);
}
.actions {
  margin-top: var(--space-lg);
}
</style>
