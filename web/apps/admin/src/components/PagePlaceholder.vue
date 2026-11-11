<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (route.meta.title as string | undefined) ?? '')
</script>

<template>
  <section class="placeholder" :aria-label="title">
    <el-empty :description="`${title}页面待开发`" />
  </section>
</template>

<style scoped>
.placeholder {
  min-height: 360px;
  display: grid;
  place-items: center;
  background: var(--color-cotton);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
</style>
