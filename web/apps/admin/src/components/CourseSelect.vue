<script setup lang="ts">
// 课程选择：按名称远程搜索（课程较多，不一次加载全部），可限定学科
import { ref, watch } from 'vue'
import type { CourseAdminView } from '@yunshu/api'
import { courseApi } from '@/api'

const model = defineModel<number | undefined>()
const props = defineProps<{ subjectId?: number; placeholder?: string; clearable?: boolean; initialLabel?: string }>()
const emit = defineEmits<{ change: [value: number | undefined] }>()

const options = ref<{ id: number; title: string; status: string }[]>([])
const loading = ref(false)

async function search(keyword: string) {
  loading.value = true
  try {
    const result = await courseApi.list({
      page: 1,
      page_size: 50,
      keyword: keyword.trim() || undefined,
      subject_id: props.subjectId,
    })
    options.value = result.items.map((c: CourseAdminView) => ({ id: c.id, title: c.title, status: c.status }))
    keepSelected()
  } catch {
    options.value = []
  } finally {
    loading.value = false
  }
}

/** 已选课程不在当前搜索结果中时，仍显示它的名称 */
function keepSelected() {
  if (model.value && props.initialLabel && !options.value.some((o) => o.id === model.value)) {
    options.value.unshift({ id: model.value, title: props.initialLabel, status: '' })
  }
}

watch(
  () => props.subjectId,
  () => search(''),
)
watch(() => props.initialLabel, keepSelected)

function onVisible(visible: boolean) {
  if (visible && !options.value.length) search('')
}
search('')
</script>

<template>
  <el-select
    v-model="model"
    filterable
    remote
    :remote-method="search"
    :loading="loading"
    :placeholder="placeholder ?? '搜索课程名称'"
    :clearable="clearable"
    @visible-change="onVisible"
    @change="(v: number | undefined) => emit('change', v)"
  >
    <el-option v-for="c in options" :key="c.id" :label="c.title" :value="c.id">
      <span>{{ c.title }}</span>
      <span v-if="c.status === 'off_shelf'" class="off">已下架</span>
    </el-option>
  </el-select>
</template>

<style scoped>
.off {
  margin-left: 8px;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
</style>
