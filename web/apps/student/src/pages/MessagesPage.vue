<script setup lang="ts">
// 消息中心（需求 5.10，S-MSG-01~04）：站内信列表、筛选、标记已读与全部已读
import { computed, onMounted, ref } from 'vue'
import type { StudentMessageItem } from '@yunshu/api'
import { errorText, messageApi } from '@/api'

const PAGE_SIZE = 20
const items = ref<StudentMessageItem[]>([])
const total = ref(0)
const page = ref(1)
const filter = ref('')
const loading = ref(false)
const error = ref('')
const busyId = ref<number | null>(null)
const readAllBusy = ref(false)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const unreadCount = computed(() => items.value.filter((one) => !one.is_read).length)

const TYPE_LABEL: Record<string, string> = {
  agreement_updated: '协议更新',
  feedback_result: '反馈处理',
  interview_report_generated: '面试报告',
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await messageApi.list({
      page: page.value,
      page_size: PAGE_SIZE,
      ...(filter.value ? { is_read: filter.value === 'read' } : {}),
    })
    items.value = data.items
    total.value = data.total
  } catch (e) {
    error.value = errorText(e, '消息加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function changeFilter(value: string) {
  filter.value = value
  page.value = 1
  load()
}

async function markRead(item: StudentMessageItem) {
  if (item.is_read) return
  busyId.value = item.id
  try {
    await messageApi.markRead(item.id)
    item.is_read = true
    window.dispatchEvent(new Event('yunshu:messages-changed'))
  } catch (e) {
    error.value = errorText(e, '标记已读失败')
  } finally {
    busyId.value = null
  }
}

async function readAll() {
  readAllBusy.value = true
  try {
    await messageApi.readAll()
    items.value.forEach((one) => (one.is_read = true))
    window.dispatchEvent(new Event('yunshu:messages-changed'))
  } catch (e) {
    error.value = errorText(e, '操作失败，请稍后重试')
  } finally {
    readAllBusy.value = false
  }
}

function timeText(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>

<template>
  <div class="messages">
    <header class="page-head has-actions">
      <div>
        <h1>消息中心</h1>
        <p>协议更新、反馈处理与面试报告都会在这里通知你。</p>
      </div>
      <div class="head-actions">
        <span class="select-wrap">
          <select
            class="select message-filter"
            :class="{ active: filter }"
            :value="filter"
            aria-label="按已读状态筛选"
            @change="changeFilter(($event.target as HTMLSelectElement).value)"
          >
            <option value="">全部消息</option>
            <option value="unread">未读消息</option>
            <option value="read">已读消息</option>
          </select>
        </span>
        <button type="button" class="btn btn-sm" :disabled="readAllBusy || unreadCount === 0" @click="readAll">
          全部标为已读
        </button>
      </div>
    </header>

    <p v-if="error" class="alert-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="skeleton">消息加载中…</div>

    <div v-else-if="items.length" class="card">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="msg-item"
        :class="{ unread: !item.is_read }"
        :disabled="busyId === item.id"
        @click="markRead(item)"
      >
        <span class="dot" aria-hidden="true" />
        <span class="msg-body">
          <span class="msg-head">
            <b class="title">{{ item.title }}</b>
            <span class="tag">{{ TYPE_LABEL[item.message_type] ?? item.message_type }}</span>
            <span class="fig small muted">{{ timeText(item.created_at) }}</span>
          </span>
          <span class="content">{{ item.content }}</span>
        </span>
        <span v-if="!item.is_read" class="badge">未读</span>
      </button>
    </div>

    <div v-else class="card empty">
      <p>{{ filter ? '没有符合筛选条件的消息。' : '暂时还没有消息。' }}</p>
    </div>

    <div v-if="pageCount > 1" class="pager">
      <button type="button" class="btn btn-sm" :disabled="page <= 1" @click="page -= 1; load()">上一页</button>
      <span>第 {{ page }} / {{ pageCount }} 页，共 {{ total }} 条</span>
      <button type="button" class="btn btn-sm" :disabled="page >= pageCount" @click="page += 1; load()">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.messages {
  display: flex;
  flex-direction: column;
}
.message-filter {
  min-width: 9rem;
}
.msg-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  width: 100%;
  padding: var(--space-md) 0;
  border: 0;
  border-bottom: var(--rule-hair) solid var(--color-rule);
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.msg-item:last-child {
  border-bottom: 0;
}
.msg-item:disabled {
  opacity: 0.6;
  cursor: default;
}
.dot {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: 8px;
  border-radius: 50%;
  background: var(--color-rule-2);
}
.msg-item.unread .dot {
  background: var(--color-accent-2);
}
.msg-body {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-2xs);
}
.msg-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}
.msg-head .title {
  font-weight: 400;
}
.msg-item.unread .title {
  font-weight: 600;
}
.content {
  color: var(--color-ink-2);
  font-size: var(--text-sm);
}
.badge {
  flex: none;
  color: var(--color-accent-2);
  font-size: var(--text-xs);
}
</style>
