import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 设计令牌与基础样式：颜色、字号取自 @yunshu/design-tokens，与管理端保持一致
import '@yunshu/design-tokens/tokens.css'
import './styles/base.css'
import App from './App.vue'
import router from './router'

createApp(App).use(createPinia()).use(router).mount('#app')
