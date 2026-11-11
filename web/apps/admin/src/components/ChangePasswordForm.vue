<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ApiError } from '@yunshu/api'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, isPasswordLengthValid } from '@yunshu/shared'
import { useAuthStore } from '@/stores/auth'
import PasswordStrength from './PasswordStrength.vue'

const emit = defineEmits<{ success: [] }>()

const auth = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const lengthRule = {
  validator: (_: unknown, value: string, callback: (e?: Error) => void) =>
    isPasswordLengthValid(value)
      ? callback()
      : callback(new Error(`密码长度为 ${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH} 位`)),
  trigger: 'blur',
}
const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, lengthRule],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_: unknown, value: string, callback: (e?: Error) => void) =>
        value === form.newPassword ? callback() : callback(new Error('两次输入的新密码不一致')),
      trigger: 'blur',
    },
  ],
}

async function submit() {
  errorMessage.value = ''
  if (!(await formRef.value?.validate().catch(() => false))) return
  submitting.value = true
  try {
    await auth.changePassword(form.oldPassword, form.newPassword)
    emit('success')
  } catch (e) {
    errorMessage.value = e instanceof ApiError ? e.message : '修改失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
    <el-form-item label="原密码" prop="oldPassword">
      <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
    </el-form-item>
    <el-form-item label="新密码" prop="newPassword">
      <el-input
        v-model="form.newPassword"
        type="password"
        show-password
        autocomplete="new-password"
        :maxlength="PASSWORD_MAX_LENGTH"
        :placeholder="`${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH} 位，不限字符类型`"
      />
      <PasswordStrength :password="form.newPassword" />
    </el-form-item>
    <el-form-item label="确认新密码" prop="confirmPassword">
      <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
    </el-form-item>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="error" />
    <el-button type="primary" native-type="submit" :loading="submitting" class="submit">确认修改</el-button>
    <p class="hint">修改后所有设备会退出登录，需要用新密码重新登录。</p>
  </el-form>
</template>

<style scoped>
.error {
  margin-bottom: var(--space-md);
}
.submit {
  width: 100%;
}
.hint {
  margin: var(--space-sm) 0 0;
  font-size: var(--text-xs);
  color: var(--color-muted);
}
</style>
