<script setup lang="ts">
// 学员端全局外壳（项目原型 s-header / s-main / s-footer）：顶部导航、搜索、账号入口与页脚
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import logoUrl from '@/assets/logo.png'
import logoMarkUrl from '@/assets/logo-mark.png'
import { useUserStore } from '@/stores/user'

const NAV = [
  { path: '/', title: '首页' },
  { path: '/routes', title: '学习路线' },
  { path: '/wrong', title: '错题本' },
  { path: '/interview', title: 'AI 面试' },
  { path: '/records', title: '学习记录' },
]

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const menuOpen = ref(false)
const keyword = ref('')
const userMenuOpen = ref(false)
const userBox = ref<HTMLElement | null>(null)

const displayName = computed(() => user.profile?.nickname || '我的账号')
const avatarText = computed(() => displayName.value.slice(0, 1))

const isActive = (path: string) => (path === '/' ? route.path === '/' : route.path.startsWith(path))

// 切换页面时收起移动端菜单与账号菜单
watch(() => route.fullPath, () => {
  menuOpen.value = false
  userMenuOpen.value = false
})

onMounted(() => {
  // 刷新后恢复本人信息（守卫已处理首次导航，这里兜底直接进入布局的情况）
  user.restore()
  document.addEventListener('click', onDocumentClick)
})
onUnmounted(() => document.removeEventListener('click', onDocumentClick))

function onDocumentClick(event: MouseEvent) {
  if (userMenuOpen.value && userBox.value && !userBox.value.contains(event.target as Node)) {
    userMenuOpen.value = false
  }
}

function onSearch() {
  const q = keyword.value.trim()
  if (!q) return
  router.push({ path: '/search', query: { q } })
}

/** 退出登录：当前页面需要登录时回到首页，避免停在受限页面 */
async function onLogout() {
  userMenuOpen.value = false
  await user.signOut()
  if (route.meta.requiresAuth) await router.push('/')
}
</script>

<template>
  <div class="shell">
    <header class="header">
      <div class="header-inner">
        <button
          type="button"
          class="menu-btn"
          :aria-expanded="menuOpen"
          aria-label="打开菜单"
          @click="menuOpen = !menuOpen"
        >
          ☰
        </button>
        <RouterLink to="/" class="logo" aria-label="云枢智学首页">
          <picture>
            <source media="(max-width: 40rem)" :srcset="logoMarkUrl" />
            <img :src="logoUrl" alt="云枢智学" width="836" height="183" />
          </picture>
        </RouterLink>
        <nav class="nav" :class="{ open: menuOpen }" aria-label="主导航">
          <RouterLink v-for="item in NAV" :key="item.path" :to="item.path" :class="{ active: isActive(item.path) }">
            {{ item.title }}
          </RouterLink>
        </nav>
        <form class="search" role="search" @submit.prevent="onSearch">
          <input v-model="keyword" class="input" type="search" aria-label="搜索路线和课程" placeholder="搜索路线、课程" />
        </form>
        <div class="user">
          <template v-if="user.isLoggedIn">
            <div ref="userBox" class="account">
              <button
                type="button"
                class="account-btn"
                :aria-expanded="userMenuOpen"
                aria-haspopup="menu"
                @click.stop="userMenuOpen = !userMenuOpen"
              >
                <span class="avatar" aria-hidden="true">{{ avatarText }}</span>
                <span class="account-name">{{ displayName }}</span>
              </button>
              <div v-if="userMenuOpen" class="account-menu" role="menu">
                <RouterLink to="/profile" role="menuitem">个人中心</RouterLink>
                <RouterLink to="/records" role="menuitem">学习记录</RouterLink>
                <RouterLink to="/interviews" role="menuitem">面试记录</RouterLink>
                <RouterLink to="/messages" role="menuitem">消息中心</RouterLink>
                <button type="button" role="menuitem" @click="onLogout">退出登录</button>
              </div>
            </div>
          </template>
          <template v-else>
            <RouterLink to="/login" class="btn btn-ghost btn-sm">登录</RouterLink>
            <RouterLink to="/register" class="btn btn-primary btn-sm">注册</RouterLink>
          </template>
        </div>
      </div>
    </header>

    <main class="main"><RouterView /></main>

    <footer class="footer">
      <div class="footer-inner">
        <span>云枢智学</span>
        <RouterLink to="/help">帮助中心</RouterLink>
        <RouterLink to="/help">用户协议</RouterLink>
        <RouterLink to="/help">隐私政策</RouterLink>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}
