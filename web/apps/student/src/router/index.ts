import { createRouter, createWebHashHistory } from 'vue-router'

// 页面路由在开发各功能时添加
const router = createRouter({
  history: createWebHashHistory(),
  routes: [{ path: '/', component: () => import('@/pages/HomePage.vue') }],
})

export default router
