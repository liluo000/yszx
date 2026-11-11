<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABEL } from '@/constants/roles'
import ChangePasswordForm from '@/components/ChangePasswordForm.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const title = computed(() => (route.meta.title as string | undefined) ?? '')
const studentUrl = import.meta.env.VITE_STUDENT_URL
const passwordDialog = ref(false)

async function onCommand(command: string) {
  if (command === 'password') {
    passwordDialog.value = true
    return
  }
  if (command === 'logout') {
    const confirmed = await ElMessageBox.confirm('确定退出登录吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    }).catch(() => false)
    if (!confirmed) return
    await auth.logout()
    await router.replace('/login')
  }
}

function onPasswordChanged() {
  passwordDialog.value = false
  router.replace({ path: '/login', query: { message: '密码已修改，请用新密码重新登录' } })
}
</script>

<template>
  <header class="top">
    <span class="crumb">{{ title }}</span>
    <div class="right">
      <a :href="studentUrl" target="_blank" rel="noopener" class="student-link">打开学员端</a>
      <el-dropdown v-if="auth.user" trigger="click" @command="onCommand">
        <button type="button" class="user" :title="`${auth.user.name}（${auth.user.account}）`">
          <span class="role">{{ ROLE_LABEL[auth.user.role] }}</span>
          <span aria-hidden="true" class="caret">▾</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="password">修改密码</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <el-dialog v-model="passwordDialog" title="修改密码" width="420px" destroy-on-close append-to-body>
      <ChangePasswordForm @success="onPasswordChanged" />
    </el-dialog>
  </header>
</template>

<style scoped>
.top {
  position: sticky;
  top: 0;
  z-index: 10;
  height: var(--bar-height);
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 0 var(--space-lg);
  background: var(--color-paper);
  border-bottom: 1px solid var(--color-rule);
}
.crumb {
  font-weight: 600;
}
.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  font-size: var(--text-sm);
}
.student-link {
  color: var(--color-accent-2);
}
.user {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 4px 8px;
  border: 0;
  border-radius: var(--radius);
  background: transparent;
  color: var(--color-ink);
  font: inherit;
  cursor: pointer;
}
.user:hover {
  background: var(--color-rule);
}
.user:focus-visible {
  outline: 2px solid var(--color-accent-2);
}
.role {
  font-weight: 600;
}
.caret {
  color: var(--color-muted);
}
@media (max-width: 960px) {
  .top {
    padding: 0 var(--space-md);
  }
  .student-link {
    display: none;
  }
}
</style>
