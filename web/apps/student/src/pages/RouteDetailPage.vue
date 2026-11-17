<script setup lang="ts">
// 路线详情（需求 5.3.2、5.3.3，S-PTL-03 / S-LRN-01~03 / S-FDB-01）
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteDetailView, RouteCourseItem } from '@yunshu/api'
import { errorText, learningApi, routeApi } from '@/api'
import { useUserStore } from '@/stores/user'

const current = useRoute()
const router = useRouter()
const user = useUserStore()

const detail = ref<RouteDetailView | null>(null)
const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const busyCourse = ref<number | null>(null)
const highlightId = ref<number | null>(null)
const feedbackCourse = ref<RouteCourseItem | null>(null)
const feedbackText = ref('')
const feedbackBusy = ref(false)
const feedbackDone = ref(false)

/** 页头展示的课程总数 */
const courseTotal = computed(() => detail.value?.stages.reduce((sum, stage) => sum + stage.courses.length, 0) ?? 0)

/** 已学课程数：还没有学习记录时按 0 展示 */
const learnedCount = computed(() => detail.value?.progress?.learned_course_count ?? 0)

/** 进度条比例（0~1），用于 --p 变量 */
function ratio(done: number, total: number) {
  return total > 0 ? Math.min(1, done / total).toFixed(3) : 0
}

