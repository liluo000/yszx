<script setup lang="ts">
// 学员端全局外壳（项目原型 s-header / s-main / s-footer）：顶部导航、搜索、账号入口与页脚
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { StudentAgreementType } from '@yunshu/api'
import logoUrl from '@/assets/logo.png'
import logoMarkUrl from '@/assets/logo-mark.png'
import { accountApi, errorText, messageApi } from '@/api'
import AgreementDialog from '@/components/AgreementDialog.vue'
import { AGREEMENT_LABEL, useAgreements } from '@/composables/useAgreements'
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
const unread = ref(0)
let unreadTimer: number | undefined

/** 消息中心未读数：登录后定时轮询，页面内标记已读时也会触发刷新 */
async function refreshUnread() {
  if (!user.isLoggedIn) {
    unread.value = 0
    return
  }
  try {
    unread.value = (await messageApi.unreadCount()).unread_count
  } catch {
    unread.value = 0
  }
}

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
  refreshUnread()
  unreadTimer = window.setInterval(refreshUnread, 60_000)
  window.addEventListener('yunshu:messages-changed', refreshUnread)
  document.addEventListener('click', onDocumentClick)
})
onUnmounted(() => {
  if (unreadTimer) window.clearInterval(unreadTimer)
  window.removeEventListener('yunshu:messages-changed', refreshUnread)
  document.removeEventListener('click', onDocumentClick)
})
watch(() => user.isLoggedIn, refreshUnread)

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

/* ===== 协议更新后要求重新同意（需求 5.1.2） ===== */
const pendingAgreements = computed(() => user.profile?.pending_agreements ?? [])
const consentOpen = computed(() => user.isLoggedIn && pendingAgreements.value.length > 0)
const consentBusy = ref(false)
const consentError = ref('')
const agreements = useAgreements()
const agreementOpen = ref<StudentAgreementType | null>(null)

watch(consentOpen, (open) => {
  if (open) agreements.load()
})

async function acceptPending() {
  consentBusy.value = true
  consentError.value = ''
  try {
    await accountApi.acceptAgreements(pendingAgreements.value)
    await user.refresh()
  } catch (e) {
    consentError.value = errorText(e, '提交失败，请稍后重试')
  } finally {
    consentBusy.value = false
  }
}

