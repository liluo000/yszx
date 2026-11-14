<script setup lang="ts">
// 协议正文弹窗：内容取自 S-HLP-01，后台未发布时给出提示而不是空白
import type { StudentAgreementType } from '@yunshu/api'
import { AGREEMENT_LABEL, type AgreementState } from '@/composables/useAgreements'

defineProps<{
  open: boolean
  type: StudentAgreementType
  state: AgreementState
}>()

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div v-if="open" class="mask" role="dialog" aria-modal="true" :aria-label="AGREEMENT_LABEL[type]" @click.self="emit('close')">
    <div class="dialog card">
      <header class="dialog-head">
        <h2>{{ AGREEMENT_LABEL[type] }}</h2>
        <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="emit('close')">✕</button>
      </header>
      <p v-if="state.published && state.publishedAt" class="muted version">
        版本 {{ state.versionNo }} · 更新于 {{ state.publishedAt.slice(0, 10) }}
      </p>
      <div v-if="state.published" class="content">{{ state.content }}</div>
      <p v-else class="muted empty">协议内容尚未发布，如需了解请联系客服。</p>
      <footer class="dialog-foot">
        <button type="button" class="btn btn-primary" @click="emit('close')">我知道了</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: grid;
  place-items: center;
  padding: var(--space-md);
  background: rgba(10, 28, 49, 0.45);
}
.dialog {
  width: min(34rem, 100%);
  max-height: min(80vh, 40rem);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  background: var(--color-paper);
}
.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}
.dialog-head h2 {
  font-size: var(--text-md);
  font-weight: 600;
}
.version {
  font-size: var(--text-xs);
}
.content {
  overflow-y: auto;
  white-space: pre-wrap;
  font-size: var(--text-sm);
  line-height: 1.8;
  color: var(--color-ink-2);
}
.empty {
  padding: var(--space-xl) 0;
  text-align: center;
  font-size: var(--text-sm);
}
.dialog-foot {
  display: flex;
  justify-content: flex-end;
}
</style>
