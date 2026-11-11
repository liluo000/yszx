<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ChangePasswordForm from '@/components/ChangePasswordForm.vue'
import logoUrl from '@/assets/logo-reversed.png'

const auth = useAuthStore()
const router = useRouter()

function onSuccess() {
  router.replace({ path: '/login', query: { message: '密码已修改，请用新密码重新登录' } })
}

async function logout() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="page">
    <header class="bar">
      <img :src="logoUrl" alt="云枢智学" width="836" height="183" />
      <el-button link class="logout" @click="logout">退出登录</el-button>
    </header>
    <main class="main">
      <div class="card">
        <h1>请先修改密码</h1>
        <p class="sub">你正在使用临时密码登录，修改密码后才能使用管理后台。</p>
        <ChangePasswordForm @success="onSuccess" />
      </div>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}
.bar {
  height: var(--bar-height);
  display: flex;
  align-items: center;
  padding: 0 var(--space-lg);
  background: var(--color-board);
}
.bar img {
  height: 24px;
  width: auto;
}
.logout {
  margin-left: auto;
  color: var(--color-board-type);
}
.main {
  display: grid;
  place-items: center;
  padding: 48px var(--space-md);
}
.card {
  width: 400px;
  max-width: 100%;
  padding: 32px;
  background: var(--color-cotton);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.card h1 {
  margin: 0;
  font-size: 22px;
}
.sub {
  margin: 6px 0 var(--space-lg);
  color: var(--color-muted);
}
</style>
