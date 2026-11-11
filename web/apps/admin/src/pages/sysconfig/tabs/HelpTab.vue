<script setup lang="ts">
// 帮助中心与联系方式（需求 7.7，A-CFG-13~18）
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { FaqView, HelpContact } from '@yunshu/api'
import { configApi } from '@/api'
import { errorText } from '@/utils/format'

const props = defineProps<{ contact: HelpContact | undefined }>()
const emit = defineEmits<{ saved: [key: string, value: unknown] }>()

/* ---------------- 常见问题 ---------------- */
const faqs = ref<FaqView[]>([])
const faqLoading = ref(false)
const faqError = ref('')
const sorting = ref(false)

async function loadFaqs() {
  faqLoading.value = true
  faqError.value = ''
  try {
    faqs.value = (await configApi.faqs()).items
  } catch (e) {
    faqError.value = errorText(e, '加载失败，请稍后重试')
  } finally {
    faqLoading.value = false
  }
}

onMounted(loadFaqs)

/** 上移、下移：交换位置后按 10、20、30… 重新编号 */
async function move(index: number, offset: -1 | 1) {
  const list = [...faqs.value]
  const target = index + offset
  ;[list[index], list[target]] = [list[target], list[index]]
  sorting.value = true
  try {
    await configApi.sortFaqs(list.map((f, i) => ({ id: f.id, sort_no: (i + 1) * 10 })))
    await loadFaqs()
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    sorting.value = false
  }
}

async function removeFaq(faq: FaqView) {
  const confirmed = await ElMessageBox.confirm(`确定删除「${faq.question}」吗？`, '删除常见问题', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  try {
    await configApi.deleteFaq(faq.id)
    ElMessage.success('已删除')
    loadFaqs()
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}

const faqVisible = ref(false)
const editingFaq = ref<FaqView | null>(null)
const faqFormRef = ref<FormInstance>()
const faqSaving = ref(false)
const faqForm = reactive({ question: '', answer: '', sort_no: 10 })
const faqRules: FormRules = {
  question: [
    { required: true, message: '请填写问题', trigger: 'blur' },
    { max: 200, message: '问题最多 200 个字符', trigger: 'blur' },
  ],
  answer: [
    { required: true, message: '请填写回答', trigger: 'blur' },
    { max: 10000, message: '回答最多 10000 个字符', trigger: 'blur' },
  ],
  sort_no: [{ required: true, message: '请填写排序号', trigger: 'blur' }],
}

function openFaq(faq?: FaqView) {
  editingFaq.value = faq ?? null
  const nextSort = (faqs.value.at(-1)?.sort_no ?? 0) + 10
  Object.assign(
    faqForm,
    faq
      ? { question: faq.question, answer: faq.answer, sort_no: faq.sort_no }
      : { question: '', answer: '', sort_no: nextSort },
  )
  faqVisible.value = true
}

async function saveFaq() {
  if (!(await faqFormRef.value?.validate().catch(() => false))) return
  faqSaving.value = true
  const body = { question: faqForm.question.trim(), answer: faqForm.answer.trim(), sort_no: faqForm.sort_no }
  try {
    if (editingFaq.value) await configApi.updateFaq(editingFaq.value.id, body)
    else await configApi.createFaq(body)
    ElMessage.success('已保存')
    faqVisible.value = false
    loadFaqs()
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    faqSaving.value = false
  }
}

/* ---------------- 客服联系方式 ---------------- */
const MAX_IMAGE_BYTES = 2 * 1024 * 1024
const contactFormRef = ref<FormInstance>()
const contactForm = reactive({ wechat_qrcode_url: '', email: '', phone: '' })
const contactSaving = ref(false)
const uploading = ref(false)
const imageInput = ref<HTMLInputElement>()
const contactRules: FormRules = {
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 100, message: '邮箱最多 100 个字符', trigger: 'blur' },
  ],
  phone: [{ max: 30, message: '电话最多 30 个字符', trigger: 'blur' }],
}

watch(
  () => props.contact,
  (value) => {
    contactForm.wechat_qrcode_url = value?.wechat_qrcode_url ?? ''
    contactForm.email = value?.email ?? ''
    contactForm.phone = value?.phone ?? ''
  },
  { immediate: true },
)

async function onImageChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > MAX_IMAGE_BYTES) {
    ElMessage.error('请上传不超过 2MB 的 JPG 或 PNG 图片')
    return
  }
  uploading.value = true
  try {
    contactForm.wechat_qrcode_url = (await configApi.uploadImage(file)).url
    ElMessage.success('图片已上传，保存后生效')
  } catch (e) {
    ElMessage.error(errorText(e, '上传失败，请稍后重试'))
  } finally {
    uploading.value = false
  }
}

