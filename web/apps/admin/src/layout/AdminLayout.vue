<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SideMenu from './SideMenu.vue'
import TopBar from './TopBar.vue'

const route = useRoute()
// 窄屏下侧边栏菜单折叠，切换页面后自动收起
const menuOpen = ref(false)
watch(
  () => route.path,
  () => (menuOpen.value = false),
)
</script>

<template>
  <div class="admin-layout">
    <SideMenu :open="menuOpen" @toggle="menuOpen = !menuOpen" />
    <div class="admin-main">
      <TopBar />
      <main class="admin-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
.admin-main {
  margin-left: var(--side-width);
  min-width: 0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.admin-content {
  flex: 1;
  padding: var(--space-lg);
}
@media (max-width: 960px) {
  .admin-main {
    margin-left: 0;
  }
  .admin-content {
    padding: var(--space-md);
  }
}
</style>
