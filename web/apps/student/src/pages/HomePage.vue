<script setup lang="ts">
// 首页（需求 5.2，S-PTL-01）：游客显示产品介绍与全部路线，登录后显示个人学习区块
import { computed, onMounted, ref } from 'vue'
import type { HomeView } from '@yunshu/api'
import { errorText, homeApi } from '@/api'
import RouteCardItem from '@/components/RouteCard.vue'

const home = ref<HomeView | null>(null)
const loading = ref(false)
const loadError = ref('')

const isGuest = computed(() => home.value?.is_guest ?? true)
const routes = computed(() => home.value?.routes ?? [])
const myRoutes = computed(() => home.value?.my_routes ?? [])
const continueLearning = computed(() => home.value?.continue_learning ?? null)
const pendingWrong = computed(() => home.value?.pending_wrong_question_count ?? 0)
const interviewRemaining = computed(() => home.value?.interview_remaining ?? 0)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    home.value = await homeApi.load()
  } catch (e) {
    loadError.value = errorText(e, '首页加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="home">
    <div v-if="loadError" class="alert-error" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" class="btn btn-sm" @click="load">重新加载</button>
    </div>

    <!-- 产品介绍：游客可见 -->
    <section v-if="isGuest" class="hero">
      <div class="hero-copy">
        <h1>从课程到 AI 面试，一条路线学到底</h1>
        <p class="lead">按学习路线系统学习，刷题检验，模拟考试，AI 面试复盘，把「学过」变成「掌握」。</p>
        <div class="actions">
          <RouterLink to="/login" class="btn btn-primary">免费注册</RouterLink>
          <RouterLink to="/routes" class="btn">浏览学习路线</RouterLink>
        </div>
      </div>
      <ul class="features">
        <li>
          <b>按路线学</b>
          <span>阶段清晰，进度可见</span>
        </li>
        <li>
          <b>刷题与考试</b>
          <span>判分即时，错题沉淀</span>
        </li>
        <li>
          <b>AI 面试</b>
          <span>文字或语音，生成报告</span>
        </li>
      </ul>
    </section>

    <!-- 已登录：个性化区块 -->
    <template v-if="!isGuest && !loading">
      <section class="section">
        <div class="section-head">
          <h2>继续学习</h2>
          <RouterLink to="/records">学习记录</RouterLink>
        </div>
        <RouterLink v-if="continueLearning" :to="`/route/${continueLearning.learning_route_id}`" class="card continue">
          <span class="muted">{{ continueLearning.route_name }}</span>
          <b>{{ continueLearning.course_title }}</b>
          <span class="muted">{{ continueLearning.stage_name }} · 继续学习 ›</span>
        </RouterLink>
        <div v-else class="skeleton">还没有学习记录，先从一条学习路线开始</div>
      </section>

      <section class="section">
        <div class="section-head">
          <h2>我的路线进度</h2>
          <span class="muted">已学与已掌握进度</span>
        </div>
        <ul v-if="myRoutes.length" class="my-routes">
          <li v-for="route in myRoutes" :key="route.id">
            <RouterLink :to="`/route/${route.id}`">{{ route.name }}</RouterLink>
            <span v-if="route.progress" class="fig">
              已学 {{ route.progress.learned_course_count }}/{{ route.progress.total_course_count }} · 已掌握
              {{ route.progress.mastered_course_count }}
            </span>
          </li>
        </ul>
        <div v-else class="skeleton">还没有开始学习任何路线</div>
      </section>

      <div class="stats">
        <RouterLink to="/wrong" class="card stat">
          <span class="muted">待重做错题</span>
          <b class="fig">{{ pendingWrong }}</b>
          <span class="muted">去错题本 ›</span>
        </RouterLink>
        <RouterLink to="/interview" class="card stat">
          <span class="muted">今日 AI 面试剩余次数</span>
          <b class="fig">{{ interviewRemaining }}</b>
          <span class="muted">去面试 ›</span>
        </RouterLink>
      </div>
    </template>

    <!-- 全部学习路线 -->
    <section class="section">
      <div class="section-head">
        <h2>全部学习路线</h2>
        <RouterLink to="/routes">查看全部</RouterLink>
      </div>
      <div v-if="loading" class="route-grid">
        <div v-for="i in 3" :key="i" class="route-skeleton" aria-hidden="true" />
      </div>
      <div v-else-if="routes.length" class="route-grid">
        <RouteCardItem v-for="route in routes" :key="route.id" :route="route" />
      </div>
      <div v-else class="skeleton">还没有已上架的学习路线</div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xl);
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
  color: var(--color-ink);
  font-size: var(--text-sm);
}
.alert-error .btn {
  margin-left: auto;
}
.hero {
  display: grid;
  gap: var(--space-xl);
  padding: var(--space-xl) 0 var(--space-lg);
  border-bottom: var(--rule-hair) solid var(--color-rule);
}
.hero-copy h1 {
  max-width: 18ch;
  font-size: var(--text-3xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.25;
}
.lead {
  max-width: 46ch;
  margin-top: var(--space-md);
  color: var(--color-muted);
  font-size: var(--text-lg);
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
}
.features {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-sm);
  margin: 0;
  padding: 0;
  list-style: none;
}
.features li {
  display: flex;
  flex-direction: column;
  gap: var(--space-3xs);
  padding: var(--space-md);
  border: var(--rule-hair) solid var(--color-rule);
  border-radius: var(--radius-card);
  background: var(--color-cotton);
}
.features b {
  font-size: var(--text-sm);
}
.features span {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.section {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-sm);
}
.section-head h2 {
  font-size: var(--text-lg);
  font-weight: 600;
}
.section-head span,
.section-head a {
  font-size: var(--text-xs);
}
.section-head a {
  margin-left: auto;
}
.continue {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
  text-decoration: none;
  color: var(--color-ink);
}
.continue:hover {
  text-decoration: none;
  border-color: var(--color-accent);
}
.continue b {
  font-size: var(--text-lg);
}
.muted {
  font-size: var(--text-sm);
}
.my-routes {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  margin: 0;
  padding: 0;
  list-style: none;
}
.my-routes li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border: var(--rule-hair) solid var(--color-rule);
  border-radius: var(--radius-card);
  background: var(--color-cotton);
  font-size: var(--text-sm);
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-sm);
}
.stat {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
  text-decoration: none;
  color: var(--color-ink);
}
.stat:hover {
  text-decoration: none;
  border-color: var(--color-accent);
}
.stat b {
  font-size: var(--text-2xl);
  font-weight: 600;
}
.route-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-sm);
}
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
.route-card .intro {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
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
.route-skeleton {
  min-height: 132px;
  border: 1px dashed var(--color-rule-2);
  border-radius: var(--radius-card);
  background: linear-gradient(180deg, var(--color-paper-2), var(--color-cotton));
}
</style>
