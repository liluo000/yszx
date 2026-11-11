<script setup lang="ts">
// 参数类配置：AI 用量、学习判定、刷题与考试、AI 面试（A-CFG-01、02）
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { GradeThresholds, PaperRule, QuestionType } from '@yunshu/api'
import { configApi } from '@/api'
import { errorText } from '@/utils/format'
import { QUESTION_TYPE_LABEL, type ConfigField } from '../configFields'

const props = defineProps<{ fields: ConfigField[]; note?: string; values: Record<string, unknown> }>()
const emit = defineEmits<{ saved: [key: string, value: unknown] }>()

/** 配置值都是 JSON，用 JSON 复制（structuredClone 不能复制响应式代理） */
function clone<T>(value: T): T {
  return value === undefined ? value : (JSON.parse(JSON.stringify(value)) as T)
}

const draft = ref<Record<string, unknown>>({})
const errors = ref<Record<string, string>>({})
const saving = ref(false)

// 每次切换分组或保存后，用服务端的值重置草稿
watch(
  () => [props.fields, props.values] as const,
  () => {
    draft.value = Object.fromEntries(props.fields.map((f) => [f.key, clone(props.values[f.key])]))
    errors.value = {}
  },
  { immediate: true, deep: true },
)

const paperTypes = Object.keys(QUESTION_TYPE_LABEL) as QuestionType[]
function numberOf(key: string) {
  const value = draft.value[key]
  return typeof value === 'number' ? value : undefined
}
function paper(key: string) {
  return draft.value[key] as PaperRule
}
function grade(key: string) {
  return draft.value[key] as GradeThresholds
}
function paperTotal(key: string) {
  const rule = paper(key)
  if (!rule) return { count: 0, score: 0 }
  return paperTypes.reduce(
    (sum, t) => ({
      count: sum.count + (rule[t]?.count ?? 0),
      score: sum.score + (rule[t]?.count ?? 0) * (rule[t]?.score ?? 0),
    }),
    { count: 0, score: 0 },
  )
}

const changedKeys = computed(() =>
  props.fields.map((f) => f.key).filter((k) => JSON.stringify(draft.value[k]) !== JSON.stringify(props.values[k])),
)

function validate(): boolean {
  const result: Record<string, string> = {}
  for (const field of props.fields) {
    const value = draft.value[field.key]
    if (field.kind === 'int') {
      if (typeof value !== 'number' || !Number.isInteger(value) || value < field.min || value > field.max) {
        result[field.key] = `请填写 ${field.min}~${field.max} 之间的整数`
      }
    } else if (field.kind === 'paper_rule') {
      if (paperTotal(field.key).count < 1) result[field.key] = '试卷至少需要 1 道题'
    } else if (field.kind === 'grade') {
      const g = grade(field.key)
      if (!(g.excellent > g.good)) result[field.key] = '优秀分数线必须高于良好分数线'
    }
  }
  errors.value = result
  return Object.keys(result).length === 0
}

async function save() {
  if (!validate()) return
  if (!changedKeys.value.length) {
    ElMessage.info('没有需要保存的修改')
    return
  }
  saving.value = true
  try {
    for (const key of changedKeys.value) {
      const value = draft.value[key]
      try {
        await configApi.updateConfig(key, value)
      } catch (e) {
        errors.value = { ...errors.value, [key]: errorText(e) }
        throw e
      }
      emit('saved', key, clone(value))
    }
    ElMessage.success('已保存，已记入操作日志')
  } catch {
    ElMessage.error('部分配置保存失败，请检查标红的项')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="rows">
    <div v-for="field in fields" :key="field.key" class="row" :class="{ invalid: errors[field.key] }">
      <div class="label">
        <b>{{ field.label }}</b>
        <span class="desc">{{ field.desc }}</span>
      </div>
      <div class="control">
        <template v-if="field.kind === 'int'">
          <el-input-number
            :model-value="numberOf(field.key)"
            :min="field.min"
            :max="field.max"
            :step="1"
            step-strictly
            controls-position="right"
            :aria-label="field.label"
            @update:model-value="(v) => (draft[field.key] = v)"
          />
          <span class="unit">{{ field.unit }}</span>
        </template>
        <div v-else-if="field.kind === 'paper_rule' && paper(field.key)" class="paper">
          <div v-for="t in paperTypes" :key="t" class="paper-row">
            <span class="type">{{ QUESTION_TYPE_LABEL[t] }}</span>
            <el-input-number
              v-model="paper(field.key)[t].count"
              :min="0"
              :max="100"
              step-strictly
              controls-position="right"
              size="small"
              :aria-label="`${QUESTION_TYPE_LABEL[t]}数量`"
            />
            <span class="unit">题 ×</span>
            <el-input-number
              v-model="paper(field.key)[t].score"
              :min="1"
              :max="100"
              step-strictly
              controls-position="right"
              size="small"
              :aria-label="`${QUESTION_TYPE_LABEL[t]}分值`"
            />
            <span class="unit">分</span>
          </div>
          <div class="desc">合计 {{ paperTotal(field.key).count }} 题，{{ paperTotal(field.key).score }} 分</div>
        </div>
        <div v-else-if="field.kind === 'grade' && grade(field.key)" class="grade">
          <span>优秀 ≥</span>
          <el-input-number
            v-model="grade(field.key).excellent"
            :min="0"
            :max="100"
            step-strictly
            controls-position="right"
            size="small"
            aria-label="优秀分数线"
          />
          <span>分，良好 ≥</span>
          <el-input-number
            v-model="grade(field.key).good"
            :min="0"
            :max="100"
            step-strictly
            controls-position="right"
            size="small"
            aria-label="良好分数线"
          />
          <span>分，其余为待提升</span>
        </div>
        <div v-if="errors[field.key]" class="error">{{ errors[field.key] }}</div>
      </div>
    </div>
  </div>
  <p v-if="note" class="note">{{ note }}</p>
  <div class="actions">
    <el-button type="primary" :loading="saving" :disabled="!changedKeys.length" @click="save">保存</el-button>
    <span v-if="changedKeys.length" class="pending">有 {{ changedKeys.length }} 项未保存</span>
  </div>
</template>

<style scoped>
.rows {
  display: flex;
  flex-direction: column;
}
.row {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) minmax(260px, 1.4fr);
  gap: var(--space-md);
  align-items: start;
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--color-rule);
}
.label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
}
.desc {
  color: var(--color-muted);
  font-size: var(--text-xs);
  line-height: 1.5;
}
.control {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}
.unit {
  color: var(--color-muted);
}
.paper {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}
.paper-row,
.grade {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}
.type {
  width: 56px;
}
.paper :deep(.el-input-number),
.grade :deep(.el-input-number) {
  width: 96px;
}
.error {
  flex-basis: 100%;
  color: var(--el-color-danger);
  font-size: var(--text-xs);
}
.note {
  margin: var(--space-md) 0 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
}
.pending {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
@media (max-width: 720px) {
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
