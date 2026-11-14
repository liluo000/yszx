<script setup lang="ts">
// 路线详情（需求 5.3.2，S-PTL-03）：阶段与课程全部开放，不按顺序解锁
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { RouteDetailView } from '@yunshu/api'
import { errorText, routeApi } from '@/api'

const current = useRoute()
const detail = ref<RouteDetailView | null>(null)
const loading = ref(false)
const loadError = ref('')

async function load() {
  const id = Number(current.params.id)
  if (!id) return
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await routeApi.detail(id)
  } catch (e) {
    detail.value = null
    loadError.value = errorText(e, '路线加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => current.params.id, load)
</script>

<template>
  <section class="page">
    <div v-if="loadError" class="alert-error" role="alert">
      <span>{{ loadError }}</span>
      <RouterLink to="/routes" class="btn btn-sm">返回路线列表</RouterLink>
    </div>

    <div v-else-if="loading" class="skeleton">正在加载路线…</div>

    <template v-else-if="detail">
      <header class="head">
        <RouterLink to="/routes" class="back">‹ 学习路线</RouterLink>
        <h1>{{ detail.name }}</h1>
        <p v-if="detail.intro" class="intro">{{ detail.intro }}</p>
        <p class="muted tip">所有阶段全部开放，可直接学习、练习、考试或面试任意阶段。</p>
      </header>

      <section v-for="stage in detail.stages" :key="stage.id" class="stage">
        <div class="stage-head">
          <div>
            <h2>{{ stage.name }}</h2>
            <p v-if="stage.intro" class="muted">{{ stage.intro }}</p>
          </div>
          <div class="entries">
            <RouterLink v-if="stage.is_random_available" :to="`/practice/random/${stage.id}`" class="btn btn-sm">
              随机抽题
            </RouterLink>
            <button v-else type="button" class="btn btn-sm" disabled>随机抽题 · 题目建设中</button>
            <RouterLink v-if="stage.is_exam_available" :to="`/exam/${stage.id}`" class="btn btn-sm">
              模拟考试
            </RouterLink>
            <button v-else type="button" class="btn btn-sm" disabled>模拟考试 · 题目建设中</button>
            <RouterLink v-if="stage.is_interview_available" :to="`/interview?stage=${stage.id}`" class="btn btn-sm">
              AI 面试
            </RouterLink>
            <button v-else type="button" class="btn btn-sm" disabled>AI 面试 · 题目建设中</button>
          </div>
        </div>

        <ul v-if="stage.courses.length" class="courses">
          <li v-for="course in stage.courses" :key="course.id" class="course">
            <div class="course-main">
              <b>{{ course.title }}</b>
              <span class="muted meta">
                <span v-if="course.platform_name">{{ course.platform_name }}</span>
                <span v-if="course.subject_names.length">{{ course.subject_names.join('、') }}</span>
                <span v-if="course.duration_minutes" class="fig">{{ course.duration_minutes }} 分钟</span>
                <span class="fig">刷题 {{ course.practice_question_count }} 道</span>
              </span>
            </div>
            <div class="course-actions">
              <span class="tag">{{ course.is_mastered ? '已掌握' : course.is_learned ? '已学' : '未学' }}</span>
              <RouterLink
                v-if="course.practice_question_count > 0"
                :to="`/practice/sequential/${course.id}`"
                class="btn btn-sm"
              >
                去练习
              </RouterLink>
            </div>
          </li>
        </ul>
        <div v-else class="skeleton">这个阶段还没有已上架课程</div>
      </section>

      <p class="muted note">课程视频跳转与学习标记将随学习进度模块开发接入；「去练习」已可开始按课程顺序练习。</p>
    </template>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}
.alert-error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border: var(--rule-hair) solid var(--color-error);
  border-radius: var(--radius-card);
  background: var(--color-error-wash);
  font-size: var(--text-sm);
}
.alert-error .btn {
  margin-left: auto;
}
.head {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding-bottom: var(--space-md);
  border-bottom: var(--rule-hair) solid var(--color-rule);
}
.back {
  font-size: var(--text-xs);
}
.head h1 {
  font-size: var(--text-2xl);
  font-weight: 600;
}
.intro {
  max-width: 60ch;
  color: var(--color-muted);
}
.tip {
  font-size: var(--text-xs);
}
.stage {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.stage-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
}
.stage-head h2 {
  font-size: var(--text-lg);
  font-weight: 600;
}
.stage-head p {
  font-size: var(--text-sm);
}
.entries {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}
.courses {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  margin: 0;
  padding: 0;
  list-style: none;
}
.course {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border: var(--rule-hair) solid var(--color-rule);
  border-radius: var(--radius-card);
  background: var(--color-cotton);
}
.course-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-3xs);
}
.course-main b {
  font-size: var(--text-sm);
  font-weight: 600;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  font-size: var(--text-xs);
}
.course-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.tag {
  padding: 0 var(--space-xs);
  border: var(--rule-hair) solid var(--color-rule-2);
  border-radius: var(--radius-pill);
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.note {
  font-size: var(--text-xs);
}
</style>