/** 从首页「继续学习」进入时带 ?course=id，滚动并高亮到上次学的那门课 */
async function locateCourse() {
  await nextTick()
  const id = Number(current.query.course)
  if (!id) {
    highlightId.value = null
    return
  }
  highlightId.value = id
  document.getElementById(`course-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

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
  await locateCourse()
}

onMounted(load)
watch(() => current.params.id, load)
watch(() => current.query.course, locateCourse)

function requireLogin(): boolean {
  if (user.isLoggedIn) return true
  router.push({ path: '/login', query: { redirect: current.fullPath } })
  return false
}

/** 需求 5.3.3：PC 新窗口打开；手机端 B 站优先唤起 App，未安装时回落网页 */
function openVideo(url: string, platform: string | null) {
  const mobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent)
  if (mobile && platform && platform.includes('B 站')) {
    const bv = /BV[0-9A-Za-z]+/.exec(url)?.[0]
    if (bv) {
      window.location.href = `bilibili://video/${bv}`
      window.setTimeout(() => window.open(url, '_blank', 'noopener,noreferrer'), 1200)
      return
    }
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function openCourse(course: RouteCourseItem, stageId: number) {
  if (!requireLogin()) return
  busyCourse.value = course.id
  actionError.value = ''
  try {
    const routeId = Number(current.params.id)
    const result = await learningApi.open(course.id, {
      learning_route_id: routeId,
      route_stage_id: stageId,
    })
    openVideo(result.video_url, result.platform_name)
  } catch (e) {
    actionError.value = errorText(e, '打开课程失败，请稍后重试')
  } finally {
    busyCourse.value = null
  }
}

async function toggleLearned(course: RouteCourseItem) {
  if (!requireLogin()) return
  busyCourse.value = course.id
  actionError.value = ''
  try {
    if (course.is_learned) await learningApi.cancelLearned(course.id)
    else await learningApi.markLearned(course.id)
    await load()
  } catch (e) {
    actionError.value = errorText(e, '操作失败，请稍后重试')
  } finally {
    busyCourse.value = null
  }
}

function showFeedback(course: RouteCourseItem) {
  if (!requireLogin()) return
  feedbackCourse.value = course
  feedbackText.value = ''
  feedbackDone.value = false
  actionError.value = ''
}

async function submitFeedback() {
  if (!feedbackCourse.value) return
  feedbackBusy.value = true
  actionError.value = ''
  try {
    await learningApi.submitFeedback({
      feedback_type: 'video_invalid',
      course_id: feedbackCourse.value.id,
      description: feedbackText.value.trim() || undefined,
    })
    feedbackDone.value = true
  } catch (e) {
    actionError.value = errorText(e, '反馈提交失败，请稍后重试')
  } finally {
    feedbackBusy.value = false
  }
}
</script>

<template>
  <section class="page">
    <div v-if="loadError" class="alert-error" role="alert">
      <span>{{ loadError }}</span>
      <RouterLink to="/routes" class="btn btn-sm">返回路线列表</RouterLink>
    </div>

    <div v-else-if="loading" class="skeleton">正在加载路线…</div>

    <template v-else-if="detail">
      <RouterLink to="/routes" class="back">返回学习路线</RouterLink>

      <p v-if="actionError" class="alert-error" role="alert">{{ actionError }}</p>

      <section class="route-head">
        <div>
          <h1>{{ detail.name }}</h1>
          <p v-if="detail.intro" class="intro">{{ detail.intro }}</p>
          <div class="meta mt-12">
            <span>{{ detail.stages.length }} 个阶段</span>
            <span class="fig">{{ courseTotal }} 门课程</span>
            <span>所有阶段都开放</span>
          </div>
        </div>
        <div class="head-side">
          <template v-if="user.isLoggedIn">
            <div>
              <div class="progress-label">
                <span>已学进度</span>
                <span class="fig">{{ learnedCount }}/{{ courseTotal }}</span>
              </div>
              <div class="progress">
                <span :style="{ '--p': ratio(learnedCount, courseTotal) }" />
              </div>
            </div>
            <div v-if="detail.progress">
              <div class="progress-label">
                <span>已掌握进度（已配题课程）</span>
                <span class="fig">{{ detail.progress.mastered_course_count }}/{{ detail.progress.configured_course_count }}</span>
              </div>
              <div class="progress teal">
                <span :style="{ '--p': ratio(detail.progress.mastered_course_count, detail.progress.configured_course_count) }" />
              </div>
            </div>
          </template>
          <RouterLink v-else to="/login" class="btn btn-primary">登录后记录进度</RouterLink>
          <RouterLink :to="user.isLoggedIn ? '/interview' : { path: '/login', query: { redirect: current.fullPath } }" class="btn">
            针对本路线 AI 面试
          </RouterLink>
        </div>
      </section>

      <section v-for="stage in detail.stages" :key="stage.id" class="card stage">
        <div class="stage-head">
          <h3>{{ stage.name }}</h3>
          <span class="meta">
            <template v-if="stage.progress">
              <span>已学 <span class="fig">{{ stage.progress.learned_course_count }}/{{ stage.progress.total_course_count }}</span></span>
              <span>已掌握 <span class="fig">{{ stage.progress.mastered_course_count }}/{{ stage.progress.configured_course_count }}</span></span>
            </template>
            <span v-else>{{ stage.courses.length }} 门课程</span>
          </span>
          <div class="stage-ops">
            <RouterLink v-if="stage.is_random_available" :to="`/practice/random/${stage.id}?route=${detail.id}`" class="btn btn-sm">
              随机抽题
            </RouterLink>
            <button v-else type="button" class="btn btn-sm" disabled>随机抽题（题目建设中）</button>
            <RouterLink v-if="stage.is_exam_available" :to="`/exam/${stage.id}?route=${detail.id}`" class="btn btn-sm">模拟考试</RouterLink>
            <button v-else type="button" class="btn btn-sm" disabled>模拟考试（题目建设中）</button>
            <RouterLink v-if="stage.is_interview_available" :to="`/interview?stage=${stage.id}`" class="btn btn-sm">
              面试本阶段
            </RouterLink>
            <button v-else type="button" class="btn btn-sm" disabled>面试本阶段（暂无面试题）</button>
          </div>
        </div>

        <template v-if="stage.courses.length">
          <div
            v-for="(course, index) in stage.courses"
            :id="`course-${course.id}`"
            :key="course.id"
            class="course-row"
            :class="{ highlight: highlightId === course.id }"
          >
            <span class="course-no">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="course-main">
              <button
                type="button"
                class="course-title"
                :disabled="busyCourse === course.id"
                :title="`前往 ${course.platform_name || '视频平台'} 观看`"
                @click="openCourse(course, stage.id)"
              >
                {{ course.title }}
              </button>
              <div class="course-meta">
                <span v-if="course.platform_name">{{ course.platform_name }}</span>
                <span v-if="course.duration_minutes" class="fig">{{ course.duration_minutes }} 分钟</span>
                <span v-if="course.subject_names.length">{{ course.subject_names.join('、') }}</span>
                <span class="fig">{{ course.practice_question_count ? `刷题 ${course.practice_question_count} 道` : '未配题' }}</span>
                <button type="button" class="link" @click="showFeedback(course)">视频无法播放？</button>
              </div>
            </div>
            <div class="course-ops">
              <span v-if="course.is_mastered" class="stamp">已掌握</span>
              <span v-else-if="course.is_learned" class="tag tag-primary">已学</span>
              <span v-else class="tag">未学</span>
              <button type="button" class="btn btn-sm btn-primary" :disabled="busyCourse === course.id" @click="openCourse(course, stage.id)">
                观看
              </button>
              <button
                v-if="!course.is_mastered"
                type="button"
                class="btn btn-sm"
                :disabled="busyCourse === course.id"
                @click="toggleLearned(course)"
              >
                {{ course.is_learned ? '取消已学' : '标记已学' }}
              </button>
              <RouterLink
                v-if="course.practice_question_count > 0"
                :to="`/practice/sequential/${course.id}`"
                class="btn btn-sm"
              >
                去练习
              </RouterLink>
            </div>
          </div>
        </template>
        <div v-else class="empty">本阶段还没有已上架课程</div>
      </section>

      <p class="muted note">点击课程名跳转视频平台并自动记录打开行为；游客需先登录。</p>
    </template>

    <!-- 视频失效反馈 -->
    <div
v-if="feedbackCourse" class="mask" role="dialog" aria-modal="true" aria-label="视频失效反馈"
         @click.self="feedbackCourse = null">
      <div class="dialog card">
        <header class="dialog-head">
          <h2>视频无法播放？</h2>
          <button
type="button" class="btn btn-ghost btn-sm" aria-label="关闭"
                  @click="feedbackCourse = null">✕</button>
        </header>
        <template v-if="feedbackDone">
          <p class="muted">已提交，运营处理后会在消息中心通知你。</p>
          <div class="dialog-foot">
            <button type="button" class="btn btn-primary" @click="feedbackCourse = null">完成</button>
          </div>
        </template>
        <template v-else>
          <p class="muted">课程：{{ feedbackCourse.title }}</p>
          <label class="field">
            <span class="label">补充说明（选填）</span>
            <textarea
v-model="feedbackText" class="input textarea" rows="3" maxlength="1000"
                      placeholder="例如：视频提示失效、跳转后不是这门课…" />
          </label>
          <div class="dialog-foot">
            <button type="button" class="btn" @click="feedbackCourse = null">取消</button>
            <button type="button" class="btn btn-primary" :disabled="feedbackBusy" @click="submitFeedback">
              {{ feedbackBusy ? '提交中…' : '提交反馈' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
}

.back {
  margin-bottom: var(--space-sm);
  color: var(--color-muted);
  font-size: var(--text-xs);
}


/* 页头：标题 + 进度/入口两栏（原型 route-head） */
.route-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--space-xl);
  align-items: end;
  padding-bottom: var(--space-lg);
  border-bottom: var(--rule-hair) solid var(--color-rule-2);
}

