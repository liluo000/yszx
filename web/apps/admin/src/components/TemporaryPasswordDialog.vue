<script setup lang="ts">
import { ElMessage } from 'element-plus'

// 临时密码只在新建账号、重置密码时返回一次，关闭后无法再次查看
const visible = defineModel<boolean>({ required: true })
defineProps<{ title: string; account: string; password: string }>()

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选中复制')
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="440px" :close-on-click-modal="false" append-to-body>
    <dl class="credential">
      <dt>账号</dt>
      <dd>{{ account }}</dd>
      <dt>临时密码</dt>
      <dd>
        <code class="password">{{ password }}</code>
        <el-button size="small" @click="copy(password)">复制</el-button>
      </dd>
    </dl>
    <el-alert type="warning" :closable="false" show-icon>
      临时密码只显示这一次，请通过安全渠道告知对方。对方首次登录后需修改密码。
    </el-alert>
    <template #footer>
      <el-button type="primary" @click="visible = false">我已记下</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.credential {
  display: grid;
  grid-template-columns: 72px 1fr;
  row-gap: var(--space-sm);
  margin: 0 0 var(--space-md);
}
.credential dt {
  color: var(--color-muted);
}
.credential dd {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.password {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
</style>
