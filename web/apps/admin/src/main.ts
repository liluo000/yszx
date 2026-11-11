import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import App from './App.vue'
import router from './router'
import { onUnauthorized } from './api'
import { useAuthStore } from './stores/auth'

const app = createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale: zhCn })

// 登录失效（如 8 小时无操作、被停用、在其他设备修改了密码）时回到登录页
onUnauthorized(() => {
  useAuthStore().reset()
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: current.fullPath, message: '登录已失效，请重新登录' } })
  }
})

app.mount('#app')
