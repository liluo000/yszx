import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import StudentLayout from '@/layout/StudentLayout.vue'
import HomePage from '@/pages/HomePage.vue'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: StudentLayout,
    children: [
      { path: '', name: 'home', component: HomePage, meta: { title: '首页' } },
      { path: 'routes', component: () => import('@/pages/RouteListPage.vue'), meta: { title: '学习路线' } },
      { path: 'route/:id(\\d+)', component: () => import('@/pages/RouteDetailPage.vue'), meta: { title: '路线详情' } },
      { path: 'search', component: () => import('@/pages/SearchPage.vue'), meta: { title: '搜索' } },
      { path: 'login', component: () => import('@/pages/LoginPage.vue'), meta: { title: '登录' } },
      { path: 'register', component: () => import('@/pages/RegisterPage.vue'), meta: { title: '注册' } },
      { path: 'practice/wrong_redo', component: () => import('@/pages/PracticePage.vue'), meta: { title: '错题重做', requiresAuth: true } },
      { path: 'practice/:mode/:key', component: () => import('@/pages/PracticePage.vue'), meta: { title: '顺序练习', requiresAuth: true } },
      { path: 'exam/:sid', component: () => import('@/pages/ExamPage.vue'), meta: { title: '模拟考试', requiresAuth: true } },
      { path: 'wrong', component: () => import('@/pages/WrongBookPage.vue'), meta: { title: '错题本', requiresAuth: true } },
      { path: 'interview', component: () => import('@/pages/InterviewPage.vue'), meta: { title: 'AI 面试', requiresAuth: true } },
      { path: 'interviews', component: () => import('@/pages/InterviewsPage.vue'), meta: { title: '面试记录', requiresAuth: true } },
      { path: 'report/:id(\\d+)', component: () => import('@/pages/InterviewReportPage.vue'), meta: { title: '面试报告', requiresAuth: true } },
      { path: 'records', component: () => import('@/pages/RecordsPage.vue'), meta: { title: '学习记录', requiresAuth: true } },
      { path: 'profile', component: () => import('@/pages/ProfilePage.vue'), meta: { title: '个人中心', requiresAuth: true } },
      { path: 'messages', component: () => import('@/pages/MessagesPage.vue'), meta: { title: '消息中心', requiresAuth: true } },
      { path: 'help', component: () => import('@/pages/HelpPage.vue'), meta: { title: '帮助中心' } },
      { path: ':pathMatch(.*)*', redirect: '/' },
    ],
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

/**
 * 登录守卫：需要登录的页面跳登录页并记录来源，登录后回跳（需求 5.1.6）。
 * 刷新页面时令牌还在但本人信息未加载，先通过 S-ACC-07 恢复再判断。
 */
router.beforeEach(async (to) => {
  const user = useUserStore()
  if (user.token && !user.profile) await user.restore()

  if (to.meta.requiresAuth && !user.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if ((to.path === '/login' || to.path === '/register') && user.isLoggedIn) {
    return { path: '/' }
  }
  return true
})

/** 页面标题：路由标题 + 站点名 */
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 云枢智学` : '云枢智学'
})

export default router
