<template>
  <el-card class="auth-card">
    <h2 class="auth-title">注册校园集市</h2>
    <p class="auth-subtitle">{{ t('auth.registerSubtitle') }}</p>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="onSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          placeholder="4-20位，登录用（同时作为昵称）"
          clearable
          :prefix-icon="User"
        />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="form.email"
          placeholder="用于找回密码（注册时不验证）"
          clearable
          :prefix-icon="Message"
        />
      </el-form-item>
      <el-form-item label="手机号（可选）" prop="phone">
        <el-input
          v-model="form.phone"
          placeholder="可不填"
          clearable
          :prefix-icon="Iphone"
        />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="6-20位密码"
          show-password
          :prefix-icon="Lock"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="请再次输入密码"
          show-password
          :prefix-icon="Lock"
          @keyup.enter="onSubmit"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" class="auth-submit" :loading="loading" @click="onSubmit">
          注册
        </el-button>
      </el-form-item>
    </el-form>
    <div class="auth-footer">
      已有账号？
      <el-link type="primary" @click="$router.push('/login')">去登录</el-link>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Iphone, Message } from '@element-plus/icons-vue'
import { register } from '@/api/user'
import { useI18n } from '@/i18n'

const router = useRouter()
const { t } = useI18n()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const validateConfirm = (_rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const validatePhone = (_rule, value, callback) => {
  if (!value) {
    callback()
    return
  }
  if (!/^1[3-9]\d{9}$/.test(value)) callback(new Error('手机号格式不正确'))
  else callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度 4-20 位', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function onSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await register({
      username: form.username.trim(),
      password: form.password,
      email: form.email.trim(),
      phone: form.phone.trim() || undefined
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    // axios interceptor
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
  color: var(--oa-text);
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
  font-size: 14px;
  color: var(--oa-text-secondary);
  margin-top: 16px;
}
</style>