async function saveContact() {
  if (!(await contactFormRef.value?.validate().catch(() => false))) return
  const value: HelpContact = {
    wechat_qrcode_url: contactForm.wechat_qrcode_url || null,
    email: contactForm.email.trim() || null,
    phone: contactForm.phone.trim() || null,
  }
  contactSaving.value = true
  try {
    await configApi.updateConfig('help.contact', value)
    emit('saved', 'help.contact', value)
    ElMessage.success('已保存，已记入操作日志')
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    contactSaving.value = false
  }
}
</script>

<template>
  <section class="block">
    <div class="block-head">
      <h4>常见问题</h4>
      <el-button size="small" type="primary" plain @click="openFaq()">新增问题</el-button>
    </div>
    <el-alert v-if="faqError" type="error" :title="faqError" :closable="false" show-icon>
      <el-button size="small" @click="loadFaqs">重试</el-button>
    </el-alert>
    <div v-loading="faqLoading || sorting" class="faqs">
      <article v-for="(faq, index) in faqs" :key="faq.id" class="faq">
        <div class="faq-head">
          <b>{{ faq.question }}</b>
          <span class="faq-ops">
            <el-button link type="primary" @click="openFaq(faq)">编辑</el-button>
            <el-button link :disabled="index === 0" @click="move(index, -1)">上移</el-button>
            <el-button link :disabled="index === faqs.length - 1" @click="move(index, 1)">下移</el-button>
            <el-button link type="danger" @click="removeFaq(faq)">删除</el-button>
          </span>
        </div>
        <p class="answer">{{ faq.answer }}</p>
      </article>
      <el-empty v-if="!faqLoading && !faqs.length && !faqError" description="还没有常见问题" :image-size="64" />
    </div>
  </section>

  <section class="block">
    <div class="block-head">
      <h4>客服联系方式（均为选填）</h4>
    </div>
    <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-position="top" class="contact">
      <el-form-item label="客服微信二维码">
        <div class="qr">
          <el-image
            v-if="contactForm.wechat_qrcode_url"
            :src="contactForm.wechat_qrcode_url"
            fit="contain"
            class="qr-image"
            :preview-src-list="[contactForm.wechat_qrcode_url]"
          />
          <div class="qr-ops">
            <el-button :loading="uploading" @click="imageInput?.click()">
              {{ contactForm.wechat_qrcode_url ? '更换图片' : '上传图片' }}
            </el-button>
            <el-button
              v-if="contactForm.wechat_qrcode_url"
              link
              type="danger"
              @click="contactForm.wechat_qrcode_url = ''"
              >移除</el-button
            >
            <span class="tip">JPG 或 PNG，不超过 2MB</span>
          </div>
          <input ref="imageInput" type="file" accept="image/jpeg,image/png" hidden @change="onImageChange" />
        </div>
      </el-form-item>
      <div class="two-col">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="contactForm.email" maxlength="100" placeholder="如 service@example.com" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="contactForm.phone" maxlength="30" placeholder="如 400-000-0000" />
        </el-form-item>
      </div>
    </el-form>
    <p class="tip">联系方式在帮助中心、封禁提示、语音面试设备检测失败等处统一引用。</p>
    <el-button type="primary" :loading="contactSaving" class="save" @click="saveContact">保存联系方式</el-button>
  </section>

  <el-dialog v-model="faqVisible" :title="editingFaq ? '编辑常见问题' : '新增常见问题'" width="560px" destroy-on-close>
    <el-form ref="faqFormRef" :model="faqForm" :rules="faqRules" label-position="top">
      <el-form-item label="问题" prop="question">
        <el-input v-model="faqForm.question" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="回答" prop="answer">
        <el-input
          v-model="faqForm.answer"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 12 }"
          maxlength="10000"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="排序号（越小越靠前）" prop="sort_no">
        <el-input-number v-model="faqForm.sort_no" :step="10" step-strictly controls-position="right" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="faqVisible = false">取消</el-button>
      <el-button type="primary" :loading="faqSaving" @click="saveFaq">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.block + .block {
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-rule);
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
}
.block-head h4 {
  margin: 0;
  font-size: var(--text-sm);
}
.faqs {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  min-height: 60px;
}
.faq {
  padding: var(--space-sm) var(--space-md);
  background: var(--color-paper);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
}
.faq-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xs);
}
.faq-ops .el-button + .el-button {
  margin-left: var(--space-xs);
}
.answer {
  margin: var(--space-2xs) 0 0;
  color: var(--color-ink-2);
  white-space: pre-line;
  line-height: 1.6;
}
.qr {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.qr-image {
  width: 96px;
  height: 96px;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius);
  background: var(--color-cotton);
}
.qr-ops {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}
.two-col {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: var(--space-md);
}
.tip {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--text-xs);
}
.save {
  margin-top: var(--space-md);
}
</style>
