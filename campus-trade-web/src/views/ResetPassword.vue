<template>
  <el-card class="auth-card">
    <h2 class="auth-title">设置新密码</h2>
    <p class="auth-subtitle">请输入新密码以完成找回</p>
    <el-alert
      v-if="!token"
      type="error"
      :closable="false"
      title="重置链接无效，请重新申请忘记密码"
      style="margin-bottom: 16px"
    />
    <el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="onSubmit"
    >
      <el-form-item label="新密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="6-20位密码"
          show-password
          :prefix-icon="Lock"
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="请再次输入"
          show-password
          :prefix-icon="Lock"
          @keyup.enter="onSubmit"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" class="auth-submit" :loading="loading" @click="onSubmit">
          确认重置
        </el-button>
      </el-form-item>
    </el-form>
    <div class="auth-footer">
      <el-link type="primary" @click="$router.push('/login')">返回登录</el-link>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { resetPassword } from '@/api/user'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const token = computed(() => {
  const t = route.query.token
  return typeof t === 'string' ? t : ''
})

const form = reactive({ password: '', confirmPassword: '' })
const validateConfirm = (_rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}
const rules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function onSubmit() {
  if (!token.value || !formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await resetPassword({ token: token.value, newPassword: form.password })
    ElMessage.success('密码已重置，请登录')
    router.push('/login')
  } catch {
    // interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-card {
  width: 100%;
  max-width: 400px;
  padding: 8px;
}
.auth-card :deep(.el-card__body) {
  padding: 32px 28px;
}
.auth-title {
  font-size: 22px;
  font-weight: 500;
  margin-bottom: 4px;
}
.auth-subtitle {
  font-size: 14px;
  color: var(--oa-text-secondary);
  margin-bottom: 28px;
}
.auth-submit {
  width: 100%;
  height: 40px;
}
.auth-footer {
  text-align: center;
  margin-top: 16px;
}
</style>
