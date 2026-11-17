<script setup lang="ts">
// 路线卡片：名称、简介、阶段课程数与已学进度，首页与路线列表共用（原型 routeCard）
import { computed } from 'vue'
import type { RouteCard } from '@yunshu/api'

const props = defineProps<{ route: RouteCard }>()

const learned = computed(() => props.route.progress?.learned_course_count ?? 0)
const total = computed(() => props.route.progress?.total_course_count ?? 0)
const ratio = computed(() => (total.value > 0 ? Math.min(1, learned.value / total.value).toFixed(3) : 0))
</script>

<template>
  <RouterLink :to="`/route/${route.id}`" class="card hoverable route-card">
    <div class="row-between">
      <h3>{{ route.name }}</h3>
      <span class="small muted">{{ route.stages.length }} 个阶段</span>
    </div>
    <p v-if="route.intro" class="intro">{{ route.intro }}</p>
    <ol v-if="route.stages.length" class="stages">
      <li v-for="stage in route.stages" :key="stage.id">
        <span>{{ stage.name }}</span>
        <span class="fig">{{ stage.course_count }} 门</span>
      </li>
    </ol>
    <span v-else class="small muted">暂无阶段</span>

    <div v-if="total > 0" class="mt-8">
      <div class="progress-label">
        <span>已学进度</span>
        <span class="fig">{{ learned }}/{{ total }}</span>
      </div>
      <div class="progress">
        <span :style="{ '--p': ratio }" />
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.route-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  color: var(--color-ink);
  text-decoration: none;
}
.route-card:hover {
  text-decoration: none;
}
.route-card h3 {
  font-size: var(--text-lg);
}
.route-card .row-between > .small {
  white-space: nowrap;
}
.intro {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-ink-2);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.stages {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.stages li {
  display: flex;
  justify-content: space-between;
  gap: var(--space-xs);
  padding: var(--space-2xs) 0;
  border-top: var(--rule-hair) solid var(--color-rule);
}
</style>
