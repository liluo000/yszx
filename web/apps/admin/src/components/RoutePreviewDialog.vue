<script setup lang="ts">
// 预览路线在学员端的展示效果（A-CNT-20）：不要求已上架，只显示已上架课程，进度为空
import { ref, watch } from 'vue'
import type { StageDetail } from '@yunshu/api'
import { routeApi } from '@/api'
import { errorText } from '@/utils/format'

const props = defineProps<{ routeId: number | null }>()
const emit = defineEmits<{ close: [] }>()

const visible = ref(false)
const loading = ref(false)
const error = ref('')
const data = ref<{ name: string; intro: string | null; stages: StageDetail[] } | null>(null)

watch(
  () => props.routeId,
  async (id) => {
    visible.value = id !== null
    if (id === null) return
    loading.value = true
    error.value = ''
    data.value = null
    try {
      data.value = await routeApi.preview(id)
    } catch (e) {
      error.value = errorText(e, '预览加载失败')
    } finally {
      loading.value = false
    }
  },
)

function hours(minutes: number | null) {
  if (!minutes) return ''
  return minutes < 60 ? `${minutes} 分钟` : `${Math.round(minutes / 6) / 10} 小时`
}
</script>

<template>
  <el-drawer v-model="visible" title="学员端预览" size="560px" @closed="emit('close')">
    <div v-loading="loading" class="preview">
      <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
      <template v-if="data">
        <header class="hero">
          <h2>{{ data.name }}</h2>
          <p v-if="data.intro">{{ data.intro }}</p>
          <span class="meta">
            {{ data.stages.length }} 个阶段 · {{ data.stages.reduce((n, s) => n + s.courses.length, 0) }} 门课程
          </span>
        </header>
        <el-empty v-if="!data.stages.length" description="还没有阶段" :image-size="64" />
        <section v-for="(stage, index) in data.stages" :key="stage.id" class="stage">
          <h3>
            <span class="no">阶段 {{ index + 1 }}</span
            >{{ stage.name }}
          </h3>
          <p v-if="stage.intro" class="stage-intro">{{ stage.intro }}</p>
          <ol class="courses">
            <li v-for="course in stage.courses" :key="course.id">
              <span class="title">{{ course.title }}</span>
              <span class="tags">
                <span v-if="course.duration_minutes">{{ hours(course.duration_minutes) }}</span>
                <span>{{ course.platform_name }}</span>
                <el-tag v-if="!course.practice_question_count" size="small" type="warning">题目建设中</el-tag>
              </span>
            </li>
            <li v-if="!stage.courses.length" class="empty">暂无已上架课程</li>
          </ol>
        </section>
        <p class="note">预览只显示已上架的课程；学员的学习进度、刷题和 AI 面试入口在学员端按实际情况显示。</p>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.preview {
  min-height: 200px;
}
.hero {
  padding: var(--space-lg);
  margin-bottom: var(--space-md);
  border-radius: var(--radius);
  background: var(--color-board);
  color: var(--color-board-type);
}
.hero h2 {
  margin: 0 0 var(--space-xs);
  font-size: 20px;
}
.hero p {
  margin: 0 0 var(--space-sm);
  line-height: 1.6;
  opacity: 0.85;
}
.meta {
  font-size: var(--text-xs);
  opacity: 0.7;
}
.stage {
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--color-rule);
}
.stage h3 {
  margin: 0;
  font-size: 15px;
}
.no {
  margin-right: var(--space-xs);
  color: var(--color-accent-2);
}
.stage-intro {
  margin: var(--space-2xs) 0 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.courses {
  margin: var(--space-sm) 0 0;
  padding-left: 20px;
}
.courses li {
  padding: 6px 0;
}
.courses li::marker {
  color: var(--color-muted);
}
.title {
  display: block;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.empty {
  color: var(--color-muted);
  list-style: none;
}
.note {
  margin-top: var(--space-md);
  color: var(--color-muted);
  font-size: var(--text-xs);
}
</style>
