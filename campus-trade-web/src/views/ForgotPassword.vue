<template>
  <el-card class="auth-card">
    <h2 class="auth-title">忘记密码</h2>
    <p class="auth-subtitle">输入用户名，我们将向绑定邮箱发送重置链接</p>
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
          placeholder="注册时的用户名"
          clearable
          :prefix-icon="User"
          @keyup.enter="onSubmit"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" class="auth-submit" :loading="loading" @click="onSubmit">
          发送重置邮件
        </el-button>
      </el-form-item>
    </el-form>
    <div class="auth-footer">
      <el-link type="primary" @click="$router.push('/login')">返回登录</el-link>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { forgotPassword } from '@/api/user'

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
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
    const res = await forgotPassword({ username: form.username.trim() })
    ElMessage.success(res.message || '如果该账号已绑定邮箱，重置链接已发送')
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
