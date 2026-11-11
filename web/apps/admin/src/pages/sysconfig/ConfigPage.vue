<script setup lang="ts">
// 系统配置（需求第 7 章，A-CFG-01~18）：左侧分组与原型一致，修改均记入操作日志
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { HelpContact } from '@yunshu/api'
import { configApi } from '@/api'
import { errorText } from '@/utils/format'
import { PARAMETER_TABS } from './configFields'
import ParameterTab from './tabs/ParameterTab.vue'
import AgreementTab from './tabs/AgreementTab.vue'
import SensitiveWordTab from './tabs/SensitiveWordTab.vue'
import HelpTab from './tabs/HelpTab.vue'
import VideoPlatformTab from './tabs/VideoPlatformTab.vue'

const TABS = [
  ...Object.entries(PARAMETER_TABS).map(([key, tab]) => ({ key, title: tab.title })),
  { key: 'agree', title: '协议文本' },
  { key: 'words', title: '敏感词库' },
  { key: 'help', title: '帮助中心与联系方式' },
  { key: 'platform', title: '视频平台' },
]

const route = useRoute()
const router = useRouter()
const tab = computed(() => {
  const key = route.query.tab as string | undefined
  return TABS.some((t) => t.key === key) ? key! : 'ai'
})
const tabTitle = computed(() => TABS.find((t) => t.key === tab.value)?.title ?? '')
const parameterTab = computed(() => PARAMETER_TABS[tab.value])

function select(key: string) {
  router.replace({ query: { ...route.query, tab: key } })
}

const values = ref<Record<string, unknown>>({})
const contact = computed(() => values.value['help.contact'] as HelpContact | undefined)
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    values.value = Object.fromEntries((await configApi.configs()).items.map((c) => [c.config_key, c.config_value]))
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function onSaved(key: string, value: unknown) {
  values.value = { ...values.value, [key]: value }
}

onMounted(load)
</script>

<template>
  <div class="layout">
    <nav class="nav" aria-label="配置分组">
      <button
        v-for="t in TABS"
        :key="t.key"
        type="button"
        class="nav-item"
        :class="{ active: t.key === tab }"
        :aria-current="t.key === tab ? 'page' : undefined"
        @click="select(t.key)"
      >
        {{ t.title }}
      </button>
    </nav>

    <section class="panel">
      <h3 class="panel-title">{{ tabTitle }}</h3>
      <template v-if="parameterTab || tab === 'help'">
        <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
          <el-button size="small" @click="load">重试</el-button>
        </el-alert>
        <div v-else-if="loading" v-loading="true" class="loading" />
        <template v-else>
          <ParameterTab
            v-if="parameterTab"
            :fields="parameterTab.fields"
            :note="parameterTab.note"
            :values="values"
            @saved="onSaved"
          />
          <HelpTab v-else :contact="contact" @saved="onSaved" />
        </template>
      </template>
      <AgreementTab v-else-if="tab === 'agree'" />
      <SensitiveWordTab v-else-if="tab === 'words'" />
      <VideoPlatformTab v-else-if="tab === 'platform'" />
    </section>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: var(--space-md);
  align-items: start;
}
.nav {
  position: sticky;
  top: calc(var(--bar-height) + var(--space-lg));
  display: flex;
  flex-direction: column;
  padding: var(--space-xs);
  background: var(--color-cotton);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.nav-item {
  padding: 10px var(--space-sm);
  border: 0;
  border-radius: var(--radius);
  background: transparent;
  color: var(--color-ink-2);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.nav-item:hover {
  background: var(--color-paper);
}
.nav-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--color-accent-2);
  font-weight: 600;
}
.nav-item:focus-visible {
  outline: 2px solid var(--color-accent-2);
  outline-offset: -2px;
}
.panel {
  min-width: 0;
  padding: var(--space-lg);
  background: var(--color-cotton);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.panel-title {
  margin: 0 0 var(--space-md);
  font-size: 16px;
}
.loading {
  min-height: 200px;
}
@media (max-width: 960px) {
  .layout {
    grid-template-columns: 1fr;
  }
  .nav {
    position: static;
    flex-direction: row;
    overflow-x: auto;
  }
  .nav-item {
    white-space: nowrap;
  }
}
</style>
