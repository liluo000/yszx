<script setup lang="ts">
// 首页（需求 5.2，S-PTL-01）
// 游客：产品介绍 + 全部路线；登录：欢迎标题 → 继续学习与数据卡两栏 → 我的路线进度 → 全部路线
import { computed, onMounted, ref } from 'vue'
import type { HomeView } from '@yunshu/api'
import { errorText, homeApi, isConsentRequired } from '@/api'
import RouteCardItem from '@/components/RouteCard.vue'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const home = ref<HomeView | null>(null)
const loading = ref(false)
const loadError = ref('')

const isGuest = computed(() => home.value?.is_guest ?? true)
const routes = computed(() => home.value?.routes ?? [])
const myRoutes = computed(() => home.value?.my_routes ?? [])
const continueLearning = computed(() => home.value?.continue_learning ?? null)
const pendingWrong = computed(() => home.value?.pending_wrong_question_count ?? 0)
const interviewRemaining = computed(() => home.value?.interview_remaining ?? 0)
const greetingName = computed(() => user.nickname || '同学')
/** 游客首屏右侧展示的路线（取第一条已上架路线） */
const leadRoute = computed(() => routes.value[0] ?? null)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    home.value = await homeApi.load()
  } catch (e) {
    // 未同意最新协议时由全局弹窗处理，这里不再叠加错误条
    if (!isConsentRequired(e)) loadError.value = errorText(e, '首页加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function continuePath(courseId: number) {
  const item = continueLearning.value
  return item ? `/route/${item.learning_route_id}?course=${courseId}` : '/routes'
}

/** 进度条比例（0~1），用于 --p 变量 */
function ratio(done: number, total: number) {
  return total > 0 ? Math.min(1, done / total).toFixed(3) : 0
}

onMounted(load)
</script>

<template>
  <div class="home">
    <div v-if="loadError" class="alert-error" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" class="btn btn-sm" @click="load">重新加载</button>
    </div>

    <!-- 产品介绍：游客可见（原型：墨色板 + 一张路线卡预览） -->
    <section v-if="home && isGuest" class="board">
      <div>
        <h1>学完一个阶段，就面一次</h1>
        <p>按路线看课、刷题，再用文字或语音和 AI 面试官过一遍，报告按阶段指出短板。</p>
        <div class="row wrap">
          <RouterLink to="/login" class="btn btn-lg">免费注册</RouterLink>
          <RouterLink to="/routes" class="btn btn-ghost btn-lg">浏览学习路线</RouterLink>
        </div>
      </div>
      <article v-if="leadRoute" class="card route-preview">
        <div class="row-between">
          <h3>{{ leadRoute.name }}</h3>
          <span class="small muted">{{ leadRoute.stages.length }} 个阶段</span>
        </div>
        <ol>
          <li v-for="stage in leadRoute.stages.slice(0, 5)" :key="stage.id">
            <span>{{ stage.name }}</span>
            <span class="fig">{{ stage.course_count }} 门</span>
          </li>
        </ol>
        <div class="row-between small">
          <span class="muted">课程看完，刷题正确率达到 70%</span>
          <span class="stamp">已掌握</span>
        </div>
      </article>
    </section>

    <dl v-if="home && isGuest" class="features">
      <div>
        <dt>学习路线</dt>
        <dd>多个方向按阶段编排视频课程，从任意阶段开始。</dd>
      </div>
      <div>
        <dt>刷题练习</dt>
        <dd>判断、单选、多选、简答四种题型，错题自动沉淀。</dd>
      </div>
      <div>
        <dt>AI 面试</dt>
        <dd>文字或语音实时对话，结束后生成分阶段的面试报告。</dd>
      </div>
    </dl>

    <!-- 已登录：欢迎标题 + 两栏（继续学习 / 数据卡） -->
    <template v-if="!isGuest && !loading">
      <header class="page-head">
        <div>
          <h1>{{ greetingName }}，继续上次的课程</h1>
          <p v-if="continueLearning">{{ continueLearning.route_name }}，{{ continueLearning.stage_name }}</p>
          <p v-else>还没有学习记录，先从一条学习路线开始</p>
        </div>
      </header>

      <div class="home-top">
        <article v-if="continueLearning" class="card continue">
          <div class="row-between wrap">
            <h2>{{ continueLearning.course_title }}</h2>
            <div class="row">
              <span v-if="continueLearning.is_mastered" class="stamp">已掌握</span>
              <span v-else-if="continueLearning.is_learned" class="tag tag-primary">已学</span>
              <span v-else class="tag">未学</span>
              <RouterLink :to="continuePath(continueLearning.course_id)" class="btn btn-primary">
                继续学习
              </RouterLink>
            </div>
          </div>

          <div class="mt-16">
            <div class="progress-label">
              <span>{{ continueLearning.route_name }} 已学进度</span>
              <span class="fig">
                {{ continueLearning.progress.learned_course_count }}/{{ continueLearning.progress.total_course_count }}
              </span>
            </div>
            <div
              class="progress"
              role="progressbar"
              aria-label="路线已学进度"
              aria-valuemin="0"
              :aria-valuemax="continueLearning.progress.total_course_count"
              :aria-valuenow="continueLearning.progress.learned_course_count"
            >
              <span :style="{ '--p': ratio(continueLearning.progress.learned_course_count, continueLearning.progress.total_course_count) }" />
            </div>
          </div>

          <div class="ledger">
            <RouterLink
              v-for="(course, index) in continueLearning.stage_courses"
              :key="course.id"
              class="ledger-row"
              :class="{ current: course.id === continueLearning.course_id }"
              :to="continuePath(course.id)"
            >
              <span class="no">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="grow">{{ course.title }}</span>
              <span v-if="course.is_mastered" class="stamp">已掌握</span>
              <span v-else-if="course.is_learned" class="tag tag-primary">已学</span>
              <span v-else class="tag">未学</span>
            </RouterLink>
          </div>
        </article>

        <div v-else class="card empty-continue">
          <p>还没有学习记录</p>
          <span class="muted">从一条路线开始，打开第一门课程后这里会显示进度</span>
          <RouterLink to="/routes" class="btn btn-primary">浏览学习路线</RouterLink>
        </div>

        <div class="stat-col">
          <RouterLink to="/wrong" class="stat-tile">
            <span class="label">待重做的错题</span>
            <span class="value">{{ pendingWrong }}<small>道</small></span>
          </RouterLink>
          <RouterLink to="/interview" class="stat-tile">
            <span class="label">今日 AI 面试剩余</span>
            <span class="value">{{ interviewRemaining }}<small>次</small></span>
            <span class="small muted">北京时间 0 点重置</span>
          </RouterLink>
        </div>
      </div>
    </template>

    <!-- 我的路线进度：已学 / 已掌握双进度条 -->
    <section v-if="!isGuest && !loading">
      <div class="section-head">
        <h2>我的路线进度</h2>
        <RouterLink to="/records">学习记录</RouterLink>
      </div>
      <div v-if="myRoutes.length" class="route-grid">
        <RouterLink v-for="route in myRoutes" :key="route.id" :to="`/route/${route.id}`" class="card hoverable my-route">
          <div class="row-between mb-12">
            <h3>{{ route.name }}</h3>
            <span class="small muted">{{ route.stages.length }} 个阶段</span>
          </div>
          <div class="col">
            <div>
              <div class="progress-label">
                <span>已学进度</span>
                <span class="fig">{{ route.progress?.learned_course_count ?? 0 }}/{{ route.progress?.total_course_count ?? 0 }}</span>
              </div>
              <div class="progress">
                <span :style="{ '--p': ratio(route.progress?.learned_course_count ?? 0, route.progress?.total_course_count ?? 0) }" />
              </div>
            </div>
            <div>
              <div class="progress-label">
                <span>已掌握进度</span>
                <span class="fig">{{ route.progress?.mastered_course_count ?? 0 }}/{{ route.progress?.configured_course_count ?? 0 }}</span>
              </div>
              <div class="progress teal">
                <span :style="{ '--p': ratio(route.progress?.mastered_course_count ?? 0, route.progress?.configured_course_count ?? 0) }" />
              </div>
            </div>
          </div>
        </RouterLink>
      </div>
      <div v-else class="empty">还没有开始学习任何路线</div>
    </section>

    <!-- 全部学习路线：游客与登录都显示 -->
    <section>
      <div class="section-head">
        <h2>全部学习路线</h2>
        <RouterLink to="/routes">查看全部</RouterLink>
      </div>
      <div v-if="loading || !home" class="route-grid">
        <div v-for="i in 3" :key="i" class="route-skeleton" aria-hidden="true" />
      </div>
      <div v-else-if="routes.length" class="route-grid">
        <RouteCardItem v-for="route in routes" :key="route.id" :route="route" />
      </div>
      <div v-else class="empty">还没有已上架的学习路线</div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
}

