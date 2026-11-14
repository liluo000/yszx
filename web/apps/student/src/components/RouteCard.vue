<script setup lang="ts">
// 路线卡片：名称、简介与各阶段课程数，首页与路线列表共用
import type { RouteCard } from '@yunshu/api'

defineProps<{ route: RouteCard }>()
</script>

<template>
  <RouterLink :to="`/route/${route.id}`" class="route-card">
    <h3>{{ route.name }}</h3>
    <p v-if="route.intro" class="intro">{{ route.intro }}</p>
    <ul v-if="route.stages.length" class="stages">
      <li v-for="stage in route.stages" :key="stage.id">
        <span>{{ stage.name }}</span>
        <span class="fig">{{ stage.course_count }} 门</span>
      </li>
    </ul>
    <span v-else class="empty">暂无阶段</span>
  </RouterLink>
</template>

<style scoped>
.route-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding: var(--space-lg);
  border: var(--rule-hair) solid var(--color-rule);
  border-radius: var(--radius-card);
  background: var(--color-cotton);
  color: var(--color-ink);
  text-decoration: none;
  transition: border-color var(--dur-short), box-shadow var(--dur-short);
}
.route-card:hover {
  text-decoration: none;
  border-color: var(--color-accent);
  box-shadow: var(--cast-stock);
}
.route-card h3 {
  font-size: var(--text-md);
  font-weight: 600;
}
.intro {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.stages {
  display: flex;
  flex-direction: column;
  gap: var(--space-3xs);
  margin: var(--space-2xs) 0 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-xs);
}
.stages li {
  display: flex;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-2xs) 0;
  border-top: var(--rule-hair) solid var(--color-rule);
  color: var(--color-muted);
}
.empty {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
</style>