async function declinePending() {
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
          <svg class="search-icon" viewBox="0 0 20 20" aria-hidden="true">
            <circle cx="8.5" cy="8.5" r="5.5" fill="none" stroke="currentColor" stroke-width="1.6" />
            <path d="M12.8 12.8 17 17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
          <input v-model="keyword" class="input" type="search" aria-label="搜索路线和课程" placeholder="搜索路线、课程" />
        </form>
        <div class="user">
          <template v-if="user.isLoggedIn">
            <RouterLink to="/messages" class="bell" aria-label="消息中心">
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M10 2.5a5 5 0 0 0-5 5v3l-1.5 2.5h13L15 10.5v-3a5 5 0 0 0-5-5Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
                <path d="M8.2 15.5a1.8 1.8 0 0 0 3.6 0" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              </svg>
              <span v-if="unread > 0" class="bell-badge">{{ unread > 99 ? '99+' : unread }}</span>
            </RouterLink>
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
                <p class="who">{{ displayName }}</p>
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
        <button type="button" class="footer-link" @click="agreementOpen = 'user_agreement'">用户协议</button>
        <button type="button" class="footer-link" @click="agreementOpen = 'privacy_policy'">隐私政策</button>
      </div>
    </footer>

    <!-- 协议更新：同意后继续，不同意则退出登录 -->
    <div v-if="consentOpen" class="consent-mask" role="dialog" aria-modal="true" aria-label="协议更新提示">
      <div class="consent card">
        <h2>协议已更新</h2>
        <p class="muted">继续使用前，请阅读并同意以下协议的最新版本。</p>
        <ul class="consent-list">
          <li v-for="item in pendingAgreements" :key="item.agreement_type">
            <span>{{ AGREEMENT_LABEL[item.agreement_type] }}</span>
            <span class="muted">版本 {{ item.version_no }}</span>
            <button type="button" class="consent-view" @click="agreementOpen = item.agreement_type">
              查看
            </button>
          </li>
        </ul>
        <p v-if="consentError" class="consent-error" role="alert">{{ consentError }}</p>
        <div class="consent-actions">
          <button type="button" class="btn" @click="declinePending">不同意并退出</button>
          <button type="button" class="btn btn-primary" :disabled="consentBusy" @click="acceptPending">
            {{ consentBusy ? '提交中…' : '同意并继续' }}
          </button>
        </div>
      </div>
    </div>

    <AgreementDialog
      v-if="agreementOpen"
      :open="true"
      :type="agreementOpen"
      :state="agreements.states.value[agreementOpen]"
      @close="agreementOpen = null"
    />
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
  height: 30px;
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
  position: relative;
  flex: 1;
  max-width: 320px;
}
.search-icon {
  position: absolute;
  left: var(--space-sm);
  top: 50%;
  width: 16px;
  height: 16px;
  color: var(--color-muted);
  transform: translateY(-50%);
  pointer-events: none;
}
.search .input {
  min-height: var(--control-h-sm);
  padding-left: 36px;
  background: var(--color-paper-2);
}
.user {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-left: auto;
}
.bell {
  position: relative;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-input);
  color: var(--color-ink-2);
}
.bell:hover {
  background: var(--color-paper-2);
  text-decoration: none;
}
.bell svg {
  width: 18px;
  height: 18px;
}
.bell-badge {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 var(--space-2xs);
  border-radius: var(--radius-pill);
  background: var(--color-board);
  color: var(--color-board-type);
  font-family: var(--font-outlier);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
.account {
  position: relative;
}
.account-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
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
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-pill);
  background: var(--color-paper-3);
  color: var(--color-ink);
  box-shadow: var(--edge-stock);
  font-size: var(--text-sm);
  font-weight: 600;
}
.account-name {
  max-width: 8rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account-menu {
  position: absolute;
  top: calc(100% + var(--space-xs));
  right: 0;
  z-index: var(--z-sticky);
  min-width: 200px;
  display: flex;
  flex-direction: column;
  gap: var(--space-3xs);
  padding: var(--space-2xs);
  border-radius: var(--radius-card);
  background: var(--color-cotton);
  box-shadow: var(--shadow-pop);
}
.account-menu .who {
  padding: var(--space-xs) var(--space-sm);
  margin-bottom: var(--space-2xs);
  border-bottom: var(--rule-hair) solid var(--color-rule);
  font-weight: 600;
}
.account-menu a,
.account-menu button {
  padding: var(--space-xs) var(--space-sm);
  border: 0;
  border-radius: var(--radius-input);
  background: none;
  color: var(--color-ink-2);
  font: inherit;
  font-size: var(--text-sm);
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}
.account-menu a:hover,
.account-menu button:hover {
  background: var(--color-paper-2);
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
/* 按钮形态的页脚链接：字号/字体一律继承页脚，避免与 span、a 不一致 */
.footer-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-ink-2);
  font: inherit;
  cursor: pointer;
}
.footer-link:hover {
  color: var(--color-ink);
  text-decoration: underline;
}

/* 协议更新弹窗 */
.consent-mask {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: grid;
  place-items: center;
  padding: var(--space-md);
  background: rgb(10 28 49 / 45%);
}
.consent {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  width: min(440px, 100%);
  padding: var(--space-lg);
}
.consent h2 {
  font-size: var(--text-lg);
  font-weight: 600;
}
.consent-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
  margin: 0;
  padding: 0;
  list-style: none;
}
.consent-list li {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-xs) 0;
  border-top: var(--rule-hair) solid var(--color-rule);
  font-size: var(--text-sm);
}
.consent-list li:first-child {
  border-top: 0;
}
.consent-view {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-accent-2);
  font: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
  text-decoration: underline;
}
.consent-error {
  color: var(--color-error);
  font-size: var(--text-sm);
}
.consent-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-xs);
  padding-top: var(--space-sm);
  border-top: var(--rule-hair) solid var(--color-rule);
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
