<script setup lang="ts">
// 敏感词库（需求 7.6，A-CFG-05~08）
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SensitiveWordView } from '@yunshu/api'
import { configApi } from '@/api'
import ImportDialog from '@/components/ImportDialog.vue'
import { errorText } from '@/utils/format'

const words = ref<SensitiveWordView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 100
const keyword = ref('')
const loading = ref(false)
const loadError = ref('')
const newWord = ref('')
const adding = ref(false)
const importVisible = ref(false)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await configApi.sensitiveWords({
      page: page.value,
      page_size: pageSize,
      keyword: keyword.value.trim() || undefined,
    })
    words.value = result.items
    total.value = result.total
  } catch (e) {
    loadError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

onMounted(load)

async function add() {
  const word = newWord.value.trim()
  if (!word) {
    ElMessage.warning('请输入敏感词')
    return
  }
  adding.value = true
  try {
    await configApi.addSensitiveWord(word)
    newWord.value = ''
    ElMessage.success('已添加')
    search()
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    adding.value = false
  }
}

async function remove(word: SensitiveWordView) {
  const confirmed = await ElMessageBox.confirm(`确定删除敏感词「${word.word}」吗？`, '删除敏感词', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  try {
    await configApi.deleteSensitiveWord(word.id)
    ElMessage.success('已删除')
    if (words.value.length === 1 && page.value > 1) page.value -= 1
    load()
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}
</script>

<template>
  <p class="intro">
    用于过滤 AI 面试与简答题评分中学员的输入和 AI 的输出。学员输入命中时拦截并提示修改，不消耗次数；AI 输出命中时替换为
    *。匹配不区分大小写。
  </p>
  <div class="toolbar">
    <div class="add">
      <el-input
        v-model="newWord"
        maxlength="100"
        placeholder="输入敏感词"
        aria-label="新敏感词"
        class="word-input"
        @keyup.enter="add"
      />
      <el-button :loading="adding" @click="add">添加</el-button>
      <el-button @click="importVisible = true">批量导入</el-button>
    </div>
    <el-input
      v-model="keyword"
      placeholder="搜索敏感词"
      clearable
      aria-label="搜索敏感词"
      class="search"
      @keyup.enter="search"
      @clear="search"
    />
  </div>

  <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
    <el-button size="small" @click="load">重试</el-button>
  </el-alert>
  <div v-loading="loading" class="words">
    <el-tag v-for="w in words" :key="w.id" closable size="large" effect="plain" type="info" @close="remove(w)">
      {{ w.word }}
    </el-tag>
    <el-empty
      v-if="!loading && !words.length && !loadError"
      :description="keyword ? '没有匹配的敏感词' : '还没有敏感词'"
      :image-size="64"
    />
  </div>
  <div class="footer">
    <span class="count">共 {{ total }} 个</span>
    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
  </div>

  <ImportDialog
    v-model="importVisible"
    title="批量导入敏感词"
    template-type="sensitive_word"
    template-name="敏感词导入模板.xlsx"
    :rules="['第 1 列填写敏感词，每行 1 个，最多 100 个字符', '与已有敏感词或文件中前面的行重复的会跳过']"
    :upload="configApi.importSensitiveWords"
    @imported="search"
  />
</template>

<style scoped>
.intro {
  margin: 0 0 var(--space-md);
  color: var(--color-muted);
  font-size: var(--text-xs);
  line-height: 1.6;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}
.add {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}
.word-input {
  width: 240px;
}
.search {
  width: 220px;
}
.words {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
  min-height: 80px;
  padding: var(--space-md);
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.words .el-empty {
  margin: 0 auto;
  padding: 0;
}
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-sm);
}
.count {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
</style>
