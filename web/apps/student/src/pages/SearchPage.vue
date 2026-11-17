<script setup lang="ts">
// 搜索（需求 5.3.4，S-PTL-04）：按名称和简介搜索已上架路线与课程，结果分两组
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { SearchResult } from '@yunshu/api'
import { errorText, routeApi } from '@/api'
import RouteCardItem from '@/components/RouteCard.vue'

const current = useRoute()
const router = useRouter()
const keyword = ref('')
const result = ref<SearchResult | null>(null)
const loading = ref(false)
const loadError = ref('')

const hasResult = computed(() => (result.value?.routes.length ?? 0) + (result.value?.courses.length ?? 0) > 0)

async function load() {
  const q = String(current.query.q ?? '').trim()
  keyword.value = q
  if (!q) {
    result.value = null
    loadError.value = ''
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    result.value = await routeApi.search(q)
  } catch (e) {
    result.value = null
    loadError.value = errorText(e, '搜索失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function submit() {
  const q = keyword.value.trim()
  router.push({ path: '/search', query: q ? { q } : {} })
}

onMounted(load)
watch(() => current.query.q, load)
</script>

<template>
  <section class="page">
    <div class="page-head">
      <div>
        <h1>搜索</h1>
        <p>搜索已上架的学习路线和课程</p>
      </div>
    </div>

    <form class="search-bar" role="search" @submit.prevent="submit">
      <input v-model="keyword" class="input" type="search" aria-label="搜索路线和课程" placeholder="输入路线或课程名称、关键词" />
      <button type="submit" class="btn btn-primary">搜索</button>
    </form>

    <div v-if="loadError" class="alert-error" role="alert">{{ loadError }}</div>
    <div v-else-if="!loading && !String(current.query.q ?? '').trim()" class="empty">
      输入关键词后显示匹配的学习路线和课程
    </div>
    <div v-else-if="loading" class="skeleton">正在搜索…</div>
    <template v-else-if="result">
      <div v-if="!hasResult" class="empty">没有匹配的学习路线或课程，换个关键词试试</div>
      <template v-else>
        <section v-if="result.routes.length" class="group">
          <h2>路线 <span class="fig">{{ result.routes.length }}</span></h2>
          <div class="route-grid">
            <RouteCardItem v-for="route in result.routes" :key="route.id" :route="route" />
          </div>
        </section>
        <section v-if="result.courses.length" class="group">
          <h2>课程 <span class="fig">{{ result.courses.length }}</span></h2>
          <ul class="courses">
            <li v-for="course in result.courses" :key="course.id" class="course">
              <div class="course-main">
                <b>{{ course.title }}</b>
                <span v-if="course.intro" class="muted intro">{{ course.intro }}</span>
              </div>
              <div class="route-links">
                <RouterLink
                  v-for="routeRef in course.routes"
                  :key="`${routeRef.learning_route_id}-${routeRef.route_stage_id}`"
                  :to="`/route/${routeRef.learning_route_id}`"
                  class="btn btn-sm"
                >
                  {{ routeRef.route_name }}
                </RouterLink>
                <span v-if="!course.routes.length" class="muted">暂未加入路线</span>
              </div>
            </li>
          </ul>
        </section>
      </template>
    </template>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}
.search-bar {
  display: flex;
  gap: var(--space-xs);
  max-width: 560px;
}
.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.group h2 {
  font-size: var(--text-md);
  font-weight: 600;
}
.group h2 .fig {
  margin-left: var(--space-2xs);
  color: var(--color-muted);
  font-weight: 400;
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
  min-width: 0;
}
.course-main b {
  font-size: var(--text-sm);
}
.intro {
  font-size: var(--text-xs);
}
.route-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
  font-size: var(--text-xs);
}
</style>
