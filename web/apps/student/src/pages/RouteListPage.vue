<script setup lang="ts">
// 学习路线列表（需求 5.3.1，S-PTL-02）：全部已上架路线
import { computed, onMounted, ref, watch } from 'vue'
import type { RouteCard } from '@yunshu/api'
import { errorText, routeApi } from '@/api'
import RouteCardItem from '@/components/RouteCard.vue'

const PAGE_SIZE = 12

const items = ref<RouteCard[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const pages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await routeApi.list({ page: page.value, page_size: PAGE_SIZE })
    items.value = result.items
    total.value = result.total
  } catch (e) {
    loadError.value = errorText(e, '学习路线加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(page, load)
</script>

<template>
  <section class="page">
    <div class="page-head">
      <h1>学习路线</h1>
      <p>共 {{ total }} 条已上架路线，按阶段循序渐进</p>
    </div>

    <div v-if="loadError" class="alert-error" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" class="btn btn-sm" @click="load">重新加载</button>
    </div>

    <div v-if="loading" class="grid">
      <div v-for="i in 3" :key="i" class="skeleton-card" aria-hidden="true" />
    </div>
    <div v-else-if="items.length" class="grid">
      <RouteCardItem v-for="route in items" :key="route.id" :route="route" />
    </div>
    <div v-else-if="!loadError" class="skeleton">还没有已上架的学习路线</div>

    <div v-if="pages > 1" class="pager">
      <button type="button" class="btn btn-sm" :disabled="page <= 1" @click="page -= 1">上一页</button>
      <span class="fig">{{ page }} / {{ pages }}</span>
      <button type="button" class="btn btn-sm" :disabled="page >= pages" @click="page += 1">下一页</button>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
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
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-sm);
}
.skeleton-card {
  min-height: 160px;
  border: 1px dashed var(--color-rule-2);
  border-radius: var(--radius-card);
  background: linear-gradient(180deg, var(--color-paper-2), var(--color-cotton));
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  color: var(--color-muted);
  font-size: var(--text-sm);
}
</style>
