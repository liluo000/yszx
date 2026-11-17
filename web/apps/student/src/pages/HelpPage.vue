<script setup lang="ts">
// 帮助中心（需求 5.11，S-HLP-03）：常见问题来自后台维护，客服联系方式取自系统配置
import { computed, onMounted, ref } from 'vue'
import type { StudentHelpView } from '@yunshu/api'
import { errorText, helpApi } from '@/api'

const data = ref<StudentHelpView | null>(null)
const loading = ref(false)
const loadError = ref('')

const faqs = computed(() => data.value?.faqs ?? [])
const contact = computed(() => data.value?.contact ?? null)
const hasContact = computed(() => {
  const item = contact.value
  return Boolean(item && (item.wechat_qrcode_url || item.email || item.phone))
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    data.value = await helpApi.load()
  } catch (e) {
    loadError.value = errorText(e, '帮助内容加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="help">
    <header class="page-head">
      <div>
        <h1>帮助中心</h1>
        <p>常见问题与客服联系方式；封禁提示、设备检测失败等场景也使用这里的联系方式。</p>
      </div>
    </header>

    <div v-if="loadError" class="alert-error" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" class="btn btn-sm" @click="load">重新加载</button>
    </div>
    <div v-if="loading" class="skeleton">帮助内容加载中…</div>

    <div v-if="!loading && data" class="help-grid">
      <section class="card block">
        <h2>常见问题</h2>
        <div v-if="faqs.length">
          <details v-for="faq in faqs" :key="faq.id" class="faq">
            <summary>{{ faq.question }}</summary>
            <p>{{ faq.answer }}</p>
          </details>
        </div>
        <p v-else class="muted">暂无常见问题，可通过下方联系方式联系我们。</p>
      </section>

      <section class="card block">
        <h2>联系我们</h2>
        <div v-if="hasContact && contact" class="contact">
          <figure v-if="contact.wechat_qrcode_url" class="qrcode">
            <img :src="contact.wechat_qrcode_url" alt="客服微信二维码" />
            <figcaption class="muted">扫码添加客服微信</figcaption>
          </figure>
          <dl v-if="contact.email || contact.phone" class="contact-text">
            <div v-if="contact.email">
              <dt>邮箱</dt>
              <dd><a :href="`mailto:${contact.email}`">{{ contact.email }}</a></dd>
            </div>
            <div v-if="contact.phone">
              <dt>电话</dt>
              <dd><a :href="`tel:${contact.phone}`">{{ contact.phone }}</a></dd>
            </div>
          </dl>
        </div>
        <p v-else class="muted">客服联系方式尚未配置。</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.help {
  display: flex;
  flex-direction: column;
}
/* 常见问题占主栏，联系方式收窄成侧栏（原型 help-grid） */
.help-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: var(--space-md);
  align-items: start;
}
.alert-error .btn {
  margin-left: auto;
}
.block {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.block h2 {
  font-size: var(--text-lg);
  font-weight: 600;
}
.qrcode {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
  margin: 0;
}
.qrcode img {
  width: 140px;
  height: 140px;
  object-fit: contain;
  border: var(--rule-hair) solid var(--color-rule);
  border-radius: var(--radius-input);
  background: var(--color-cotton);
}
.contact-text {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin: 0;
}
.contact-text dt {
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.contact-text dd {
  margin: var(--space-3xs) 0 0;
  font-size: var(--text-sm);
}

@media (max-width: 60rem) {
  .help-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
