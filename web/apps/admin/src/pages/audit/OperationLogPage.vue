<script setup lang="ts">
// 操作日志（需求 6.11，A-AUD-01）：只读，保留 1 年
import { onMounted, reactive, ref } from 'vue'
import type { AdminRole, OperationLogView, SysUserView } from '@yunshu/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditApi, sysUserApi } from '@/api'
import { ROLE_LABEL } from '@/constants/roles'
import { errorText, formatDateTime, saveBlob } from '@/utils/format'

/** 已记录日志的模块，新增业务模块时在此补充 */
const MODULES = ['账号', '系统用户', '系统配置', '操作日志']

const filters = reactive<{ sysUserId?: number; module?: string; dates: [string, string] | null }>({ dates: null })
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<OperationLogView[]>([])
const operators = ref<SysUserView[]>([])
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await auditApi.list({
      page: page.value,
      page_size: pageSize.value,
      sys_user_id: filters.sysUserId,
      module: filters.module,
      start_date: filters.dates?.[0],
      end_date: filters.dates?.[1],
    })
    rows.value = result.items
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

// 操作人下拉：后台账号数量少，取前 100 个即可
async function loadOperators() {
  try {
    operators.value = (await sysUserApi.list({ page: 1, page_size: 100 })).items
  } catch {
    operators.value = []
  }
}

/** 按当前筛选条件导出全部记录（不只当前页），导出本身也会记入操作日志 */
const exporting = ref(false)
async function exportLogs() {
  if (total.value === 0) {
    ElMessage.info('当前筛选条件下没有可导出的记录')
    return
  }
  const confirmed = await ElMessageBox.confirm(
    `将按当前筛选条件导出 ${total.value} 条操作日志（Excel），本次导出会记入操作日志。`,
    '导出日志',
    { confirmButtonText: '导出', cancelButtonText: '取消', type: 'info' },
  ).catch(() => false)
  if (!confirmed) return
  exporting.value = true
  try {
    const { blob, filename } = await auditApi.export({
      sys_user_id: filters.sysUserId,
      module: filters.module,
      start_date: filters.dates?.[0],
      end_date: filters.dates?.[1],
    })
    saveBlob(blob, filename ?? '操作日志.xlsx')
    ElMessage.success('导出成功')
    load()
  } catch (e) {
    ElMessage.error(errorText(e, '导出失败，请稍后重试'))
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  load()
  loadOperators()
})
</script>

<template>
  <section class="page">
    <div class="filters">
      <el-select
        v-model="filters.sysUserId"
        placeholder="全部操作人"
        clearable
        filterable
        class="filter"
        @change="search"
      >
        <el-option v-for="u in operators" :key="u.id" :label="`${u.name}（${u.account}）`" :value="u.id" />
      </el-select>
      <el-select v-model="filters.module" placeholder="全部模块" clearable class="filter" @change="search">
        <el-option v-for="m in MODULES" :key="m" :label="m" :value="m" />
      </el-select>
      <el-date-picker
        v-model="filters.dates"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        range-separator="至"
        class="dates"
        @change="search"
      />
      <el-button class="export" :loading="exporting" :disabled="loading" @click="exportLogs">导出日志</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" class="table" empty-text="暂无操作记录">
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作人" min-width="140">
        <template #default="{ row }">
          <div>{{ row.sys_user_name }}</div>
          <div class="sub">{{ ROLE_LABEL[row.role as AdminRole] ?? row.role }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column label="操作类型" width="140">
        <template #default="{ row }">
          <el-tag effect="plain" type="info">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作对象" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.target_label || '—' }}</template>
      </el-table-column>
      <el-table-column label="IP 地址" width="150">
        <template #default="{ row }">{{ row.ip_address || '—' }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      class="pager"
      @current-change="load"
      @size-change="search"
    />
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.export {
  margin-left: auto;
}
.filter {
  width: 200px;
}
.filters :deep(.el-date-editor.dates) {
  flex: 0 0 auto;
  width: 300px;
}
.table {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.sub {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.pager {
  justify-content: flex-end;
}
</style>