.header {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky-nav);
  background: var(--color-paper);
  border-bottom: var(--rule-hair) solid var(--color-rule);
}
.header-inner {
  max-width: var(--content-w);
  margin: 0 auto;
  height: var(--bar-h);
  padding: 0 var(--space-md);
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}
.logo {
  display: flex;
  align-items: center;
  white-space: nowrap;
}
.logo img {
  height: 26px;
  width: auto;
  max-width: none;
}
.logo:hover {
  text-decoration: none;
}
.nav {
  display: flex;
  gap: var(--space-2xs);
}
.nav a {
  padding: var(--space-2xs) var(--space-sm);
  border-radius: var(--radius-input);
  color: var(--color-muted);
  font-size: var(--text-sm);
  font-weight: 500;
  white-space: nowrap;
}
.nav a:hover {
  color: var(--color-ink);
  background: var(--color-paper-2);
  text-decoration: none;
}
.nav a.active {
  color: var(--color-ink);
  background: var(--color-paper-2);
}
.search {
  flex: 1;
  max-width: 320px;
}
.search .input {
  min-height: var(--control-h-sm);
  background: var(--color-paper-2);
}
.user {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-left: auto;
}
.account {
  position: relative;
}
.account-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
  padding: var(--space-3xs) var(--space-2xs);
  border: 0;
  border-radius: var(--radius-pill);
  background: none;
  color: var(--color-ink);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}
.account-btn:hover {
  background: var(--color-paper-2);
}
.avatar {
  display: grid;
  place-items: center;
  width: 1.75rem;
  height: 1.75rem;
  border-radius: var(--radius-pill);
  background: var(--color-board);
  color: var(--color-board-type);
  font-size: var(--text-xs);
}
.account-name {
  max-width: 8rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account-menu {
  position: absolute;
  top: calc(100% + var(--space-2xs));
  right: 0;
  z-index: var(--z-sticky);
  min-width: 9rem;
  display: flex;
  flex-direction: column;
  gap: var(--space-3xs);
  padding: var(--space-2xs);
  border: var(--rule-hair) solid var(--color-rule);
  border-radius: var(--radius-card);
  background: var(--color-cotton);
  box-shadow: var(--cast-stock);
}
.account-menu a,
.account-menu button {
  padding: var(--space-2xs) var(--space-sm);
  border: 0;
  border-radius: var(--radius-input);
  background: none;
  color: var(--color-ink-2);
  font: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}
.account-menu a:hover,
.account-menu button:hover {
  background: var(--color-paper-2);
  text-decoration: none;
}
.menu-btn {
  display: none;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: var(--radius-input);
  background: none;
  color: var(--color-ink-2);
  font-size: 18px;
  cursor: pointer;
}
.main {
  flex: 1;
  width: 100%;
  max-width: var(--content-w);
  margin: 0 auto;
  padding: var(--space-lg) var(--space-md) var(--space-3xl);
}
.footer {
  border-top: var(--rule-hair) solid var(--color-rule);
  padding: var(--space-lg) var(--space-md);
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.footer-inner {
  max-width: var(--content-w);
  margin: 0 auto;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-lg);
}
.footer a {
  color: var(--color-ink-2);
}

@media (max-width: 60rem) {
  .menu-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  .nav {
    display: none;
    position: absolute;
    top: var(--bar-h);
    left: 0;
    right: 0;
    flex-direction: column;
    gap: var(--space-2xs);
    padding: var(--space-sm) var(--space-md) var(--space-md);
    background: var(--color-paper);
    border-bottom: var(--rule-hair) solid var(--color-rule);
    box-shadow: var(--shadow-card);
  }
  .nav.open {
    display: flex;
  }
}
@media (max-width: 40rem) {
  .search {
    display: none;
  }
  .header-inner {
    gap: var(--space-sm);
  }
}
</style>
