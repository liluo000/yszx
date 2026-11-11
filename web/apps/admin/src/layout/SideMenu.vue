<script setup lang="ts">
import { computed } from 'vue'
import { menuGroups } from './menu'
import { useAuthStore } from '@/stores/auth'
import logoUrl from '@/assets/logo-reversed.png'

defineProps<{ open: boolean }>()
defineEmits<{ toggle: [] }>()

const auth = useAuthStore()
// 只显示当前账号有权限的菜单，整组都没有权限时隐藏分组
const visibleGroups = computed(() =>
  menuGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => auth.hasPermission(item.permission)) }))
    .filter((group) => group.items.length > 0),
)
</script>

<template>
  <aside class="side" :class="{ open }">
    <div class="brand">
      <RouterLink to="/workbench" class="brand-lockup" aria-label="云枢智学管理后台首页">
        <img :src="logoUrl" alt="云枢智学" width="836" height="183" />
        <span>管理后台</span>
      </RouterLink>
      <button
        type="button"
        class="menu-toggle"
        :aria-expanded="open"
        :aria-label="open ? '收起菜单' : '打开菜单'"
        @click="$emit('toggle')"
      >
        <span aria-hidden="true">{{ open ? '✕' : '☰' }}</span>
      </button>
    </div>
    <nav class="menu" aria-label="后台菜单">
      <template v-for="group in visibleGroups" :key="group.title || 'default'">
        <div v-if="group.title" class="menu-group">{{ group.title }}</div>
        <RouterLink v-for="item in group.items" :key="item.path" :to="item.path" class="menu-item">
          {{ item.title }}
        </RouterLink>
      </template>
    </nav>
  </aside>
</template>

<style scoped>
.side {
  position: fixed;
  inset: 0 auto 0 0;
  width: var(--side-width);
  background: var(--color-board);
  color: var(--color-board-type);
  overflow-y: auto;
  z-index: 20;
}
.brand {
  height: var(--bar-height);
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 0 var(--space-md);
}
.brand-lockup {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--color-board-type);
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
  text-decoration: none;
}
.brand-lockup img {
  height: 24px;
  width: auto;
}
.menu-toggle {
  display: none;
  margin-left: auto;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: var(--radius);
  background: transparent;
  color: var(--color-board-type);
  font-size: 18px;
  cursor: pointer;
}
.menu {
  padding: var(--space-xs) var(--space-sm) var(--space-lg);
}
.menu-group {
  padding: var(--space-md) var(--space-sm) var(--space-2xs);
  font-size: var(--text-xs);
  opacity: 0.6;
}
.menu-item {
  display: flex;
  align-items: center;
  min-height: 36px;
  padding: 0 var(--space-sm);
  border-radius: var(--radius);
  color: var(--color-board-type);
  opacity: 0.8;
  text-decoration: none;
}
.menu-item:hover {
  background: var(--color-ink-2);
  opacity: 1;
}
.menu-item.router-link-active {
  background: var(--color-ink-2);
  opacity: 1;
  box-shadow: inset 2px 0 0 var(--color-accent);
}
.menu-item:focus-visible,
.menu-toggle:focus-visible,
.brand-lockup:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: -2px;
}
@media (max-width: 960px) {
  .side {
    position: static;
    width: auto;
  }
  .menu-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  .menu {
    display: none;
  }
  .side.open .menu {
    display: block;
  }
}
</style>
