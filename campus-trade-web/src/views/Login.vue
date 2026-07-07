<template>
  <el-card class="auth-card">
    <h2 class="auth-title">登录校园淘</h2>
    <p class="auth-subtitle">Sign in to your account</p>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="onSubmit"
    >
      <el-form-item label="Username" prop="username">
        <el-input
          v-model="form.username"
          placeholder="Enter username"
          clearable
          :prefix-icon="User"
        />
      </el-form-item>
      <el-form-item label="Password" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="Enter password"
          show-password
          :prefix-icon="Lock"
          @keyup.enter="onSubmit"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" class="auth-submit" :loading="loading" @click="onSubmit">
          Continue
        </el-button>
      </el-form-item>
    </el-form>
    <div class="auth-footer">
      Don't have an account?
      <el-link type="primary" @click="$router.push('/register')">Sign up</el-link>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/user'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
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
    const res = await login({
      username: form.username,
      password: form.password
    })
    userStore.setLoginInfo(res.data)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' ? redirect : '/')
  } catch {
    // 错误提示已由 axios 拦截器统一处理
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
