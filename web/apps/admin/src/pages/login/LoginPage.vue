<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ApiError } from '@yunshu/api'
import { PASSWORD_MAX_LENGTH } from '@yunshu/shared'
import { useAuthStore } from '@/stores/auth'
import logoUrl from '@/assets/logo-reversed.png'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')
// 来自其他页面的提示，如"密码已修改""登录已失效"，不是错误
const notice = ref((route.query.message as string | undefined) ?? '')
const form = reactive({ account: '', password: '' })
const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  errorMessage.value = ''
  notice.value = ''
  if (!(await formRef.value?.validate().catch(() => false))) return
  submitting.value = true
  try {
    await auth.login(form.account, form.password)
    const redirect = route.query.redirect as string | undefined
    await router.replace(auth.mustChangePassword ? '/change-password' : redirect || '/workbench')
  } catch (e) {
    errorMessage.value = e instanceof ApiError ? e.message : '登录失败，请稍后重试'
    form.password = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="side">
      <img class="logo" :src="logoUrl" alt="云枢智学" width="836" height="183" />
      <div>
        <h1>管理后台</h1>
        <p>维护学习路线、课程和题库，处理学员反馈，查看学习与面试数据。</p>
      </div>
      <p class="note">密码连续输错 5 次锁定 15 分钟；8 小时无操作自动退出。</p>
    </section>
    <main class="main">
      <div class="card">
        <h2>登录</h2>
        <p class="sub">使用后台账号和密码登录</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="账号" prop="account">
            <el-input v-model.trim="form.account" name="username" autocomplete="username" :maxlength="50" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              name="password"
              show-password
              autocomplete="current-password"
              :maxlength="PASSWORD_MAX_LENGTH"
            />
          </el-form-item>
          <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="error" />
          <el-alert v-else-if="notice" :title="notice" type="info" :closable="false" show-icon class="error" />
          <el-button type="primary" native-type="submit" size="large" :loading="submitting" class="submit">
            登录
          </el-button>
        </el-form>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}
.side {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
  background: var(--color-board);
  color: var(--color-board-type);
}
.logo {
  height: 36px;
  width: auto;
  align-self: flex-start;
}
.side h1 {
  margin: 0;
  font-size: 32px;
  line-height: 1.2;
}
.side p {
  margin: var(--space-sm) 0 0;
  max-width: 46ch;
  opacity: 0.75;
}
.side .note {
  font-size: var(--text-xs);
  opacity: 0.6;
}
.main {
  display: grid;
  place-items: center;
  padding: 32px var(--space-md);
}
.card {
  width: 380px;
  max-width: 100%;
}
.card h2 {
  margin: 0;
  font-size: 24px;
}
.sub {
  margin: 4px 0 var(--space-lg);
  color: var(--color-muted);
}
.error {
  margin-bottom: var(--space-md);
}
.submit {
  width: 100%;
}
@media (max-width: 760px) {
  .login {
    grid-template-columns: minmax(0, 1fr);
  }
  .side {
    padding: var(--space-lg);
    gap: var(--space-lg);
  }
  .side .note {
    display: none;
  }
}
</style>
