<script setup lang="ts">
import { computed } from 'vue'
import { PASSWORD_STRENGTH_LABEL, passwordStrength } from '@yunshu/shared'

const props = defineProps<{ password: string }>()
const strength = computed(() => passwordStrength(props.password))
const level = computed(() => ({ low: 1, medium: 2, high: 3 })[strength.value ?? 'low'])
</script>

<template>
  <div v-if="strength" class="strength" :class="strength" aria-live="polite">
    <span v-for="n in 3" :key="n" class="bar" :class="{ on: n <= level }" />
    <span class="label">密码强度：{{ PASSWORD_STRENGTH_LABEL[strength] }}</span>
  </div>
</template>

<style scoped>
.strength {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: var(--text-xs);
  color: var(--color-muted);
}
.bar {
  width: 32px;
  height: 4px;
  border-radius: 2px;
  background: var(--color-rule);
}
.low .bar.on {
  background: #d9534f;
}
.medium .bar.on {
  background: #e6a23c;
}
.high .bar.on {
  background: var(--color-accent-2);
}
.label {
  margin-left: 6px;
}
</style>
