<script setup lang="ts">
// 通用批量导入弹窗：下载模板、选择 xlsx、查看导入结果（04.API接口设计.md ImportResult）
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ImportResult } from '@yunshu/api'
import { configApi } from '@/api'
import { errorText, saveBlob } from '@/utils/format'

const MAX_BYTES = 5 * 1024 * 1024

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{
  title: string
  templateType: 'sensitive_word'
  templateName: string
  rules: string[]
  upload: (file: File) => Promise<ImportResult>
}>()
const emit = defineEmits<{ imported: [] }>()

const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)
const downloading = ref(false)
const result = ref<ImportResult | null>(null)

watch(visible, (open) => {
  if (open) {
    file.value = null
    result.value = null
  }
})

async function downloadTemplate() {
  downloading.value = true
  try {
    const { blob, filename } = await configApi.downloadTemplate(props.templateType)
    saveBlob(blob, filename ?? props.templateName)
  } catch (e) {
    ElMessage.error(errorText(e, '下载失败，请稍后重试'))
  } finally {
    downloading.value = false
  }
}

function onFileChange(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0] ?? null
  ;(event.target as HTMLInputElement).value = ''
  if (!selected) return
  if (!selected.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('请选择 xlsx 格式的文件')
    return
  }
  if (selected.size > MAX_BYTES) {
    ElMessage.error('文件不能超过 5MB')
    return
  }
  file.value = selected
  result.value = null
}

async function submit() {
  if (!file.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    result.value = await props.upload(file.value)
    file.value = null
    if (result.value.success_count > 0) emit('imported')
  } catch (e) {
    ElMessage.error(errorText(e, '导入失败，请稍后重试'))
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="520px" destroy-on-close>
    <ol class="steps">
      <li>
        <span>下载导入模板，按模板填写</span>
        <el-button link type="primary" :loading="downloading" @click="downloadTemplate">{{ templateName }}</el-button>
      </li>
      <li>
        <span>选择填写好的文件（xlsx，不超过 5MB，最多 1000 行）</span>
        <div class="picker">
          <el-button @click="fileInput?.click()">选择文件</el-button>
          <span class="file-name">{{ file?.name ?? '未选择文件' }}</span>
          <input ref="fileInput" type="file" accept=".xlsx" hidden @change="onFileChange" />
        </div>
      </li>
    </ol>
    <ul class="rules">
      <li v-for="rule in rules" :key="rule">{{ rule }}</li>
    </ul>

    <div v-if="result" class="result">
      <el-alert
        :type="result.failure_count ? 'warning' : 'success'"
        :closable="false"
        show-icon
        :title="`成功 ${result.success_count} 条，失败 ${result.failure_count} 条`"
      />
      <el-table v-if="result.failures.length" :data="result.failures" max-height="240" size="small" class="failures">
        <el-table-column prop="row_no" label="行号" width="80" />
        <el-table-column prop="reason" label="失败原因" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ result ? '关闭' : '取消' }}</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!file" @click="submit">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.steps {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.steps li > span {
  display: block;
  margin-bottom: var(--space-2xs);
}
.picker {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.file-name {
  color: var(--color-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rules {
  margin: var(--space-md) 0 0;
  padding-left: 20px;
  color: var(--color-muted);
  font-size: var(--text-xs);
  line-height: 1.7;
}
.result {
  margin-top: var(--space-md);
}
.failures {
  margin-top: var(--space-sm);
}
</style>