.route-head h1 {
  font-size: var(--text-3xl);
}

.intro {
  max-width: 60ch;
  margin-top: var(--space-xs);
  color: var(--color-ink-2);
}

.head-side {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}


/* 阶段卡：标题行 + 课程行，行间只用横线 */
.stage {
  margin-top: var(--space-lg);
  padding: 0;
  overflow: hidden;
}

.stage-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  border-bottom: var(--rule-hair) solid var(--color-rule);
}

.stage-head h3 {
  margin-right: auto;
}

.stage-ops {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}

.course-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  border-bottom: var(--rule-hair) solid var(--color-rule);
}

.course-row:last-child {
  border-bottom: 0;
}

.course-row.highlight {
  background: var(--color-accent-wash);
}

.course-no {
  width: 28px;
  flex-shrink: 0;
  color: var(--color-muted);
  font-family: var(--font-outlier);
  font-size: var(--text-xs);
}

.course-main {
  flex: 1;
  min-width: 200px;
}

.course-title {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-ink);
  font: inherit;
  font-size: var(--text-md);
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.course-title:hover {
  text-decoration: underline;
  text-decoration-color: var(--color-accent-2);
}

.course-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2xs) var(--space-md);
  margin-top: var(--space-2xs);
  color: var(--color-muted);
  font-size: var(--text-xs);
}

.link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-ink-2);
  font: inherit;
  font-size: var(--text-xs);
  text-decoration: underline;
  text-decoration-color: var(--color-rule-2);
  text-underline-offset: 0.16em;
  cursor: pointer;
}

.link:hover {
  text-decoration-color: var(--color-accent-2);
}

.course-ops {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

/* 状态与操作统一宽度，纵向对齐成一列 */
.course-ops > .btn,
.course-ops > .tag,
.course-ops > .stamp {
  box-sizing: border-box;
  min-width: 74px;
  justify-content: center;
  padding: 0 var(--space-xs);
  border-radius: var(--radius-stock);
}

.note {
  margin-top: var(--space-lg);
  font-size: var(--text-xs);
}


@media (max-width: 60rem) {
  .route-head {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-md);
  }

}
@media (max-width: 40rem) {
  .stage-head,
  .course-row {
    padding-left: var(--space-md);
    padding-right: var(--space-md);
  }

}

/* 视频失效反馈弹窗 */
.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
}

.field .label {
  font-size: var(--text-xs);
  color: var(--color-ink-2);
}

</style>
