import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layout/AdminLayout.vue'
import { menuItems } from '@/layout/menu'
import { useAuthStore } from '@/stores/auth'

const pageRoutes: RouteRecordRaw[] = menuItems.map((item) => ({
  path: item.path.slice(1),
  component: item.component,
  meta: { title: item.title, permission: item.permission },
}))

const router = createRouter({
  history: createWebHashHistory(),
  // 切换页面时回到顶部
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/login', component: () => import('@/pages/login/LoginPage.vue'), meta: { title: '登录', public: true } },
    {
      path: '/change-password',
      component: () => import('@/pages/account/ChangePasswordPage.vue'),
      meta: { title: '修改密码' },
    },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/workbench',
      children: pageRoutes,
    },
    { path: '/:pathMatch(.*)*', redirect: '/workbench' },
  ],
})

/** 登录校验、必须修改密码、按权限访问页面（04.API接口设计.md 2.7） */
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return auth.isLoggedIn && to.path === '/login' ? '/workbench' : true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!auth.loaded) {
    try {
      await auth.fetchMe()
    } catch {
      auth.reset()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (auth.mustChangePassword) {
    return to.path === '/change-password' ? true : '/change-password'
  }
  if (to.path === '/change-password') {
    return '/workbench'
  }
  const permission = to.meta.permission as string | undefined
  if (permission && !auth.hasPermission(permission)) {
    const first = menuItems.find((item) => auth.hasPermission(item.permission))
    return first ? first.path : '/login'
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} - 云枢智学 管理后台` : '云枢智学 管理后台'
})

export default router