/* ===== 游客首屏：墨色板 + 路线卡预览 ===== */
.board {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 420px);
  gap: var(--space-xl);
  align-items: center;
  padding: var(--space-2xl) var(--space-xl);
  border-radius: 12px;
  background: var(--color-board);
  color: var(--color-board-type);
}
.board h1 {
  max-width: 14ch;
  color: var(--color-board-type);
  font-size: var(--text-3xl);
  line-height: 1.15;
}
.board p {
  max-width: 46ch;
  margin-top: var(--space-sm);
  color: var(--color-board-type);
  font-size: var(--text-md);
  opacity: 0.82;
}
.board .row {
  margin-top: var(--space-lg);
}
.board .btn-ghost {
  color: var(--color-board-type);
}
.board .btn-ghost:hover {
  background: var(--color-ink-2);
  color: var(--color-board-type);
}
.board .card {
  transform: rotate(-1deg);
  box-shadow: var(--edge-stock), 12px 10px 24px -10px oklch(var(--deep) / 0.6);
}
.route-preview {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  color: var(--color-ink);
}
.route-preview h3 {
  font-size: var(--text-lg);
}
.route-preview ol {
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.route-preview ol li {
  display: flex;
  justify-content: space-between;
  gap: var(--space-xs);
  padding: var(--space-2xs) 0;
  border-top: var(--rule-hair) solid var(--color-rule);
}
.features {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-lg);
  margin: var(--space-xl) 0 0;
}
.features div {
  padding-top: var(--space-sm);
  border-top: var(--rule-hair) solid var(--color-rule-2);
}
.features dt {
  font-size: var(--text-md);
  font-weight: 600;
}
.features dd {
  margin: var(--space-2xs) 0 0;
  color: var(--color-ink-2);
}

/* ===== 已登录 ===== */
.continue {
  display: flex;
  flex-direction: column;
}
.empty-continue {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-xs);
}
.empty-continue p {
  font-size: var(--text-lg);
  font-weight: 600;
}
.empty-continue .btn {
  margin-top: var(--space-2xs);
}
.my-route {
  display: flex;
  flex-direction: column;
  color: var(--color-ink);
  text-decoration: none;
}
.my-route:hover {
  text-decoration: none;
}
.my-route h3 {
  font-size: var(--text-md);
}
.route-skeleton {
  min-height: 132px;
  border-radius: var(--radius-card);
  background: linear-gradient(180deg, var(--color-paper-2), var(--color-cotton));
  box-shadow: var(--edge-stock);
}

@media (max-width: 60rem) {
  .board {
    grid-template-columns: minmax(0, 1fr);
    padding: var(--space-xl) var(--space-lg);
  }
  .features {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 40rem) {
  .board h1 {
    font-size: var(--text-2xl);
  }
  .features {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
