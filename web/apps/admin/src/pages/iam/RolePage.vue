<script setup lang="ts">
// 角色管理（需求 6.10，A-IAM-10）：固定三个角色，只读
import { onMounted, ref } from 'vue'
import type { AdminRole, RoleView } from '@yunshu/api'
import { sysUserApi } from '@/api'
import { ROLE_LABEL } from '@/constants/roles'
import { menuItems } from '@/layout/menu'
import { errorText } from '@/utils/format'

const permissionTitle = Object.fromEntries(menuItems.map((item) => [item.permission, item.title]))

const roles = ref<RoleView[]>([])
const counts = ref<Partial<Record<AdminRole, number>>>({})
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    roles.value = (await sysUserApi.roles()).items
    const totals = await Promise.all(
      roles.value.map((r) => sysUserApi.list({ role: r.role, page: 1, page_size: 1 }).then((p) => p.total)),
    )
    counts.value = Object.fromEntries(roles.value.map((r, i) => [r.role, totals[i]]))
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <el-alert type="info" :closable="false" show-icon>
      系统内置三个固定角色，不能新增、编辑或删除；新建系统用户时指定角色。
    </el-alert>
    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <div v-loading="loading" class="cards">
      <article v-for="role in roles" :key="role.role" class="card">
        <header class="head">
          <h3>{{ ROLE_LABEL[role.role] }}</h3>
          <span class="count">{{ counts[role.role] ?? '—' }} 个账号</span>
        </header>
        <p class="desc">{{ role.description }}</p>
        <div class="perms">
          <el-tag v-for="code in role.permissions" :key="code" effect="plain" type="info">
            {{ permissionTitle[code] ?? code }}
          </el-tag>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-md);
  min-height: 160px;
}
.card {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-lg);
  background: var(--color-cotton);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.head h3 {
  margin: 0;
  font-size: 16px;
}
.count {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.desc {
  margin: 0;
  color: var(--color-ink-2);
  line-height: 1.6;
}
.perms {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}
</style>
