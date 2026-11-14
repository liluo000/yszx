<script setup lang="ts">
// 页面框架占位：路由已接通，标题取路由 meta，具体功能在后续开发中替换本组件
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (route.meta.title as string | undefined) ?? '页面')
const desc = computed(() => (route.meta.desc as string | undefined) ?? '')
</script>

<template>
  <section class="page">
    <div class="page-head">
      <h1>{{ title }}</h1>
      <p v-if="desc">{{ desc }}</p>
    </div>
    <div class="skeleton">
      <strong>{{ title }}</strong>
      <span>页面框架已就绪，功能将在后续开发中实现。</span>
    </div>
  </section>
</template>
